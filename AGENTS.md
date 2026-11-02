# Open IPTV — project guide for Pi agents

## Mission

A tiny, transparent, open-source Android IPTV player. The user pastes **one or
more** playlist URLs (M3U / M3U8 / Xtream `get.php`), names each one, and
switches between them; the app loads the active list's channels, the user
searches and taps a channel, and it plays. No account, no backend, no ads, no
analytics, no cloud sync.

The app ships **empty**. It never bundles, hardcodes, or suggests a channel list
or a provider: it plays a source the user is entitled to use, and nothing else.

The app must make failures understandable. A blank screen or a generic "error"
is a bug.

## v1 scope (do not expand without an explicit decision)

Explicit scope decision on record: **multi-list management** (named lists, M3U
URL or Xtream server, one active list at a time, per-list cache) was approved
and merged on 2026-11-01. It is in scope, and the older "single list only" rule
is superseded.

Explicit scope decision on record: **Xtream `player_api.php` catalogue for
Movies/Series** was approved. For `PlaylistKind.XTREAM` lists the M3U download
stays the source of playback URLs (it is requested with `output=m3u8`, so every
movie/series URL is HLS), and the API contributes metadata only
(`get_vod_streams`, `get_series`, category lists and the lazy
`get_series_info`). Movies/Series render as a 3-column poster grid with a
persisted list/grid toggle, provider category chips instead of group pills on
those tabs, and a series drill-down (series -> seasons -> episodes) as its own
screen. A `container_extension` URL (`.mkv`, `.avi`, ...) is never produced:
unmatched catalogue ids fall back to the provider's own
`<origin>/<type>/<user>/<pass>/<id>.m3u8` pattern. Plain-M3U (`PlaylistKind.URL`)
lists are never augmented with API calls. If the API fails while the M3U works,
the flat list is shown with a one-line warning; a load never fails because the
catalogue could not be fetched.

In scope:

- One or more named list configurations, persisted locally, with a list picker
  and a single active list.
- M3U/M3U8 parsing, including Xtream-style `get.php?...&type=m3u_plus` URLs.
- Source tabs: **Live**, **Movies**, **Series**, **All**. Default to **Live**,
  but if Live is empty and another tab has entries, start on **All** and explain
  why (e.g. a demo list or a provider without `/live/` paths).
- Search by channel name (case/accent-insensitive), scoped to the active tab,
  with an explicit "search all" toggle.
- Group filter (e.g. `ES - FUTBOL`, `ESPAÑA - Drama`).
- Per-category favorites (Live/Movies/Series/All), persisted on-device.
- Provider-supplied `tvg-logo` images with a generated initial fallback. No brand
  logos are bundled in the APK or repository.
- Playback with Media3 ExoPlayer (HLS `.m3u8` and MPEG-TS `.ts`).
- Human-readable errors for: invalid URL, timeout, HTTP 401/403/404/5xx,
  empty playlist, malformed entries, unsupported stream, playback failure.
- Loading progress with bytes read (when known) and parsed channel count.
- Cache the last successful playlist in app-private storage. Startup reads the
  cache without a network request and shows the last-updated time.
- Manual **Actualizar** keeps the current list visible and only replaces it on
  success; a failed refresh must never destroy the working list.
- Multiple named lists with a picker and one active list at a time; each list
  keeps its own cache. No cross-list search, aggregation or profiles.
- Fullscreen playback (immersive, landscape) with an explicit exit button; back
  exits fullscreen before closing the player.
- Rotation must not recreate the Activity or reconnect the player.
- A release-signed APK published on GitHub Releases, plus a debug APK for
  development with `adb install`.
- Actionable error messages. "Unknown error" is a bug.
- Spanish UI by default (`values/strings.xml`), with all user-facing text in
  resources so English can be added later.

Out of scope for v1 (unless the user explicitly asks):

- EPG/XMLTV, catch-up, recording, Chromecast, PiP, Android TV, downloads.
  EPG and radio overlay are researched in `docs/FEATURE_RESEARCH.md` and planned
  in `docs/ROADMAP.md`; they are not v1 scope.
- Profiles, cloud sync, accounts, cross-list aggregation, recommendations.
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
- Poster grids use a fixed 3-column `LazyVerticalGrid`, stable keys
  (`movie:<id>`, `series:<id>`), one `contentType` per grid, explicit Coil
  `size()` matching the measured cell and a small prefetch window (8 posters).
  No `SubcomposeAsyncImage` and no `BoxWithConstraints` per cell: the only
  per-cell state is Coil's painter, never list data or derived values.

## Architecture target (v1)

- Kotlin, single `:app` module. Package `dev.lluissunol.iptv`, on the
  `lluissunol.dev` domain. Decided 2026-11-01; renaming it later is a breaking
  change for installed users, so do not revisit lightly.
- Jetpack Compose + Material 3. No XML layouts.
- Media3 ExoPlayer (`media3-exoplayer`, `media3-exoplayer-hls`, `media3-ui`,
  `media3-datasource-okhttp`). `PlayerView` inside `AndroidView`.
