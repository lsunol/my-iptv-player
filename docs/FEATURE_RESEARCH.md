# Open IPTV — Feature Research Report

*Research only; no code, no files edited. All findings from public web sources cited below. This document is not legal advice.*

> **Implementation note (v1):** the first favorites implementation uses a small
> `Set<String>` in DataStore because a single user's favorite list is small. Room
> remains the recommended store for EPG programmes and any future local stats.
>

---

## Decisions taken (2026-10-31)

- **Favorites:** implemented. Per-category tabs (Live/Movies/Series/All), persisted
  on-device, stable `favoriteKey` (`tvg-id` → URL hash).
- **Channel icons:** only the provider's `tvg-logo` URL is used; no brand logos
  are bundled. Fallback is a generated initial.
- **EPG:** low priority, kept in the roadmap; do not disturb the clean list.
- **Most-played:** discarded. Local stats do not interest the user, and global
  popularity would require telemetry/backend and privacy work.
- **Radio overlay:** kept in the roadmap, with the two-phase plan below.
- **Logo:** generated with `google/gemini-3.1-flash-image`; chosen mark and brief
  are in `design/`.

---

## 1) Favorites

**Bottom line:** Favorites are low-risk and high-value. Use Room (not DataStore), keyed by a composite stable ID (`tvg-id` → URL hash → name+group hash), with per-category tabs. Never silently delete favorites on refresh.

**Recommended approach**
- **Stable identity** must be computed once at parse time. Priority order: sanitized `tvg-id`; if absent or colliding, a hash of the stream URL; finally a hash of display name + group. This mirrors what mature players (Emby, xTeVe, TVirl) and libraries like `crispy-m3u` converged on, because `tvg-id` is often missing, non-unique, or reused, and URLs/names change frequently.
- **Storage: Room**, not DataStore. DataStore is for small key-value settings; Room gives indexed, queryable, partial-update storage for tens of thousands of rows, which is what a favorites table needs. (Android docs explicitly steer structured/large datasets to Room.)
- **Data model** — one `FavoriteChannel` entity keyed by `(stableId, playlistUrlHash)` storing a *snapshot* (`name`, `group`, `logoUrl`, `streamUrl`, `type` = LIVE/MOVIE/SERIES, `addedAt`). Scoping by playlist-URL hash prevents cross-playlist collisions when the user swaps their single URL.
- **Migration/staleness** — on refresh, match by `stableId`: update the snapshot if found; if not found, mark "unavailable" (dimmed + badge) instead of deleting. Offer "remove" and "clear unavailable" actions.
- **UX** — a top-level Favorites destination with tabs Live/Films/Series/All, star/heart toggle on rows, default sort by recency. Per-category favorites are sensible and consistent with the app's existing tabs.

**Risks / edge cases:** duplicate FHD/HEVC variants share the same name/group but differ by URL — the URL hash keeps them distinct (v1 deliberately shows variants); missing `tvg-id` falls to URL hash; a provider that rotates stream URLs for the same logical channel can make a favorite go stale (mitigate by re-matching via `tvg-id` first); changing the whole playlist URL orphans old favorites by design.

**Sources:** Android DataStore docs (developer.android.com/topic/libraries/architecture/datastore), "Prefer Storing Data with Jetpack DataStore" blog (android-developers.googleblog.com/2020/09), Room docs (developer.android.com/training/data-storage/room), Emby live-TV identity (emby.media/learn/articles/live-tv/channels/identity/TV-Lineups.html), xTeVe issue #101, crispy-m3u id.rs.

---

## 2) Channel Icons / Brand Logos

**Bottom line:** Do **not** bundle Movistar/DAZN logos. Use provider-supplied `tvg-logo` URLs (fetched at runtime) and generic fallback icons. Treat "free" logo libraries as trademark-encumbered. This is not legal advice.

