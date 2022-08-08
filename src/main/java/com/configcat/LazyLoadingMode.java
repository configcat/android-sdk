package com.configcat;

class LazyLoadingMode implements PollingMode {
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
    public String getPollingIdentifier() {
        return "l";
    }
}
