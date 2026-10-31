# Open IPTV — project guide for Pi agents

## Mission

A tiny, transparent, open-source Android IPTV player. The user pastes **one**
playlist URL (M3U / M3U8 / Xtream `get.php`), the app loads the channels, the
user searches and taps a channel, and it plays. No account, no backend, no ads,
no analytics, no cloud sync.

The app must make failures understandable. A blank screen or a generic "error"
is a bug.

## v1 scope (do not expand without an explicit decision)

In scope:

- One playlist URL input, persisted locally.
- M3U/M3U8 parsing, including Xtream-style `get.php?...&type=m3u_plus` URLs.
- Source tabs: **Live**, **Movies**, **Series**, **All**. Default to **Live**,
  but if Live is empty and another tab has entries, start on **All** and explain
  why (e.g. a demo list or a provider without `/live/` paths).
- Search by channel name (case/accent-insensitive), scoped to the active tab,
  with an explicit "search all" toggle.
- Group filter (e.g. `ES - FUTBOL`, `ESPAÑA - Drama`).
- Playback with Media3 ExoPlayer (HLS `.m3u8` and MPEG-TS `.ts`).
- Human-readable errors for: invalid URL, timeout, HTTP 401/403/404/5xx,
  empty playlist, malformed entries, unsupported stream, playback failure.
- Loading progress with bytes read (when known) and parsed channel count.
- Cache the last successful playlist in app-private storage. Startup reads the
  cache without a network request and shows the last-updated time.
- Manual **Actualizar** keeps the current list visible and only replaces it on
  success; a failed refresh must never destroy the working list.
- A single playlist only. No multi-list UI in v1.
- Fullscreen playback (immersive, landscape) with an explicit exit button; back
  exits fullscreen before closing the player.
- Rotation must not recreate the Activity or reconnect the player.
- A debug APK the user can install with `adb install`.
- Actionable error messages. "Unknown error" is a bug.
- Spanish UI by default (`values/strings.xml`), with all user-facing text in
  resources so English can be added later.

Out of scope for v1 (unless the user explicitly asks):

- EPG/XMLTV, catch-up, recording, Chromecast, PiP, Android TV, downloads.
- Multiple playlists/profiles, cloud sync, accounts, recommendations.
- DRM, subtitles, audio track UI, per-channel HTTP headers.
- Room, Hilt/Koin, WorkManager, Firebase, analytics, crash reporting.
- A custom player. Use Media3.

## Large-playlist requirements

The app must handle a large mixed playlist with tens of thousands of entries
without ANR, OOM, or jank. Use a synthetic fixture for tests; never commit a
real provider list or a real playlist URL.

Assumptions the UI and parser must handle:

- Tens of thousands of entries and a multi-megabyte document.
- Live, movie, and series paths (`/live/`, `/movie/`, `/series/`).
- Many multilingual groups and duplicate channels with quality variants
  (`FHD`, `HD`, `HEVC`, `HDR`).

Consequences:

- Parse **off the main thread**, line by line. Never regex the whole document.
- Never put a `remember`/state object inside a list row that can be repeated
  tens of thousands of times.
- Use stable keys (hash of channel URL + display name) and `contentType`.
- Precompute a normalized search string per channel once.
- Debounce search ~150 ms. Filtering tens of thousands of in-memory items is acceptable.
- Default to Live so the first screen is small and fast.
- Do not hide duplicates in v1. Show quality tags as text; variant grouping is v1.1.
- VOD entries are not channels. The Live/Movies/Series tabs keep the product honest.

## Architecture target (v1)

- Kotlin, single `:app` module. Package `app.opentv` (provisional).
- Jetpack Compose + Material 3. No XML layouts.
- Media3 ExoPlayer (`media3-exoplayer`, `media3-exoplayer-hls`, `media3-ui`,
  `media3-datasource-okhttp`). `PlayerView` inside `AndroidView`.
