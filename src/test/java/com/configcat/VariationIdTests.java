package com.configcat;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class VariationIdTests {

    private static final String TEST_JSON = "{ p: { s: 'test-salt' },  f: { key1: { v: { b: true }, i: 'fakeId1', p: [] ,r: [] }, key2: { v: { b: false }, i: 'fakeId2', p: [] ,r: [] } } }";
    private ConfigCatClient client;
    private MockWebServer server;

    @BeforeEach
    public void setUp() throws IOException {
        this.server = new MockWebServer();
        this.server.start();

        this.client = ConfigCatClient.get(Helpers.SDK_KEY, options -> {
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
    void getVariationIdWorks() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));
        EvaluationDetails<Boolean> valueDetails = client.getValueDetails(Boolean.class, "key1", null);
        assertEquals("fakeId1", valueDetails.getVariationId());
    }

    @Test
    void getVariationIdAsyncWorks() throws ExecutionException, InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));
        EvaluationDetails<Boolean> valueDetails = client.getValueDetailsAsync(Boolean.class, "key2", null).get();
        assertEquals("fakeId2", valueDetails.getVariationId());
    }

    @Test
    void getVariationIdNotFound() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));
        EvaluationDetails<Boolean> valueDetails = client.getValueDetails(Boolean.class, "nonexisting", false);
        assertEquals("", valueDetails.getVariationId());
    }

    @Test
    void getAllVariationIdsWorks() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));

        List<EvaluationDetails<?>> allValueDetails = client.getAllValueDetails(null);
        assertEquals(2, allValueDetails.size());
        assertEquals("fakeId1", allValueDetails.get(0).getVariationId());
        assertEquals("fakeId2", allValueDetails.get(1).getVariationId());
    }

    @Test
    void getAllVariationIdsEmpty() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        List<EvaluationDetails<?>> allValueDetails = client.getAllValueDetails(null);
        assertEquals(0, allValueDetails.size());
    }

    @Test
    void getAllVariationIdsAsyncWorks() throws ExecutionException, InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));

        List<EvaluationDetails<?>> allValueDetails = client.getAllValueDetailsAsync(null).get();
        assertEquals(2, allValueDetails.size());
        assertEquals("fakeId1", allValueDetails.get(0).getVariationId());
        assertEquals("fakeId2", allValueDetails.get(1).getVariationId());
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