**Recommended approach**
- **Fetch `tvg-logo` from the provider's own playlist** at runtime via Coil with caching. The provider is presenting the logo; this is the lowest-friction, most defensible path.
- **Bundle nothing brand-specific.** Use a generic, self-drawn or CC0 icon/monogram placeholder for channels without a logo.
- **Attribution**: if you do bundle any CC0/CC-BY artwork (e.g., Simple Icons for a *feature icon* like "favorite"), include attribution per the license. CC0 artwork needs no attribution, but its trademark disclaimer matters.
- **Avoid**: iptv-org logos (active DMCA takedowns from Warner Bros. Discovery and Nagravision); logo APIs (Clearbit Logo API was sunset Dec 1 2025; Brandfetch terms prohibit caching >30 days and don't transfer logo IP); Wikimedia Commons logo images (allowed only below "threshold of originality," and only copyright — never trademark — is waived).

**Legal caveats (not legal advice):** Logos are dual-protected — copyright on the artwork and trademark on the brand. A CC0/Unlicense on a repository's code or artwork does **not** waive trademark. Movistar's press terms allow editorial use with attribution and prohibit commercial use; DAZN's press kit grants only limited revocable licenses. Nominative/editorial use is a jurisdiction-dependent gray area. An open-source app that is redistributed is not clearly "personal use."

**Sources:** USPTO trademark/patent/copyright (uspto.gov/trademarks/basics/trademark-patent-copyright), U.S. Copyright Office FAQ, Wikimedia Commons Licensing (commons.wikimedia.org/wiki/Commons:Licensing), Simple Icons LICENSE.md, iptv-org DMCA issues #16839 and #1831, Clearbit Logo API FAQ, Brandfetch Terms.

---

## 3) EPG

**Bottom line:** Feasible and valuable. Start with `tvg-id` mapping + Now/Next only (in-memory or Room), then add a full grid. Use Room for tens of thousands of programmes.

**Recommended phased plan**
- **Phase 1 — Now/Next:** Parse XMLTV (or Xtream `xmltv.php`, which is gzip-compressed XMLTV) off the main thread with an event-driven parser (XmlPullParser/SAX). Map programmes to channels by exact `tvg-id` match against the playlist's `tvg-id`; fall back to normalized `tvg-name`/display-name matching only as a last resort (it's fragile). Store parsed `programme` rows keyed by `channelId`, indexed on `start`/`stop`, in Room. Parse `YYYYMMDDhhmmss [+-]ZZZZ` with `java.time`, store as UTC `Instant`, render in local zone. Show "Now / Next" per channel row.
- **Phase 2 — Grid:** A dedicated EPG screen using `LazyColumn` (channels) × `LazyRow` (time) with stable keys and `contentType`. Add WorkManager refresh (daily/6–12h) plus manual "Actualizar EPG", and search by title.

**Data model:** `EpgChannel(id, displayName, iconUrl)` and `EpgProgramme(channelId, startUtc, endUtc, title, desc, category)` with indices on `channelId` + times. XMLTV has no incremental format, so the common pattern is full re-download and diff/replace in Room.

**Sources:** For EPG data prefer the provider's `xmltv.php` (Xtream) first; external public sources (iptv-org/epg, epg.pw) are user-supplied fallbacks.

**Risks:** `tvg-id` mismatch is the #1 cause of empty guides (strict, case/whitespace-sensitive); timezone mistakes shift all times; large grids jank without lazy virtualization and stable keys; OOM if you hold the whole document in memory.

**Sources:** XMLTV wiki + DTD (wiki.xmltv.org, github.com/XMLTV/xmltv), Xtream `xmltv.php` docs (worldofiptvcom/xtream-codes-api), UniPlayer M3U reference, StreamTest tvg-id guide, Android Room/WorkManager/Compose docs.

---

## 4) Most-Played Channels

**Bottom line:** Implement **local, on-device play counts only**. Any global popularity data contradicts the app's "no telemetry, no analytics" mission and triggers GDPR/Play-policy overhead that isn't worth it for v1.

