package com.configcat.configcatsample

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.configcat.*

class MainActivity : AppCompatActivity() {
    private lateinit var client: ConfigCatClient

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        this.client = ConfigCatClient.newBuilder()
                .mode(PollingModes.autoPoll(5) {
                    run {
                        this.fetchNewConfig()
                    }
                })
                // Info level logging helps to inspect the feature flag evaluation process.
                // Use the default Warning level to avoid too detailed logging in your application.
                .logLevel(LogLevel.INFO)
                .build("977YCH1UFkOKnxvxOmQ96A/hFK5A8EqZUaQ2DExpuBl2Q")
    }

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }

    @SuppressLint("SetTextI18n")
    fun fetchNewConfig() {
        val user = User.newBuilder()
                .email("someone@example.com")
                .build("key")

        this.client.getValueAsync(Boolean::class.java, "isPOCFeatureEnabled", user, false)
            .thenAccept { value ->
                this@MainActivity.runOnUiThread {
                    val textField = findViewById<TextView>(R.id.editText)
                    textField.text = "isPOCFeatureEnabled: $value"
                }
            }
    }
}
