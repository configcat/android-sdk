package com.configcat;

import java9.util.concurrent.CompletableFuture;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

class SettingResult {
    private final Map<String, Setting> settings;
    private final long fetchTime;

    public SettingResult(Map<String, Setting> settings, long fetchTime) {
        this.settings = settings;
        this.fetchTime = fetchTime;
    }

    public Map<String, Setting> settings() {
        return settings;
    }

    public long fetchTime() {
        return fetchTime;
    }

    boolean isEmpty() {
        return EMPTY.equals(this);
    }

    public static final SettingResult EMPTY = new SettingResult(new HashMap<>(), Constants.DISTANT_PAST);
}

class ConfigService implements Closeable {
    private static final String CACHE_BASE = "%s_" + Constants.CONFIG_JSON_NAME + "_" + Constants.SERIALIZATION_FORMAT_VERSION;
    private ScheduledExecutorService initScheduler;
    private ScheduledExecutorService pollScheduler;
    private String cachedEntryString = "";
    private Entry cachedEntry = Entry.EMPTY;
    private CompletableFuture<Result<Entry>> runningTask;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean offline;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final String cacheKey;
    private final PollingMode mode;
    private final ConfigCache cache;
    private final ConfigCatLogger logger;
    private final ConfigFetcher fetcher;
    private final ConfigCatHooks hooks;
    private final ReentrantLock lock = new ReentrantLock(true);

    public ConfigService(String sdkKey,
                         PollingMode mode,
                         ConfigCache cache,
                         ConfigCatLogger logger,
                         ConfigFetcher fetcher,
                         ConfigCatHooks hooks,
                         boolean offline) {
        this.cacheKey = Utils.sha1(String.format(CACHE_BASE, sdkKey));
        this.mode = mode;
        this.cache = cache;
        this.logger = logger;
        this.fetcher = fetcher;
        this.hooks = hooks;
        this.offline = new AtomicBoolean(offline);

        if (mode instanceof AutoPollingMode && !offline) {
            AutoPollingMode autoPollingMode = (AutoPollingMode) mode;

            startPoll(autoPollingMode);

            initScheduler = Executors.newSingleThreadScheduledExecutor();
            initScheduler.schedule(() -> {
                lock.lock();
                try {
                    if (initialized.compareAndSet(false, true)) {
                        hooks.invokeOnClientReady(determineCacheState());
                        FormattableLogMessage message = ConfigCatLogMessages.getAutoPollMaxInitWaitTimeReached(autoPollingMode.getMaxInitWaitTimeSeconds());
                        logger.warn(4200, message);
                        completeRunningTask(Result.error(message, cachedEntry));
                    }
                } finally {
                    lock.unlock();
                }
            }, autoPollingMode.getMaxInitWaitTimeSeconds(), TimeUnit.SECONDS);
        } else {
            // Sync up with cache before reporting ready state
            cachedEntry = readCache();
            setInitialized();
        }
    }

    public CompletableFuture<SettingResult> getSettings() {
        if (mode instanceof LazyLoadingMode) {
            LazyLoadingMode lazyLoadingMode = (LazyLoadingMode) mode;
            return fetchIfOlder(System.currentTimeMillis() - (lazyLoadingMode.getCacheRefreshIntervalInSeconds() * 1000L), false)
                    .thenApply(entryResult -> !entryResult.value().isEmpty()
                            ? new SettingResult(entryResult.value().getConfig().getEntries(), entryResult.value().getFetchTime())
                            : SettingResult.EMPTY);
        } else {
            long threshold = Constants.DISTANT_PAST;
            if (!initialized.get() && mode instanceof AutoPollingMode) {
                AutoPollingMode autoPollingMode = (AutoPollingMode) mode;
                threshold = System.currentTimeMillis() - (autoPollingMode.getAutoPollRateInSeconds() * 1000L);
            }
            return fetchIfOlder(threshold, initialized.get()) // If we are initialized, we prefer the cached results
                    .thenApply(entryResult -> !entryResult.value().isEmpty()
                            ? new SettingResult(entryResult.value().getConfig().getEntries(), entryResult.value().getFetchTime())
                            : SettingResult.EMPTY);
        }
    }

    public CompletableFuture<RefreshResult> refresh() {
        if (offline.get()) {
            String offlineWarning = ConfigCatLogMessages.CONFIG_SERVICE_CANNOT_INITIATE_HTTP_CALLS_WARN;
            logger.warn(3200, offlineWarning);
            return CompletableFuture.completedFuture(new RefreshResult(false, offlineWarning));
        }

        return fetchIfOlder(Constants.DISTANT_FUTURE, false)
                .thenApply(entryResult -> new RefreshResult(entryResult.error() == null, entryResult.error()));
    }

    public void setOnline() {
        lock.lock();
        try {
            if (!offline.compareAndSet(true, false)) return;
            if (mode instanceof AutoPollingMode) {
                startPoll((AutoPollingMode) mode);
            }
            logger.info(5200, ConfigCatLogMessages.getConfigServiceStatusChanged("ONLINE"));
        } finally {
            lock.unlock();
        }
    }

