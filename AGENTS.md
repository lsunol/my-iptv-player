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

Explicit scope decision on record: **multilingual UI** (approved 2026-11-17): en (default), es, ca, de, fr, it, pt; see "Multilingual UI" below. Provider data (groups, categories, titles) is never translated.

Explicit scope decision on record: **catalogue sorting, filters and detail metadata** (approved 2026-11-15, `design/ui/catalog-sort-filter*.md`): sort and filter controls on the Movies/Series tabs that are offered only when the loaded catalogue carries the data (any Xtream panel), long-press movie detail from `get_vod_info` (display only, never used for playback) and richer series headers. Metadata-only; no third-party services.

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
- A mute toggle for playback, in the top bar and in fullscreen (approved
  2026-11-09). It scales only the app's own output: the phone's volume, its
  stream volumes and other apps' audio are never touched, and no volume slider
  is offered. Muted is a persisted state, and it is shown on screen.
- Rotation must not recreate the Activity or reconnect the player.
- A release-signed APK published on GitHub Releases, plus a debug APK for
  development with `adb install`.
- Actionable error messages. "Unknown error" is a bug.
- Multilingual UI (decision 2026-11-17): English (`values/`, the fallback),
  Spanish, Catalan, German, French, Italian and Portuguese (`values-<lang>/`).
  All user-facing text lives in resources; data/player layers carry `UiText`
  (resource id + args), never a `Context` or literal text. Language selection is plain Android
  resolution, no custom code: the first language in the user's list that the
  app ships wins (so Catalan only wins when it is the first supported one),
  and English is the fallback when none matches. The Android 13+ per-app
  language works through `locales_config.xml`. New strings must be added
  to all 7 files.

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

- Download the playlist straight to a temp file on disk (16 KB copy buffer);
  the raw document never enters the heap. Parse **off the main thread**, line
  by line, from the file through a small reader. Never regex the whole
  document. Startup parses the cached file the same way, never as a
  `ByteArray` + `String`.
- Peak memory therefore depends on the parsed channel count, not on the
  document size. The `MAX_BYTES` download ceiling stays only as a disk /
  anti-endless-stream guard, not a heap guard.
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
# STANDING RULE (owner, 2026-11-16): "send it to the phone" / "mándalo al móvil"
# always means the RELEASE build, signed with the project key, installed over the
# existing app with `-r` (keeps lists and favourites). The debug APK uses another
# signature and fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE on the phone. Use
# the debug build only on the emulator, or when the owner asks for it explicitly.
# See .pi/skills/android-apk/SKILL.md "Send to the phone (release)".
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

Project defaults live in `.pi/settings.json`.

**Decisión 2026-11-15: todos los subagentes de este proyecto corren en la
suscripción de Anthropic** (OAuth, `pi auth login anthropic`), no en OpenRouter.
Los identificadores nativos usan guiones (`anthropic/claude-opus-5-5`), no el
formato de OpenRouter con puntos (`openrouter/anthropic/claude-opus-5.5`).
Verificado con llamadas mínimas a `claude-haiku-5-5`, `claude-sonnet-5-5`,
`claude-opus-5-5` y `claude-fable-5-2`: los cuatro resuelven por la OAuth.

El coste de un subagente ya no se mide en dólares por token sino en **cuota**
de la suscripción, así que el tiering busca gastar la ventana de uso lo más
lento posible: Opus solo donde el juicio es la tarea, Sonnet para código y
revisión acotada, Haiku para recon y trabajo mecánico.

| Role | Model | Thinking | Why |
|---|---|---|---|
| Main session | `openrouter/deepseek/deepseek-v4-pro` | medium | orchestrator (sin cambios) |
| `scout` | `anthropic/claude-haiku-5-5` | low | fast recon |
| `researcher` | `anthropic/claude-haiku-5-5` | medium | web synthesis |
| `android-architect` | `anthropic/claude-opus-5-5` | high | judgment, plan |
| `android-dev` | `anthropic/claude-sonnet-5-5` | medium | Kotlin/Compose quality |
| `iptv-engineer` | `anthropic/claude-sonnet-5-5` | high | correctness-critical parsing |
| `apk-smith` | `anthropic/claude-haiku-5-5` | medium | mechanical build loop |
| `qa-reviewer` | `anthropic/claude-opus-5-5` | high | independent review |
| `brand-designer` | `anthropic/claude-opus-5-5` | high | visual identity, image evaluation |
| `ui-designer` | `anthropic/claude-opus-5-5` | high | interaction design, states, judgment |
| `android-emulator` | `anthropic/claude-haiku-5-5` | low | emulator run + validation |
| `reviewer` (builtin) | `anthropic/claude-opus-5-5` | high | independent review |
| `worker` (builtin) | `anthropic/claude-sonnet-5-5` | medium | generic implementation |
| `delegate` (builtin) | `anthropic/claude-haiku-5-5` | medium | lightweight delegate |
| `evidence-auditor` (builtin) | `anthropic/claude-haiku-5-5` | low | source checking |
| `oracle` (builtin) | `anthropic/claude-opus-5-5` | high | second opinion, explicit use only |

