---
name: media3-hls
description: Play IPTV streams with AndroidX Media3 ExoPlayer in Open IPTV. Use for HLS .m3u8, raw MPEG-TS .ts, HTTP headers, buffering, error mapping, PlayerView in Compose, lifecycle release, and cleartext HTTP. Covers DefaultMediaSourceFactory, OkHttp data sources, and playback diagnostics.
---

# Media3 / HLS playback

Use Media3. Do not write a custom HLS or MPEG-TS player. The goal is a reliable
player with honest errors, not a media framework.

## Baseline dependencies

```kotlin
// app/build.gradle.kts
implementation("androidx.media3:media3-exoplayer:1.11.1")
implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
implementation("androidx.media3:media3-ui:1.11.1")            // PlayerView
implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
```

Media3 1.11.1 requires `compileSdk = 36`. Do not mix with Compose 1.12 / AGP 9.

## Manifest requirements

```xml
<uses-permission android:name="android.permission.INTERNET" />

<application
    android:usesCleartextTraffic="true"
    ... >
```

`usesCleartextTraffic="true"` is intentional: user-supplied IPTV lists commonly
use `http://`. The app sends no app-generated secrets over cleartext. Do not
remove it without providing a user-facing HTTP/HTTPS choice.

## Data source

Reuse the app's OkHttp client so playlist fetches and media share connection
pooling and the HTTP cache:

```kotlin
val okHttp = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .cache(Cache(File(context.cacheDir, "http"), 10L * 1024 * 1024))
    .build()

val dataSourceFactory = OkHttpDataSource.Factory(okHttp)
    .setUserAgent("OpenIPTV/1.0 (Android)")
    .setDefaultRequestProperties(mapOf("Accept" to "*/*"))
```

If you do not need the cache for media, `DefaultHttpDataSource.Factory()` is
fine. Enable cross-protocol redirects (`setAllowCrossProtocolRedirects(true)`)
when a provider redirects HTTP → HTTPS.

## Create the player once

```kotlin
val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
val player = ExoPlayer.Builder(context)
    .setMediaSourceFactory(mediaSourceFactory)
    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
    .build()
```

`DefaultMediaSourceFactory` selects:

- `.m3u8` → `HlsMediaSource` (master/variant playlists, AES-128, fMP4 or TS segments)
- `.ts`, `.mp4`, unknown → `ProgressiveMediaSource` with the right extractor
  (`TsExtractor` for MPEG-TS)

Do **not** force `HlsMediaSource` for a raw `.ts` URL, and do not force
`ProgressiveMediaSource` for an `.m3u8` manifest.

## Play a channel

```kotlin
val item = MediaItem.Builder()
    .setUri(channel.url)
    .setMediaId(channel.id)
    .apply {
        // The extension is usually enough; set the MIME type only when the URL
        // has no useful extension but the content is known to be HLS.
        if (channel.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            setMimeType(MimeTypes.APPLICATION_M3U8)
        }
    }
    .build()

player.setMediaItem(item)
player.prepare()
player.playWhenReady = true
```

For a live HLS stream, leave `setLiveConfiguration` alone unless you need a
custom target offset. ExoPlayer detects live from the manifest. For raw TS,
duration is usually unknown; treat `player.isCurrentMediaItemLive` as advisory.

## Compose integration

`PlayerView` via `AndroidView` is the stable path. Do not put a player surface
inside a `LazyColumn` row.

```kotlin
@Composable
fun PlayerSurface(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                keepScreenOn = true
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier
    )
}
```

Lifecycle:

- Create the player in the `ViewModel` (or a `remember`ed controller), never on
  every recomposition.
- Release in `ViewModel.onCleared()` and on `ON_STOP` if playback must stop.
- Observe `Lifecycle.Event.ON_STOP` to pause; resume only on explicit user play.
- On configuration change, keep the same player and let `PlayerView` reattach.
- Back navigation must stop playback and clear the media item before leaving.

## Error mapping

`PlaybackException.errorCodeName` is the stable diagnostic. Map it to a short
Spanish message and keep the code in "detalles":

