package com.configcat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

final class Utils {
    private Utils() { /* prevent from instantiation*/ }

    static final Gson gson = new GsonBuilder().create();

    public static Config deserializeConfig(String json){
        Config config = Utils.gson.fromJson(json, Config.class);
        for (Setting setting: config.getEntries().values()) {
            //TODO clarify the salt is required and always presented or should I handle when it missing?
            setting.setConfigSalt(config.getPreferences().getSalt());
            setting.setSegments(config.getSegments());
            //TODO check override case
        }
        return config;
    }
}

final class Constants {
    private Constants() { /* prevent from instantiation*/ }

    static final long DISTANT_FUTURE = Long.MAX_VALUE;
    static final long DISTANT_PAST = 0;
    static final String CONFIG_JSON_NAME = "config_v6.json";
    static final String SERIALIZATION_FORMAT_VERSION = "v2";
    static final String VERSION = "9.0.0";

    static final String SDK_KEY_PROXY_PREFIX = "configcat-proxy/";
    static final String SDK_KEY_PREFIX = "configcat-sdk-1";

    static final int SDK_KEY_SECTION_LENGTH = 22;
}

final class Result<T> {
    private final T value;
    private final String error;

    private Result(T value, String error) {
        this.value = value;
        this.error = error;
    }

    T value() { return this.value; }
    String error() { return this.error; }

    static <T> Result<T> error(String error, T value) {
        return new Result<>(value, error);
    }

    static <T> Result<T> success(T value) {
        return new Result<>(value, null);
    }
}
