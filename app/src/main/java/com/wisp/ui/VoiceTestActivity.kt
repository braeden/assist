package com.wisp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wisp.ui.theme.WispTheme
import dagger.hilt.android.AndroidEntryPoint

/** Hosts the manual voice test screen (phase-08). Launched from onboarding. */
@AndroidEntryPoint
class VoiceTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WispTheme {
                VoiceTestScreen()
            }
        }
    }
}
