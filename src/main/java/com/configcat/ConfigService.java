package com.configcat;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

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
    private final AtomicBoolean initialized  = new AtomicBoolean(false);
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
        this.cacheKey = new String(Hex.encodeHex(DigestUtils.sha1(String.format(CACHE_BASE, sdkKey))));
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
                        hooks.invokeOnClientReady();
                        String message = ConfigCatLogMessages.getAutoPollMaxInitWaitTimeReached(autoPollingMode.getMaxInitWaitTimeSeconds());
                        logger.warn(4200, message);
                        completeRunningTask(Result.error(message, cachedEntry));
                    }
                } finally {
                    lock.unlock();
                }
            }, autoPollingMode.getMaxInitWaitTimeSeconds(), TimeUnit.SECONDS);
        } else {
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
            return fetchIfOlder(Constants.DISTANT_PAST, initialized.get()) // If we are initialized, we prefer the cached results
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
            if (cachedEntry.getFetchTime() > threshold) {
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
            setInitialized();
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
            hooks.invokeOnClientReady();
        }
    }

    private void startPoll(AutoPollingMode mode) {
        long ageThreshold = (long) ((mode.getAutoPollRateInSeconds() * 1000L) * 0.7);
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
