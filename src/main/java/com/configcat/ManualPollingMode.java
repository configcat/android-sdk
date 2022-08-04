package com.configcat;

/**
 * The manual polling mode configuration.
 */
class ManualPollingMode implements PollingMode {
    @Override
    public String getPollingIdentifier() {
        return "m";
    }
}
