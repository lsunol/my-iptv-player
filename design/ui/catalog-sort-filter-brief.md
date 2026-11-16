# Brief: catalogue sorting, filters and detail metadata (Movies / Series)

Approved by the owner on 2026-11-15. Scope expansion of v1, recorded here.
Baseline commit: `bf8e662`. Language of the UI: Spanish (`values/strings.xml`).

## Golden rule: any list, not just ours

Open IPTV must work with **any** Xtream panel. Everything measured below comes
from one real account; other panels behave differently (fields filled that are
empty here, numbers sent as strings, dates in other formats, fields missing
altogether). Every feature must:

- **Detect** whether the data exists in the loaded catalogue and only offer a
  sort/filter when it is meaningful for that catalogue (enough items carry the
  field, and the values are not all identical). Otherwise hide the option (or
  disable it with an explanation) — never offer a sort that does nothing.
- **Tolerate** every shape: JSON number or string, `""`, `"0"`, `null`, `"N/A"`,
  `"0000-00-00"`, arrays where a string is expected, seconds vs milliseconds
  epoch, `YYYY`, `YYYY-MM-DD`, `DD/MM/YYYY`, `YYYY-MM-DD HH:MM:SS`. Bad values
  degrade to "unknown", never to a crash or a dropped item.
- Never fail a catalogue load because a metadata field is malformed.
- Stay fast with tens of thousands of titles: derive sort keys once when the
  catalogue is built (Dispatchers.Default), never per row or per recomposition.
- Keep the disk catalogue cache (`XtreamCache`, kotlinx JSON with
  `ignoreUnknownKeys`) backward compatible: new fields get defaults so an old
  cache still decodes (and a refresh fills them).

## Measured on the real account (2026-11-15, no credentials here)

Movies, `get_vod_streams` (13,609 items):
- `added` (epoch seconds as string) 100%, but only 13 distinct days; 12,699
  items were added on 2023-11-29 and 2023-12-05 (bulk import). ~1,000 items
  from 2024-2025 have distinct dates. The provider order (`num`) already lists
  the newest additions first.
- `rating` 97% (integer 0-10 as string, coarse), `rating_5based`.
- `category_id` 100% (14 categories like `ESPAÑA - Acción`, `ENGLISH - Drama`).
- `tmdb_id` 99%, `container_extension` 100%. **No genre, no release date, no
  duration in the list.** Year in the title only for 399 items.
- `get_vod_info&vod_id=N` (per movie) has `info.releasedate` (`2016-10-19`),
  `duration_secs`/`duration`, `genre`, `cast`, `director`, `plot`, `rating`
  (sample of 10: 100% / 90%).

Series, `get_series` (2,351 items):
- `releaseDate` **0%** here (other panels fill it, also as `release_date` or
  `year`): must be used when present.
- `last_modified` 100% but only 5 distinct days (96% in Dec 2023): useless as
  "newest", do not offer it as a release date.
- `genre` 100%, 52 values mixing English and Spanish (`Comedy`/`Comedia`,
  `Crime`/`Crimen`, `Documentary`/`Documental`, `Mystery`/`Misterio`,
  `Animation`/`Animación`, `Family`/`Familia`...), separated by `/` with
  irregular spaces (`"Crime /  Drama /  Thriller"`).
- `rating` 95%, `cast` 98%, `director` 1%, `plot` 99%, `episode_run_time` 96%
  (minutes as string), `youtube_trailer` 3%, `category_id` 100% (15 categories,
  same `LANG - Genre` naming), `tmdb_id` 99%.
- `get_series_info` (already fetched lazily for the drill-down): every episode
  has `info.releaseDate` (`2023-10-04`) and `added`; `info` repeats the list
  fields (no release date).

Category names: `ESPAÑA - Acción`, `ENGLISH - Thriller`... A language/region
filter can be derived from a shared prefix, but other panels name categories
freely (`Acción`, `|ES| Drama`, `VOD 4K`, `Netflix`): only offer it when the
prefix pattern is clearly present (several prefixes, each shared by several
categories); otherwise hide it.

## Approved features

1. **Sort** (Movies and Series tabs, grid and list layouts):
   - Default: provider order (as today).
   - Movies: "Añadidas recientemente" (`added`), "Mejor valoradas"
     (`rating`), "A-Z".
   - Series: "Mejor valoradas", "A-Z", and "Estreno más reciente" **only when
     the list carries a release date/year** (`releaseDate`/`release_date`/`year`
     in `get_series`; also movies if a panel sends `releasedate`/`year` in
     `get_vod_streams`).
   - Ties and unknown values: deterministic (unknowns last, then provider
     order).
2. **Filters**:
   - Language/region derived from the category prefix (when detectable).
   - Genre on Series, with English/Spanish genres unified and shown in Spanish
     (unknown genres pass through, deduplicated case/accent-insensitively).
     Movies too if a panel sends `genre` in the list.
   - "Nota ≥ 7" (when ratings exist).
   - Must combine with the existing provider-category chips, search,
     "search all", favorites and the list/grid toggle.
3. **Detail metadata, loaded lazily on open**:
   - Movies: release year, duration, genre, director (and plot/cast if room)
     from `get_vod_info`. Today tapping a movie plays it directly; the designer
     decides how the detail is reached without breaking tap-to-play.
   - Series detail header: broadcast years from the episodes' release dates
     (`2015 – 2021`), plus genre/rating/runtime; `releaseDate` from
     `get_series` when the panel sends it.

## Constraints (from AGENTS.md)

- No third-party metadata services (TMDB API etc.): only the playlist server.
- No new heavy dependencies, no Room, no DI. DataStore for small preferences.
- No credentials or provider hosts in code, tests, logs, screenshots or docs.
  The real account's credentials are in `xtream-credentials.json` (git-ignored):
  read it only from scripts, **never print it**.
- Every visible string in `strings.xml`.
- Test fixtures are synthetic.
