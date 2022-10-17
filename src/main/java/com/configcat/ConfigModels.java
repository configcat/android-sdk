package com.configcat;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

class Entry {
    public Config config;
    public String eTag;
    public long fetchTime;

    public Entry(Config config, String eTag, long fetchTime) {
        this.config = config;
        this.eTag = eTag;
        this.fetchTime = fetchTime;
    }

    boolean isEmpty() {
        return this == empty;
    }

    public static final Entry empty = new Entry(Config.empty, "", 0);
}

class Config {
    @SerializedName(value = "p")
    public Preferences preferences;
    @SerializedName(value = "f")
    public Map<String, Setting> entries = new HashMap<>();

    boolean isEmpty() {
        return this == empty;
    }

    public static final Config empty = new Config();
}

class Preferences {
    @SerializedName(value = "u")
    public String baseUrl;
    @SerializedName(value = "r")
    public int redirect;
}

