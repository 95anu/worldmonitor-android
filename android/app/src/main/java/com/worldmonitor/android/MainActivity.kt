package com.worldmonitor.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.worldmonitor.android.ui.navigation.AppNavigation
import com.worldmonitor.android.ui.theme.WorldMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorldMonitorTheme {
                AppNavigation()
            }
        }
    }
}
