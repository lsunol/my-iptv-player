# Open IPTV roadmap

This is the working backlog after the first functional release. It records what
is done, what is deliberately postponed, and what was discarded.

## Done

- One playlist URL, cached locally and loaded on startup without a network call.
- Manual **Actualizar** keeps the current list if the refresh fails.
- Live / Movies / Series / All tabs, search, group filters.
- Fullscreen playback and rotation without recreating the player.
- Modern dark Material 3 UI, compact rows, provider `tvg-logo` images with a
  generated initial fallback.
- **Favorites** per category (Live / Movies / Series / All), stored on-device.
- Neutral branding: a mint/violet play-and-signal mark, matching launcher icon
  and in-app header.
- MIT/GPL-compatible dependency set; the repo is public under GPLv3.

## Next: EPG (low priority, do not disturb the current list screen)

Goal: show **Now / Next** for a channel without making the main screen busy.

- Source: provider XMLTV or Xtream `xmltv.php` first.
- Map programmes by exact `tvg-id`; fall back only if necessary.
- Parse with XmlPullParser/SAX off the main thread; store UTC timestamps.
- Store in Room once Room is introduced; show Now/Next in the channel row or a
  detail sheet, not as a permanent block on the clean list.
- Phase 2: a separate EPG screen with a lazy channel × time grid and search.
- Risks: `tvg-id` mismatch, timezones, large XMLTV documents, update cadence.
- Research: see `docs/FEATURE_RESEARCH.md` section 3.

## Next: radio overlay and time-shift

Goal: play a user-supplied radio stream over muted TV audio, with a 50–120 s
sync slider.

- Phase 1: two Media3 players, TV muted, radio audio; fixed offset. If the TV
  stream is HLS with enough DVR, time-shift the TV instead of the radio.
- Phase 2: local ring buffer/recording of the radio stream for arbitrary
  50–120 s delay. This is the only robust approach for Icecast; it costs CPU,
  battery and buffer lifecycle work.
- Do not bundle radio stations. The user supplies a legal stream URL. Add a
  disclaimer about broadcast rights and personal time-shifting.
- Research: see `docs/FEATURE_RESEARCH.md` section 5.

## Discarded for now

- **Global “most played” channels**: would require telemetry/backend, consent,
  privacy policy, GDPR and Play Data Safety work. The app stays no-telemetry.
- **Local most-played**: the user is not interested in per-device statistics.
- **Bundled brand logos** (Movistar, DAZN, etc.): trademark/copyright risk. The
  app only uses provider-supplied `tvg-logo` URLs and a generic fallback.
- **Multi-list management**: the product is intentionally one list.

## APK delivery / GitHub Actions (separate chapter)

A GitHub Actions workflow can build the APK on every push, run unit tests, upload
an artifact, and create a GitHub Release on tags. For a public download link,
use a Release asset, not an expiring workflow artifact:

```text
https://github.com/lsunol/my-iptv-player/releases/latest/download/app-debug.apk
```

A release-signed APK needs a keystore stored in GitHub Secrets. Debug APKs are
for testers only. A workflow can also add a build badge to the README.

## Suggested order

1. EPG Now/Next (when the UI can stay clean).
2. Radio overlay Phase 1.
3. GitHub Actions + release.
4. EPG grid and radio Phase 2 only if the simpler versions are solid.