- `ViewModel` + immutable `UiState` + `StateFlow`; repository owns fetching.
- OkHttp for playlist HTTP (disk cache, redirects, gzip, timeouts).
- DataStore Preferences for `playlistUrl` and `lastLoadedAt`.
- Coroutines/Flow. Parsing on `Dispatchers.IO` / `Default`, UI on Main.
- No DI framework. Construct dependencies in `Application`/`MainActivity`.
- No database. The parsed list is in memory; the raw playlist may be cached by
  OkHttp's HTTP cache.

Suggested packages:

```text
app.opentv
├── MainActivity.kt
├── OpenTvApp.kt
├── data/
│   ├── PlaylistRepository.kt
│   ├── PlaylistParser.kt
│   ├── PlaylistHttpClient.kt
│   ├── SettingsStore.kt
│   └── model/Channel.kt
├── player/
│   ├── PlayerController.kt
│   └── PlaybackErrorMapper.kt
└── ui/
    ├── App.kt
    ├── playlist/PlaylistScreen.kt
    ├── playlist/PlaylistViewModel.kt
    └── player/PlayerScreen.kt
```

## Toolchain baseline (build-verified 2026-10-31)

Use this exact baseline unless an explicit decision changes it:

| Tool | Version | Notes |
|---|---|---|
| Gradle | **8.14.6** | Use the wrapper; no global Gradle installed |
| AGP | **8.13.2** | Max compileSdk 36 |
| Kotlin | **2.2.21** | |
| Compose BOM | **2026.06.01** | Compose UI 1.11.4, Material3 1.4.0 |
| Media3 | **1.11.1** | Requires compileSdk 36 |
| compileSdk / targetSdk | **36** | |
| minSdk | **24** | Android 7.0+ |
| JDK | **17** | `JAVA_HOME` already set to Temurin 17 |
| Build tools | **36.0.0** | Installed |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` | Installed |

Other verified versions: core-ktx 1.18.0, activity-compose 1.13.0,
lifecycle 2.10.0, navigation-compose 2.9.8, DataStore 1.3.0, OkHttp 4.12.0,
coroutines 1.11.0, Coil 3.6.3.

**Do not upgrade to AGP 9.x or Compose 1.12 yet.** Compose 1.12 requires
compileSdk 37 and AGP 9.1+. AGP 9 uses built-in Kotlin and pins KGP 2.2.10,
which complicates matching the Compose compiler plugin. If the user asks for
it, install `platforms;android-37.0`, read the `agp-9-upgrade` skill, and get
explicit approval first.

Re-verify versions before changing them:

```bash
# latest stable AGP / Compose BOM / Media3
curl -s https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml
curl -s https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml
curl -s https://dl.google.com/dl/android/maven2/androidx/media3/media3-exoplayer/maven-metadata.xml
# AAR minCompileSdk
unzip -p some.aar META-INF/com/android/build/gradle/aar-metadata.properties
```

## Commands

```bash
# Build
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug

# Artifact
app/build/outputs/apk/debug/app-debug.apk

# Install on a connected phone
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.opentv/.MainActivity

# Logs
adb logcat --pid=$(adb shell pidof -s app.opentv)

