# Changelog

## [Unreleased]

- Large playlists no longer need to fit in memory to be loaded. The download is
  streamed straight to a file and parsed line by line from there, so peak memory
  now depends on the number of channels and not on the size of the document that
  describes them (a 35 MB list used to cost ~70-140 MB of transient heap on top
  of the channels). The 128 MiB ceiling is kept, now as a disk guard against a
  server that never stops sending. Startup from the cache reads the file the same
  way instead of loading it whole.
- Playlists are decoded with a stricter charset policy: byte order marks
  (UTF-8 and UTF-16) are honoured, and a list whose accents only appear after an
  all-ASCII beginning is re-read as ISO-8859-1 instead of showing replacement
  characters in channel and group names. A server that declares a charset the
  document does not actually use now fails with a readable error rather than
  quietly corrupting names.
- Fixed a UTF-8 byte order mark hiding an HTML error page, which was reported as
  an empty playlist instead of "the URL returned a web page".

## [0.3.0] - 2026-11-02

- Added multiple named lists (M3U URL or Xtream), with a picker, per-list cache
  and one active list at a time. Existing single-list settings and cache are
  migrated on first run; deleting a list does not touch favorites.
- Credentials are masked while typing or editing an Xtream URL, so usernames and
  passwords are not shown in full on screen.
- Fixed the display cutout leaking as a left inset after returning from
  fullscreen landscape on Android 15+.
- Fixed the phone staying in landscape after leaving fullscreen. The orientation
  in use before fullscreen is now remembered and restored explicitly, because
  the forced landscape request also rewrites the system user rotation on some
  OEM builds, so releasing the display to the system left it stuck sideways.
- Package renamed from `app.opentv` to `dev.lluissunol.iptv`. Android treats this
  as a different app: installed lists and favorites do not carry over, and the
  previous package stays installed until it is removed.
- Raised the downloaded-playlist ceiling from 32 MiB to 128 MiB. Provider Xtream
  dumps reach ~35 MB and were being rejected by a guard that dated from the first
  commit. The download also hands its buffer over without the extra copy a
  `ByteArrayOutputStream` forces, and a list that cannot fit in the heap now
  reports that it ran out of memory instead of killing the process.

## [0.2.0] - 2026-11-01

- Added per-category favorites (Live / Movies / Series / All) with a star on
  each channel, a Favorites screen, stable keys (`tvg-id` first), and on-device
  persistence.
- Channel logos now come exclusively from provider `tvg-logo` URLs; missing or
  broken logos fall back to a generated initial. No brand logos are bundled.
- Added a generated brand mark (play + broadcast arcs, mint/violet on navy),
  used for the adaptive and round launcher icons, the in-app header and the URL
  hero.
- Modern dark Material 3 UI with compact rows, pill tabs and contextual group
  filters.
- Cached playlist startup, refresh-with-fallback, fullscreen playback and
  rotation-safe player lifecycle.
- Added the `brand-designer` agent, the `logo-generation` skill and
  `tools/generate-logo.mjs`.
- Added feature research and roadmap documents.

## [0.1.0] - 2026-10-31

- Initial public release: one playlist URL, M3U/Xtream parsing, Live/Movies/
  Series tabs, search, group filters, Media3 playback, cache, fullscreen and
  Android debug APK.