| Code / cause | Message | Hint |
|---|---|---|
| `ERROR_CODE_IO_BAD_HTTP_STATUS` 403 | "El proveedor rechazó el canal (403). Puede requerir otro User-Agent o que la suscripción esté activa." | show status |
| 404 | "El canal ya no existe en el proveedor (404)." | refresh list |
| 5xx | "El servidor del proveedor falló. Inténtalo más tarde." | retry |
| `ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT` | "El canal tardó demasiado en responder." | retry |
| `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` | "No hay conexión con el proveedor." | check network |
| `ERROR_CODE_PARSING_CONTAINER_MALFORMED` | "El proveedor devolvió un formato que no se puede reproducir." | try another variant |
| `ERROR_CODE_DECODING_FAILED` | "El móvil no puede decodificar este canal (códec no soportado)." | try non-HEVC variant |
| `ERROR_CODE_UNSPECIFIED` | "No se pudo reproducir el canal." | details |

For `HttpDataSource.InvalidResponseCodeException`, read
`responseCode` and `responseHeaders`. Never print the full URL; print the
redacted host and channel id.

## Buffering and retries

Start with defaults. Only if the user reports frequent stalls:

```kotlin
val loadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        minBufferMs = 15_000,
        maxBufferMs = 60_000,
        bufferForPlaybackMs = 1_500,
        bufferForPlaybackAfterRebufferMs = 3_000
    )
    .build()
```

`DefaultLoadErrorHandlingPolicy(3)` retries a few times. Do not build an
infinite retry loop; it hammers the provider and hides real errors.

## HLS vs MPEG-TS

- Prefer HLS (`.m3u8`). It is adaptive, seekable when VOD, and best supported.
- Xtream users can switch the playlist URL from `output=ts` to `output=m3u8`.
  If the app sees mostly `.ts` channels, mention this in the error hint.
- Raw TS is a continuous stream. It may play but can fail on servers that do not
  support range requests or on providers that expect a specific UA.
- Do not try to rewrite a `.ts` URL into `.m3u8` unless the provider documents a
  pattern. URL surgery breaks providers.

## Headers and 403s

Some providers require a browser-like `User-Agent`, a `Referer`, or an
`Origin`. v1 sets a single app UA and shows an actionable 403 message. Do not add
a per-channel header editor until the core works. If a header is required, add a
single advanced "User-Agent" field in settings, not per channel.

## Testing

1. `samples/demo.m3u` — three public HLS streams. All must play.
2. `node .pi/skills/stream-probe/probe.mjs <url|file> --probe-stream` to verify
   the manifest and first segment before blaming the player.
3. Real list in `local/` — test at least one live, one movie, one series.
4. `adb logcat` filters: `ExoPlayerImpl`, `MediaCodec`, `HttpDataSource`,
   `Loader`, `AndroidRuntime`.
5. Test: play → back → play another; rotate; background/foreground; airplane
   mode during playback; HTTP 403 channel.

## Common traps

- Creating `ExoPlayer` inside a composable without `remember` → a new player per
  recomposition and audio overlap.
- Releasing the player while `PlayerView` is still attached → surface crash.
- Calling `prepare()` in a `LaunchedEffect` keyed on unstable state → repeated
  reloads. Key on `channel.id`.
- Forgetting `playWhenReady = true`.
- Using `PlayerView` in a `LazyColumn` → surface leaks and black frames.
- Assuming `.ts` is HLS.
- Logging the stream URL: Xtream URLs embed credentials in the path.
- Cleartext not enabled → `CLEARTEXT communication not permitted`.
- Old devices failing HEVC: offer the non-HEVC variant from the list; do not
  silently fall back.

## Definition of done

- [ ] Public demo streams play with audio/video.
- [ ] Live, movie, and series entries from a large test list play.
- [ ] Back stops playback and releases the player.
- [ ] Rotation and background/foreground do not leak or double-play.
- [ ] 403/timeout/malformed stream show a specific message.
- [ ] Logs contain redacted URLs only.
