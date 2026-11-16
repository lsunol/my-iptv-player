# Changelog

## [Unreleased]

- Ratings are now visible: poster cells show the rating as `7/10` in the
  top-left corner, white and bold with a text shadow over a soft top gradient so
  it reads on bright artwork. It deliberately has no star (the favourite star is
  the only star in the app); titles without a rating show nothing. Rows, the
  movie detail and the series header use the same `7/10` form. List rows show the facts the
  provider sent, in order: rating, year, genres (unified, in Spanish), episode
  runtime for series, and the date a movie was added while sorting by
  "Añadidas recientemente". A panel with less data just gets a shorter line.
- Choosing the active sort again reverses it. The tick in the sort menu is now
  an arrow (up = smallest/oldest/A first, down = best/newest/Z first), the
  count line shows the same arrow (`Mejor valoradas ↓`), and TalkBack announces
  the direction in words. Titles without a value stay last in both directions,
  and the direction is saved per list and tab.
- Movies and Series can be sorted (provider order, "Añadidas recientemente",
  "Estreno más reciente", "Mejor valoradas", A-Z) and filtered by language
  (derived from category prefixes such as `ESPAÑA - …` / `|EN| …`), genre
  (English and Spanish genres unified, shown in Spanish) and "Nota 7 o más".
  Every option is offered only when the loaded catalogue actually carries the
  data, so a panel without dates, ratings or genres gets fewer options instead
  of sorts that do nothing; a refresh that removes the data resets the choice
  with a notice. Choices persist, work in grid and list, and combine with the
  category chips, search and favorites.
- A long-press on a movie opens its detail (year, duration, genre, director,
  plot, cast) loaded lazily from `get_vod_info`; tap still plays directly.
  Series headers show the broadcast years computed from episode dates
  (`2008 – 2013`), rating and runtime, and `releaseDate` from `get_series` when
  the panel sends it.
- Metadata parsing tolerates numbers sent as strings, `""`/`"0"`/`N/A`,
  second or millisecond epochs, `YYYY`, `YYYY-MM-DD`, `DD/MM/YYYY`; bad values
  become "unknown" and sort last. Old catalogue caches still load.
- Playback errors where the provider answers a web page instead of video
  (seen on a real panel's VOD server: HTML "Server Error" with a 2xx status)
  now say so in Spanish instead of showing a raw Media3 error code.
- Catalogue artwork is normalized before it reaches the image loader: a
  PHP-serialized path leaked into the JSON (`s:308:/images/x.jpeg`), a
  root-relative path and a protocol-relative URL are resolved against the
  panel's server, and junk becomes the generated placeholder instead of a
  request that cannot succeed. Applies to movie and series posters and to the
  series detail cover.
- Posters whose image host refuses connections are explained instead of
  leaving a silent grid of initials: "N de M portadas no se pueden mostrar: el
  servidor de imágenes que indica el proveedor rechaza la conexión." After the
  first refused connection the host is skipped for 5 minutes (no socket per
  grid cell), then probed again. Some panels point series covers at a private
  Plex `:32400` server that is firewalled from the internet; the app cannot
  show those, and now it says so.
- Playback can be muted from the player, in the top bar and in fullscreen. It
  silences only the app's own output: the phone's volume and any other app
  playing audio are left alone, so a channel can be watched while listening to
  something else. The state is remembered across channels and restarts, and a
  "Sin sonido" badge keeps the silence explicit instead of looking like a
  broken stream.
- Episode titles are reduced to the episode name. Panels that send the whole
  line (`"<series> - S01E01 - <title>"`, the same string the M3U's `tvg-name`
  carries) no longer repeat the series name in every row and in the player's top
  bar; a title that legitimately contains a dash or a colon is left alone.
- The episode row's chip shows the episode number alone (`01`) instead of
  `S01E05`, which printed the same fact three times: the season pill above the
  list, the chip and the row's subtitle.
- Series detail now falls back to the `cover` returned by the lazy
  `get_series_info` call when the catalogued card carries none; the fetched
  poster used to be discarded.

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
