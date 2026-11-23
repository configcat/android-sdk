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
import java.util.concurrent.atomic.AtomicReference;
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
    private final AtomicReference<Entry> cachedEntry = new AtomicReference<>(Entry.EMPTY);
    private CompletableFuture<Result<Entry, RefreshErrorCode>> runningTask;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean userIndicatedOffline;
    private final AtomicBoolean inForegroundAndHasNetwork;
    private final AtomicBoolean offline;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final String cacheKey;
    private final StateMonitor stateMonitor;
    private final PollingMode mode;
    private final ConfigCache cache;
    private final ConfigCatLogger logger;
    private final ConfigFetcher fetcher;
    private final ConfigCatHooks hooks;
    private final ReentrantLock lock = new ReentrantLock(true);

    public ConfigService(String sdkKey,
                         StateMonitor stateMonitor,
                         PollingMode mode,
                         ConfigCache cache,
                         ConfigCatLogger logger,
                         ConfigFetcher fetcher,
                         ConfigCatHooks hooks,
                         boolean userIndicatedOffline) {
        this.cacheKey = Utils.sha1(String.format(CACHE_BASE, sdkKey));
        this.stateMonitor = stateMonitor;
        this.mode = mode;
        this.cache = cache;
        this.logger = logger;
        this.fetcher = fetcher;
        this.hooks = hooks;
        this.userIndicatedOffline = new AtomicBoolean(userIndicatedOffline);
        this.inForegroundAndHasNetwork = new AtomicBoolean(stateMonitor == null || stateMonitor.isNetworkAvailable());
        this.offline = new AtomicBoolean(isOffline());

        if (stateMonitor != null) {
            stateMonitor.addStateChangeListener(state -> {
                inForegroundAndHasNetwork.set(state);
                switchStateIfNeeded();
            });
        }

        if (mode instanceof AutoPollingMode) {
            AutoPollingMode autoPollingMode = (AutoPollingMode) mode;

            startPoll(autoPollingMode);

            initScheduler = Executors.newSingleThreadScheduledExecutor();
            initScheduler.schedule(() -> {
                lock.lock();
                try {
                    if (initialized.compareAndSet(false, true)) {
                        hooks.invokeOnClientReady(determineCacheState(cachedEntry.get()));
                        FormattableLogMessage message = ConfigCatLogMessages.getAutoPollMaxInitWaitTimeReached(autoPollingMode.getMaxInitWaitTimeSeconds());
                        logger.warn(4200, message);
                        completeRunningTask(Result.error(message, cachedEntry.get(), RefreshErrorCode.CLIENT_INIT_TIMED_OUT, null));
                    }
                } finally {
                    lock.unlock();
                }
            }, autoPollingMode.getMaxInitWaitTimeSeconds(), TimeUnit.SECONDS);
        } else {
            // Sync up with cache before reporting ready state
            Entry fromCache = readCache();
            if (!fromCache.isEmpty() && !fromCache.getETag().equals(cachedEntry.get().getETag())) {
                cachedEntry.set(fromCache);
                hooks.invokeOnConfigChanged(fromCache.getConfig().getEntries());
            }
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
        if (isOffline()) {
            String offlineWarning = ConfigCatLogMessages.CONFIG_SERVICE_CANNOT_INITIATE_HTTP_CALLS_WARN;
            logger.warn(3200, offlineWarning);
            return CompletableFuture.completedFuture(new RefreshResult(false, offlineWarning, RefreshErrorCode.OFFLINE_CLIENT, null));
        }

        return fetchIfOlder(Constants.DISTANT_FUTURE, false)
                .thenApply(entryResult -> {
                    boolean isSucceed = entryResult.error() == null;
                    return new RefreshResult(isSucceed, entryResult.error(),
                            isSucceed ? RefreshErrorCode.NONE : entryResult.errorCode(), entryResult.errorException());
                });
    }

    public void setOnline() {
        if (!userIndicatedOffline.compareAndSet(true, false)) return;
        switchStateIfNeeded();
    }

    public void setOffline() {
        if (!userIndicatedOffline.compareAndSet(false, true)) return;
        switchStateIfNeeded();
    }

    public boolean isOffline() {
        return userIndicatedOffline.get() || !inForegroundAndHasNetwork.get();
    }

    private void switchStateIfNeeded() {
        boolean isOffline = isOffline();
        if (isOffline && offline.compareAndSet(false, true)) {
            logger.info(5200, ConfigCatLogMessages.getConfigServiceStatusChanged("OFFLINE"));
        }
        if (!isOffline && offline.compareAndSet(true, false)) {
            if (mode instanceof AutoPollingMode) {
                startPoll((AutoPollingMode) mode);
            }
            logger.info(5200, ConfigCatLogMessages.getConfigServiceStatusChanged("ONLINE"));
        }
    }

    private CompletableFuture<Result<Entry, RefreshErrorCode>> fetchIfOlder(long threshold, boolean preferCached) {
        Entry fromCache = readCache();
        Entry current = cachedEntry.get();
        // Sync up with the cache and use it when it's not expired.
        if (!fromCache.isEmpty() &&
                (current.isEmpty() || (!fromCache.getETag().equals(current.getETag()) && fromCache.getFetchTime() > current.getFetchTime())) &&
                cachedEntry.compareAndSet(current, fromCache)) {
            hooks.invokeOnConfigChanged(fromCache.getConfig().getEntries());
        }
        current = cachedEntry.get();
        // Cache isn't expired
        if (!current.isExpired(threshold)) {
            setInitialized();
            return CompletableFuture.completedFuture(Result.success(current, RefreshErrorCode.NONE));
        }
        // If we are in offline mode or the caller prefers cached values, do not initiate fetch.
        if (isOffline() || preferCached) {
            setInitialized();
            return CompletableFuture.completedFuture(Result.success(current, RefreshErrorCode.NONE));
        }

        lock.lock();
        try {
            if (runningTask == null) {
                // No fetch is running, initiate a new one.
                runningTask = new CompletableFuture<>();
                Entry finalCurrent = current;
                fetcher.fetchAsync(finalCurrent.getETag())
                        .thenAccept(response -> this.processResponse(response, finalCurrent));
            }

            return runningTask;

        } finally {
            lock.unlock();
        }
    }

    private void processResponse(FetchResponse response, Entry current) {
        lock.lock();
        try {
            if (response.isFetched()) {
                Entry entry = response.entry();
                cachedEntry.set(entry);
                writeCache(entry);
                completeRunningTask(Result.success(entry, RefreshErrorCode.NONE));
                hooks.invokeOnConfigChanged(entry.getConfig().getEntries());
            } else {
                if (response.isFetchTimeUpdatable()) {
                    Entry updateEntry = current.withFetchTime(System.currentTimeMillis());
                    if (cachedEntry.compareAndSet(current, updateEntry)) {
                        writeCache(updateEntry);
                    }
                }
                completeRunningTask(response.isFailed()
                        ? Result.error(response.error(), cachedEntry.get(), response.errorCode(), response.errorException())
                        : Result.success(cachedEntry.get(), RefreshErrorCode.NONE));
            }
            setInitialized();
        } finally {
            lock.unlock();
        }
    }

    private void completeRunningTask(Result<Entry, RefreshErrorCode> result) {
        runningTask.complete(result);
        runningTask = null;
    }

    private void setInitialized() {
        if (initialized.compareAndSet(false, true)) {
            hooks.invokeOnClientReady(determineCacheState(cachedEntry.get()));
        }
    }

    private void startPoll(AutoPollingMode mode) {
        lock.lock();
        try {
            long ageThreshold = (mode.getAutoPollRateInSeconds() * 1000L) - 500;
            if (pollScheduler != null) pollScheduler.shutdown();
            pollScheduler = Executors.newSingleThreadScheduledExecutor();
            pollScheduler.scheduleWithFixedDelay(() -> this.fetchIfOlder(System.currentTimeMillis() - ageThreshold, false),
                    0, mode.getAutoPollRateInSeconds(), TimeUnit.SECONDS);
        } finally {
            lock.unlock();
        }
    }

    private void writeCache(Entry entry) {
        try {
            cache.write(cacheKey, entry.getCacheString());
        } catch (Exception e) {
            logger.error(2201, ConfigCatLogMessages.CONFIG_SERVICE_CACHE_WRITE_ERROR, e);
        }
    }

    private Entry readCache() {
        try {
            String cachedConfigJson = cache.read(cacheKey);
            if (cachedConfigJson != null && cachedConfigJson.equals(cachedEntry.get().getCacheString())) {
                return Entry.EMPTY;
            }
            Entry deserialized = Entry.fromString(cachedConfigJson);
            return deserialized == null || deserialized.getConfig() == null ? Entry.EMPTY : deserialized;
        } catch (Exception e) {
            this.logger.error(2200, ConfigCatLogMessages.CONFIG_SERVICE_CACHE_READ_ERROR, e);
            return Entry.EMPTY;
        }
    }

    private ClientCacheState determineCacheState(Entry cachedEntry) {
        if (cachedEntry.isEmpty()) {
            return ClientCacheState.NO_FLAG_DATA;
        }
        if (mode instanceof ManualPollingMode) {
            return ClientCacheState.HAS_CACHED_FLAG_DATA_ONLY;
        } else if (mode instanceof LazyLoadingMode) {
            if (cachedEntry.isExpired(System.currentTimeMillis() - (((LazyLoadingMode) mode).getCacheRefreshIntervalInSeconds() * 1000L))) {
                return ClientCacheState.HAS_CACHED_FLAG_DATA_ONLY;
            }
        } else if (mode instanceof AutoPollingMode) {
            if (cachedEntry.isExpired(System.currentTimeMillis() - (((AutoPollingMode) mode).getAutoPollRateInSeconds() * 1000L))) {
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
        if (stateMonitor != null) stateMonitor.close();
        fetcher.close();
    }
}
