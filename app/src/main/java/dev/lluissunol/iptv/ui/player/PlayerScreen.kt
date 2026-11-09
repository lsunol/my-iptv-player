package dev.lluissunol.iptv.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import dev.lluissunol.iptv.R
import kotlinx.coroutines.delay
import dev.lluissunol.iptv.data.model.Channel
import dev.lluissunol.iptv.player.userMessage

private const val ORIENTATION_RELEASE_DELAY_MS = 700L

/** Maps a remembered [Configuration] orientation back to a request code. */
private fun restoreOrientation(orientation: Int): Int = when (orientation) {
    Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    channel: Channel,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val configuration = LocalConfiguration.current
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    val player = remember(channel.id) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("OpenIPTV/0.1 (Android)")
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(45_000)
            .setAllowCrossProtocolRedirects(true)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                // `mute()` scales this player's own output to 0. It is not
                // `setDeviceVolume`/`setDeviceMuted`: the phone's volume and any
                // other app playing audio are left completely alone. Applied
                // here, before `prepare()`, so a muted channel never blips.
                if (muted) mute()
            }
    }

    // Covers toggles after creation; the factory above covers creation itself,
    // which is why the player must never be rebuilt just because `muted` changed.
    LaunchedEffect(player, muted) {
        if (muted) player.mute() else player.unmute()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.userMessage()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(channel.id) {
        playbackError = null
        val item = MediaItem.Builder()
            .setUri(channel.url)
            .setMediaId(channel.id)
            .apply {
                if (channel.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player.pause()
    }

    // Entering fullscreen forces the window to landscape. On some OEM builds
    // (observed on One UI) that request also rewrites the system's user rotation,
    // so handing control back with SCREEN_ORIENTATION_UNSPECIFIED leaves the
    // whole app stuck in landscape once fullscreen is exited. Remember the
    // orientation in use beforehand and restore it explicitly instead.
    val orientationBeforeFullscreen =
        remember { intArrayOf(Configuration.ORIENTATION_UNDEFINED) }

    DisposableEffect(activity, isFullscreen) {
        if (activity != null) {
            if (isFullscreen) {
                if (orientationBeforeFullscreen[0] == Configuration.ORIENTATION_UNDEFINED) {
                    orientationBeforeFullscreen[0] = configuration.orientation
                }
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                hideSystemBars(activity)
            } else {
                val previous = orientationBeforeFullscreen[0]
                orientationBeforeFullscreen[0] = Configuration.ORIENTATION_UNDEFINED
                activity.requestedOrientation = restoreOrientation(previous)
                showSystemBars(activity)
            }
        }
        onDispose {
            if (activity != null) {
                activity.requestedOrientation = restoreOrientation(orientationBeforeFullscreen[0])
                showSystemBars(activity)
            }
        }
    }

    // Restoring the orientation explicitly holds the display in place long enough
    // for the system to settle; after that, hand orientation back to the user so a
    // device with auto-rotate enabled can still be turned while watching.
    LaunchedEffect(activity, isFullscreen) {
        if (activity != null && !isFullscreen) {
            delay(ORIENTATION_RELEASE_DELAY_MS)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(activity, configuration.orientation, isFullscreen) {
        if (activity != null && isFullscreen) hideSystemBars(activity)
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBack()
        }
    }

    Scaffold(
        contentWindowInsets = if (isFullscreen) {
            WindowInsets(0, 0, 0, 0)
        } else {
            WindowInsets.systemBars
        },
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = channel.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            channel.group?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        MuteButton(muted = muted, onToggle = onToggleMute)
                        IconButton(onClick = { isFullscreen = true }) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.fullscreen)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        keepScreenOn = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        this.player = player
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize()
            )

            // Silence is deliberate, so it must never look like a broken stream:
            // a badge stays on screen for as long as the player is muted.
            if (muted) {
                MutedBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
            }

            if (isFullscreen) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MuteButton(
                        muted = muted,
                        onToggle = onToggleMute,
                        modifier = Modifier.background(
                            Color.Black.copy(alpha = 0.45f),
                            CircleShape
                        ),
                        tint = Color.White
                    )
                    IconButton(
                        onClick = { isFullscreen = false },
                        modifier = Modifier.background(
                            Color.Black.copy(alpha = 0.45f),
                            CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = stringResource(R.string.exit_fullscreen),
                            tint = Color.White
                        )
                    }
                }
            }

            playbackError?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = message,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                playbackError = null
                                player.prepare()
                                player.playWhenReady = true
                            }
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                        TextButton(onClick = onBack) {
                            Text(stringResource(R.string.back), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mute toggle. The icon says what the player is doing, the description says what
 * tapping it will do.
 */
@Composable
private fun MuteButton(
    muted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            contentDescription = stringResource(if (muted) R.string.unmute else R.string.mute),
            tint = tint
        )
    }
}

/** Explains silent playback instead of leaving the user guessing. */
@Composable
private fun MutedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.VolumeOff,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(R.string.player_muted),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun hideSystemBars(activity: Activity) {
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
}

private fun showSystemBars(activity: Activity) {
    WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
}
