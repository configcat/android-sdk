package com.configcat.configcatsample;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.configcat.*;


public class MainActivity extends AppCompatActivity {

    private ConfigCatClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        client = ConfigCatClient.get("PKDVCLf-Hq-h-kCzMp-L7Q/HhOWfwVtZ0mb30i9wi17GQ", options -> {
            options.pollingMode(PollingModes.autoPoll(5));

            // Use ConfigCat's shared preferences cache.
            options.cache(new SharedPreferencesCache(getApplicationContext()));

            // With this option, the SDK automatically switches between offline and online modes based on
            // whether the application is in the foreground or background and on network availability.
            options.watchAppStateChanges(getApplicationContext());

            // Info level logging helps to inspect the feature flag evaluation process.
            // Use the default Warning level to avoid too detailed logging in your application.
            options.logLevel(LogLevel.DEBUG);

            options.hooks().addOnConfigChanged(map -> fetchNewConfig());
        });
    }

    private void fetchNewConfig() {
        User user = User.newBuilder()
                .email("someone@example.com")
                .build("key");

        this.client.getValueAsync(Boolean.class, "isPOCFeatureEnabled", user, false)
                .thenAccept(value -> {
                    this.runOnUiThread(() -> {
                        TextView viewById = this.findViewById(R.id.editText);
                        viewById.setText(String.format("isPOCFeatureEnabled: %s", value));
                    });
                });
    }

}