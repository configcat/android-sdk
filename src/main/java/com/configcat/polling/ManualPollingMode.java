package com.configcat.polling;

public class ManualPollingMode implements PollingMode {
    @Override
    public String getPollingIdentifier() {
        return "m";
    }
}
