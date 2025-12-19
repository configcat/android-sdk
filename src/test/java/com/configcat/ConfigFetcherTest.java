package com.configcat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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

        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(), logger,
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
        ConfigFetcher fetch = new ConfigFetcher(new ConfigCatClient.HttpOptions().readTimeoutMillis(1000),
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
        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(), logger,
                "", this.server.url("/").toString(), false, PollingModes.manualPoll().getPollingIdentifier());

        ConfigService policy = new ConfigService("", null, PollingModes.autoPoll(2), cache, logger, fetcher, new ConfigCatHooks(), false);
        assertEquals("fakeValue", policy.getSettings().get().settings().get("fakeKey").getSettingsValue().getStringValue());

        verify(cache, never()).write(anyString(), eq(TEST_JSON));

        policy.close();
    }

    @Test
    void fetchedSameResponseNotUpdatesCache() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));

        ConfigCache cache = mock(ConfigCache.class);
        when(cache.read(anyString())).thenReturn(TEST_JSON);
        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(), logger,
                "", this.server.url("/").toString(), false, PollingModes.manualPoll().getPollingIdentifier());

        ConfigService policy = new ConfigService("", null, PollingModes.autoPoll(2), cache, logger, fetcher, new ConfigCatHooks(), false);
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

        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(), logger,
                "", this.server.url("/").toString(), false, PollingModes.manualPoll().getPollingIdentifier());

        FetchResponse response = fetcher.fetchAsync(null).get();
        assertTrue(response.isFetched());
        assertEquals("fakeValue", response.entry().getConfig().getEntries().get("fakeKey").getSettingsValue().getStringValue());

        fetcher.close();
    }

    private static Stream<Arguments> emptyFetchTestData() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("null")
        );
    }

    @ParameterizedTest
    @MethodSource("emptyFetchTestData")
    void fetchEmpty(String body) throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(body));

        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(),
                logger,
                "",
                this.server.url("/").toString(),
                false,
                PollingModes.manualPoll().getPollingIdentifier());

        FetchResponse response = fetcher.fetchAsync(null).get();
        assertFalse(response.isFetched());
        assertEquals("Fetching config JSON was successful but the HTTP response content was invalid.", response.error().toString());

        fetcher.close();
    }

    @Test
    void testIntegration() throws IOException, ExecutionException, InterruptedException {
        ConfigFetcher fetch = new ConfigFetcher(new ConfigCatClient.HttpOptions().readTimeoutMillis(1000),
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

    @Test
    void fetchedFail403ContainsCFRAY() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(403).setBody(TEST_JSON).setHeader("ETag", "fakeETag").setHeader("CF-RAY", "12345"));

        Logger mockLogger = mock(Logger.class);

        ConfigCatLogger localLogger = new ConfigCatLogger(mockLogger, LogLevel.DEBUG, null, null);

        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(),
                localLogger,
                "",
                this.server.url("/").toString(),
                false,
                PollingModes.manualPoll().getPollingIdentifier());


        FetchResponse response = fetcher.fetchAsync("fakeETag").get();
        assertTrue(response.isFailed());
        assertTrue(response.error().toString().contains("(Ray ID: 12345)"));

        verify(mockLogger, times(1)).error(anyString(),  eq(1100), eq(ConfigCatLogMessages.getFetchFailedDueToInvalidSDKKey("12345")));

        fetcher.close();
    }

    @Test
    void fetchedNotModified304ContainsCFRAY() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(304).setHeader("CF-RAY", "12345"));

        Logger mockLogger = mock(Logger.class);

        ConfigCatLogger localLogger = new ConfigCatLogger(mockLogger, LogLevel.DEBUG, null, null);

        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(),
                localLogger,
                "",
                this.server.url("/").toString(),
                false,
                PollingModes.manualPoll().getPollingIdentifier());


        FetchResponse response = fetcher.fetchAsync("fakeETag").get();

        assertTrue(response.isNotModified());

        verify(mockLogger, times(1)).debug(anyString(), eq(0), eq(String.format("Fetch was successful: config not modified. %s", ConfigCatLogMessages.getCFRayIdPostFix("12345"))));

        fetcher.close();
    }

    @Test
    void fetchedReceivedInvalidBodyContainsCFRAY() throws Exception {
        this.server.enqueue(new MockResponse().setResponseCode(200).setHeader("CF-RAY", "12345").setBody("test"));

        Logger mockLogger = mock(Logger.class);

        ConfigCatLogger localLogger = new ConfigCatLogger(mockLogger, LogLevel.DEBUG, null, null);

        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(),
                localLogger,
                "",
                this.server.url("/").toString(),
                false,
                PollingModes.manualPoll().getPollingIdentifier());


        FetchResponse response = fetcher.fetchAsync("fakeETag").get();

        assertTrue(response.isFailed());
        assertTrue(response.error().toString().contains("(Ray ID: 12345)"));

        verify(mockLogger, times(1)).error(anyString(), eq(1105), eq(ConfigCatLogMessages.getFetchReceived200WithInvalidBodyError("12345")), any(), any(Exception.class));

        fetcher.close();
    }

    @Test
    void ensureStateMonitorWorks() throws IOException {
        this.server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON));
        ConfigFetcher fetcher = new ConfigFetcher(new ConfigCatClient.HttpOptions(), logger,
                "", this.server.url("/").toString(), false, PollingModes.manualPoll().getPollingIdentifier());

        TestStateMonitor monitor = new TestStateMonitor();
        ConfigService service = new ConfigService("", monitor, PollingModes.autoPoll(), new NullConfigCache(), logger, fetcher, new ConfigCatHooks(), false);

        assertFalse(service.isOffline());

        monitor.setState(false);
        monitor.notifyListeners();

        assertTrue(service.isOffline());

        monitor.setState(true);
        monitor.notifyListeners();

        assertFalse(service.isOffline());

        service.setOffline();
        assertTrue(service.isOffline());

        monitor.setState(false);
        monitor.notifyListeners();

        assertTrue(service.isOffline());

        monitor.setState(true);
        monitor.notifyListeners();

        assertTrue(service.isOffline());

        service.setOnline();

        assertFalse(service.isOffline());

        service.close();
    }
}
