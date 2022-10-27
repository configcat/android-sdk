package com.configcat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

final class Utils {
    private Utils() { /* prevent from instantiation*/ }

    static void trimElements(List<String> arr) {
        for (int i = 0; i < arr.size(); i++)
            arr.set(i, arr.get(i).trim());
    }

    static final Gson gson = new GsonBuilder().create();
}

final class Constants {
    private Constants() { /* prevent from instantiation*/ }

    static final long DISTANT_FUTURE = Long.MAX_VALUE;
    static final long DISTANT_PAST = 0;
    static final String CONFIG_JSON_NAME = "config_v5";
    static final String VERSION = "8.0.0";
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
