package com.configcat;

import java.io.IOException;
import java.util.Map;

public class ClassPathResourceOverrideDataSource extends OverrideDataSource {
    private final Map<String, Setting> loadedSettings;

    @Override
    public Map<String, Setting> getLocalConfiguration() {
        return this.loadedSettings;
    }

    public ClassPathResourceOverrideDataSource(String fileName) throws IOException {
        String contents = Helpers.readFileFromClassPath(fileName);
        Config config = Utils.deserializeConfig(contents);
        this.loadedSettings = config.getEntries();
    }
}
