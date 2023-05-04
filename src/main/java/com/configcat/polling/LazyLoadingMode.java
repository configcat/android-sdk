package com.configcat.polling;

public class LazyLoadingMode implements PollingMode {
    private final int cacheRefreshIntervalInSeconds;

    LazyLoadingMode(int cacheRefreshIntervalInSeconds) {
        if (cacheRefreshIntervalInSeconds < 1)
            throw new IllegalArgumentException("cacheRefreshIntervalInSeconds cannot be less than 1 second");

        this.cacheRefreshIntervalInSeconds = cacheRefreshIntervalInSeconds;
    }

    public int getCacheRefreshIntervalInSeconds() {
        return cacheRefreshIntervalInSeconds;
    }

    @Override
    public String getPollingIdentifier() {
        return "l";
    }
}
