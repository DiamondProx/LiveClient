package com.jiang.liveclient.ui.webrtc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jiang.liveclient.ui.theme.LiveClientTheme

class WebRTCActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveClientTheme {
                WebRTCScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
