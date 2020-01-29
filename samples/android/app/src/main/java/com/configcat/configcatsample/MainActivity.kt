package com.configcat.configcatsample

import android.annotation.SuppressLint
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.configcat.*

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ConfigCatClient.newBuilder()
                .mode(PollingModes.AutoPoll(5) { parser, newConfiguration ->
                    run {
                        // create a user object to identify the caller
                        val user = User.newBuilder()
                                .email("someone@example.com")
                                .build("key")

                        var config = parser.parseValue(Boolean::class.java, newConfiguration, "isPOCFeatureEnabled", user)
                        this@MainActivity.runOnUiThread {
                            var textField = findViewById<TextView>(R.id.editText)
                            textField.text = "isPOCFeatureEnabled: $config"
                        }
                    }
                })
                .build("PKDVCLf-Hq-h-kCzMp-L7Q/HhOWfwVtZ0mb30i9wi17GQ")
    }
}
