package com.configcat;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

enum SettingType {
    BOOLEAN,
    STRING,
    INT,
    DOUBLE,
}

class Config {
    @SerializedName(value = "p")
    public Preferences preferences;
    @SerializedName(value = "f")
    public Map<String, Setting> entries = new HashMap<>();
    @SerializedName(value = "e")
    public String eTag = "";

    public static Config empty = new Config();
}

class Preferences {
    @SerializedName(value = "u")
    public String baseUrl;
    @SerializedName(value = "r")
    public int redirect;
}

