package com.configcat;

import com.configcat.models.Config;
import com.configcat.models.Entry;

import java.util.function.Supplier;

final class Helpers {
    static final String RULES_JSON = "{ f: { key: { v: 'def', t: 1, i: 'defVar', p: [] ,r: [" +
            "{ v: 'fake1', i: 'id1', a: 'Identifier', t: 2, c: '@test1.com' }," +
            "{ v: 'fake2', i: 'id2', a: 'Identifier', t: 2, c: '@test2.com' }," +
            "] } } }";

    static String cacheValueFromConfigJson(String json) {
        Config config = Utils.gson.fromJson(json, Config.class);
        Entry entry = new Entry(config, "fakeTag", json, DateTimeUtils.format(System.currentTimeMillis()));
        return entry.serialize();
    }

    static String cacheValueFromConfigJsonWithDistantPast(String json) {
        Config config = Utils.gson.fromJson(json, Config.class);
        Entry entry = new Entry(config, "fakeTag", json, DateTimeUtils.format(Constants.DISTANT_PAST));
        return entry.serialize();
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
