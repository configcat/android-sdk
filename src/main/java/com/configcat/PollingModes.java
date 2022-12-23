package com.configcat;

/**
 * Describes the polling modes.
 */
public final class PollingModes {
    private PollingModes() { /* prevent from instantiating */ }

    /**
     * Set up the auto polling mode with default parameters.
     *
     * @return the auto polling mode.
     */
    public static PollingMode autoPoll() {
        return new AutoPollingMode(60, 5);
    }

    /**
     * Set up the auto polling mode with custom parameters.
     *
     * @param autoPollIntervalInSeconds Sets how often the config.json should be fetched and cached.
     * @return the auto polling mode.
     */
    public static PollingMode autoPoll(int autoPollIntervalInSeconds) {
        return new AutoPollingMode(autoPollIntervalInSeconds, 5);
    }

    /**
     * Set up the auto polling mode with custom parameters.
     *
     * @param autoPollIntervalInSeconds Sets how often the config.json should be fetched and cached.
     * @param maxInitWaitTimeSeconds    Sets the time limit between the initialization of the client and the first config.json acquisition.
     * @return the auto polling mode.
     */
    public static PollingMode autoPoll(int autoPollIntervalInSeconds, int maxInitWaitTimeSeconds) {
        return new AutoPollingMode(autoPollIntervalInSeconds, maxInitWaitTimeSeconds);
    }

    /**
     * Set up a lazy polling mode with default parameters.
     *
     * @return the lazy polling mode.
     */
    public static PollingMode lazyLoad() {
        return new LazyLoadingMode(60);
    }

    /**
     * Set up a lazy polling mode with custom parameters.
     *
     * @param cacheRefreshIntervalInSeconds Sets how long the cache will store its value before fetching the latest from the network again.
     * @return the lazy polling mode.
     */
    public static PollingMode lazyLoad(int cacheRefreshIntervalInSeconds) {
        return new LazyLoadingMode(cacheRefreshIntervalInSeconds);
    }

    /**
     * Set up the manual polling mode.
     *
     * @return the manual polling mode.
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
            throw new IllegalArgumentException("autoPollRateInSeconds cannot be less than 1 second");

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
            throw new IllegalArgumentException("cacheRefreshIntervalInSeconds cannot be less than 1 second");

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
