package app.opentv

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.opentv.ui.OpenTvApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The device has a punch-hole camera cutout that is on TOP in portrait
        // and on the LEFT in landscape. With the default LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
        // the displayCutout inset is reported to Compose as a left inset while the player is
        // fullscreen in landscape. When the user exits fullscreen and returns to portrait, a
        // stale left cutout inset can leak into the Scaffold content padding, shifting the whole
        // list ~103px to the right with a dark band on the left.
        //
        // SHORT_EDGES stops the system from ever reporting the cutout as a left/right inset
        // (it is only honored on the short edges), which removes that class of bug at the source.
        // The status bar inset still keeps the TopAppBar clear of the camera in portrait, and the
        // fullscreen player is still free to draw behind the hole (the desired immersive behavior).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent { OpenTvApp() }
    }
}
