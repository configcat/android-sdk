package com.configcat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

final class Utils {
    private Utils() { /* prevent from instantiation*/ }

    static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public static Config deserializeConfig(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("Config JSON content cannot be null or empty.");
        }
        Config config = Utils.gson.fromJson(json, Config.class);

        if (config == null) {
            throw new IllegalArgumentException("Invalid config JSON content: " + json);
        }

        String salt = config.getPreferences() != null ? config.getPreferences().getSalt() : null;
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
    static final String VERSION = "11.0.0";

    static final String SDK_KEY_PROXY_PREFIX = "configcat-proxy/";
    static final String SDK_KEY_PREFIX = "configcat-sdk-1";

    static final int SDK_KEY_SECTION_LENGTH = 22;
}

/**
 * Common interface for Result.
 */
interface ErrorCode {
    int code();
}

final class Result<T, E extends ErrorCode> {
    private final T value;
    private final Object error;
    private final E errorCode;

    private Result(T value, Object error, E errorCode) {
        this.value = value;
        this.error = error;
        this.errorCode = errorCode;
    }

    T value() {
        return this.value;
    }

    Object error() {
        return this.error;
    }

    E errorCode() {return this.errorCode;}

    static <T, E extends ErrorCode> Result<T, E> error(Object error, T value, E errorCode) {
        return new Result<>(value, error, errorCode);
    }

    static <T> Result<T, RefreshErrorCode> success(T value) {
        return new Result<>(value, null, RefreshErrorCode.NONE);
    }

    static <T, E extends ErrorCode> Result<T, E> success(T value, E errorCode) {
        return new Result<>(value, null, errorCode);
    }
}

final class EvaluationException extends IllegalArgumentException {
    EvaluationException(String message) {
        super(message);
    }
}

