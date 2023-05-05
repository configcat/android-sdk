package com.configcat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public final class Utils {
    private Utils() { /* prevent from instantiation*/ }

    public static void trimElements(List<String> arr) {
        for (int i = 0; i < arr.size(); i++)
            arr.set(i, arr.get(i).trim());
    }

    public static final Gson gson = new GsonBuilder().create();
}

