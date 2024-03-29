package com.configcat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.OkHttpClient;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConfigFetcherTest {
    private MockWebServer server;
    private final ConfigCatLogger logger = new ConfigCatLogger(LoggerFactory.getLogger(ConfigFetcherTest.class), LogLevel.WARNING, new ConfigCatHooks());
    private static final String TEST_JSON = "{ p: { s: 'test-salt' }, f: { fakeKey: { v: {s: 'fakeValue'}, s: 0, p: [] ,r: [] } } }";

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
    void fetchNotModified() throws InterruptedException, ExecutionException, IOException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON).setHeader("ETag", "fakeETag"));
        this.server.enqueue(new MockResponse().setResponseCode(304));

        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(), logger,
                "", this.server.url("/").toString(), false, PollingModes.manualPoll().getPollingIdentifier());

        FetchResponse fResult = fetcher.fetchAsync(null).get();

        assertEquals("fakeValue", fResult.entry().getConfig().getEntries().get("fakeKey").getSettingsValue().getStringValue());
        assertTrue(fResult.isFetched());
        assertFalse(fResult.isNotModified());
        assertFalse(fResult.isFailed());

        FetchResponse notModifiedResponse = fetcher.fetchAsync(fResult.entry().getETag()).get();
        assertTrue(notModifiedResponse.isNotModified());
        assertFalse(notModifiedResponse.isFailed());
        assertFalse(notModifiedResponse.isFetched());

        assertNull(this.server.takeRequest().getHeader("If-None-Match"));
        assertEquals("fakeETag", this.server.takeRequest().getHeader("If-None-Match"));

        fetcher.close();
    }

    @Test
    void fetchException() throws IOException, ExecutionException, InterruptedException {

        ConfigFetcher fetch = new ConfigFetcher(new OkHttpClient.Builder()
                .readTimeout(1, TimeUnit.SECONDS)
                .build(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                PollingModes.manualPoll().getPollingIdentifier());

        this.server.enqueue(new MockResponse().setBody("test").setBodyDelay(2, TimeUnit.SECONDS));

        FetchResponse response = fetch.fetchAsync(null).get();
        assertTrue(response.isFailed());
        assertEquals(Entry.EMPTY, response.entry());

        fetch.close();
    }

    @Test
    void fetchedETagNotUpdatesCache() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON).setHeader("ETag", "fakeETag"));
        this.server.enqueue(new MockResponse().setResponseCode(304));

        Gson gson = new GsonBuilder().create();
        Config config = gson.fromJson(TEST_JSON, Config.class);
        Entry entry = new Entry(config, "fakeETag", TEST_JSON, Constants.DISTANT_PAST);

        ConfigCache cache = mock(ConfigCache.class);
        when(cache.read(anyString())).thenReturn(gson.toJson(entry));
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(), logger,
                "", this.server.url("/").toString(), false, PollingModes.manualPoll().getPollingIdentifier());

        ConfigService policy = new ConfigService("", PollingModes.autoPoll(2), cache, logger, fetcher, new ConfigCatHooks(), false);
        assertEquals("fakeValue", policy.getSettings().get().settings().get("fakeKey").getSettingsValue().getStringValue());

        verify(cache, never()).write(anyString(), eq(TEST_JSON));

        policy.close();
    }

    @Test
    void fetchedSameResponseNotUpdatesCache() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));

        ConfigCache cache = mock(ConfigCache.class);
        when(cache.read(anyString())).thenReturn(TEST_JSON);
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(), logger,
                "", this.server.url("/").toString(), false, PollingModes.manualPoll().getPollingIdentifier());

        ConfigService policy = new ConfigService("", PollingModes.autoPoll(2), cache, logger, fetcher, new ConfigCatHooks(), false);
        assertEquals("fakeValue", policy.getSettings().get().settings().get("fakeKey").getSettingsValue().getStringValue());

        verify(cache, never()).write(anyString(), eq(TEST_JSON));

        policy.close();
    }

    @Test
    void fetchSuccess() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));
        ConfigCache cache = mock(ConfigCache.class);

        doThrow(new Exception()).when(cache).read(anyString());
        doThrow(new Exception()).when(cache).write(anyString(), anyString());

        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(), logger,
                "", this.server.url("/").toString(), false, PollingModes.manualPoll().getPollingIdentifier());

        FetchResponse response = fetcher.fetchAsync(null).get();
        assertTrue(response.isFetched());
        assertEquals("fakeValue", response.entry().getConfig().getEntries().get("fakeKey").getSettingsValue().getStringValue());

        fetcher.close();
    }

    @Test
    void testIntegration() throws IOException, ExecutionException, InterruptedException {
        ConfigFetcher fetch = new ConfigFetcher(new OkHttpClient.Builder()
                .readTimeout(1, TimeUnit.SECONDS)
                .build(),
                logger,
                "PKDVCLf-Hq-h-kCzMp-L7Q/PaDVCFk9EpmD6sLpGLltTA",
                "https://cdn-global.configcat.com",
                false,
                PollingModes.manualPoll().getPollingIdentifier());

        FetchResponse result = fetch.fetchAsync(null).get();
        assertTrue(result.isFetched());
        assertTrue(fetch.fetchAsync(result.entry().getETag()).get().isNotModified());

        fetch.close();
    }
}
