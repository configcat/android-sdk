package com.configcat;

import java.util.List;

final class Utils {
    private Utils() { /* prevent from instantiation*/ }

    static void trimElements(List<String> arr) {
        for (int i = 0; i < arr.size(); i++)
            arr.set(i, arr.get(i).trim());
    }
}
