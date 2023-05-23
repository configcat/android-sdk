package com.configcat;

import com.configcat.cache.CacheException;
import com.configcat.cache.ConfigCache;
import com.configcat.cache.NullConfigCache;
import com.configcat.cache.SingleValueCache;
import com.configcat.log.LogLevel;
import com.configcat.polling.PollingMode;
import com.configcat.polling.PollingModes;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutoPollingPolicyTest {
    private MockWebServer server;
    private final ConfigCatLogger logger = new ConfigCatLogger(LoggerFactory.getLogger(AutoPollingPolicyTest.class), LogLevel.WARNING, new ConfigCatHooks());
    private static final String TEST_JSON = "{ f: { fakeKey: { v: %s, p: [] ,r: [] } } }";

    @BeforeEach
    public void setUp() throws IOException {
        this.server = new MockWebServer();
        this.server.start();
    }

    @AfterEach
    public void tearDown() throws IOException {
        this.server.shutdown();
    }

    @Test
    void ensuresPollingIntervalGreaterThanOneSeconds() {
        assertThrows(IllegalArgumentException.class, () -> PollingModes.autoPoll(0));
    }

    @Test
    void get() throws InterruptedException, ExecutionException, IOException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test2")).setBodyDelay(3, TimeUnit.SECONDS));

        ConfigCache cache = new NullConfigCache();
        PollingMode pollingMode = PollingModes.autoPoll(2);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService policy = new ConfigService("", pollingMode, cache, logger, fetcher, new ConfigCatHooks(), false);

        //first call
        assertEquals("test", policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        //wait for cache refresh
        Thread.sleep(6000);

        //next call will get the new value
        assertEquals("test2", policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        policy.close();
    }

    @Test
    void getFail() throws InterruptedException, ExecutionException, IOException {
        this.server.enqueue(new MockResponse().setResponseCode(500).setBody(""));

        ConfigCache cache = new NullConfigCache();
        PollingMode pollingMode = PollingModes.autoPoll(2);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService policy = new ConfigService("", pollingMode, cache, logger, fetcher, new ConfigCatHooks(), false);

        //first call
        assertTrue(policy.getSettings().get().settings().isEmpty());

        policy.close();
    }

    @Test
    void getMany() throws InterruptedException, ExecutionException, IOException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test2")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test3")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test4")));

        ConfigCache cache = new NullConfigCache();
        PollingMode pollingMode = PollingModes.autoPoll(2);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService policy = new ConfigService("", pollingMode, cache, logger, fetcher, new ConfigCatHooks(), false);

        //first calls
        assertEquals("test", policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());
        assertEquals("test", policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());
        assertEquals("test", policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        //wait for cache refresh
        Thread.sleep(2500);

        //next call will get the new value
        assertEquals("test2", policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        //wait for cache refresh
        Thread.sleep(2500);

        //next call will get the new value
        assertEquals("test3", policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        //wait for cache refresh
        Thread.sleep(2500);

        //next call will get the new value
        assertEquals("test4", policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        policy.close();
    }

    @Test
    void getWithFailedRefresh() throws InterruptedException, ExecutionException, IOException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(500));

        ConfigCache cache = new NullConfigCache();
        PollingMode pollingMode = PollingModes.autoPoll(2);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService policy = new ConfigService("", pollingMode, cache, logger, fetcher, new ConfigCatHooks(), false);

        //first call
        assertEquals("test", policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        //wait for cache invalidation
        Thread.sleep(3000);

        //previous value returned because of the refresh failure
        assertEquals("test", policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        policy.close();
    }

    @Test
    void getCacheFails() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));

        ConfigCache cache = mock(ConfigCache.class);

        doThrow(new CacheException()).when(cache).read(anyString());
        doThrow(new CacheException()).when(cache).write(anyString(), anyString());

        PollingMode pollingMode = PollingModes.autoPoll(2);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService policy = new ConfigService("", pollingMode, cache, logger, fetcher, new ConfigCatHooks(), false);

        assertEquals("test", policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());

        policy.close();
    }

    @Test
    void testInitWaitTimeTimeout() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")).setBodyDelay(2, TimeUnit.SECONDS));

        PollingMode pollingMode = PollingModes.autoPoll(60, 1);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService policy = new ConfigService("", pollingMode, new NullConfigCache(), logger, fetcher, new ConfigCatHooks(), false);

        long start = System.currentTimeMillis();
        assertTrue(policy.getSettings().get().settings().isEmpty());
        long duration = System.currentTimeMillis() - start;
        assertTrue(duration < 1500);

        policy.close();
    }

    @Test
    void testPollIntervalRespectsCacheExpiration() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));

        ConfigCache cache = new SingleValueCache(Helpers.cacheValueFromConfigJson(String.format(TEST_JSON, "test")));

        PollingMode pollingMode = PollingModes.autoPoll(2);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService policy = new ConfigService("", pollingMode, cache, logger, fetcher, new ConfigCatHooks(), false);

        policy.getSettings().get();

        assertEquals(0, this.server.getRequestCount());

        Helpers.waitFor(3000, () -> this.server.getRequestCount() == 1);

        policy.close();
    }

    @Test
    void testNonExpiredCacheCallsReady() throws Exception {
        ConfigCache cache = new SingleValueCache(Helpers.cacheValueFromConfigJson(String.format(TEST_JSON, "test")));

        AtomicBoolean ready = new AtomicBoolean(false);
        ConfigCatHooks hooks = new ConfigCatHooks();
        hooks.addOnClientReady(() -> ready.set(true));
        PollingMode pollingMode = PollingModes.autoPoll(2);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService policy = new ConfigService("", pollingMode, cache, logger, fetcher, hooks, false);

        assertEquals(0, this.server.getRequestCount());

        Helpers.waitFor(ready::get);

        policy.close();
    }

    @Test
    void testOnlineOffline() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));

        PollingMode pollingMode = PollingModes.autoPoll(1);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService policy = new ConfigService("", pollingMode, new NullConfigCache(), logger, fetcher, new ConfigCatHooks(), false);

        Thread.sleep(1500);

        policy.setOffline();
        assertTrue(policy.isOffline());
        assertEquals(2, this.server.getRequestCount());

        Thread.sleep(2000);

        assertEquals(2, this.server.getRequestCount());
        policy.setOnline();
        assertFalse(policy.isOffline());

        Helpers.waitFor(() -> this.server.getRequestCount() >= 3);

        policy.close();
    }

    @Test
    void testInitOffline() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));

        PollingMode pollingMode = PollingModes.autoPoll(1);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService policy = new ConfigService("", pollingMode, new NullConfigCache(), logger, fetcher, new ConfigCatHooks(), true);

        assertTrue(policy.isOffline());
        assertEquals(0, this.server.getRequestCount());

        Thread.sleep(2000);

        assertEquals(0, this.server.getRequestCount());
        policy.setOnline();
        assertFalse(policy.isOffline());

        Helpers.waitFor(() -> this.server.getRequestCount() >= 2);

        policy.close();
    }

    @Test
    void testInitWaitTimeIgnoredWhenCacheIsNotExpired() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test1")).setBodyDelay(2, TimeUnit.SECONDS));

        ConfigCache cache = new SingleValueCache(Helpers.cacheValueFromConfigJson(String.format(TEST_JSON, "test")));

        PollingMode pollingMode = PollingModes.autoPoll(60, 1);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService policy = new ConfigService("", pollingMode, cache, logger, fetcher, new ConfigCatHooks(), false);

        long start = System.currentTimeMillis();
        assertFalse(policy.getSettings().get().settings().isEmpty());
        long duration = System.currentTimeMillis() - start;
        assertTrue(duration < 1000);

        policy.close();
    }

    @Test
    void testInitWaitTimeReturnCached() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test1")).setBodyDelay(2, TimeUnit.SECONDS));

        ConfigCache cache = new SingleValueCache(Helpers.cacheValueFromConfigJsonWithDistantPast(String.format(TEST_JSON, "test")));

        PollingMode pollingMode = PollingModes.autoPoll(60, 1);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                pollingMode.getPollingIdentifier());
        ConfigService policy = new ConfigService("", pollingMode, cache, logger, fetcher, new ConfigCatHooks(), false);

        long start = System.currentTimeMillis();
        assertEquals("test", policy.getSettings().get().settings().get("fakeKey").getValue().getAsString());
        long duration = System.currentTimeMillis() - start;
        assertTrue(duration < 1500);

        policy.close();
    }
}