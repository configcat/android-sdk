package com.configcat;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java9.util.concurrent.CompletableFuture;

import java.io.Closeable;
import java.io.IOException;
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

    public Map<String, Setting> settings() { return settings; }
    public long fetchTime() { return fetchTime; }
}

class ConfigService implements Closeable {
    private static final String CACHE_BASE = "java_" + Constants.CONFIG_JSON_NAME + "_%s_v2";

    private ScheduledExecutorService initScheduler;
    private ScheduledExecutorService pollScheduler;
    private String cachedEntryString = "";
    private Entry cachedEntry = Entry.empty;
    private CompletableFuture<Result<Entry>> runningTask;
    private boolean offline = false;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final String cacheKey;
    private final PollingMode mode;
    private final ConfigCache cache;
    private final ConfigCatLogger logger;
    private final ConfigFetcher fetcher;
    private final ConfigCatClient.Hooks hooks;
    private final ReentrantLock lock = new ReentrantLock(true);

    public ConfigService(String sdkKey,
                         PollingMode mode,
                         ConfigCache cache,
                         ConfigCatLogger logger,
                         ConfigFetcher fetcher,
                         ConfigCatClient.Hooks hooks,
                         boolean offline) {
        this.cacheKey = new String(Hex.encodeHex(DigestUtils.sha1(String.format(CACHE_BASE, sdkKey))));
        this.mode = mode;
        this.cache = cache;
        this.logger = logger;
        this.fetcher = fetcher;
        this.hooks = hooks;
        this.offline = offline;

        if (mode instanceof AutoPollingMode) {
            AutoPollingMode autoPollingMode = (AutoPollingMode)mode;

            if (!offline) {
                startPoll(autoPollingMode);
            }

            initScheduler = Executors.newSingleThreadScheduledExecutor();
            initScheduler.schedule(() -> {
                if (initialized.compareAndSet(false, true)) {
                    hooks.invokeOnClientReady();
                    String message = "Max init wait time for the very first fetch reached (" + autoPollingMode.getMaxInitWaitTimeSeconds() + "s). Returning cached config.";
                    logger.warn(message);
                    completeRunningTask(Result.error(message));
                }
            }, autoPollingMode.getMaxInitWaitTimeSeconds(), TimeUnit.SECONDS);
        } else {
            setInitialized();
        }
    }

    public CompletableFuture<SettingResult> getSettings() {
        if (mode instanceof LazyLoadingMode) {
            LazyLoadingMode lazyLoadingMode = (LazyLoadingMode)mode;
            return fetchIfOlder(System.currentTimeMillis() - (lazyLoadingMode.getCacheRefreshIntervalInSeconds() * 1000L), false)
                    .thenApply(entryResult -> {
                       Entry result = entryResult.value() == null ? cachedEntry : entryResult.value();
                       return new SettingResult(result.config.entries, result.fetchTime);
                    });
        } else {
            return fetchIfOlder(Constants.DISTANT_PAST, true)
                    .thenApply(entryResult -> {
                        Entry result = entryResult.value() == null ? cachedEntry : entryResult.value();
                        return new SettingResult(result.config.entries, result.fetchTime);
                    });
        }
    }

    public CompletableFuture<RefreshResult> refresh() {
        return fetchIfOlder(Constants.DISTANT_FUTURE, false)
                .thenApply(entryResult -> new RefreshResult(entryResult.error() == null, entryResult.error()));
    }

    public void setOnline() {
        lock.lock();
        try {
            if (!offline) return;
            offline = false;
            if (mode instanceof AutoPollingMode) {
                startPoll((AutoPollingMode)mode);
            }
            logger.debug("Switched to ONLINE mode.");
        } finally {
            lock.unlock();
        }
    }

    public void setOffline() {
        lock.lock();
        try {
            if (offline) return;
            offline = true;
            if (pollScheduler != null) pollScheduler.shutdown();
            if (initScheduler != null) initScheduler.shutdown();
            logger.debug("Switched to OFFLINE mode.");
        } finally {
            lock.unlock();
        }
    }

    public boolean isOffline() {
        return offline;
    }

    private CompletableFuture<Result<Entry>> fetchIfOlder(long time, boolean preferCached) {
        lock.lock();
        try {
            // Sync up with the cache and use it when it's not expired.
            if (cachedEntry.isEmpty() || cachedEntry.fetchTime > time) {
                Entry fromCache = readCache();
                if (!fromCache.isEmpty() && !fromCache.eTag.equals(cachedEntry.eTag)) {
                    hooks.invokeOnConfigChanged(fromCache.config.entries);
                    cachedEntry = fromCache;
                }
                // Cache isn't expired
                if (cachedEntry.fetchTime > time) {
                    return CompletableFuture.completedFuture(Result.success(cachedEntry));
                }
            }
            // Use cache anyway (get calls on auto & manual poll must not initiate fetch).
            // The initialized check ensures that we subscribe for the ongoing fetch during the
            // max init wait time window in case of auto poll.
            if (preferCached && initialized.get()) {
                return CompletableFuture.completedFuture(Result.success(cachedEntry));
            }
            // If we are in offline mode we are not allowed to initiate fetch.
            if (offline) {
                return CompletableFuture.completedFuture(Result.error("The SDK is in offline mode, it can't initiate HTTP calls."));
            }

            if (runningTask == null) {
                // No fetch is running, initiate a new one.
                runningTask = new CompletableFuture<>();
                fetcher.fetchAsync(cachedEntry.eTag)
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
                hooks.invokeOnConfigChanged(entry.config.entries);
            } else if (response.isNotModified()) {
                cachedEntry.fetchTime = System.currentTimeMillis();
                writeCache(cachedEntry);
                completeRunningTask(Result.success(cachedEntry));
            } else {
                completeRunningTask(Result.error(response.error()));
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
        if (!initialized.compareAndSet(false, true)) return;
        this.hooks.invokeOnClientReady();
    }

    private void startPoll(AutoPollingMode mode) {
        long ageThreshold = (long)((mode.getAutoPollRateInSeconds() * 1000L) * 0.7);
        pollScheduler = Executors.newSingleThreadScheduledExecutor();
        pollScheduler.scheduleAtFixedRate(() -> this.fetchIfOlder(System.currentTimeMillis() - ageThreshold, false),
                0, mode.getAutoPollRateInSeconds(), TimeUnit.SECONDS);
    }

    private void writeCache(Entry entry) {
        try {
            String configToCache = Utils.gson.toJson(entry);
            cachedEntryString = configToCache;
            cache.write(cacheKey, configToCache);
        } catch (Exception e) {
            logger.error("An error occurred during the cache write.", e);
        }
    }

    private Entry readCache() {
        try {
            String json = cache.read(cacheKey);
            if (json != null && json.equals(cachedEntryString)) {
                return Entry.empty;
            }
            cachedEntryString = json;
            Entry deserialized = Utils.gson.fromJson(json, Entry.class);
            return deserialized == null || deserialized.config == null ? Entry.empty : deserialized;
        } catch (Exception e) {
            this.logger.error("An error occurred during the cache read.", e);
            return Entry.empty;
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
