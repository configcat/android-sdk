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
                .mode(PollingModes.AutoPoll(5) {
                    run {
                        this.fetchNewConfig()
                    }
                })
                .build("PKDVCLf-Hq-h-kCzMp-L7Q/HhOWfwVtZ0mb30i9wi17GQ")
    }

    @SuppressLint("SetTextI18n")
    fun fetchNewConfig() {
        val user = User.newBuilder()
                .email("someone@example.com")
                .build("key")

        this@MainActivity.runOnUiThread {
            var textField = findViewById<TextView>(R.id.editText)
            textField.text = "isPOCFeatureEnabled: ${this.client.getKeyAndValue(
                    Boolean::class.java, 
                    "ca36009d"
            ).value}"
        }
    }
}
