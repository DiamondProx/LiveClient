package com.jiang.liveclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jiang.liveclient.ui.debug.DebugScreen
import com.jiang.liveclient.ui.theme.LiveClientTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveClientTheme {
                DebugScreen()
            }
        }
    }
}