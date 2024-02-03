package com.configcat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

final class Helpers {

    public static final String SDK_KEY = "configcat-sdk-1/TEST_KEY-0123456789012/1234567890123456789012";
    public static final String RULES_JSON = "{ p: { s: 'test-salt' }, f: { key: {  t: 1, v: {s: 'def'}, t: 1, i: 'defVar', p: [] , r: [ {c: [ {u: { a: 'Identifier', c: 2, l: ['@test1.com']}}],s: { v: {s: 'fake1'},i: 'id1'}},{c: [{u: {a: 'Identifier', c: 2,l: ['@test2.com']}}],s: { v: {s: 'fake2'},i: 'id2'}}] } } }";


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

    static String cacheValueFromConfigJsonWithEtag(String json, String etag) {
        Config config = Utils.gson.fromJson(json, Config.class);
        Entry entry = new Entry(config, etag, json, System.currentTimeMillis());
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

    public static String readFile(String filePath) throws IOException {

        try (InputStream stream = Helpers.class.getClassLoader().getResourceAsStream(filePath)) {
            if (stream == null) {
                throw new IOException();
            }
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int temp;
            while ((temp = stream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, temp);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }

    }
}
