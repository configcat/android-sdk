package com.configcat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java9.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

class LazyLoadingPolicy extends RefreshPolicy {
    private Date lastRefreshedTime;
    private final int cacheRefreshIntervalInSeconds;
    private final boolean asyncRefresh;
    private final AtomicBoolean isFetching;
    private final AtomicBoolean initialized;
    private CompletableFuture<String> fetchingFuture;
    private final CompletableFuture<Void> init;

    LazyLoadingPolicy(ConfigFetcher configFetcher, ConfigCache cache, Logger logger, String sdkKey, LazyLoadingMode config) {
        super(configFetcher, cache, logger, sdkKey);
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
                        ? CompletableFuture.completedFuture(super.readConfigCache())
                        : this.fetchingFuture;

            this.logger.debug("Cache expired, refreshing.");
            if(isInitialized) {
                this.fetchingFuture = this.fetch();
                if(this.asyncRefresh) {
                    return CompletableFuture.completedFuture(super.readConfigCache());
                }
                return this.fetchingFuture;
            } else {
                if(this.isFetching.compareAndSet(false, true)) {
                    this.fetchingFuture = this.fetch();
                }
                return this.init.thenApplyAsync(v -> super.readConfigCache());
            }
        }

        return CompletableFuture.completedFuture(super.readConfigCache());
    }

    private CompletableFuture<String> fetch() {
        return super.fetcher().getConfigurationJsonStringAsync()
                .thenApplyAsync(response -> {
                    String cached = super.readConfigCache();
                    if (response.isFetched() && !response.config().equals(cached)) {
                        super.writeConfigCache(response.config());
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
