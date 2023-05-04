package com.configcat.polling;

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
