package com.configcat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

final class Utils {
    private Utils() { /* prevent from instantiation*/ }

    static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public static Config deserializeConfig(String json) {
        Config config = Utils.gson.fromJson(json, Config.class);
        String salt = config.getPreferences().getSalt();
        Segment[] segments = config.getSegments();
        if (segments == null) {
            segments = new Segment[]{};
        }
        for (Setting setting : config.getEntries().values()) {
            setting.setConfigSalt(salt);
            setting.setSegments(segments);
        }
        return config;
    }

    public static String sha256(byte[] byteArray) {
        return new String(Hex.encodeHex(DigestUtils.sha256(byteArray)));
    }

    public static String sha256(String text) {
        return new String(Hex.encodeHex(DigestUtils.sha256(text)));
    }

    public static String sha1(String text) {
        return new String(Hex.encodeHex(DigestUtils.sha1(text)));
    }

    public static String sha1(byte[] byteArray) {
        return new String(Hex.encodeHex(DigestUtils.sha1(byteArray)));
    }
}

final class Constants {
    private Constants() { /* prevent from instantiation*/ }

    static final long DISTANT_FUTURE = Long.MAX_VALUE;
    static final long DISTANT_PAST = 0;
    static final String CONFIG_JSON_NAME = "config_v6.json";
    static final String SERIALIZATION_FORMAT_VERSION = "v2";
    static final String VERSION = "10.4.1";

    static final String SDK_KEY_PROXY_PREFIX = "configcat-proxy/";
    static final String SDK_KEY_PREFIX = "configcat-sdk-1";

    static final int SDK_KEY_SECTION_LENGTH = 22;
}

final class Result<T> {
    private final T value;
    private final Object error;

    private Result(T value, Object error) {
        this.value = value;
        this.error = error;
    }

    T value() {
        return this.value;
    }

    Object error() {
        return this.error;
    }

    static <T> Result<T> error(Object error, T value) {
        return new Result<>(value, error);
    }

    static <T> Result<T> success(T value) {
        return new Result<>(value, null);
    }
}
