package com.configcat;

import java.util.HashMap;

class FailingCache extends ConfigCache {

    @Override
    protected String read(String key) throws Exception {
        throw new Exception();
    }

    @Override
    protected void write(String key, String value) throws Exception {
        throw new Exception();
    }
}

class SingleValueCache extends ConfigCache {
    private String value;

    public SingleValueCache(String value) {
        this.value = value;
    }

    @Override
    protected String read(String key) {
        return this.value;
    }

    @Override
    protected void write(String key, String value) {
        this.value = value;
    }
}

class InMemoryCache extends ConfigCache {
    HashMap<String, String> map = new HashMap<>();

    @Override
    protected String read(String key) {
        return map.get(key);
    }

    @Override
    protected void write(String key, String value) {
        this.map.put(key, value);
    }

    public HashMap<String, String> getMap() {
        return map;
    }
}
