package com.configcat;

/**
 * Describes the polling modes.
 */
public final class PollingModes {
    private PollingModes() { /* prevent from instantiating */ }

    /**
     * Creates a configured auto polling configuration.
     *
     * @return the auto polling configuration.
     */
    public static PollingMode autoPoll() {
        return new AutoPollingMode(60, 5);
    }

    /**
     * Creates a configured auto polling configuration.
     *
     * @param autoPollIntervalInSeconds Sets at least how often this policy should fetch the latest configuration and refresh the cache.
     * @return the auto polling configuration.
     */
    public static PollingMode autoPoll(int autoPollIntervalInSeconds) {
        return new AutoPollingMode(autoPollIntervalInSeconds, 5);
    }

    /**
     * Creates a configured auto polling configuration.
     *
     * @param autoPollIntervalInSeconds Sets at least how often this policy should fetch the latest configuration and refresh the cache.
     * @param maxInitWaitTimeSeconds    Sets the maximum waiting time between initialization and the first config acquisition in seconds.
     * @return the auto polling configuration.
     */
    public static PollingMode autoPoll(int autoPollIntervalInSeconds, int maxInitWaitTimeSeconds) {
        return new AutoPollingMode(autoPollIntervalInSeconds, maxInitWaitTimeSeconds);
    }

    /**
     * Creates a configured lazy loading polling configuration.
     *
     * @return the lazy loading polling configuration.
     */
    public static PollingMode lazyLoad() {
        return new LazyLoadingMode(60);
    }

    /**
     * Creates a configured lazy loading polling configuration.
     *
     * @param cacheRefreshIntervalInSeconds Sets how long the cache will store its value before fetching the latest from the network again.
     * @return the lazy loading polling configuration.
     */
    public static PollingMode lazyLoad(int cacheRefreshIntervalInSeconds) {
        return new LazyLoadingMode(cacheRefreshIntervalInSeconds);
    }

    /**
     * Creates a configured manual polling configuration.
     *
     * @return the manual polling configuration.
     */
    public static PollingMode manualPoll() {
        return new ManualPollingMode();
    }
}

class AutoPollingMode implements PollingMode {
    private final int autoPollRateInSeconds;
    private final int maxInitWaitTimeSeconds;

    AutoPollingMode(int autoPollRateInSeconds, int maxInitWaitTimeSeconds) {
        if (autoPollRateInSeconds < 1)
            throw new IllegalArgumentException("autoPollRateInSeconds cannot be less than 1 seconds");

        this.autoPollRateInSeconds = autoPollRateInSeconds;
        this.maxInitWaitTimeSeconds = maxInitWaitTimeSeconds;
    }

    int getAutoPollRateInSeconds() {
        return autoPollRateInSeconds;
    }

    public int getMaxInitWaitTimeSeconds() {
        return maxInitWaitTimeSeconds;
    }

    @Override
    public String getPollingIdentifier() {
        return "a";
    }
}

class LazyLoadingMode implements PollingMode {
    private final int cacheRefreshIntervalInSeconds;

    LazyLoadingMode(int cacheRefreshIntervalInSeconds) {
        if (cacheRefreshIntervalInSeconds < 1)
            throw new IllegalArgumentException("cacheRefreshIntervalInSeconds cannot be less than 1 seconds");

        this.cacheRefreshIntervalInSeconds = cacheRefreshIntervalInSeconds;
    }

    int getCacheRefreshIntervalInSeconds() {
        return cacheRefreshIntervalInSeconds;
    }

    @Override
    public String getPollingIdentifier() {
        return "l";
    }
}

class ManualPollingMode implements PollingMode {
    @Override
    public String getPollingIdentifier() {
        return "m";
    }
}
