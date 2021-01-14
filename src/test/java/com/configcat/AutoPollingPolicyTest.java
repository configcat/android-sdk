package com.configcat;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java9.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class AutoPollingPolicyTest {
    private final Logger logger = LoggerFactory.getLogger(AutoPollingPolicyTest.class);

    @Test
    public void getCacheFails() throws Exception {
        String result = "test";

        ConfigFetcher fetcher = mock(ConfigFetcher.class);
        ConfigCache cache = mock(ConfigCache.class);

        doThrow(new Exception()).when(cache).read(anyString());
        doThrow(new Exception()).when(cache).write(anyString(), anyString());

        when(fetcher.getConfigurationJsonStringAsync())
                .thenReturn(CompletableFuture.completedFuture(new FetchResponse(FetchResponse.Status.FETCHED, result)));

        RefreshPolicy policy = new AutoPollingPolicy(fetcher, cache, logger, "", (AutoPollingMode)PollingModes.AutoPoll(2));

        assertEquals(result, policy.getConfigurationJsonAsync().get());
    }

    @Test
    public void getFetchedSameResponseNotUpdatesCache() throws Exception {
        String result = "test";

        ConfigFetcher fetcher = mock(ConfigFetcher.class);
        ConfigCache cache = mock(ConfigCache.class);

        when(cache.read(anyString())).thenReturn(result);

        when(fetcher.getConfigurationJsonStringAsync())
                .thenReturn(CompletableFuture.completedFuture(new FetchResponse(FetchResponse.Status.FETCHED, result)));

        RefreshPolicy policy = new AutoPollingPolicy(fetcher, cache, logger, "", (AutoPollingMode)PollingModes.AutoPoll(2));
        assertEquals("test", policy.getConfigurationJsonAsync().get());

        verify(cache, never()).write(anyString(), eq(result));
    }

    @Test
    public void configChanged() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.start();

        AtomicBoolean isCalled  = new AtomicBoolean();
        PollingMode mode = PollingModes
                .AutoPoll(2, () -> isCalled.set(true));
        ConfigFetcher fetcher = new ConfigFetcher(new OkHttpClient.Builder().build(), logger, "", server.url("/").toString(), false, mode.getPollingIdentifier());
        ConfigCache cache = new InMemoryConfigCache();

        RefreshPolicy policy = new AutoPollingPolicy(fetcher, cache, logger, "", (AutoPollingMode)mode);

        server.enqueue(new MockResponse().setResponseCode(200).setBody("test"));

        Thread.sleep(1000);

        assertTrue(isCalled.get());

        server.close();
        policy.close();
    }
}
