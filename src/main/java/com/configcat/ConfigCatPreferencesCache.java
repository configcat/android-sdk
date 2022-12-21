package com.configcat;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * {@link ConfigCache} implementation that uses {@link SharedPreferences} for persistent storage.
 */
public class ConfigCatPreferencesCache extends ConfigCache {
    private final SharedPreferences sharedPreferences;

    public ConfigCatPreferencesCache(android.content.Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences("configcat_preferences", Context.MODE_PRIVATE);
    }

    @Override
    protected String read(String key) {
        return this.sharedPreferences.getString(key, null);
    }

    @Override
    protected void write(String key, String value) {
        this.sharedPreferences.edit().putString(key, value).apply();
    }
}
