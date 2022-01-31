package com.configcat;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import org.slf4j.LoggerFactory;

import java9.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ManualPollingPolicyTest {
    private RefreshPolicyBase policy;
    private MockWebServer server;
    private final ConfigCatLogger logger = new ConfigCatLogger(LoggerFactory.getLogger(ManualPollingPolicyTest.class));
    private static final String TEST_JSON = "{ f: { fakeKey: { v: %s, p: [] ,r: [] } } }";

    @BeforeEach
    public void setUp() throws IOException {
        this.server = new MockWebServer();
        this.server.start();

        ConfigJsonCache memoryCache = new ConfigJsonCache(logger, new NullConfigCache(), "");
        PollingMode mode = PollingModes.manualPoll();
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(), logger, memoryCache, "", this.server.url("/").toString(), false, mode.getPollingIdentifier());
        this.policy = new ManualPollingPolicy(fetcher, logger, memoryCache);
    }

    @AfterEach
    public void tearDown() throws IOException {
        this.policy.close();
        this.server.shutdown();
    }

    @Test
    public void get() throws InterruptedException, ExecutionException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test2")).setBodyDelay(2, TimeUnit.SECONDS));

        //first call
        this.policy.refreshAsync().get();
        assertEquals("test", this.policy.getConfigurationAsync().get().entries.get("fakeKey").value.getAsString());

        //next call will get the new value
        this.policy.refreshAsync().get();
        assertEquals("test2", this.policy.getConfigurationAsync().get().entries.get("fakeKey").value.getAsString());
    }

    @Test
    public void getCacheFails() throws InterruptedException, ExecutionException {
        PollingMode mode = PollingModes.manualPoll();
        ConfigJsonCache cache = new ConfigJsonCache(logger, new FailingCache(), "");
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(), logger, cache, "", this.server.url("/").toString(), false, mode.getPollingIdentifier());
        RefreshPolicyBase lPolicy = new ManualPollingPolicy(fetcher, logger, cache);

        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test2")).setBodyDelay(2, TimeUnit.SECONDS));

        //first call
        lPolicy.refreshAsync().get();
        assertEquals("test", lPolicy.getConfigurationAsync().get().entries.get("fakeKey").value.getAsString());

        //next call will get the new value
        lPolicy.refreshAsync().get();
        assertEquals("test2", lPolicy.getConfigurationAsync().get().entries.get("fakeKey").value.getAsString());
    }

    @Test
    public void getWithFailedRefresh() throws InterruptedException, ExecutionException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, "test")));
        this.server.enqueue(new MockResponse().setResponseCode(500));

        //first call
        this.policy.refreshAsync().get();
        assertEquals("test", this.policy.getConfigurationAsync().get().entries.get("fakeKey").value.getAsString());

        //previous value returned because of the refresh failure
        this.policy.refreshAsync().get();
        assertEquals("test", this.policy.getConfigurationAsync().get().entries.get("fakeKey").value.getAsString());
    }

    @Test
    public void getFetchedSameResponseUpdatesCache() throws Exception {
        String result = "test";
        ConfigCache cache = mock(ConfigCache.class);
        ConfigJsonCache memoryCache = new ConfigJsonCache(logger, cache, "");
        ConfigFetcher fetcher = mock(ConfigFetcher.class);
        when(cache.read(anyString())).thenReturn(String.format(TEST_JSON, result));
        when(fetcher.fetchAsync())
                .thenReturn(CompletableFuture.completedFuture(new FetchResponse(FetchResponse.Status.FETCHED, memoryCache.readFromJson(String.format(TEST_JSON, result), ""))));
        ManualPollingPolicy policy = new ManualPollingPolicy(fetcher, logger, memoryCache);
        policy.refreshAsync().get();
        assertEquals(result, policy.getConfigurationAsync().get().entries.get("fakeKey").value.getAsString());
        verify(cache, atMostOnce()).write(anyString(), eq(String.format(TEST_JSON, result)));
    }
}