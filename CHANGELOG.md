# Changelog

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
