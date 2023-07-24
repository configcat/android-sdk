package com.configcat;

import java.util.function.Supplier;

final class Helpers {

    public static final String SDK_KEY = "configcat-sdk-1/TEST_KEY-0123456789012/1234567890123456789012";
    static final String RULES_JSON = "{ f: { key: { v: 'def', t: 1, i: 'defVar', p: [] ,r: [" +
            "{ v: 'fake1', i: 'id1', a: 'Identifier', t: 2, c: '@test1.com' }," +
            "{ v: 'fake2', i: 'id2', a: 'Identifier', t: 2, c: '@test2.com' }," +
            "] } } }";

    static String cacheValueFromConfigJson(String json) {
        Config config = Utils.gson.fromJson(json, Config.class);
        Entry entry = new Entry(config, "fakeTag", json, System.currentTimeMillis());
        return entry.serialize();
    }

    static String cacheValueFromConfigJsonAndTime(String json, long time) {
        Config config = Utils.gson.fromJson(json, Config.class);
        Entry entry = new Entry(config, "fakeTag", json, time);
        return entry.serialize();
    }

    static String entryToJson(Entry entry) {
        return Utils.gson.toJson(entry);
    }

    static void waitFor(Supplier<Boolean> predicate) throws InterruptedException {
        waitFor(2000, predicate);
    }

    static void waitFor(long timeout, Supplier<Boolean> predicate) throws InterruptedException {
        long end = System.currentTimeMillis() + timeout;
        while (!predicate.get()) {
            Thread.sleep(200);
            if (System.currentTimeMillis() > end) {
                throw new RuntimeException("Test timed out.");
            }
        }
    }
}