# Probe a playlist or stream without the app
node .pi/skills/stream-probe/probe.mjs local/list.m3u --limit 5
node .pi/skills/stream-probe/probe.mjs samples/demo.m3u
```

The SDK location goes in `local.properties` (git-ignored), for example:

```properties
sdk.dir=C:/Android/Sdk
```

## Agent workflow

Use the smallest agent that owns the next decision. Do not spawn agents for
one-line edits.

| Phase | Agent | Output |
|---|---|---|
| Recon | builtin `scout` | files, entry points, risks |
| Research | builtin `researcher` | current Android/Media3 facts with sources |
| Plan | `android-architect` | short plan, file list, risks, verification |
| General code | `android-dev` | code + focused build |
| Playlist / player | `iptv-engineer` | parser, search, Media3 integration |
| Build / sign / install | `apk-smith` | APK path, SHA-256, install result |
| Review | `qa-reviewer` | findings by severity, pass/fail |
| Risky decision | builtin `oracle` | second opinion (explicit use only) |

Recommended loop:

```text
clarify → scout → architect → (dev | iptv-engineer) → apk-smith → qa-reviewer
```

Rules:

- `android-architect` and `qa-reviewer` are read-only; they must not edit files.
- Implementation agents run a focused compile/build before reporting done.
- `apk-smith` owns Gradle/build config, not app logic.
- If a build error repeats twice, escalate the model (see below) or ask `oracle`.
- The parent must not claim the app works until `apk-smith` installed the APK on
  a device/emulator and `qa-reviewer` passed it.

## Model routing

Project defaults live in `.pi/settings.json`. Prices are approximate OpenRouter
USD per 1M tokens at setup time; verify before relying on them.

| Role | Model | Thinking | Why |
|---|---|---|---|
| Main session | `openrouter/deepseek/deepseek-v4-pro` | medium | cheap orchestrator (~$0.71/$1.42) |
| `scout` | `openrouter/anthropic/claude-haiku-5.5` | low | fast recon (~$0.10/$0.50) |
| `researcher` | `openrouter/google/gemini-2.5-flash` | medium | web synthesis (~$0.30/$2.50) |
| `android-architect` | `openrouter/anthropic/claude-sonnet-5.5` | high | judgment (~$2/$10) |
| `android-dev` | `openrouter/anthropic/claude-sonnet-5.5` | medium | Kotlin/Compose quality |
| `iptv-engineer` | `openrouter/anthropic/claude-sonnet-5.5` | high | correctness-critical parsing |
| `apk-smith` | `openrouter/deepseek/deepseek-v4-pro` | high | mechanical build loop, cheap |
| `qa-reviewer` | `openrouter/anthropic/claude-sonnet-5.5` | high | independent review |
| `reviewer` (builtin) | `openrouter/anthropic/claude-sonnet-5.5` | high | independent review |
| `oracle` (builtin) | `openrouter/anthropic/claude-opus-5.5` | high | expensive, explicit use only |

Escalation examples:

```text
/run apk-smith[model=openrouter/anthropic/claude-sonnet-5.5:medium] "Fix the Gradle failure and install the debug APK"
/run android-dev[model=openrouter/deepseek/deepseek-v4-pro:high] "Implement the channel list screen"
```

If the user has no budget concerns, switch the main session to
`openrouter/anthropic/claude-sonnet-5.5` with `Ctrl+P`.

## Skills

Global skills installed for Android work:

| Skill | Source | Use |
|---|---|---|
| `agp-9-upgrade` | `android/skills` | only if AGP 9 is explicitly approved |
| `testing-setup` | `android/skills` | unit/UI/screenshot test strategy |
| `edge-to-edge` | `android/skills` | insets/system bars on Compose |
| `compose-ui-testing-patterns` | `chrisbanes/skills` | Compose semantics/screenshot tests |
| `mobile-android-design` | `wshobson/agents` | Material 3 UI guidance |

Project skills:

| Skill | Use |
|---|---|
| `iptv-playlists` | M3U/Xtream parsing, classification, search, performance |
| `media3-hls` | ExoPlayer, HLS/TS, headers, lifecycle, errors |
| `android-apk` | Gradle build, signing, verification, adb install |
| `stream-probe` | CLI probe for playlists/HLS/TS endpoints |

Read the matching skill before touching that area. Project skills live in
`.pi/skills/`; global skills live in `~/.pi/agent/skills/`.

## Security and privacy rules

- **Never** commit, print, screenshot, or paste a real playlist URL,
  username, password, token, provider host, or keystore.
- Real playlists live in `local/` (git-ignored). Samples use public streams.
- Implement `redactUrl()` and use it in every log/error message. Redact
  `username`, `password`, `token`, `key`, `auth`, and URL userinfo. For Xtream
  paths such as `/live/USER/PASS/123.m3u8`, never log the full path; log host +
  channel id only.
- The app makes network calls only to: the playlist URL, channel stream URLs,
  and channel logos. No telemetry.
- HTTP cleartext is required because user-supplied IPTV lists commonly use
  `http://`. Enable it explicitly in the manifest and document the tradeoff.
  Never send app-generated secrets over cleartext.
