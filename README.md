# Open IPTV

A small, transparent Android IPTV player.

**Status: functional Android player with multiple lists, favorites and a custom
brand mark.** Paste one or more playlist URLs, browse Live/Movies/Series,
search, play with Media3, save per-category favorites, and use
provider-supplied channel logos. Tested on a physical Android 16 device.

## Download

APKs will be published as **GitHub Releases**, which is the only supported
download channel:

```text
https://github.com/lsunol/my-iptv-player/releases/latest
```

Every release is signed with the project release key, so newer versions install
over older ones, and each release lists the SHA-256 of its APK so you can verify
what you downloaded. There is no signed release yet: until the first one is
published, build from source with the development instructions below.

Do **not** distribute `app-debug.apk`. It is signed with the public Android
debug key, so anyone can forge an "update" for it, and it cannot be upgraded in
place by a release-signed build.

## What it does

1. Paste one or more playlist URLs (M3U/M3U8 or Xtream `get.php`), give each a
   name, and switch between them.
2. See Live / Movies / Series tabs and the channel count.
3. Search by name (accents and case do not matter), filter by group.
4. Tap a channel and watch it, in portrait or fullscreen landscape.
5. Understand every failure: bad URL, expired subscription, HTTP 403, timeout,
   malformed playlist, unsupported codec.
6. Keep the last working list on disk; opening the app does not re-download it.
   **Actualizar** refreshes it, and a failed refresh keeps the old list.
7. Mark channels as favorites and open a per-category **Favoritos** screen.
8. Use provider-supplied channel logos when they exist; otherwise show a
   generated initial. No brand logos are bundled.

No account, no backend, no ads, no analytics, no cloud sync.

## What this app does not do

It ships **empty**. There is no bundled channel list, no demo provider, and no
"free TV" catalogue, and it will never help you find one. Open IPTV is a
player: you paste a source you are entitled to use. It is not affiliated with,
endorsed by, or sponsored by any provider, and no brand logos are bundled in
the APK or the repository.

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
in [`AGENTS.md`](AGENTS.md). The feature research and backlog are in
[`docs/FEATURE_RESEARCH.md`](docs/FEATURE_RESEARCH.md) and
[`docs/ROADMAP.md`](docs/ROADMAP.md). Changes are listed in
[`CHANGELOG.md`](CHANGELOG.md).

## Install

**From a release (recommended).** Download the APK from
[Releases](https://github.com/lsunol/my-iptv-player/releases/latest), open it on
the phone, and allow installing from that source when Android asks. Releases are
signed with the project release key, so newer versions install over older ones.
Check the published SHA-256 if you want to verify the download.

**From your own build.** On the phone, enable **Developer options** and **USB
debugging**. Connect a **data** USB cable and choose **File transfer / MTP**
(not charge-only). Accept the RSA authorization prompt.

```bash
./gradlew :app:assembleDebug
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.lluissunol.iptv/.MainActivity
```

If `adb devices` is empty, the phone is not visible to Windows yet: try another
cable/port, unlock the phone, or use wireless debugging (`adb pair` +
`adb connect`).

## Repository layout

```text
.
├── AGENTS.md                 # project rules for Pi agents
├── CHANGELOG.md              # release notes
├── LICENSE                   # GPLv3
├── .pi/
│   ├── settings.json         # model routing, packages, subagent config
│   ├── agents/               # android-architect, android-dev, iptv-engineer,
│   │                         # apk-smith, qa-reviewer, brand-designer
│   └── skills/
│       ├── iptv-playlists/   # M3U/Xtream parsing and search
│       ├── media3-hls/       # ExoPlayer, HLS/TS, lifecycle
│       ├── android-apk/      # build, sign, verify, install
│       ├── stream-probe/     # probe.mjs CLI
│       └── logo-generation/  # OpenRouter image logo workflow
├── docs/AGENT_SETUP.md       # why this harness, how to reproduce it
├── docs/FEATURE_RESEARCH.md  # EPG, radio, logos, favorites research
├── docs/ROADMAP.md           # done, next and discarded features
├── design/                   # generated logo concepts and brief
├── tools/generate-logo.mjs   # OpenRouter image generator
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
