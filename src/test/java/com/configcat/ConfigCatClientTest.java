package com.configcat;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConfigCatClientTest {

    private static final String APIKEY = "TEST_KEY";

    private static final String TEST_JSON = "{ f: { fakeKey: { v: fakeValue, t: 0, p: [] ,r: [] } } }";
    private static final String TEST_JSON_MULTIPLE = "{ f: { key1: { v: true, t: 0, i: 'fakeId1', p: [] ,r: [] }, key2: { v: false, t: 0, i: 'fakeId2', p: [] ,r: [] } } }";

    @Test
    void ensuresApiKeyIsNotNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> ConfigCatClient.get(null));

        assertEquals("'sdkKey' cannot be null or empty.", exception.getMessage());
    }

    @Test
    void ensuresApiKeyIsNotEmpty() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> ConfigCatClient.get(""));

        assertEquals("'sdkKey' cannot be null or empty.", exception.getMessage());
    }

    @Test
    void getValueWithDefaultConfigTimeout() throws IOException {
        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> options.httpClient(new OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()));

        // makes a call to a real url which would fail, default expected
        boolean config = cl.getValue(Boolean.class, "key", true);
        assertTrue(config);

        cl.close();
    }

    @Test
    void getConfigurationWithFailingCache() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.cache(new FailingCache());
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));
        cl.forceRefresh();
        assertEquals("fakeValue", cl.getValue(String.class, "fakeKey", null));

        server.close();
        cl.close();
    }

    @Test
    void getConfigurationAutoPollFail() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.cache(new FailingCache());
            options.mode(PollingModes.autoPoll(5));
            options.baseUrl(server.url("/").toString());
        });

        server.enqueue(new MockResponse().setResponseCode(500).setBody(""));

        assertEquals("", cl.getValue(String.class, "fakeKey", ""));

        server.close();
        cl.close();
    }

    @Test
    void getConfigurationExpCacheFail() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.cache(new FailingCache());
            options.mode(PollingModes.lazyLoad(5));
            options.baseUrl(server.url("/").toString());
        });

        server.enqueue(new MockResponse().setResponseCode(500).setBody(""));

        assertEquals("", cl.getValue(String.class, "fakeKey", ""));

        server.close();
        cl.close();
    }

    @Test
    void getConfigurationManualFail() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.cache(new FailingCache());
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        server.enqueue(new MockResponse().setResponseCode(500).setBody(""));

        assertEquals("", cl.getValue(String.class, "fakeKey", ""));

        server.close();
        cl.close();
    }

    @Test
    void getConfigurationReturnsPreviousCachedOnTimeout() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.httpClient(new OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build());
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("delayed").setBodyDelay(3, TimeUnit.SECONDS));

        cl.forceRefresh();
        assertEquals("fakeValue", cl.getValue(String.class, "fakeKey", null));
        cl.forceRefresh();
        assertEquals("fakeValue", cl.getValue(String.class, "fakeKey", null));

        server.close();
        cl.close();
    }

    @Test
    void maxInitWaitTimeTest() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(200).setBody("delayed").setBodyDelay(2, TimeUnit.SECONDS));

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.autoPoll(60, 1));
            options.baseUrl(server.url("/").toString());
        });

        Instant previous = Instant.now();
        assertNull(cl.getValue(String.class, "fakeKey", null));
        assertTrue(Duration.between(previous, Instant.now()).toMillis() < 1500);

        server.close();
        cl.close();
    }

    @Test
    void getConfigurationReturnsPreviousCachedOnFailAsync() throws IOException, ExecutionException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.start();

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));
        server.enqueue(new MockResponse().setResponseCode(500));

        cl.forceRefresh();
        assertEquals("fakeValue", cl.getValueAsync(String.class, "fakeKey", null).get());
        cl.forceRefresh();
        assertEquals("fakeValue", cl.getValueAsync(String.class, "fakeKey", null).get());

        server.close();
        cl.close();
    }

    @Test
    void getValueReturnsDefaultOnExceptionRepeatedly() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.httpClient(new OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build());
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        String badJson = "{ test: test] }";
        String def = "def";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(badJson));
        server.enqueue(new MockResponse().setResponseCode(200).setBody(badJson).setBodyDelay(3, TimeUnit.SECONDS));

        cl.forceRefresh();
        assertSame(def, cl.getValue(String.class, "test", def));

        cl.forceRefresh();
        assertSame(def, cl.getValue(String.class, "test", def));

        server.shutdown();
        cl.close();
    }

    @Test
    void forceRefreshWithTimeout() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.httpClient(new OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build());
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        server.enqueue(new MockResponse().setResponseCode(200).setBody("test").setBodyDelay(3, TimeUnit.SECONDS));

        cl.forceRefresh();

        server.shutdown();
        cl.close();
    }

    @Test
    void getAllValues() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON_MULTIPLE));
        cl.forceRefresh();

        Map<String, Object> allValues = cl.getAllValues(null);

        assertEquals(true, allValues.get("key1"));
        assertEquals(false, allValues.get("key2"));

        server.shutdown();
        cl.close();
    }

    @Test
    void getValueInvalidArguments() throws IOException {
        ConfigCatClient client = ConfigCatClient.get("key");
        assertThrows(IllegalArgumentException.class, () -> client.getValue(Boolean.class, null, false));
        assertThrows(IllegalArgumentException.class, () -> client.getValue(Boolean.class, "", false));

        assertThrows(IllegalArgumentException.class, () -> client.getValueAsync(Boolean.class, null, false).get());
        assertThrows(IllegalArgumentException.class, () -> client.getValueAsync(Boolean.class, "", false).get());
        client.close();
    }

    @Test
    void testAutoPollRefreshFail() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(500).setBody(""));

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.autoPoll());
            options.baseUrl(server.url("/").toString());
        });

        cl.forceRefresh();
        assertEquals("", cl.getValue(String.class, "fakeKey", ""));

        server.close();
        cl.close();
    }

    @Test
    void testLazyRefreshFail() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(500).setBody(""));

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.lazyLoad());
            options.baseUrl(server.url("/").toString());
        });

        cl.forceRefresh();
        assertEquals("", cl.getValue(String.class, "fakeKey", ""));

        server.close();
        cl.close();
    }

    @Test
    void testManualPollRefreshFail() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(500).setBody(""));

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        cl.forceRefresh();
        assertEquals("", cl.getValue(String.class, "fakeKey", ""));

        server.close();
        cl.close();
    }

    @Test
    void testAutoPollUserAgentHeader() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.autoPoll());
            options.baseUrl(server.url("/").toString());
        });

        cl.forceRefresh();

        assertEquals("ConfigCat-Droid/a-" + Constants.VERSION, server.takeRequest().getHeader("X-ConfigCat-UserAgent"));

        server.shutdown();
        cl.close();
    }

    @Test
    void testLazyUserAgentHeader() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.lazyLoad());
            options.baseUrl(server.url("/").toString());
        });

        cl.forceRefresh();

        assertEquals("ConfigCat-Droid/l-" + Constants.VERSION, server.takeRequest().getHeader("X-ConfigCat-UserAgent"));

        server.shutdown();
        cl.close();
    }

    @Test
    void testManualAgentHeader() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        cl.forceRefresh();

        assertEquals("ConfigCat-Droid/m-" + Constants.VERSION, server.takeRequest().getHeader("X-ConfigCat-UserAgent"));

        server.shutdown();
        cl.close();
    }

    @Test
    void testOnlineOffline() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));
        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        assertFalse(cl.isOffline());

        cl.forceRefresh();

        assertEquals(1, server.getRequestCount());

        cl.setOffline();
        assertTrue(cl.isOffline());

        cl.forceRefresh();

        assertEquals(1, server.getRequestCount());

        cl.setOnline();
        cl.forceRefresh();

        assertEquals(2, server.getRequestCount());

        server.shutdown();
        cl.close();
    }

    @Test
    void testInitOffline() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
            options.offline(true);
        });

        assertTrue(cl.isOffline());

        cl.forceRefresh();

        assertEquals(0, server.getRequestCount());

        cl.setOnline();
        cl.forceRefresh();

        assertEquals(1, server.getRequestCount());

        server.shutdown();
        cl.close();
    }

    @Test
    void testDefaultUser() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(200).setBody(Helpers.RULES_JSON));

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        cl.forceRefresh();

        User user1 = new User.Builder().build("test@test1.com");
        User user2 = new User.Builder().build("test@test2.com");

        cl.setDefaultUser(user1);

        assertEquals("fake1", cl.getValue(String.class, "key", ""));
        assertEquals("fake2", cl.getValue(String.class, "key", user2, ""));

        cl.clearDefaultUser();

        assertEquals("def", cl.getValue(String.class, "key", ""));

        server.shutdown();
        cl.close();
    }

    @Test
    void testDefaultUserVariationId() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(200).setBody(Helpers.RULES_JSON));

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        cl.forceRefresh();

        User user1 = new User.Builder().build("test@test1.com");
        User user2 = new User.Builder().build("test@test2.com");

        cl.setDefaultUser(user1);

        assertEquals("id1", cl.getVariationId("key", ""));
        assertEquals("id2", cl.getVariationId("key", user2, ""));

        cl.clearDefaultUser();

        assertEquals("defVar", cl.getVariationId("key", ""));

        server.shutdown();
        cl.close();
    }

    @Test
    void testHooks() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(200).setBody(Helpers.RULES_JSON));
        server.enqueue(new MockResponse().setResponseCode(500).setBody(""));

        AtomicBoolean changed = new AtomicBoolean(false);
        AtomicBoolean ready = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>("");

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
            options.hooks().addOnConfigChanged(map -> changed.set(true));
            options.hooks().addOnReady(() -> ready.set(true));
            options.hooks().addOnError(error::set);
        });

        cl.forceRefresh();
        cl.forceRefresh();

        assertTrue(changed.get());
        assertTrue(ready.get());
        assertEquals("Double-check your SDK Key at https://app.configcat.com/sdkkey. Received unexpected response: 500", error.get());

        server.shutdown();
        cl.close();
    }

    @Test
    void testHooksSub() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(200).setBody(Helpers.RULES_JSON));
        server.enqueue(new MockResponse().setResponseCode(500).setBody(""));

        AtomicBoolean changed = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>("");

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.manualPoll());
            options.baseUrl(server.url("/").toString());
        });

        cl.getHooks().addOnConfigChanged(map -> changed.set(true));
        cl.getHooks().addOnError(error::set);

        cl.forceRefresh();
        cl.forceRefresh();

        assertTrue(changed.get());
        assertEquals("Double-check your SDK Key at https://app.configcat.com/sdkkey. Received unexpected response: 500", error.get());

        server.shutdown();
        cl.close();
    }

    @Test
    void testHooksAutoPollSub() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(200).setBody(Helpers.RULES_JSON));
        server.enqueue(new MockResponse().setResponseCode(500).setBody(""));

        AtomicBoolean changed = new AtomicBoolean(false);
        AtomicBoolean ready = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>("");

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.autoPoll());
            options.baseUrl(server.url("/").toString());
        });

        cl.getHooks().addOnConfigChanged(map -> changed.set(true));
        cl.getHooks().addOnReady(() -> ready.set(true));
        cl.getHooks().addOnError(error::set);

        cl.forceRefresh();
        cl.forceRefresh();

        assertTrue(changed.get());
        assertTrue(ready.get());
        assertEquals("Double-check your SDK Key at https://app.configcat.com/sdkkey. Received unexpected response: 500", error.get());

        server.shutdown();
        cl.close();
    }

    @Test
    void testOnFlagEvaluationError() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(500).setBody(""));

        AtomicBoolean called = new AtomicBoolean(false);

        ConfigCatClient cl = ConfigCatClient.get(APIKEY, options -> {
            options.mode(PollingModes.lazyLoad());
            options.baseUrl(server.url("/").toString());
            options.hooks().addOnFlagEvaluated(details -> {
                assertEquals("", details.getValue());
                assertEquals("Config JSON is not present. Returning defaultValue: [].", details.getError());
                assertTrue(details.isDefaultValue());
                called.set(true);
            });
        });

        cl.getValue(String.class, "key", "");
        assertTrue(called.get());

        server.shutdown();
        cl.close();
    }
}