Los agentes de `.pi/agents/` declaran el modelo en su frontmatter; los builtin
se fijan en `subagents.agentOverrides` de `.pi/settings.json`. Precedencia:
override de lanzamiento → `agentOverrides.<name>.model` → frontmatter →
`subagents.defaultModel` → modelo de la sesión padre.

Los adaptadores externos (`claude-code`, `claude-code-writer`, `codex-exec*`,
`cursor-agent*`) **no** son hijos de Pi: su `model` es un alias de su propia CLI
y se deja como está.

La sesión principal sigue en OpenRouter (`deepseek-v4-pro`), cuyo coste sí está
**medido** sobre el gasto real registrado el 2026-11-03: ~$0,90/$1,50 por 1M con
caché a $0,25/M. `defaultModel` de la sesión principal va SIN prefijo de
proveedor (`deepseek/deepseek-v4-pro`); los modelos de subagentes sí lo llevan
(`anthropic/claude-opus-5-5`).

### Modelos gratuitos y failover (decisión 2026-11-03, fuera del camino por defecto)

Desde 2026-11-15 ningún subagente del proyecto usa `openrouter/free-first`: los
roles corren en la suscripción de Anthropic. Lo de abajo sigue vigente si se
quiere volver a un modelo gratuito con `/run <agente>[model=openrouter/free-first]`
o reactivarlo en `.pi/settings.json`.

`openrouter/free-first` es un **modelo virtual** que registra
`.pi/extensions/free-first-router.ts`: intenta modelos `:free` de OpenRouter y,
si fallan por cuota, rate limit o proveedor caído, **escala solo** a
`deepseek/deepseek-v4.1-flash` y sigue la tarea. Sin ese failover no se usa un
modelo gratuito: `pi-subagents` resuelve **un único modelo por lanzamiento** y no
hace fallback (hay que relanzar explícitamente con otro modelo si un hijo falla).

Reglas:

- El tier gratuito de OpenRouter son **20 req/min** y **50 req/día** si la cuenta
  compró < 10 créditos, o **1000 req/día** si compró ≥ 10 (esta cuenta: 1000).
  Un run de agente completo son 6-10 requests.
- Para comprobar cuota: `docs/TOKEN_AUDIT.md` §2.1 (no imprimir la clave).
- Los hijos no heredan modelos virtuales: `subagents.defaultSubagentOnlyExtensions`
  en `.pi/settings.json` los carga. Esas rutas se resuelven desde el **cwd del
  hijo**, no desde `.pi/`, así que `.pi/extensions/free-first-router.ts` vale
  mientras el hijo corra con la raíz del repo como cwd; desde otro cwd (worktree,
  subdirectorio) hay que usar ruta absoluta. Si la extensión no carga, el hijo
  manda `free-first` crudo a OpenRouter y falla con 400.
- Un modelo gratuito que responde mal **no** dispara el failover: si la calidad
  no basta, se cambia de modelo o se sube la tarea a un agente de pago.
- La auditoría completa (consumo real, tarifas medidas, pruebas de los modelos
  gratuitos) está en `docs/TOKEN_AUDIT.md`. El gasto está en el contexto del
  orquestador (92% cache reads), no en los subagentes: compactar sesión ahorra
  más que mover agentes a gratis.

Escalation examples:

