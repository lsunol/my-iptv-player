package dev.lluissunol.iptv

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.lluissunol.iptv.ui.OpenTvApp

class MainActivity : ComponentActivity() {
    // Language: no custom logic. Android picks the first language of the
    // user's list that the app ships (en, es, ca, de, fr, it, pt) and falls
    // back to English (`values/`) when none matches.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent { OpenTvApp() }
    }
}
