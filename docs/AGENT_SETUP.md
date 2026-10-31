# Agent and skill setup

This document explains the agent harness chosen for **Open IPTV** on
2026-10-31, why it was chosen, what was installed, and how to reproduce it.

The short version: Pi does not ship sub-agents or plan mode by default. We added
`pi-subagents` for role-based delegation with per-agent models, `pi-web-access`
for current Android/Media3 research, six project agents, and five project
skills tailored to IPTV. We deliberately avoided a Python/CrewAI stack.

## Environment at setup time

| Item | Value |
|---|---|
| OS | Windows (Git Bash) |
| Pi | 1.4.0 |
| Auth | your own Pi provider key (OpenRouter is one option) |
| Parent model | any capable orchestrator |
| JDK | 17 (Temurin or equivalent) |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` |
| SDK platforms | 36 (37.0 installable for an AGP 9 migration) |
| Build tools | 36.0.0 or newer |
| Global Gradle | not installed; use the wrapper |
| Device | a physical Android device with USB debugging |

The project must be started **from this directory** (`my-iptv-player`) so Pi
loads `.pi/settings.json`, `.pi/agents/`, and `.pi/skills/`. Approve project
trust when prompted, or run `pi --approve` once.

## Why this harness

Pi's own README says it skips sub-agents and plan mode by default and suggests
installing a package. We evaluated the options:

| Option | Verdict |
|---|---|
| `pi-subagents` (nicobailon) | **Chosen.** ~660k npm downloads/month, builtin scout/worker/reviewer/oracle, per-agent models, chains, background runs, FleetView. MIT. |
| `@henryqw/pi-subagent` | Viable alternative (~10k/month) focused on durable task graphs. More machinery than this project needs. |
| CrewAI / Python crews | Wrong shape. It adds a Python runtime, a second agent framework, and a server around a single Android app. Pi already has the tools and the session. |
| Custom bash sub-agent wrapper | Reinventing model routing, context assembly, run state, and cancellation. No. |
| No delegation | Slower for review/research and a single model for everything. |

`pi-web-access` was added because `researcher`/`evidence-auditor` need
`web_search`, `fetch_content`, `get_search_content`, and `source_check`. It
works without API keys (zero-config Exa MCP) and can use keys later.

## Installed packages

```bash
pi install npm:pi-subagents
pi install npm:pi-web-access
```

They are declared both in the user settings and in this project's
`.pi/settings.json`, so a fresh clone can reproduce the harness after trust.

User-level addition (machine-specific, not committed):

```json
{
  "subagents": {
    "defaultSubagentOnlyExtensions": [
      "<agent-dir>/npm/node_modules/pi-web-access/dist"
    ]
  }
}
```

This gives sub-agents the web tools without hard-coding a machine path in the
repository.

## Agents

Project agents live in `.pi/agents/`. Builtins are overridden in
`.pi/settings.json`.

| Agent | Kind | Model | Thinking | Job |
|---|---|---|---|---|
| `android-architect` | project | sonnet-5.5 | high | read-only plan, risks, file list |
| `android-dev` | project | sonnet-5.5 | medium | Compose/UI/state/persistence |
| `iptv-engineer` | project | sonnet-5.5 | high | parser, search, Media3, streams |
| `apk-smith` | project | deepseek-v4-pro | high | Gradle, APK, signing, adb |
| `qa-reviewer` | project | sonnet-5.5 | high | read-only review and tests |
| `brand-designer` | project | sonnet-5.5 | high | logo concepts, image generation, brand brief |
| `scout` | builtin | haiku-5.5 | low | fast recon |
| `researcher` | builtin | gemini-2.5-flash | medium | web research with sources |
| `reviewer` | builtin | sonnet-5.5 | high | generic review |
| `oracle` | builtin | opus-5.5 | high | second opinion, explicit use |
| `worker` | builtin | sonnet-5.5 | medium | generic implementation |
| `delegate` | builtin | deepseek-v4-pro | medium | lightweight delegate |

Why this routing:

- **Judgment and Kotlin/Compose correctness** go to Claude Sonnet 5.5.
- **Planning and review** get `high` thinking; implementation `medium`.
- **Build loops** are mechanical and benefit from deepseek-v4-pro's much lower
  price at high thinking. Escalate to Sonnet if a Gradle error repeats.
- **Recon/research** use cheap models; the expensive Opus is opt-in via `oracle`.
- The parent can stay on deepseek-v4-pro; it delegates the expensive thinking.

Approximate OpenRouter prices per 1M tokens at setup time:

| Model | Input | Output |
|---|---:|---:|
| deepseek-v4-pro | $0.71 | $1.42 |
| deepseek-v4.1-flash-1017 | $0.014 | $0.144 |
| haiku-5.5 | $0.10 | $0.50 |
| gemini-2.5-flash | $0.30 | $2.50 |
| sonnet-5.5 | $2.00 | $10.00 |
| opus-5.5 | $4.00 | $20.00 |

Verify prices before budgeting; the model catalog changes.

## Skills

### Global skills (user-level, installed with `npx skills`)

| Skill | Source | Installs | Why |
|---|---|---:|---|
| `agp-9-upgrade` | `android/skills` (Google) | official | only for an approved AGP 9 migration |
| `testing-setup` | `android/skills` (Google) | official | unit/UI/screenshot test strategy |
| `edge-to-edge` | `android/skills` (Google) | official | insets/system bars for a video UI |
| `compose-ui-testing-patterns` | `chrisbanes/skills` | 2.3k | Compose semantics/screenshot tests |
| `mobile-android-design` | `wshobson/agents` | 25k | Material 3 patterns for a simple UI |

```bash
npx skills add android/skills --skill agp-9-upgrade testing-setup edge-to-edge -g -y --agent pi
npx skills add chrisbanes/skills --skill compose-ui-testing-patterns -g -y --agent pi
npx skills add wshobson/agents --skill mobile-android-design -g -y --agent pi
```

### Project skills (committed in `.pi/skills/`)

| Skill | Why it exists |
|---|---|
| `iptv-playlists` | M3U/Xtream parsing, classification, search, large-list performance, redaction |
| `media3-hls` | ExoPlayer/HLS/TS, lifecycle, error mapping, cleartext HTTP |
| `android-apk` | verified Gradle matrix, build/sign/verify/install, troubleshooting |
| `stream-probe` | `probe.mjs` to test a playlist/stream before blaming the app |
| `logo-generation` | OpenRouter image API, prompt rules, adaptive-icon specs |

`stream-probe` is a real tool, not just prose:

```bash
node .pi/skills/stream-probe/probe.mjs local/list.m3u --limit 5
node .pi/skills/stream-probe/probe.mjs local/list.m3u --probe-stream
```

It reports entry totals, per-type counts, HLS master/variant/segment details,
and TS sync bytes, with credentials redacted. Use a synthetic or local test
playlist; never commit a real provider list.

### Skills evaluated and rejected

- Community `exoplayer`/`media3` skills with 2–19 installs: too small to trust,
  and the official Media3 API is small enough to encode in our own skill.
- `limrun-gradle` / `limrun-android-emulator` (40k / 39k): cloud build/emulator
  services. Unnecessary: JDK 17 + Android SDK + a phone are already present.
- `navigation-3`, `adaptive`, `r8-analyzer`, `android-profiler`: not needed for
  v1. Adding them would enlarge the context without changing a decision.
- `android-clean-architecture`, `kotlin-specialist`: generic and duplicative of
  the project guide + Compose skill. One module, no DI, no clean-architecture
  ceremony.

## Bootstrap on a fresh machine

```bash
# 1. Pi packages
pi install npm:pi-subagents
pi install npm:pi-web-access

