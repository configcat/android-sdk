package com.configcat;

import com.configcat.models.Setting;
import com.configcat.models.SettingType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

class LocalMapDataSource extends OverrideDataSource {
    private final Map<String, Setting> loadedSettings = new HashMap<>();

    public LocalMapDataSource(Map<String, Object> source) {
        if (source == null)
            throw new IllegalArgumentException("'source' cannot be null.");

        Gson gson = new GsonBuilder().create();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Setting setting = new Setting();
            setting.setValue(gson.toJsonTree(entry.getValue()));
            setting.setType(determineSettingType(entry.getValue()));
            this.loadedSettings.put(entry.getKey(), setting);
        }
    }

    @Override
    public Map<String, Setting> getLocalConfiguration() {
        return this.loadedSettings;
    }

    private SettingType determineSettingType(Object value) {
        if (value instanceof String) {
            return SettingType.STRING;
        } else if (value instanceof Boolean) {
            return SettingType.BOOLEAN;
        } else if (value instanceof Integer) {
            return SettingType.INT;
        } else if (value instanceof Double) {
            return SettingType.DOUBLE;
        } else {
            throw new IllegalArgumentException("Could not determine the setting type of '"+value+"'");
        }
    }
}
