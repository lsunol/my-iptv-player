# Open IPTV roadmap

This is the working backlog after the first functional release. It records what
is done, what is deliberately postponed, and what was discarded.

## Done

- One or more named lists (M3U URL or Xtream), with a picker and one active list.
  The active list's channels are cached locally and loaded on startup without a
  network call.
- Manual **Actualizar** keeps the current list if the refresh fails.
- Live / Movies / Series / All tabs, search, group filters.
- Fullscreen playback and rotation without recreating the player.
- Modern dark Material 3 UI, compact rows, provider `tvg-logo` images with a
  generated initial fallback.
- **Favorites** per category (Live / Movies / Series / All), stored on-device.
- Neutral branding: a mint/violet play-and-signal mark, matching launcher icon
  and in-app header.
- Apache-2.0/MIT dependency set, compatible with the repo license (GPLv3).
- Release signing: the project release keystore lives in the git-ignored
  `signing/` directory, and `app/build.gradle.kts` signs release builds from
  `signing/keystore.properties` and fails fast when it is missing, so a release
  build can never fall back to the debug key. First release-signed APK: 0.3.0.
- **Xtream catalogue (Movies/Series)**: for Xtream lists the app also calls
  `player_api.php` (`get_vod_streams`, `get_series`, both category lists and the
  lazy `get_series_info`). Playback URLs still come from the downloaded M3U
  (`output=m3u8`, so HLS); the API contributes metadata only. The merged
  catalogue is cached per list (`files/xtream-<listId>.json`, atomic write on
  full success only), so a Wi-Fi-off relaunch should show the grid without a
  network call. (The code path is wired and reviewed, but a Wi-Fi-off relaunch
  showing the grid has **not** been verified on a device yet.)
- **Poster grid** for Movies/Series: 3-column `LazyVerticalGrid`, persisted
  list/grid toggle (`v1_vod_layout`, grid by default), provider category chips
  replacing group pills on those tabs (a catalogue tab whose provider sends no
  categories shows no pills at all, instead of M3U group pills that the
  catalogue branch would ignore), and a series drill-down screen
  (series -> seasons -> episodes). Episodes map to the existing `Channel` shape,
  so the player is unchanged. If the API fails while the M3U works, the flat
  list is shown with a one-line warning.

### Catalogue follow-ups (not done yet)

- **Favourites**: the Favorites screen lists catalogue-only favourites as well,
  not just M3U channels, so a starred series (`series:<id>`) appears and opens
  its drill-down, and a movie whose fallback URL is not in the M3U
  (`url:<hash>`) appears and plays — both can be counted and un-starred there.
  Known gap: an *episode* whose stream is not in the M3U has the same invisible
  `url:<hash>` key and is only un-starrable from the row that starred it.
  Covering it needs the episode list of every starred series (one network call
  per series), so it waits for a batch/persisted episode cache.
- Deduplication/quality-variant grouping of grid items is still v1.1.
- Ratings and movie plot screens are not rendered in the grid.

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

**Multi-list management** used to be listed here as "the product is
intentionally one list". That decision was reversed on 2026-11-01; the feature
shipped and now lives under **Done** above.

## Next: signed release and GitHub Releases (top priority)

Distribution is **GitHub Releases**. There is no Play Store track: the app ships
empty, needs cleartext HTTP for user-supplied sources, and Play would add policy
review without adding reach that matters here.

Remaining work:

- Wire the existing release keystore into CI with GitHub Secrets, so a tagged
  build on a clean runner can sign without a local `signing/` directory.
- Enable `isMinifyEnabled` / `shrinkResources` for `release` and verify the
  parser and Media3 still work under R8.
- Add a GitHub Actions workflow that runs `:app:testDebugUnitTest` and
  `:app:assembleRelease` on tags, then attaches the APK to a Release.
- Publish the APK SHA-256 and the signing certificate fingerprint in each
  release so downloads can be verified independently.
- Keep `versionCode` monotonically increasing and tag releases `vX.Y.Z`.

Link to a Release asset, never an expiring workflow artifact, and never the
debug APK:

```text
https://github.com/lsunol/my-iptv-player/releases/latest/download/app-release.apk
```

Optional, once releases exist: submit to **IzzyOnDroid** (an F-Droid-compatible
repo that consumes your already-signed release APK) and mention that
**Obtainium** can follow the releases for automatic updates.

## Next: parse the playlist as a stream

Today the whole document is materialised before parsing: raw bytes, then a
`String`, then a `List<String>` of lines, then the channels. For a 35 MB list
that is a peak of a few hundred MB, which is why a byte ceiling has to exist at
all. Streaming the response into a line-by-line parser, and writing the cache
file as the bytes arrive, would drop the peak to roughly the parsed channels
alone, and would let the byte ceiling go away. It also matches what
`AGENTS.md` already asks for ("parse line by line, never regex the whole
document"). Not urgent while real lists stay under ~35 MB.

## Suggested order

1. Signed release + GitHub Actions (see above). Nothing is distributable until
   this exists.
2. EPG Now/Next (when the UI can stay clean).
3. Radio overlay Phase 1.
4. EPG grid and radio Phase 2 only if the simpler versions are solid.
