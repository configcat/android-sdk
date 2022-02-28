package com.configcat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalTest {
    private static final String TEST_JSON = "{ f: { fakeKey: { v: %s, p: [] ,r: [] } } }";

    @Test
    public void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> ConfigCatClient.newBuilder()
                .flagOverrides(null, null).build("key"));
        assertThrows(IllegalArgumentException.class, () -> ConfigCatClient.newBuilder()
                .flagOverrides(null, OverrideBehaviour.LOCAL_ONLY).build("key"));
        Map<String, Object> map = new HashMap<>();
        map.put("enabledFeature", true);
        assertThrows(IllegalArgumentException.class, () -> ConfigCatClient.newBuilder()
                .flagOverrides(OverrideDataSource.map(map), null).build("key"));
    }

    @Test
    public void object() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("enabledFeature", true);
        map.put("disabledFeature", false);
        map.put("intSetting", 5);
        map.put("doubleSetting", 3.14);
        map.put("stringSetting", "test");
        ConfigCatClient client = new ConfigCatClient.Builder()
                .flagOverrides(OverrideDataSource.map(map), OverrideBehaviour.LOCAL_ONLY)
                .build("localhost");

        assertTrue(client.getValue(Boolean.class, "enabledFeature", User.newBuilder().build("test"), false));
        assertFalse(client.getValue(Boolean.class, "disabledFeature", User.newBuilder().build("test"), true));
        assertEquals(5, (int)client.getValue(Integer.class, "intSetting", User.newBuilder().build("test"), 0));
        assertEquals(3.14, (double)client.getValue(Double.class, "doubleSetting", User.newBuilder().build("test"), 0.0));
        assertEquals("test", client.getValue(String.class, "stringSetting", User.newBuilder().build("test"), ""));

        client.close();
    }

    @Test
    public void getAll() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("enabledFeature", true);
        map.put("disabledFeature", false);
        map.put("intSetting", 5);
        map.put("doubleSetting", 3.14);
        map.put("stringSetting", "test");
        ConfigCatClient client = new ConfigCatClient.Builder()
                .flagOverrides(OverrideDataSource.map(map), OverrideBehaviour.LOCAL_ONLY)
                .build("localhost");

        Map<String, Object> values = client.getAllValues(User.newBuilder().build("test"));
        assertEquals(5, values.entrySet().size());

        client.close();
    }

    @Test
    public void localOverRemote() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        Map<String, Object> map = new HashMap<>();
        map.put("fakeKey", true);
        map.put("nonexisting", true);
        ConfigCatClient client = new ConfigCatClient.Builder()
                .mode(PollingModes.manualPoll())
                .baseUrl(server.url("/").toString())
                .flagOverrides(OverrideDataSource.map(map), OverrideBehaviour.LOCAL_OVER_REMOTE)
                .build("localhost");

        server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, false)));

        client.forceRefresh();
        assertTrue(client.getValue(Boolean.class, "fakeKey", false));
        assertTrue(client.getValue(Boolean.class, "nonexisting", false));

        server.close();
        client.close();
    }

    @Test
    public void remoteOverLocal() throws IOException {
        MockWebServer server = new MockWebServer();
        server.start();

        Map<String, Object> map = new HashMap<>();
        map.put("fakeKey", true);
        map.put("nonexisting", true);
        ConfigCatClient client = new ConfigCatClient.Builder()
                .mode(PollingModes.manualPoll())
                .baseUrl(server.url("/").toString())
                .flagOverrides(OverrideDataSource.map(map), OverrideBehaviour.REMOTE_OVER_LOCAL)
                .build("localhost");

        server.enqueue(new MockResponse().setResponseCode(200).setBody(String.format(TEST_JSON, false)));

        client.forceRefresh();
        assertFalse(client.getValue(Boolean.class, "fakeKey", false));
        assertTrue(client.getValue(Boolean.class, "nonexisting", false));

        server.close();
        client.close();
    }
}