- Use `android:usesCleartextTraffic="true"` for v1. Do not add an allowlist UI
  until the core works.
- The debug APK is for personal sideloading. Do not publish a release build
  with a debug keystore.

## Definition of done (v1)

- [ ] User can paste a URL, see loading progress, and get a clear error if it fails.
- [ ] A synthetic large playlist (tens of thousands of entries) parses without ANR or OOM on a mid-range phone.
- [ ] Live/Movies/Series/All tabs show correct counts.
- [ ] A playlist with no Live entries still opens on a non-empty tab.
- [ ] Search finds channels with accents/case differences and is debounced.
- [ ] Tapping a live channel plays it; back stops playback and releases the player.
- [ ] Rotating the device or changing configuration does not leak the player.
- [ ] HTTP 401/403/404/timeout/malformed playlist produce actionable messages.
- [ ] `samples/demo.m3u` plays all three public HLS streams.
- [ ] `./gradlew :app:assembleDebug` succeeds from a clean checkout with JDK 17.
- [ ] APK installs on Android 7+ with `adb install -r`.
- [ ] No credentials in git, logs, screenshots, or the README.
- [ ] README explains install, URL input, and the cleartext HTTP tradeoff.

## Verification log

- **2026-10-31**: debug APK installed on a physical Android 16 device via
  `adb install -r`. No `FATAL EXCEPTION` on launch.
- A large mixed playlist loaded in the app and the Live/Movies/Series tabs
  showed the expected totals.
- A live channel played (Media3 `AudioTrack` active; provider watermark
  visible). The player is one composable in `MainActivity`, so the surface is
  the only video layer.
- The public Mux HLS stream from `samples/demo.m3u` played video and audio
  through a temporary local HTTP server.
- **Cache startup**: with Wi-Fi and mobile data disabled, killing and relaunching
  the app still showed the cached channels from `files/playlist.cache`,
  with the last-updated timestamp and no network request.
- **Refresh failure**: with the network disabled, tapping Actualizar kept the old
  list and channels visible while showing "No se pudo encontrar el servidor…".
- **Refresh success**: after restoring the network, Actualizar updated the
  timestamp to 18:33 and rewrote the cache.
- **Fullscreen**: tapping the fullscreen button switched to landscape
  (`ROTATION_90`, 2340x1080), hid the system bars and top bar, and showed an
  exit button. Tapping it restored the top bar; back also exited fullscreen
  before closing the player.
- **Rotation**: forcing portrait -> landscape while playing kept the same
  `AudioTrack` player ID; no new player and no `FATAL EXCEPTION`.
- **Back**: the player was released (`event:stopped`, `releasing player`) and the
  list returned.
- Still unverified: Android 7 devices, all three demo streams, end-to-end search,
  and real HTTP 401/403/404 handling.

## Known traps

1. **Cleartext HTTP**: without it, every `http://` playlist/stream fails with
   `CLEARTEXT communication not permitted`.
2. **`.ts` is not HLS**: a raw MPEG-TS URL is not an HLS playlist. Let Media3's
   `DefaultMediaSourceFactory` choose `ProgressiveMediaSource` + `TsExtractor`;
   do not force `HlsMediaSource`.
3. **Huge playlist on the main thread**: tens of thousands of entries will ANR.
4. **Very large Compose list**: stable keys + simple rows + precomputed search text.
5. **Player lifecycle**: release in `DisposableEffect`/`onStop`; never keep a
   player attached to a destroyed surface.
6. **Logging URLs**: Xtream URLs embed credentials in the path and query.
7. **AGP/Compose mismatch**: Compose 1.12 needs compileSdk 37 + AGP 9.1; do not
   mix it with the AGP 8.13 baseline.
8. **Duplicate channels**: expected. Do not dedupe silently in v1.
9. **M3U encodings**: UTF-8 with BOM is common; some providers use Latin-1.
10. **Relative URLs**: resolve against the playlist URL, not the app cwd.
