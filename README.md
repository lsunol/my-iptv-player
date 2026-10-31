# Open IPTV

A small, transparent Android IPTV player.

**Status: first functional APK builds, installs, and plays.** Paste a playlist
URL, browse Live/Movies/Series, search, and play with Media3. Tested on a
physical Android 16 device with a large mixed playlist and a public HLS stream.
The APK is a debug build for personal sideloading.

## What it will do

1. Paste one playlist URL (M3U/M3U8 or Xtream `get.php`).
2. See Live / Movies / Series tabs and the channel count.
3. Search by name (accents and case do not matter), filter by group.
4. Tap a channel and watch it, in portrait or fullscreen landscape.
5. Understand every failure: bad URL, expired subscription, HTTP 403, timeout,
   malformed playlist, unsupported codec.
6. Keep the last working list on disk; opening the app does not re-download it.
   **Actualizar** refreshes it, and a failed refresh keeps the old list.

No account, no backend, no ads, no analytics, no cloud sync.

## Why

IPTV playlists are often huge, inconsistent, and full of duplicate variants.
Most players hide what is happening. Open IPTV is intentionally simple and
explains itself.

The app is designed for large mixed playlists: tens of thousands of entries,
many groups, and duplicate quality variants. That shape drives the design:
stream-parsing off the main thread, a Live-first list, precomputed search keys,
and no expensive work per row.

## Quick start (development)

Prerequisites: JDK 17, Android SDK with platform 36, `adb`, and Node.js 22+ for
the probe tool. A phone with USB debugging is recommended.

```bash
# 1. Point Gradle at your Android SDK (git-ignored)
cp local.properties.example local.properties
# edit sdk.dir in local.properties if your SDK lives elsewhere

# 2. Agent harness (Pi)
pi install npm:pi-subagents
pi install npm:pi-web-access
cd my-iptv-player
pi --approve

# 3. Probe the sample playlist
node .pi/skills/stream-probe/probe.mjs samples/demo.m3u --limit 3

# 4. Build
./gradlew :app:assembleDebug

# 5. Install
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The full harness rationale, agent roster, model routing, and bootstrap commands
are in [`docs/AGENT_SETUP.md`](docs/AGENT_SETUP.md). Project rules for agents are
in [`AGENTS.md`](AGENTS.md).

## Install on your phone

1. On the phone: enable **Developer options** and **USB debugging**.
2. Connect a **data** USB cable and choose **File transfer / MTP** (not
   charge-only). Accept the RSA authorization prompt.
3. From the project:

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.opentv/.MainActivity
```

The APK is a debug build signed with the local debug key. It is for personal
sideloading, not for publishing. If `adb devices` is empty, the phone is not
visible to Windows yet: try another cable/port, unlock the phone, or use
wireless debugging (`adb pair` + `adb connect`).

## Repository layout

```text
.
├── AGENTS.md                 # project rules for Pi agents
├── .pi/
│   ├── settings.json         # model routing, packages, subagent config
│   ├── agents/               # android-architect, android-dev, iptv-engineer,
│   │                         # apk-smith, qa-reviewer
│   └── skills/
│       ├── iptv-playlists/   # M3U/Xtream parsing and search
│       ├── media3-hls/       # ExoPlayer, HLS/TS, lifecycle
│       ├── android-apk/      # build, sign, verify, install
│       └── stream-probe/     # probe.mjs CLI
├── docs/AGENT_SETUP.md       # why this harness, how to reproduce it
├── local/                    # optional local playlists (git-ignored)
└── samples/                  # public test streams only
```

## Privacy and security

- The app only contacts the playlist URL, channel streams, and channel logos.
- No telemetry, accounts, ads, or third-party SDKs.
- Local playlists live in `local/` and are never committed.
- Playlist credentials are redacted from logs and errors.
- HTTP cleartext is enabled because user-supplied IPTV lists commonly use
  `http://`; this is documented as a tradeoff.

## Toolchain

Pinned for reproducibility: Gradle 8.14.6, AGP 8.13.2, Kotlin 2.2.21, Compose
BOM 2026.06.01, Media3 1.11.1, compileSdk/targetSdk 36, minSdk 24, JDK 17.

Do not upgrade to AGP 9 / Compose 1.12 without an explicit decision: Compose 1.12
requires compileSdk 37 and AGP 9.1+, and AGP 9's built-in Kotlin complicates the
Compose compiler alignment. See
[`.pi/skills/android-apk/references/versions.md`](.pi/skills/android-apk/references/versions.md).

## License

GNU General Public License v3.0. See [`LICENSE`](LICENSE).

Copyright (C) 2026 Open IPTV contributors.
