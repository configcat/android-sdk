package com.configcat;

/**
 * The lazy loading polling mode configuration.
 */
class LazyLoadingMode extends PollingMode {
    private final int cacheRefreshIntervalInSeconds;
    private final boolean asyncRefresh;

    LazyLoadingMode(int cacheRefreshIntervalInSeconds, boolean asyncRefresh) {
        this.cacheRefreshIntervalInSeconds = cacheRefreshIntervalInSeconds;
        this.asyncRefresh = asyncRefresh;
    }

    int getCacheRefreshIntervalInSeconds() {
        return cacheRefreshIntervalInSeconds;
    }

    boolean isAsyncRefresh() {
        return asyncRefresh;
    }

    @Override
    String getPollingIdentifier() {
        return "l";
    }
}