**Recommended approach**
- Track per-channel `playCount`, `lastPlayedAt`, and optionally `totalWatchMs`, stored in **Room** (structured, indexed aggregation over tens of thousands of rows — DataStore is wrong for this).
- Rank "most played" by a small recency-weighted score (count + last-played decay), computed locally, shown as a "Recently/Most played" row or filter.
- Add a **"Clear play history / Reset statistics"** control in Settings (and note Android's own App-info "Clear data" also wipes it).
- **No telemetry, no opt-in analytics, no curated global list** in v1. If a global list is ever wanted, ship it as a static, non-personal, bundled/curated list rather than collecting user data.

**Privacy caveats:** Data stays on device and is never transmitted — say so in-app ("personal, on-device metric"). Opt-in telemetry would require a privacy policy, consent flow, Data-safety declarations, data-deletion controls (Play requires data-deletion options since Dec 2023), and GDPR lawful-basis/erasure compliance — disproportionate for this feature.

**Sources:** Google Play Developer Policy Center (privacy + SDKs topics), GDPR-info.eu (principles, consent, right to erasure), Android Room docs, Android DataStore docs, Android data-storage overview.

---

## 5) Radio Overlay + 50–120s Sync

**Bottom line:** Two players + mute is easy. A true user-adjustable 50–120s delay is **hard and requires local buffering** — you cannot simply "seek back" on a live radio stream. Ship the easy parts first.

**Feasibility breakdown**
- **EASY:** Two Media3 `ExoPlayer` instances; muting the TV audio via `setVolume(0f)`; playing Icecast MP3/AAC and HLS audio; configuring a *fixed* HLS `liveTargetOffsetMs`. Audio focus is largely an inter-app concern; in-app you control volumes yourself.
- **HARD but feasible:** A local ring-buffer recording of the radio stream (custom `AudioSink` capturing PCM, writing to an in-memory/file ring buffer, then reading back after a user-set delay). This is the only robust way to delay Icecast reliably.
- **NOT feasible without a server or local buffering:** seeking back 50–120s on a typical live stream. Icecast is a continuous push with no seekable window; HLS only offers whatever DVR/sliding window the server retains (usually a few segments). `AudioProcessor` and `DefaultAudioSink` are for real-time effects, not 50–120s time-shifting; Media3 Transformer is offline file processing, not real-time.

**Realistic phased plan**
1. **Phase 1:** Dual players + TV mute + manual/fixed offset. If the **TV** stream is HLS with a DVR window, time-shift the *TV* via `liveTargetOffsetMs` (or seek within its seekable range) to match the radio — this works where the TV manifest retains 50–120s. Low risk, ships fast.
2. **Phase 2:** Local ring-buffer radio delay (custom AudioSink) for Icecast, enabling robust arbitrary 50–120s sync independent of the source. Significant engineering and CPU/battery cost (two concurrent decoders), plus buffer lifecycle/disk management.

**Legal caveats (not legal advice):** Personal, private time-shifting is generally unproblematic, but any redistribution, public performance, or modification of the broadcast content can infringe rights. Add a user-facing disclaimer.

**Sources:** Android audio focus + live-streaming docs (developer.android.com/media/media3/exoplayer/live-streaming), `DefaultAudioSink` reference, ExoPlayer issues #6216/#4107, Icecast config docs + Xiph mailing list, hls.js DVR issues #2493/#4004, Dolby OptiView DVR docs.

---

## Prioritized implementation order

1. **Favorites** — highest value, lowest risk/legal exposure; pure client work with Room and a stable-ID algorithm.
2. **Most-Played (local only)** — trivial additive Room table + one Settings control; reinforces the privacy stance.
3. **EPG Phase 1 (Now/Next)** — high value, moderate parsing/storage work; Phase 2 grid only after Now/Next is solid.
4. **Channel icons (tvg-logo + generic fallback)** — runtime fetch of provider logos, no bundling of brand art; ship alongside any polish pass.
5. **Radio overlay Phase 1** — dual players + mute + fixed offset; defer the ring-buffer delay (Phase 2) until everything above is stable, given its complexity and battery cost.

**Cross-cutting legal reminder (not legal advice):** the only features with meaningful legal exposure are brand-logos (trademark) and radio time-shift (broadcast rights). Both are mitigated by using provider-supplied assets and keeping everything personal/on-device.
