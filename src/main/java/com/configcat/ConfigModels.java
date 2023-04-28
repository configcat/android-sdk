package com.configcat;

import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

class Entry {
    private Config config;
    private String eTag;
    private String configJson;
    private String fetchTimeRaw;

    public Config getConfig() {
        return config;
    }

    public String getETag() {
        return eTag;
    }

    public long getFetchTime() {
        if(fetchTimeRaw == null || fetchTimeRaw.isEmpty()){
            return 0;
        }
        try {
            return  DateTimeUtils.parseToMillis(fetchTimeRaw);
        } catch (ParseException e) {
            return 0;
        }
    }

    public String getConfigJson() {
        return configJson;
    }

    public String getFetchTimeRaw() {
        return fetchTimeRaw;
    }

    public Entry withFetchTime(String fetchTimeRaw) {
        return new Entry(getConfig(), getETag(), getConfigJson(), fetchTimeRaw);
    }

    public Entry(Config config, String eTag, String configJson, String fetchTimeRaw) {
        this.config = config;
        this.eTag = eTag;
        this.configJson = configJson;
        this.fetchTimeRaw = fetchTimeRaw;
    }

    boolean isEmpty() {
        return EMPTY.equals(this);
    }

    public static final Entry EMPTY = new Entry(Config.EMPTY, "", "", null);

    public String serialize() {
        return getFetchTimeRaw() + "\n" + getETag() + "\n" + getConfigJson();
    }

    public static Entry fromString(String cacheValue) throws IllegalArgumentException {
        if (cacheValue == null || cacheValue.isEmpty()) {
            return Entry.EMPTY;
        }

        int fetchTimeIndex = cacheValue.indexOf("\n");
        int eTagIndex = cacheValue.indexOf("\n", fetchTimeIndex + 1);
        if (fetchTimeIndex < 0 || eTagIndex < 0) {
            throw new IllegalArgumentException("Number of values is fewer than expected.");
        }
        String fetchTimeRaw = cacheValue.substring(0, fetchTimeIndex);
        if (!DateTimeUtils.isValidDate(fetchTimeRaw)) {
            throw new IllegalArgumentException("Invalid fetch time: " + fetchTimeRaw);
        }

        String eTag = cacheValue.substring(fetchTimeIndex + 1, eTagIndex);
        if (eTag.isEmpty()) {
            throw new IllegalArgumentException("Empty eTag value.");
        }
        String configJson = cacheValue.substring(eTagIndex + 1);
        if (configJson.isEmpty()) {
            throw new IllegalArgumentException("Empty config jsom value.");
        }
        try {
            Config config = Utils.gson.fromJson(configJson, Config.class);
            return new Entry(config, eTag, configJson, fetchTimeRaw);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON content: " + configJson);
        }
    }

}

class Config {
    @SerializedName(value = "p")
    private Preferences preferences;
    @SerializedName(value = "f")
    private Map<String, Setting> entries = new HashMap<>();

    public Preferences getPreferences() {
        return preferences;
    }

    public Map<String, Setting> getEntries() {
        return entries;
    }

    boolean isEmpty() {
        return EMPTY.equals(this);
    }

    public static final Config EMPTY = new Config();
}

class Preferences {
    @SerializedName(value = "u")
    private String baseUrl;
    @SerializedName(value = "r")
    private int redirect;

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getRedirect() {
        return redirect;
    }
}