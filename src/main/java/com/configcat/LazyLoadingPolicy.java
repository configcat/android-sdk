package com.configcat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java9.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Describes a {@link RefreshPolicy} which uses an expiring cache
 * to maintain the internally stored configuration.
 */
class LazyLoadingPolicy extends RefreshPolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger(LazyLoadingPolicy.class);
    private Date lastRefreshedTime;
    private int cacheRefreshIntervalInSeconds;
    private boolean asyncRefresh;
    private final AtomicBoolean isFetching;
    private final AtomicBoolean initialized;
    private CompletableFuture<String> fetchingFuture;
    private CompletableFuture<Void> init;

    /**
     * Constructor used by the child classes.
     *
     * @param configFetcher the internal config fetcher instance.
     * @param cache the internal cache instance.
     */
    LazyLoadingPolicy(ConfigFetcher configFetcher, ConfigCache cache, LazyLoadingMode config) {
        super(configFetcher, cache);
        this.asyncRefresh = config.isAsyncRefresh();
        this.cacheRefreshIntervalInSeconds = config.getCacheRefreshIntervalInSeconds();
        this.isFetching = new AtomicBoolean(false);
        this.initialized = new AtomicBoolean(false);
        this.lastRefreshedTime = new Date(0L);
        this.init = new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<String> getConfigurationJsonAsync() {
        Date now = new Date();
        if (now.after(new Date(lastRefreshedTime.getTime() + this.cacheRefreshIntervalInSeconds * 1000))) {
            boolean isInitialized = this.init.isDone();

            if(isInitialized && !this.isFetching.compareAndSet(false, true))
                return this.asyncRefresh && this.initialized.get()
                        ? CompletableFuture.completedFuture(super.cache().get())
                        : this.fetchingFuture;

            LOGGER.debug("Cache expired, refreshing.");
            if(isInitialized) {
                this.fetchingFuture = this.fetch();
                if(this.asyncRefresh) {
                    return CompletableFuture.completedFuture(super.cache().get());
                }
                return this.fetchingFuture;
            } else {
                if(this.isFetching.compareAndSet(false, true)) {
                    this.fetchingFuture = this.fetch();
                }
                return this.init.thenApplyAsync(v -> super.cache().get());
            }
        }

        return CompletableFuture.completedFuture(super.cache().get());
    }

    private CompletableFuture<String> fetch() {
        return super.fetcher().getConfigurationJsonStringAsync()
                .thenApplyAsync(response -> {
                    String cached = super.cache().get();
                    if (response.isFetched() && !response.config().equals(cached)) {
                        super.cache().set(response.config());
                    }

                    if(!response.isFailed())
                        this.lastRefreshedTime = new Date();

                    if(this.initialized.compareAndSet(false, true)) {
                        this.init.complete(null);
                    }

                    this.isFetching.set(false);

                    return response.isFetched() ? response.config() : cached;
                });
    }
}
