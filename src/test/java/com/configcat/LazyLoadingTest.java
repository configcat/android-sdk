package com.configcat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LazyLoadingTest {
    private ConfigService policy;
    private MockWebServer server;
    private final ConfigCatLogger logger = new ConfigCatLogger(LoggerFactory.getLogger(LazyLoadingTest.class));

    private static final String TEST_JSON = "{ p: { s: 'test-salt'}, f: { fakeKey: { v: { s: %s }, p: [], r: [] } } }";

    @BeforeEach
    public void setUp() throws IOException {
        this.server = new MockWebServer();
        this.server.start();

        PollingMode mode = PollingModes
                .lazyLoad(5);
        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(), logger, "", this.server.url("/").toString(), false, mode.getPollingIdentifier());
        this.policy = new ConfigService("", null, mode, new NullConfigCache(), logger, fetcher, new ConfigCatHooks(), false);
    }

    @AfterEach
    public void tearDown() throws IOException {
        this.policy.close();
        this.server.shutdown();
    }

    @Test
    void get() throws InterruptedException, ExecutionException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test2")).setBodyDelay(3, TimeUnit.SECONDS));

        //first call
        assertEquals("test", this.policy.getSettings().get().settings().get("fakeKey").getSettingsValue().getStringValue());

        //wait for cache invalidation
        Thread.sleep(6000);

        //next call will block until the new value is fetched
        assertEquals("test2", this.policy.getSettings().get().settings().get("fakeKey").getSettingsValue().getStringValue());
    }

    @Test
    void getCacheFails() throws InterruptedException, ExecutionException {
        PollingMode mode = PollingModes
                .lazyLoad(5);
        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(), logger, "", this.server.url("/").toString(), false, mode.getPollingIdentifier());
        ConfigService lPolicy = new ConfigService("", null, mode, new NullConfigCache(), logger, fetcher, new ConfigCatHooks(), false);

        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test2")).setBodyDelay(3, TimeUnit.SECONDS));

        //first call
        assertEquals("test", lPolicy.getSettings().get().settings().get("fakeKey").getSettingsValue().getStringValue());

        //wait for cache invalidation
        Thread.sleep(6000);

        //next call will block until the new value is fetched
        assertEquals("test2", lPolicy.getSettings().get().settings().get("fakeKey").getSettingsValue().getStringValue());
    }

    @Test
    void getWithFailedRefresh() throws InterruptedException, ExecutionException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(500));

        //first call
        assertEquals("test", this.policy.getSettings().get().settings().get("fakeKey").getSettingsValue().getStringValue());

        //wait for cache invalidation
        Thread.sleep(6000);

        //previous value returned because of the refresh failure
        assertEquals("test", this.policy.getSettings().get().settings().get("fakeKey").getSettingsValue().getStringValue());
    }

    @Test
    void testCacheExpirationRespectedInTTLCalc() throws InterruptedException, ExecutionException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));

        ConfigCache cache = new SingleValueCache(Helpers.cacheValueFromConfigJson(String.format(TEST_JSON, "test")));

        PollingMode mode = PollingModes
                .lazyLoad(1);
        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(), logger, "", this.server.url("/").toString(), false, mode.getPollingIdentifier());
        ConfigService service = new ConfigService("", null, mode, cache, logger, fetcher, new ConfigCatHooks(), false);

        assertFalse(service.getSettings().get().settings().isEmpty());
        assertFalse(service.getSettings().get().settings().isEmpty());

        assertEquals(0, this.server.getRequestCount());

        Thread.sleep(1000);

        assertFalse(service.getSettings().get().settings().isEmpty());
        assertFalse(service.getSettings().get().settings().isEmpty());

        assertEquals(1, this.server.getRequestCount());
    }

    @Test
    void testCacheExpirationRespectedInTTLCalc304() throws InterruptedException, ExecutionException {
        this.server.enqueue(new MockResponse().setResponseCode(304).setBody(""));

        ConfigCache cache = new SingleValueCache(Helpers.cacheValueFromConfigJson(String.format(TEST_JSON, "test")));

        PollingMode mode = PollingModes
                .lazyLoad(1);
        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(), logger, "", this.server.url("/").toString(), false, mode.getPollingIdentifier());
        ConfigService service = new ConfigService("", null, mode, cache, logger, fetcher, new ConfigCatHooks(), false);

        assertFalse(service.getSettings().get().settings().isEmpty());
        assertFalse(service.getSettings().get().settings().isEmpty());

        assertEquals(0, this.server.getRequestCount());

        Thread.sleep(1000);

        assertFalse(service.getSettings().get().settings().isEmpty());
        assertFalse(service.getSettings().get().settings().isEmpty());

        assertEquals(1, this.server.getRequestCount());
    }

    @Test
    void testOnlineOffline() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));

        PollingMode pollingMode = PollingModes.lazyLoad(1);
        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService service = new ConfigService("", null, pollingMode, new NullConfigCache(), logger, fetcher, new ConfigCatHooks(), false);

        assertFalse(service.getSettings().get().settings().isEmpty());
        assertEquals(1, this.server.getRequestCount());

        service.setOffline();
        assertTrue(service.isOffline());

        Thread.sleep(1500);

        assertFalse(service.getSettings().get().settings().isEmpty());
        assertEquals(1, this.server.getRequestCount());

        service.setOnline();
        assertFalse(service.isOffline());

        assertFalse(service.getSettings().get().settings().isEmpty());
        assertEquals(2, this.server.getRequestCount());

        service.close();
    }

    @Test
    void testInitOffline() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));

        PollingMode pollingMode = PollingModes.lazyLoad(1);
        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService service = new ConfigService("", null, pollingMode, new NullConfigCache(), logger, fetcher, new ConfigCatHooks(), true);

        assertTrue(service.getSettings().get().settings().isEmpty());
        assertEquals(0, this.server.getRequestCount());

        Thread.sleep(1500);

        assertTrue(service.getSettings().get().settings().isEmpty());
        assertEquals(0, this.server.getRequestCount());

        service.setOnline();
        assertFalse(service.isOffline());

        assertFalse(service.getSettings().get().settings().isEmpty());
        assertEquals(1, this.server.getRequestCount());

        service.close();
    }
}