- `ViewModel` + immutable `UiState` + `StateFlow`; repository owns fetching.
- OkHttp for playlist HTTP (disk cache, redirects, gzip, timeouts).
- DataStore Preferences for `playlistUrl`, `lastLoadedAt`, cache metadata and the
  small set of favorite keys. Room is reserved for future EPG/large datasets.
- Coroutines/Flow. Parsing on `Dispatchers.IO` / `Default`, UI on Main.
- No DI framework. Construct dependencies in `Application`/`MainActivity`.
- No database. The parsed list is in memory; the raw playlist may be cached by
  OkHttp's HTTP cache.

Suggested packages:

```text
dev.lluissunol.iptv
├── MainActivity.kt
├── OpenTvApplication.kt
├── data/
│   ├── PlaylistRepository.kt      # fetch + state; owns the OkHttp client
│   ├── PlaylistParser.kt          # streaming M3U/Xtream parser
│   ├── PlaylistCache.kt           # last successful playlist on disk
│   ├── SearchNormalizer.kt        # accent/accent-insensitive search keys
│   ├── SettingsStore.kt           # DataStore: lists, active list, favorites
│   └── model/
│       ├── Channel.kt
│       └── PlaylistConfig.kt
├── player/
│   └── PlaybackErrorMapper.kt
└── ui/
    ├── OpenTvApp.kt               # navigation host
    ├── Formatters.kt
    ├── lists/ListsScreen.kt       # picker, add/edit/delete, credential masking
    ├── playlist/PlaylistScreen.kt
    ├── playlist/PlaylistViewModel.kt
    ├── player/PlayerScreen.kt
    └── theme/Theme.kt
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
coroutines 1.11.0, kotlinx-serialization-json 1.9.0, Coil **2.7.0**.

Coil 2.7.0 is the version actually installed and used
(`coil.compose.SubcomposeAsyncImage`, `AsyncImage`, `context.imageLoader`); the
earlier "Coil 3.6.3" line here was aspirational. Do not migrate to Coil 3 as
part of unrelated work.

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
adb shell am start -n dev.lluissunol.iptv/.MainActivity

# Logs
adb logcat --pid=$(adb shell pidof -s dev.lluissunol.iptv)

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
| Brand / logo | `brand-designer` | generated logo concepts, chosen asset, brief |
| UI / UX design | `ui-designer` | interaction spec, sizes, states, tradeoffs |
| Risky decision | builtin `oracle` | second opinion (explicit use only) |

Recommended loop:

```text
clarify → scout → ui-designer → architect → (dev | iptv-engineer) → apk-smith → qa-reviewer
```

Rules:

- `android-architect`, `ui-designer` and `qa-reviewer` are read-only for the app:
  they must not edit code, resources or Gradle files. `ui-designer` may only write
  interaction specs under `design/ui/`.
- Implementation agents run a focused compile/build before reporting done.
- `apk-smith` owns Gradle/build config, not app logic.
- If a build error repeats twice, escalate the model (see below) or ask `oracle`.
- The parent must not claim the app works until `apk-smith` installed the APK on
  a device/emulator and `qa-reviewer` passed it.

## Model routing

Project defaults live in `.pi/settings.json`. These are the roles the harness
uses; prices change, so check OpenRouter before budgeting.

| Role | Model | Thinking | AA IQ† | Code‡ | Precio (in/out) | Why |
|---|---|---|---|---|---|---|
| Main session | `openrouter/deepseek/deepseek-v4-pro` | medium | 23 | 63% | ~$1.60/$3.20 | orchestrator (routes to V4.1-Flash pricing) |
| `scout` | `openrouter/z-ai/glm-5.3-flash` | low | 28 | 63% | ~$0.02/$0.15 | fast recon |
| `researcher` | `openrouter/z-ai/glm-5.3-flash` | medium | 28 | 63% | ~$0.02/$0.15 | web synthesis |
| `android-architect` | `openrouter/z-ai/glm-5.3` | high | 32 | 67% | ~$0.07/$1.40 | judgment, plan |
| `android-dev` | `openrouter/deepseek/deepseek-v4.1-flash` | medium | 28 | 74% | ~$0.05/$0.50 | Kotlin/Compose quality |
| `iptv-engineer` | `openrouter/z-ai/glm-5.3` | high | 32 | 67% | ~$0.07/$1.40 | correctness-critical parsing |
| `apk-smith` | `openrouter/z-ai/glm-5.3-flash` | high | 28 | 63% | ~$0.02/$0.15 | mechanical build loop, cheap |
| `qa-reviewer` | `openrouter/z-ai/glm-5.3` | high | 32 | 67% | ~$0.07/$1.40 | independent review |
| `brand-designer` | `openrouter/z-ai/glm-5.3` | high | 32 | 67% | ~$0.07/$1.40 | visual identity, image evaluation |
| `ui-designer` | `openrouter/z-ai/glm-5.3` | high | 32 | 67% | ~$0.07/$1.40 | interaction design, states, judgment |
| `reviewer` (builtin) | `openrouter/z-ai/glm-5.3` | high | 32 | 67% | ~$0.07/$1.40 | independent review |
| `worker` (builtin) | `openrouter/deepseek/deepseek-v4.1-flash` | medium | 28 | 74% | ~$0.05/$0.50 | generic implementation |
| `delegate` (builtin) | `openrouter/deepseek/deepseek-v4.1-flash` | medium | 28 | 74% | ~$0.05/$0.50 | lightweight delegate |
| `oracle` (builtin) | `openrouter/anthropic/claude-opus-5.5` | high | 58 | 74% | ~$4/$20 | expensive, explicit use only |

† Artificial Analysis Intelligence Index v5.0 (0–100, general; mediana ≈ 23).
‡ DeepSWE v1.1 (abiertos) / ChatOSS Agentic Coding Index (Opus). Orientativo.

Precios OpenRouter USD/1M tokens verificados contra la API el 2026-10-31.
Ojo: `defaultModel` (sesión principal) va SIN prefijo de proveedor
(`deepseek/deepseek-v4-pro`); los modelos de subagentes sí llevan prefijo
(`openrouter/z-ai/glm-5.3`). Desde 2026-09-14, `deepseek-v4-pro` redirige a
V4.1-Flash con precio Flash hasta que salga V4.1-Pro.

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
| `logo-generation` | OpenRouter image API, logo prompt rules, adaptive-icon specs |

Read the matching skill before touching that area. Project skills live in
`.pi/skills/`; global skills live in `~/.pi/agent/skills/`.

## Security and privacy rules

- **Never** commit, print, screenshot, or paste a real playlist URL,
  username, password, token, provider host, or keystore.
- Keep private playlists in `local/` (git-ignored) or outside the repository.
  Samples use public streams only.
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
- The debug APK is for development only. Never publish a build signed with a
  debug keystore, and never commit a keystore or its passwords.
- **The app ships empty.** Never bundle, hardcode, link, or otherwise suggest a
  channel list, a provider host, or a "free TV" catalogue — not in the APK,
  samples, docs, or store metadata. Open IPTV plays a source the user is
  entitled to use; presenting it as a way to obtain content is a rule violation,
  not a judgement call.

## Definition of done (v1)

- [ ] User can paste a URL, see loading progress, and get a clear error if it fails.
- [ ] A synthetic large playlist (tens of thousands of entries) parses without ANR or OOM on a mid-range phone.
- [ ] Live/Movies/Series/All tabs show correct counts.
- [ ] A playlist with no Live entries still opens on a non-empty tab.
- [ ] Search finds channels with accents/case differences and is debounced.
- [ ] Favorites can be toggled, filtered by category, and survive a restart.
- [ ] Channel logos come only from `tvg-logo`; missing logos fall back to an initial.
- [ ] Launcher icon and in-app header use the same brand mark.
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
- **Favorites**: on a physical Android 16 device, starring a channel marked its
  variants that share the same `tvg-id`, the Favorites screen showed per-category
  tabs and counts, and favorites survived a force-stop/relaunch. Removing the
  star removed the favorite.
- **Logo**: the generated play-and-signal mark is used in the launcher (adaptive
  and round icons), the header, and the URL hero; the launcher icon matches the
  in-app mark.
- **2026-11-01 — QA fixes on a physical Galaxy S25 (Android 17 / API 37)**:
  created, edited and deleted lists from the UI; changed the active list's URL
  from the playlist header and the list **reloaded** (the header timestamp
  advanced) instead of keeping the stale error; a demo HLS channel played with
  `AudioTrack` active and `MediaCodec`/`CCodec` decoding; `adb install -r` over
  the previous debug build preserved the existing lists.
- **2026-11-01 — fullscreen rotation**: entering fullscreen still forces
  landscape, and exiting it now restores portrait, including the system's own
  user rotation (which the forced landscape request had rewritten). Verified
  across two enter/exit cycles and after closing the player. No
  `FATAL EXCEPTION`.
- **2026-11-01 — large playlist**: a synthetic 35.2 MB / 165,000-entry list
  (Live/Movies/Series paths, 8 accented groups, FHD/HD/HEVC/HDR/SD variants)
  loaded in 15 s with all 165,000 channels parsed; 0 crashes, 0 OOM, 0 ANR. The
  three source tabs were verified to filter by type, and accent-insensitive
  search matched `ESPAÑA - Drama` from the unaccented query `espana`. Peak Java
  heap was ~220 MB -- that, not the byte guard, is the real ceiling for a list
  this size. Fast synthetic flings janked ~12-15% of frames (p50 8 ms, p90
  16-21 ms).
- Still unverified: Android 7 devices, all three demo streams, real HTTP
  401/403/404 handling, and scrolling a large list by hand instead of with
  synthetic flings.

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
