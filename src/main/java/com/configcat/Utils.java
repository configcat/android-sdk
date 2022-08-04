package com.configcat;

import java.util.List;

class Utils {
    static void trimElements(List<String> arr) {
        for (int i = 0; i < arr.size(); i++)
            arr.set(i, arr.get(i).trim());
    }
}
