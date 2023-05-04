package com.configcat.override;

import com.configcat.models.Setting;

import java.util.HashMap;
import java.util.Map;

/**
 * Describes a data source for feature flag and setting overrides.
 */
public class OverrideDataSource {
    /**
     * Gets all the overrides defined in the given source.
     * @return the overrides key-setting map.
     */
    public Map<String, Setting> getLocalConfiguration() {
        return new HashMap<>();
    }

    /**
     * Create an override data source that stores the overrides in a key-value map.
     * @param map the map that holds the overrides.
     * @return the map based data source.
     */
    public static OverrideDataSource map(Map<String, Object> map) {
        return new LocalMapDataSource(map);
    }
}
