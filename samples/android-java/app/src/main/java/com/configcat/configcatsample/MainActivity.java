package com.configcat.configcatsample;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.configcat.ConfigCatClient;
import com.configcat.LogLevel;
import com.configcat.SharedPreferencesCache;
import com.configcat.User;


public class MainActivity extends AppCompatActivity {

    private ConfigCatClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        client = ConfigCatClient.get("PKDVCLf-Hq-h-kCzMp-L7Q/HhOWfwVtZ0mb30i9wi17GQ", options -> {
            // Use ConfigCat's shared preferences cache.
            options.cache(new SharedPreferencesCache(getApplicationContext()));

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
                        viewById.setText("isPOCFeatureEnabled: " + value);
                    });
                });
    }

}