```text
/run apk-smith[model=anthropic/claude-opus-5-5:high] "Fix the Gradle failure and install the debug APK"
/run android-dev[model=anthropic/claude-opus-5-5:high] "Implement the channel list screen"
/run scout[model=openrouter/free-first:low] "Map the parser entry points"
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
- **2026-11-03 — streaming parse, measured on an emulator (A/B)**: a synthetic
  32.2 MB / 165,000-entry list (`scripts/generate-large-playlist.mjs`, served
  from the host over `10.0.2.2`) on `emulator-5554` (`sdk_gphone64_x86_64`,
  API 36, the AVD's stock 192 MB Java heap growth limit), the same list and the
  same procedure on both builds, `dumpsys meminfo` **Dalvik Heap** sampled
  throughout. Opening the cached list: **168.1 MB** peak before the change,
  **75.9 MB** after it. The old path jumped to 98.3 MB in a single step (the
  document materialised) and settled at 74.4 MB once the parse was done, i.e.
  ~92 MB of transient scaffolding over a 32 MB file — 32 MB of bytes plus the
  ~64 MB `String` built from them. First network load after the change: 78.4 MB.
  `Actualizar` with the list already loaded: **the old build crashed** with
  `FATAL EXCEPTION: java.lang.OutOfMemoryError` at 191.8 MB (it would have
  finished on a device whose heap limit is larger, at roughly twice the channel
  model); the new build peaked at **147.2 MB**, finished, advanced the header
  timestamp and rendered 165,000 channels (Live/Movies/Series 55.0k each). A
  refresh holding two channel models at once is by design: the old list stays
  until the new one parses, which is what makes a failed refresh harmless.
  Deleting the test list left no `.tmp`/`.copy` staging file in `filesDir`.
  Emulator numbers are not comparable with the Galaxy S25 numbers above; what is
  comparable is old against new on the same device.
- Still unverified: Android 7 devices, all three demo streams, real HTTP
  401/403/404 handling, scrolling a large list by hand instead of with synthetic
  flings, and the streaming-parse heap measurement on a physical phone rather
  than an emulator.
- **2026-11-03 — auditoría de consumo y enrutado gratuito**: medido el coste real
  de todas las sesiones del repo (7,99 USD: 6,05 el orquestador, 1,93 los
  subagentes en 25 runs). Cinco modelos `:free` de OpenRouter resolvieron la misma
  tarea de tool-calling en este repo con coste 0 y sin tocar ficheros del repo.
  `openrouter/free-first` verificado en sesión principal (despacho a
  `nemotron-3.5-lightning:free`), en la escalada (cadena inexistente y modelo sin
  soporte de tools → `deepseek-v4.1-flash`, exit 0) y en hijos `scout` y `apk-smith`
  lanzados con el modelo de configuración (coste 0). Los 1000 requests gratuitos
  del día: 49 usados por todas las pruebas. Ruta de la extensión para hijos:
  `.pi/extensions/free-first-router.ts`, relativa al **cwd** del hijo (no a
  `.pi/`); verificada con un hijo `scout` en primer plano que despachó a
  `nemotron-3.5-lightning:free` (exit 0, coste 0). Detalle en `docs/TOKEN_AUDIT.md`.
- **2026-11-09 — mute, measured on an emulator (API 36)**: the toggle uses
  Media3's `Player.mute()`/`unmute()` (per-player output gain) and never
  `setDeviceVolume`/`setDeviceMuted`/`AudioManager`. Objective proof from the
  `dumpsys media.audio_flinger` track row of the app's own AudioTrack (uid 10216):
  `G db = -33`, `L dB`/`R dB = 0` unmuted → `G db = -inf` with `L dB`/`R dB =
  -inf` muted → back to `-33` on unmute, while the track stayed active and
  `FrmRdy` kept growing, i.e. the video keeps playing and only its audio is
  silenced. The phone's volume never moved: `settings get system volume_music`
  returned 5 before and after, `STREAM_MUSIC` stayed `Muted: false`/`Muted
  Internally: false`. `dumpsys audio`'s audio-focus stack was **empty** while a
  channel played, so the app never takes focus from another app (Media3 1.11.1's
  `ExoPlayer.Builder.handleAudioFocus` is never assigned and so defaults to false;
  `setAudioAttributes` is never called). The `Sin sonido` badge and the
  `VolumeOff` icon showed in portrait and in fullscreen, and entering/exiting
  fullscreen still forced landscape and restored portrait. A player built for
  another channel was already at `-inf` on its first dump, and the mute survived
  a force-stop and relaunch (the new track session was created already muted).
  Screenshots: `captures/player-muted.png`, `captures/player-fullscreen-muted.png`.
  Not verified on a physical phone; the DataStore write-failure path is untested.
- **2026-11-15 — series covers, emulator (API 36)**: unavailable artwork can
  come from refused image hosts or malformed provider paths, not the app. The
  app normalizes artwork URLs, short-circuits refused image hosts for 5 min, and
  reports unavailable covers. No third-party metadata service fills the gap.
  A local fake panel (`scripts/fake-xtream-artwork.mjs`, served at
  `10.0.2.2:8787`) proved that PHP-serialized, root-relative and absolute covers
  render, a refused host falls back to the initial with a warning, and series
  detail shows the `s:NN:` cover from `get_series_info`. 0 `FATAL EXCEPTION`.
  Not verified on a physical phone.

- **2026-11-16 — catalogue sort/filter/detail, emulator (API 36)**: built by
  agents (ui-designer spec -> android-architect plan + critique -> android-dev
  -> qa-reviewer + ui-designer cross-review -> fixes -> android-emulator
  evidence -> qa-reviewer gate: PASS with warnings). 238 unit tests pass,
  including measured / rich / bare / malformed panel fixtures and an old-cache
  decode. Sort order, genre filtering and missing-date handling were checked
  against catalogue data. With `scripts/fake-xtream-panel.mjs`: `rich` offers
  and sorts by release date, `bare` offers only provider order and A-Z,
  `malformed` loads 1,398/1,400 synthetic titles and hides sorts below 50%
  coverage. Live playback was also checked. A VOD endpoint returning HTML in
  place of video produced Media3's `PARSING_CONTAINER_UNSUPPORTED`; the message
  is now actionable. Not verified: physical phone, rotation, TalkBack, 2x font,
  heap with 100k titles.

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
11. **Virtual models don't reach subagents by inheritance**: a child only gets
   `openrouter/free-first` if the router extension is listed for it
   (`subagents.defaultSubagentOnlyExtensions`). Those paths resolve from the
   child's **cwd** (not from `.pi/`), so `.pi/extensions/free-first-router.ts`
   works as long as children run with the repo root as cwd; from any other cwd
   (worktree, subdirectory) use an absolute path. If the extension does not load
   in the child, it sends `free-first` to OpenRouter and fails with
   `400 free-first is not a valid model ID`.
