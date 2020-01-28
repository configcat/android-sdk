package com.configcat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java9.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Describes a {@link RefreshPolicy} which polls the latest configuration
 * over HTTP and updates the local cache repeatedly.
 */
class AutoPollingPolicy extends RefreshPolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoPollingPolicy.class);
    private static final ConfigurationParser parser = new ConfigurationParser();
    private final ScheduledExecutorService scheduler;
    private final CompletableFuture<Void> initFuture;
    private final AtomicBoolean initialized;
    private final ArrayList<ConfigurationChangeListener> listeners;

    /**
     * Constructor used by the child classes.
     *
     * @param configFetcher the internal config fetcher instance.
     * @param cache the internal cache instance.
     */
    AutoPollingPolicy(ConfigFetcher configFetcher, ConfigCache cache, AutoPollingMode modeConfig) {
        super(configFetcher, cache);
        this.listeners = new ArrayList<>();

        if(modeConfig.getListener() != null)
            this.listeners.add(modeConfig.getListener());

        this.initialized = new AtomicBoolean(false);
        this.initFuture = new CompletableFuture<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduler.scheduleAtFixedRate(() -> {
            try {
                FetchResponse response = super.fetcher().getConfigurationJsonStringAsync().get();
                String cached = super.cache().get();
                String config = response.config();
                if (response.isFetched() && !config.equals(cached)) {
                    super.cache().set(config);
                    this.broadcastConfigurationChanged(config);
                }

                if(!initialized.getAndSet(true))
                    initFuture.complete(null);

            } catch (Exception e){
                LOGGER.error("Exception in AutoPollingCachePolicy", e);
            }
        }, 0, modeConfig.getAutoPollRateInSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public CompletableFuture<String> getConfigurationJsonAsync() {
        if(this.initFuture.isDone())
            return CompletableFuture.completedFuture(super.cache().get());

        return this.initFuture.thenApplyAsync(v -> super.cache().get());
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.scheduler.shutdown();
        this.listeners.clear();
    }

    /**
     * Subscribes a new listener to the configuration changed event.
     *
     * @param listener the listener.
     */
    public synchronized void addConfigurationChangeListener(ConfigurationChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a given listener from the configuration changed event.
     *
     * @param listener the listener.
     */
    public synchronized void removeConfigurationChangeListener(ConfigurationChangeListener listener) {
        listeners.remove(listener);
    }

    private synchronized void broadcastConfigurationChanged(String newConfiguration) {
        for (ConfigurationChangeListener listener : this.listeners)
            listener.onConfigurationChanged(parser, newConfiguration);
    }
}
