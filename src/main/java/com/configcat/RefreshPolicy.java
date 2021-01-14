package com.configcat;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java9.util.concurrent.CompletableFuture;

/**
 * The interface of a refresh policy, the implementors
 * should describe the configuration update rules.
 */
abstract class RefreshPolicy implements Closeable {
    private static final String CacheBase = "android_"+ ConfigFetcher.CONFIG_JSON_NAME +"_%s";
    private final ConfigCache cache;
    private final ConfigFetcher configFetcher;
    private final String CacheKey;
    protected final Logger logger;

    private String inMemoryConfig;

    protected String readConfigCache() {
        try {
            return this.cache.read(CacheKey);
        } catch (Exception e) {
            this.logger.error("An error occurred during the cache read.", e);
            return this.inMemoryConfig;
        }
    }

    protected void writeConfigCache(String value) {
        try {
            this.inMemoryConfig = value;
            this.cache.write(CacheKey, value);
        } catch (Exception e) {
            this.logger.error("An error occurred during the cache write.", e);
        }
    }

    /**
     * Through this getter, child classes can use the fetcher to
     * get the latest configuration over HTTP.
     *
     * @return the config fetcher.
     */
    protected ConfigFetcher fetcher() {
        return configFetcher;
    }

    /**
     * Constructor used by the child classes.
     *
     * @param configFetcher the internal config fetcher instance.
     * @param cache the internal cache instance.
     * @param sdkKey the sdk key.
     */
    RefreshPolicy(ConfigFetcher configFetcher, ConfigCache cache, Logger logger, String sdkKey) {
        this.configFetcher = configFetcher;
        this.cache = cache;
        this.CacheKey = new String(Hex.encodeHex(DigestUtils.sha1(String.format(CacheBase, sdkKey))));
        this.logger = logger;
    }

    /**
     * Child classes has to implement this method, the {@link ConfigCatClient}
     * uses it to read the current configuration value through the applied policy.
     *
     * @return the future which computes the configuration.
     */
    public abstract CompletableFuture<String> getConfigurationJsonAsync();

    /**
     * Initiates a force refresh on the cached configuration.
     *
     * @return the future which executes the refresh.
     */
    public CompletableFuture<Void> refreshAsync() {
        return this.fetcher().getConfigurationJsonStringAsync()
                .thenAcceptAsync(response -> {
                    if(response.isFetched())
                        this.writeConfigCache(response.config());
                });
    }

    String getLatestCachedValue() {
        return this.inMemoryConfig;
    }

    @Override
    public void close() throws IOException {
        this.configFetcher.close();
    }
}