    public void setOffline() {
        lock.lock();
        try {
            if (!offline.compareAndSet(false, true)) return;
            if (pollScheduler != null) pollScheduler.shutdown();
            if (initScheduler != null) initScheduler.shutdown();
            logger.info(5200, ConfigCatLogMessages.getConfigServiceStatusChanged("OFFLINE"));
        } finally {
            lock.unlock();
        }
    }

    public boolean isOffline() {
        return offline.get();
    }

    private CompletableFuture<Result<Entry>> fetchIfOlder(long threshold, boolean preferCached) {
        lock.lock();
        try {
            Entry fromCache = readCache();
            // Sync up with the cache and use it when it's not expired.
            if (!fromCache.isEmpty() && !fromCache.getETag().equals(cachedEntry.getETag())) {
                hooks.invokeOnConfigChanged(fromCache.getConfig().getEntries());
                cachedEntry = fromCache;
            }
            // Cache isn't expired
            if (!cachedEntry.isExpired(threshold)) {
                setInitialized();
                return CompletableFuture.completedFuture(Result.success(cachedEntry));
            }
            // If we are in offline mode or the caller prefers cached values, do not initiate fetch.
            if (offline.get() || preferCached) {
                return CompletableFuture.completedFuture(Result.success(cachedEntry));
            }

            if (runningTask == null) {
                // No fetch is running, initiate a new one.
                runningTask = new CompletableFuture<>();
                fetcher.fetchAsync(cachedEntry.getETag())
                        .thenAccept(this::processResponse);
            }

            return runningTask;

        } finally {
            lock.unlock();
        }
    }

    private void processResponse(FetchResponse response) {
        lock.lock();
        try {
            if (response.isFetched()) {
                Entry entry = response.entry();
                cachedEntry = entry;
                writeCache(entry);
                completeRunningTask(Result.success(entry));
                hooks.invokeOnConfigChanged(entry.getConfig().getEntries());
            } else {
                if (response.isFetchTimeUpdatable()) {
                    cachedEntry = cachedEntry.withFetchTime(System.currentTimeMillis());
                    writeCache(cachedEntry);
                }
                completeRunningTask(response.isFailed()
                        ? Result.error(response.error(), cachedEntry)
                        : Result.success(cachedEntry));
            }
            setInitialized();
        } finally {
            lock.unlock();
        }
    }

    private void completeRunningTask(Result<Entry> result) {
        runningTask.complete(result);
        runningTask = null;
    }

    private void setInitialized() {
        if (initialized.compareAndSet(false, true)) {
            hooks.invokeOnClientReady(determineCacheState());
        }
    }

    private void startPoll(AutoPollingMode mode) {
        long ageThreshold = (mode.getAutoPollRateInSeconds() * 1000L) - 500;
        pollScheduler = Executors.newSingleThreadScheduledExecutor();
        pollScheduler.scheduleAtFixedRate(() -> this.fetchIfOlder(System.currentTimeMillis() - ageThreshold, false),
                0, mode.getAutoPollRateInSeconds(), TimeUnit.SECONDS);
    }

    private void writeCache(Entry entry) {
        try {
            String configToCache = entry.serialize();
            cachedEntryString = configToCache;
            cache.write(cacheKey, configToCache);
        } catch (Exception e) {
            logger.error(2201, ConfigCatLogMessages.CONFIG_SERVICE_CACHE_WRITE_ERROR, e);
        }
    }

    private Entry readCache() {
        try {
            String cachedConfigJson = cache.read(cacheKey);
            if (cachedConfigJson != null && cachedConfigJson.equals(cachedEntryString)) {
                return Entry.EMPTY;
            }
            cachedEntryString = cachedConfigJson;
            Entry deserialized = Entry.fromString(cachedConfigJson);
            return deserialized == null || deserialized.getConfig() == null ? Entry.EMPTY : deserialized;
        } catch (Exception e) {
            this.logger.error(2200, ConfigCatLogMessages.CONFIG_SERVICE_CACHE_READ_ERROR, e);
            return Entry.EMPTY;
        }
    }

    private ClientCacheState determineCacheState(){
        if(cachedEntry.isEmpty()) {
            return ClientCacheState.NO_FLAG_DATA;
        }
        if(mode instanceof ManualPollingMode) {
            return ClientCacheState.HAS_CACHED_FLAG_DATA_ONLY;
        } else if(mode instanceof LazyLoadingMode) {
            if(cachedEntry.isExpired(System.currentTimeMillis() - (((LazyLoadingMode)mode).getCacheRefreshIntervalInSeconds() * 1000L))) {
                return ClientCacheState.HAS_CACHED_FLAG_DATA_ONLY;
            }
        } else if(mode instanceof AutoPollingMode) {
            if(cachedEntry.isExpired(System.currentTimeMillis() - (((AutoPollingMode)mode).getAutoPollRateInSeconds() * 1000L))) {
                return ClientCacheState.HAS_CACHED_FLAG_DATA_ONLY;
            }
        }
        return ClientCacheState.HAS_UP_TO_DATE_FLAG_DATA;
    }

    @Override
    public void close() throws IOException {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        if (pollScheduler != null) pollScheduler.shutdown();
        if (initScheduler != null) initScheduler.shutdown();
        fetcher.close();
    }
}
