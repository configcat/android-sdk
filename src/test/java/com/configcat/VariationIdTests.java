package com.configcat;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class VariationIdTests {

    private static final String TEST_JSON = "{ f: { key1: { v: true, i: 'fakeId1', p: [] ,r: [] }, key2: { v: false, i: 'fakeId2', p: [] ,r: [] } } }";
    private ConfigCatClient client;
    private MockWebServer server;

    @BeforeEach
    public void setUp() throws IOException {
        this.server = new MockWebServer();
        this.server.start();

        this.client = ConfigCatClient.get("TEST_KEY", options -> {
            options.httpClient(new OkHttpClient.Builder().build());
            options.pollingMode(PollingModes.lazyLoad(2));
            options.baseUrl(this.server.url("/").toString());
        });
    }

    @AfterEach
    public void tearDown() throws IOException {
        this.client.close();
        this.server.shutdown();
    }

    @Test
    void getKeyAndValueWorks() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));
        Map.Entry<String, Boolean> result = client.getKeyAndValue(boolean.class, "fakeId2");
        assertEquals("key2", result.getKey());
        assertFalse(result.getValue());
    }

    @Test
    void getKeyAndValueAsyncWorks() throws ExecutionException, InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));
        Map.Entry<String, Boolean> result = client.getKeyAndValueAsync(boolean.class, "fakeId1").get();
        assertEquals("key1", result.getKey());
        assertTrue(result.getValue());
    }

    @Test
    void getKeyAndValueNotFound() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));
        Map.Entry<String, Boolean> result = client.getKeyAndValue(boolean.class, "nonexisting");
        assertNull(result);
    }
}