# 2. Global Android skills
npx skills add android/skills --skill agp-9-upgrade testing-setup edge-to-edge -g -y --agent pi
npx skills add chrisbanes/skills --skill compose-ui-testing-patterns -g -y --agent pi
npx skills add wshobson/agents --skill mobile-android-design -g -y --agent pi

# 3. Project trust + run
cd my-iptv-player
pi --approve
```

Then set the user-level `defaultSubagentOnlyExtensions` path to the actual
`pi-web-access/dist` location on that machine (see above).

## How to use it

Natural language is enough; Pi decides whether to delegate:

```text
Use scout to map the current app and list the files we need to touch.
Use android-architect to plan the playlist screen. Do not write code yet.
Use iptv-engineer to implement the parser and prove it on samples/demo.m3u.
Use apk-smith to build a debug APK and install it on my phone.
Use qa-reviewer to review the parser and the player lifecycle.
Ask oracle whether our Live/Movies/Series split is the right v1 decision.
```

Explicit model override for one run:

```text
/run apk-smith[model=openrouter/anthropic/claude-sonnet-5.5:medium] "Fix the Gradle failure and install the debug APK"
```

Orchestration:

```text
Run a chain: scout → android-architect → iptv-engineer → apk-smith → qa-reviewer.
Stop and report if any step fails.
```

The recommended low-supervision loop:

```text
clarify → scout → architect → (dev | iptv-engineer) → apk-smith → qa-reviewer
```

## Validation performed

After setup, these checks were run from inside `my-iptv-player` with
`pi --approve --no-session`:

| Check | Result |
|---|---|
| Project agent discovery | 5 project agents listed with their models |
| Real subagent launch | `scout` returned a repository summary; no edits |
| Web research launch | `researcher` returned Media3 1.11.1 with the Google Maven URL |
| Skill discovery | project skills `iptv-playlists`, `media3-hls`, `android-apk`, `stream-probe` visible |
| Probe on demo playlist | 3 entries, public HLS URLs |
| Probe on a large mixed playlist | entry totals and per-type counts reported |
| Probe stream check | Mux master → 64-segment media playlist → first segment HTTP 206, TS sync true |
| Secret scan | no provider host/username in committed files; `local/` ignored by git |
| Android debug build | `./gradlew :app:assembleDebug` succeeded; APK ~16 MB |
| Unit tests | parser/search tests pass |
| Large playlist parser | parser totals and classification matched the probe |
| Device install | `adb install -r` succeeded on a physical Android 16 device |
| Large playlist in app | cached and loaded; tabs showed the expected totals |
| Live channel playback | Media3 `AudioTrack` active; no `FATAL EXCEPTION` |
| Public HLS playback | Mux test stream played video + audio from a local HTTP demo playlist |
| Cache startup | Wi-Fi/data disabled; relaunch showed the cached list, no network request |
| Refresh failure | Network disabled; Actualizar kept the old list and showed a connectivity error |
| Refresh success | Network restored; timestamp and cache updated |
| Fullscreen | Landscape + immersive; exit button and back restored the top bar |
| Rotation | Same Media3 player ID after portrait -> landscape; no reconnect, no crash |
| Back | Player stopped and released; list returned |

The debug APK, unit tests, device install, large-playlist load, playback, cache,
refresh fallback, fullscreen, rotation, and player release are verified. Android
7 compatibility, all three demo streams, and end-to-end search are still
unverified.

OkHttp is pinned to 4.12.0 in the app: OkHttp 5.5.1 pulls `okhttp-android`,
whose AAR requires compileSdk 37, which conflicts with the AGP 8.13.2 baseline.

## Toolchain decision

We intentionally pinned **AGP 8.13.2 + Gradle 8.14.6 + Kotlin 2.2.21 +
Compose BOM 2026.06.01 + Media3 1.11.1 + compileSdk 36**.

Reasons:

- Compose 1.12 (BOM 2026.09.00) requires **compileSdk 37 and AGP 9.1+**.
- AGP 9 uses **built-in Kotlin** and pins **KGP 2.2.10**, which complicates
  matching the Compose compiler plugin.
- Media3 1.11.1 and activity-compose 1.13.0 require **compileSdk 36**.
- The installed SDK has platform 36 and build-tools 36.0.0. Platform 37.0 is
  installable, but only needed for the AGP 9 migration.
- A boring, reproducible build is more valuable than the newest plugin.

The full matrix and re-verification commands are in
`.pi/skills/android-apk/references/versions.md`.

## Security notes

- Real playlists moved to `local/`, which is git-ignored. `samples/` contains
  only public test streams.
- `*.url`, keystores, `keystore.properties`, and `local.properties` are ignored.
- `stream-probe` redacts Xtream username/password path segments and sensitive
  query parameters by default. `--show-urls` is explicit and dangerous.
- Third-party skills run with full agent permissions. We installed Google's
  official skills and two well-known community sources; review them if the
  project's trust model changes.

## What is still missing

- The app is scaffolded and `:app:assembleDebug` succeeds; the APK is
  `app/build/outputs/apk/debug/app-debug.apk`.
- The package is now `app.opentv`; it installs on a physical Android 16 device.
  Favorites (star toggles, per-category tabs, persistence) and the generated
  launcher/header mark were verified there. Android 7 and the final release
  signing are still pending.
- No CI workflow yet. GitHub Actions can build/test the APK and attach it to a
  release; see `docs/ROADMAP.md`.
- The repo is public under GPLv3.
