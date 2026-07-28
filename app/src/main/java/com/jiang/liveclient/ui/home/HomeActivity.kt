package com.jiang.liveclient.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jiang.liveclient.MainActivity
import com.jiang.liveclient.ui.theme.LiveClientTheme
import com.jiang.liveclient.ui.webview.WebViewActivity
import com.jiang.liveclient.ui.webrtc.WebRTCActivity

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveClientTheme {
                HomeScreen(
                    onNavigateToNative = {
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                    onNavigateToWebView = {
                        startActivity(Intent(this, WebViewActivity::class.java))
                    },
                    onNavigateToWebRTCNew = {
                        startActivity(Intent(this, WebRTCActivity::class.java))
                    }
                )
            }
        }
    }
}