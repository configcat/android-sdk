package com.configcat;

import com.configcat.log.ConfigCatLogger;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ManualPollingPolicyTest {
    private ConfigService policy;
    private MockWebServer server;
    private final ConfigCatLogger logger = new ConfigCatLogger(LoggerFactory.getLogger(ManualPollingPolicyTest.class));
    private static final String TEST_JSON = "{ f: { fakeKey: { v: %s, p: [] ,r: [] } } }";

    @BeforeEach
    public void setUp() throws IOException {
        this.server = new MockWebServer();
        this.server.start();

        PollingMode mode = PollingModes.manualPoll();
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(), logger, "", this.server.url("/").toString(), false, mode.getPollingIdentifier());
        this.policy = new ConfigService("", mode, new NullConfigCache(), logger, fetcher, new ConfigCatHooks(), false);
    }

    @AfterEach
    public void tearDown() throws IOException {
        this.policy.close();
        this.server.shutdown();
    }

    @Test
    void get() throws InterruptedException, ExecutionException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test2")).setBodyDelay(2, TimeUnit.SECONDS));

        //first call
        this.policy.refresh().get();
        assertEquals("test", this.policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        //next call will get the new value
        this.policy.refresh().get();
        assertEquals("test2", this.policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());
    }

    @Test
    void getCacheFails() throws InterruptedException, ExecutionException, IOException {
        PollingMode mode = PollingModes.manualPoll();
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(), logger, "", this.server.url("/").toString(), false, mode.getPollingIdentifier());
        ConfigService lPolicy = new ConfigService("", mode, new NullConfigCache(), logger, fetcher, new ConfigCatHooks(), false);

        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test2")).setBodyDelay(2, TimeUnit.SECONDS));

        //first call
        lPolicy.refresh().get();
        assertEquals("test", lPolicy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        //next call will get the new value
        lPolicy.refresh().get();
        assertEquals("test2", lPolicy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        lPolicy.close();
    }

    @Test
    void getWithFailedRefresh() throws InterruptedException, ExecutionException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(500));

        //first call
        this.policy.refresh().get();
        assertEquals("test", this.policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        //previous value returned because of the refresh failure
        this.policy.refresh().get();
        assertEquals("test", this.policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());
    }

    @Test
    void testCache() throws InterruptedException, ExecutionException, IOException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test2")));

        InMemoryCache cache = new InMemoryCache();
        PollingMode mode = PollingModes.manualPoll();
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(), logger, "", this.server.url("/").toString(), false, mode.getPollingIdentifier());
        ConfigService service = new ConfigService("", mode, cache, logger, fetcher, new ConfigCatHooks(), false);

        service.refresh().get();
        assertEquals("test", service.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        service.refresh().get();
        assertEquals("test2", service.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        assertEquals(1, cache.getMap().size());

        service.close();
    }

    @Test
    void testEmptyCacheDoesNotInitiateHTTP() throws InterruptedException, ExecutionException, IOException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));

        assertTrue(this.policy.getSettings().get().settings().isEmpty());
        assertEquals(0, this.server.getRequestCount());
    }

    @Test
    void testOnlineOffline() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));

        PollingMode pollingMode = PollingModes.manualPoll();
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService service = new ConfigService("", pollingMode, new NullConfigCache(), logger, fetcher, new ConfigCatHooks(), false);

        assertFalse(service.isOffline());
        assertTrue(service.refresh().get().isSuccess());
        assertEquals(1, this.server.getRequestCount());

        service.setOffline();

        assertTrue(service.isOffline());
        assertFalse(service.refresh().get().isSuccess());
        assertEquals(1, this.server.getRequestCount());

        service.setOnline();

        assertFalse(service.isOffline());
        assertTrue(service.refresh().get().isSuccess());
        assertEquals(2, this.server.getRequestCount());

        service.close();
    }

    @Test
    void testInitOffline() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));

        PollingMode pollingMode = PollingModes.manualPoll();
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService service = new ConfigService("", pollingMode, new NullConfigCache(), logger, fetcher, new ConfigCatHooks(), true);

        assertTrue(service.isOffline());
        assertFalse(service.refresh().get().isSuccess());
        assertEquals(0, this.server.getRequestCount());

        service.setOnline();
        assertFalse(service.isOffline());

        assertTrue(service.refresh().get().isSuccess());
        assertEquals(1, this.server.getRequestCount());

        service.close();
    }
}