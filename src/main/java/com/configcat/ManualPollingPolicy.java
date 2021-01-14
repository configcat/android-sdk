package com.configcat;

import java9.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

class ManualPollingPolicy extends RefreshPolicy {

    public ManualPollingPolicy(ConfigFetcher configFetcher, ConfigCache cache, Logger logger, String sdkKey) {
        super(configFetcher, cache, logger, sdkKey);
    }

    @Override
    public CompletableFuture<String> getConfigurationJsonAsync() {
        return CompletableFuture.completedFuture(super.readConfigCache());
    }
}
