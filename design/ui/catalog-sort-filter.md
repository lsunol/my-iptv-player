# Catalogue sorting, filters and detail metadata (Movies / Series)

Spec: `design/ui/catalog-sort-filter.md` · Screens: `PlaylistScreen` (Movies and
Series tabs, grid and list layouts), new `MovieDetailScreen`, `SeriesDetailScreen`
header · Author: ui-designer · Baseline: `bf8e662` · Scope: the approved brief
`design/ui/catalog-sort-filter-brief.md`. Status: ready to implement.

Golden rule applied throughout: **every control is offered because the loaded
catalogue proves it is useful, never because one measured panel has the data.**
All examples below use invented category and title names.

No screenshot was taken: no device was attached (`adb devices` was empty), and a
capture of the real account would show the provider's category naming anyway.

---

## 1. The problem

On an Xtream list the Movies and Series tabs show a catalogue of thousands of
titles (13,609 movies and 2,351 series on the measured account) in one fixed
order: the provider's. The only way to narrow it is a single provider-category
chip plus a name search. A user cannot ask for "the best-rated Spanish comedies",
"what was added lately" or "A-Z". When a mixed-language catalogue gives
`ESPAÑA - Acción` and `ENGLISH - Action` as separate chips, the user has to pick
one category at a time. Tapping a movie starts playback right away, so a user
cannot find out the year, length, genre or plot before committing to a stream.
The series screen shows a plot and an episode count, but not when the show ran
or what kind of show it is. The data is mostly there (`added`, `rating`, `genre`,
`get_vod_info`, the episodes' `releaseDate`), but the UI does not use it. On other
panels the same fields are missing, empty or shaped differently, so whatever we
add has to detect that rather than assume it.

## 2. Options considered

### 2.1 Where sort and filters live

**Option A: a permanent filter-chip row** (`[↕ Recientes ▾] [Idioma ▾] [Género ▾] [Nota ≥ 7]`)
under the category chips, with each chip opening a dropdown.
- Buys: the controls are always visible and the state is readable in place, with
  no hidden surface.
- Costs: about 48 dp of permanent height on both catalogue tabs, which leaves two
  stacked chip rows above the content. A genre dropdown with 25 to 40 entries
  makes a long, awkward menu. In landscape the content would start below the
  middle of the screen.

**Option B: a results toolbar plus a filter sheet (recommended).** The count row
that already exists ("13.609 títulos") becomes the toolbar for the catalogue tabs.
It holds three 48 dp icon buttons: **Ordenar** (opens a dropdown menu), **Filtros**
(opens a modal bottom sheet, with a badge showing the active count) and the
existing **list/grid toggle**, which moves here from the category row. When at
least one sheet filter is active, a row of removable chips appears under the
category chips.
- Buys: no extra height while nothing is filtered, which is the default and the
  most common case. The category chips get the full width back, because the
  layout toggle moves out of their row. The three "how results are shown"
  controls sit together. Genres get a FlowRow inside a scrollable sheet, which
  scales to 40 values. Active filters are always visible and can be removed with
  one tap.
- Costs: the count row grows from about 30 dp to 48 dp, and the active-filter row
  adds about 48 dp only while filters are on. Filters are one tap further away
  than in A. The layout toggle moves, which breaks muscle memory slightly.

**Option C: put sort and filter controls inside the category `LazyRow`.**
- Buys: no new rows.
- Costs: with 14 or 15 categories, the controls scroll off-screen the moment the
  user browses categories. That mixes "a category" with "a way of looking at
  categories". Rejected.

**Option D: one "Ordenar y filtrar" sheet holding both sort and filters.**
- Buys: a single entry point.
- Costs: changing the order, the most frequent action, would take a sheet, a
  scroll and a close. A four-item choice belongs in a menu (M3 guidance).
  Rejected.

**Why B wins.** It is the smallest change that keeps both the default screen and
the content height as they are today, and it scales to large genre lists. It
also meets the explainability rule, because a filtered list always shows its
active filters and "X de Y títulos". A only wins on discoverability, and it pays
for that with permanent height on every visit.

### 2.2 How a movie detail is reached (tap-to-play must survive)

| Option | Buys | Costs | Verdict |
|---|---|---|---|
| Tap opens the detail, which has a Play button | discoverable, the industry norm | **breaks tap-to-play** (2 taps to watch) | rejected by the brief |
| Info "ⓘ" button on each cell | visible | a second button on a ~105 dp poster that already carries the star; easy to mistap; clutters 13k cells | rejected |
| Info button in the player | no list change | the detail comes only *after* you commit to a stream, which is the opposite of the need | rejected |
| **Long-press opens the detail**, with a TalkBack custom action and a one-time hint | tap-to-play intact, no visual clutter, standard Android secondary gesture | long-press is not discoverable on its own, so the hint and the a11y action are required | **chosen** |

## 3. The chosen design

### 3.1 Screen anatomy of a catalogue tab (Movies or Series, `isCatalogTab == true`)

Top to bottom. Rows marked **new** or **changed**; everything else is unchanged.

| # | Row | Status |
|---|---|---|
| 1 | Top app bar | unchanged |
| 2 | Tab pills (Directos / Películas / Series / Todo) | unchanged |
| 3 | Search field with clear and "Buscar en todo" | unchanged |
| 4 | Category chips (`CatalogPills`) | **changed**: the layout toggle leaves this row (the chips get the full width); the label is prefix-stripped while a language is selected (§3.5) |
| 5 | Active-filter chips | **new**, visible only when ≥ 1 sheet filter is active |
| 6 | Results toolbar = `ListHeader` | **changed** on catalogue tabs only: count text + warnings + [Ordenar] [Filtros] [layout] |
| 7 | Error banner, notice line | unchanged slot; new notice texts (§4.3) |
| 8 | Grid / list content | unchanged cells; long-press added on movies (§3.7) |

On Directos, Todo, with "Buscar en todo" on, on plain-M3U lists and on Xtream lists
with no catalogue, rows 5 and 6 render exactly as today: a count plus warnings and
no buttons. The layout toggle is still shown only when `isCatalogTab`, as today.

#### 3.1.1 Results toolbar (row 6)

`Row`, `fillMaxWidth`, padding `start = 20.dp, end = 8.dp`, `heightIn(min = 48.dp)`,
`CenterVertically`.

| Element | Spec |
|---|---|
| Count text | `labelMedium`, `onSurfaceVariant` (`#94A3B8`), `weight(1f)`, `maxLines = 2`, ellipsis. Content in §3.1.2 |
| Warnings | existing "N avisos" (`labelSmall`, `error` `#F87171`, 14 dp warning icon), unchanged, placed before the buttons |
| Ordenar | `IconButton` 48 dp, `Icons.AutoMirrored.Filled.Sort`. Tint `primary` `#5EEAD4` when the sort is not provider order, `onSurfaceVariant` otherwise (same "active = mint" language as the favourites star and "Buscar en todo"). Anchors the dropdown (§3.2). Hidden when the tab has < 2 titles |
| Filtros | `IconButton` 48 dp, `Icons.Default.FilterList`, inside a `BadgedBox`. Badge: M3 `Badge`, container `secondary` `#A78BFA`, content `onSecondary`, number of active sheet filters (1 to 3); no badge at 0. Tint `primary` when ≥ 1 is active. **Hidden** when no filter is available for this tab (§3.4) |
| Layout | existing toggle (`ViewList` / `GridView`, same content descriptions), moved here unchanged |

#### 3.1.2 Count text

| Situation | Text | Key |
|---|---|---|
| No sheet filter, provider order | `13.609 títulos` (as today) | `items_count` (existing) |
| No sheet filter, other sort | `13.609 títulos · Mejor valoradas` | `items_count_sorted` |
| ≥ 1 sheet filter | `1.234 de 13.609 títulos` | `items_count_of` |
| ≥ 1 sheet filter + other sort | `1.234 de 13.609 títulos · A-Z` | `items_count_of` wrapped in `items_count_sorted` |

The total (`de N`) is the tab's catalogue size, the same number as the tab pill.
A category chip or a search query alone keeps today's single number. Only the
sheet filters, which can be invisible after a restart (language persists, §5.3),
get the "de N" so the user can see that the list is narrowed.

### 3.2 Sort menu

M3 `DropdownMenu` anchored to the Ordenar button. Container `surfaceContainerHigh`
`#16202F`, default M3 menu shape (4 dp, M3 1.4 default), `widthIn(min = 220.dp, max = 300.dp)`.

```
 Ordenar por                       ← labelSmall, onSurfaceVariant, 12dp padding, not focusable as an item
 ✓ Orden del proveedor             ← DropdownMenuItem, 48dp min height
   Añadidas recientemente
   Estreno más reciente
   Mejor valoradas
   A-Z
 ──────────────────────────        ← HorizontalDivider, only when an option is hidden
 Solo se muestran los órdenes      ← bodySmall, onSurfaceVariant, padding 12dp, max 3 lines
 para los que esta lista tiene datos.
```

- Leading icon: `Icons.Default.Check` (20 dp, `primary`) on the selected item. On the
  others, a 20 dp spacer so the labels stay aligned. Item text `labelLarge`, `onSurface`.
- Selection semantics: each item is `Modifier.selectable(selected, role = Role.RadioButton)`.
- Tapping an item applies the sort, closes the menu, scrolls the grid or list to
  item 0 (§5.1) and persists the choice (§5.3).
- Fixed item order as above. Each item shows only when it is available (§3.3):

| Option | Key | Movies | Series |
|---|---|---|---|
| Orden del proveedor | `sort_provider` | always (default) | always (default) |
| Añadidas recientemente | `sort_recent` | if `added` qualifies | **never** (`last_modified` is not a date the user cares about; not approved) |
| Estreno más reciente | `sort_release` | if `releasedate` / `release_date` / `releaseDate` / `year` qualifies in `get_vod_streams` | if `releaseDate` / `release_date` / `year` qualifies in `get_series` |
| Mejor valoradas | `sort_rating` | if rating qualifies | if rating qualifies |
| A-Z | `sort_az` | always (≥ 2 titles) | always (≥ 2 titles) |

The footnote shows only when at least one of Recientes, Estreno or Valoradas was
hidden for this tab, so the user knows the absence is a property of the list and
not a bug.

**Sort semantics** (deterministic; ties and unknowns are always settled by provider
order, i.e. the original catalogue index):

| Sort | Key | Unknowns |
|---|---|---|
| Proveedor | catalogue index | — |
| Añadidas recientemente | `added` epoch, descending | last |
| Estreno más reciente | release `yyyymmdd` as an int, descending; a year-only value becomes `yyyy0000` | last |
| Mejor valoradas | normalised rating 0–10, descending | last (0 = unknown) |
| A-Z | collation key of the **displayed** title (`cleanChannelName`, then `SearchNormalizer`-style accent/case folding, then leading non-alphanumerics stripped); digits sort before letters | — (titles are never unknown; "Sin nombre" sorts as text) |

Search results, category chips and filters all apply on top of the chosen order.
Searching never resets the sort.

### 3.3 Value parsing and capability thresholds

**Parsing (data layer, once per catalogue build, on `Dispatchers.Default`).** Every
field accepts a JSON number, a string, `null`, `""`, `"0"`, `"N/A"`, `"null"`, or an
array (first element). A bad value becomes **unknown**. It never throws and never
drops the item.

| Field | Accepted | Unknown when |
|---|---|---|
| `added` | epoch seconds; epoch **ms** if > 10¹¹ (divide by 1000); `YYYY-MM-DD`, `YYYY-MM-DD HH:MM:SS`, `DD/MM/YYYY` | ≤ 0, unparseable, or more than 2 days in the future |
| release (`releasedate`, `release_date`, `releaseDate`, `year`) | `YYYY`, `YYYY-MM-DD`, `YYYY-MM-DD HH:MM:SS`, `DD/MM/YYYY`, number | `0000-00-00`, a year < 1900, or a year > current year + 1 |
| rating | `rating` (0–10). If `rating_5based` is in (0, 5], use `rating_5based × 2` (finer grained). A `rating` > 10 and ≤ 100 is divided by 10 | ≤ 0 or > 100 |
| `genre` | string split on `/` `,` `|` `;` (§3.6); an array of strings | empty after cleaning |
| duration (`duration_secs`, `duration`, `episode_run_time`) | seconds (`duration_secs`); `HH:MM:SS` / `MM:SS` (`duration`); minutes (`episode_run_time`; a value > 300 is taken as seconds) | ≤ 0 |

**Capability detection.** These are pure functions of one tab's catalogue,
computed once when the catalogue is built or decoded from cache, never per
recomposition. Thresholds are named constants:

| Constant | Value | Why |
|---|---|---|
| `MIN_COVERAGE` | **0.50** of the tab's titles carry a known value | below half, "unknowns last" turns the bottom of the list back into provider order, so the sort mostly does nothing |
| `MIN_DISTINCT` | **2** distinct known values | one value means the sort or filter does nothing |
| `MAX_DOMINANT_SHARE` (sorts only) | the most frequent known value covers **< 0.95** of the titles that carry the field | a panel that stamps every movie with the same `added` except a handful would offer a sort that moves 3 titles |

| Control | Offered when |
|---|---|
| Sort: Añadidas recientemente | `added`: coverage ≥ 0.50, distinct ≥ 2, dominant < 0.95. *Measured account: 100% / 13 days / the largest single day holds well under 95%, so it is offered. The bulk-import ties fall back to provider order, which on that panel is already newest-first, so the result is sane.* |
| Sort: Estreno más reciente | release: coverage ≥ 0.50, distinct years ≥ 2, dominant < 0.95. *Measured: series 0%, so hidden; movies have none in the list, so hidden.* |
| Sort: Mejor valoradas | rating: coverage ≥ 0.50, distinct ≥ 2, dominant < 0.95 |
| Sort: A-Z | the tab has ≥ 2 titles |
| Filter: Nota ≥ 7 | rating coverage ≥ 0.50 **and** ≥ 1 rated title is ≥ 7 **and** ≥ 1 rated title is < 7 |
| Filter: Género | ≥ 0.50 of the titles have ≥ 1 genre token and there are ≥ 2 canonical genres with ≥ 1 title. *Measured: Series yes; Movies no (no `genre` in `get_vod_streams`).* |
| Filter: Idioma | rules in §3.5 |

**Unavailable options are hidden, not disabled.** A greyed control with no reason
next to it is an anti-pattern (same reasoning as `no-catalogue-notice.md` P2). The
reason is given once, as the footnote in the menu or the sheet. The Filtros button
itself is hidden when none of the three filters is available for the tab.

### 3.4 Filter sheet

M3 `ModalBottomSheet`, `containerColor = surface` `#0D1420`, default drag handle and
top corner radius (28 dp), default `contentWindowInsets` (it handles the navigation
bar edge-to-edge). Opened by the Filtros button. Filters apply **live** on every
change (the existing `scheduleFilter`, not debounced), so the result count in the
footer is always true.

```
 ─────  (drag handle)
 Filtrar películas                                   ← titleLarge, 24dp side padding
 ┌ scrollable column (verticalScroll) ───────────────┐
 │ Idioma                                  ← labelLarge, onSurfaceVariant, top 16dp
 │ [Todos] [Español] [Inglés] [Latino] [Otros]   ← FlowRow, FilterChip, 8dp gaps
 │ Género                                            │
 │ [Todos] [Acción] [Animación] [Bélica] [Comedia] … │
 │ (Se muestran los 40 géneros más frecuentes.)      ← only when capped
 │ Valoración                                        │
 │ Solo nota 7 o más                       (Switch)  ← toggleable Row, 56dp min
 │ Se ocultan los títulos sin nota.                  ← bodySmall, onSurfaceVariant
 │ Solo se muestran los filtros para los que esta    ← bodySmall, only if a section is hidden
 │ lista tiene datos.                                │
 └───────────────────────────────────────────────────┘
 [ Quitar filtros ]               [  Ver 1.234 títulos  ]   ← sticky footer, 16dp padding
```

- **Title**: `filters_title_movies` / `filters_title_series`.
- **Sections**: present only when available (§3.3). Fixed order: Idioma, Género,
  Valoración. Section header `labelLarge`, 16 dp above and 8 dp below.
- **Chips**: M3 `FilterChip` (32 dp visual, 48 dp touch target from M3's
  `minimumInteractiveComponentSize`). Selected: container `secondary` at 18%
  alpha, label and check icon `secondary` `#A78BFA`, border `secondary` at 50%, which
  is the same selection language as `GroupPill`. Unselected: transparent, label
  `onSurfaceVariant`, border `outline` at 25%. Label `labelLarge`, `maxLines = 1`,
  ellipsis, `widthIn(max = 220.dp)`.
- **Single select** for Idioma and Género. "Todos" is selected when the filter is
  off, and tapping the selected chip goes back to "Todos". (Multi-select genre is
  out of scope; see §9.)
- **Chip order**. Idioma: by title count descending, with "Otros" always last.
  Género: alphabetical by Spanish label (`Collator` for `es`), which is predictable
  to scan. If there are more than 40 genres, keep the 40 with the most titles
  (still alphabetical) and show `filter_genre_capped`.
- **Valoración**: a `Row` with `Modifier.toggleable(role = Role.Switch)` holding the
  label `bodyLarge` + the `Switch` (thumb/track checked `primary`). Supporting text
  below.
- **Footer** (outside the scroll, always visible): `TextButton` "Quitar filtros",
  enabled only when ≥ 1 sheet filter is active. It clears language, genre and
  rating, not the category chip. Then a filled `Button` "Ver N títulos" (plural),
  `primary` container, `onPrimary` text, 12 dp corner radius (as `ErrorCard`),
  `heightIn(min = 48.dp)`. It only closes the sheet, because the filters are
  already applied. With 0 results it reads "Ver 0 títulos" and still closes, and
  the empty state then offers the way out (§4.2).
- No counts on the chips in v1 (see §7, performance).

### 3.5 Language / region filter (derived from category prefixes)

**Step 1: extract a prefix from each category name** (trimmed). Patterns, first
match wins:

| Pattern | Example → prefix |
|---|---|
| Bracketed at the start: `\|XX\|`, `[XX]`, `(XX)` | `\|ES\| Drama` → `ES` |
| Token followed by a **spaced** separator: ` - `, ` – `, ` — `, ` \| ` | `ESPAÑA - Acción` → `ESPAÑA` |
| Token of ≤ 3 letters followed by `: ` | `EN: Comedy` → `EN` |
| Token followed by `\| ` with no leading pipe | `ES\| Drama` → `ES` |

The prefix must be 2 to 16 characters, contain **no digits**, and is normalised
(uppercase, accent-folded, inner spaces collapsed).

**Step 2: recognise it.** Only prefixes found in the built-in **language/region
token table** become options. This table is a locale lookup, not content and not
a provider list. Every other prefix, and every category without a prefix, falls
into **"Otros"**.

| Option (label key) | Recognised normalised tokens |
|---|---|
| Español (`lang_es`) | ES, ESP, SPA, SPAIN, SPANISH, ESPANA, ESPANOL, CASTELLANO, CAST |
| Latino (`lang_latino`) | LAT, LATAM, LATINO, LATINOAMERICA, MX, MEX, MEXICO |
| Inglés (`lang_en`) | EN, ENG, ENGLISH, INGLES, UK, GB, US, USA |
| Francés (`lang_fr`) | FR, FRA, FRE, FRANCE, FRENCH, FRANCAIS, FRANCES |
| Alemán (`lang_de`) | DE, GER, DEU, GERMAN, GERMANY, DEUTSCH, ALEMAN |
| Italiano (`lang_it`) | IT, ITA, ITALY, ITALIAN, ITALIANO, ITALIA |
| Portugués (`lang_pt`) | PT, POR, PORTUGAL, PORTUGUESE, PORTUGUES |
| Portugués (Brasil) (`lang_pt_br`) | BR, BRA, BRAZIL, BRASIL |
| Catalán (`lang_ca`) | CAT, CATALA, CATALAN |
| Gallego (`lang_gl`) | GALEGO, GALLEGO |
| Euskera (`lang_eu`) | EUSKERA, EUSKARA |
| Árabe (`lang_ar`) | AR, ARA, ARAB, ARABIC, ARABE |
| Turco (`lang_tr`) | TR, TUR, TURK, TURKEY, TURKISH, TURCO |
| Neerlandés (`lang_nl`) | NL, NLD, DUTCH, NEDERLANDS |
| Polaco (`lang_pl`) | PL, POL, POLISH, POLSKA |
| Ruso (`lang_ru`) | RU, RUS, RUSSIAN |
| Rumano (`lang_ro`) | RO, ROM, ROMANIA, ROMANIAN |
| Hindi (`lang_hi`) | HINDI, INDIA |
| V.O. subtitulada (`lang_vose`) | VOSE, VOS, VO, SUBTITULADO, SUBTITULADA |

Deliberately excluded because they are ambiguous: `CA` (Catalan or Canada), `EU`
(Basque or Europe), `IN`, `HI` as two letters. Excluded as IPTV noise (never
recognised): TV, SD, HD, FHD, UHD, 4K, 3D, HDR, HEVC, VOD, PPV, VIP, NEW, TOP, ALL,
MULTI, PLUS, KIDS, ADULT, XXX.

**Step 3: decide whether to offer the filter.** Counted over the tab's categories
that have ≥ 1 title:

1. ≥ 2 distinct recognised options have ≥ 1 title, **and**
2. the titles in recognised categories make up ≥ 0.50 of the tab's titles, **and**
3. at least one recognised option spans ≥ 2 categories (added during
   implementation, accepted by QA 2026-11-16): when every language has a
   single category, the category chips already are the language filter and a
   second control would be redundant.

Otherwise the filter is hidden. "Otros" is an option only if it holds ≥ 1 title.

**Counter-examples (must be unit tests):**

| Category names in the tab | Result | Why |
|---|---|---|
| `ESPAÑA - Acción`, `ESPAÑA - Drama`, `ENGLISH - Action`, `ENGLISH - Drama` | offered: Español, Inglés | 2 recognised, 100% coverage |
| `\|ES\| Drama`, `\|EN\| Drama`, `VOD 4K` | hidden | each language has one category (rule 3); the chips already filter by language |
| `\|ES\| Drama`, `\|ES\| Acción`, `\|EN\| Drama`, `VOD 4K` | offered: Español, Inglés, Otros (if `VOD 4K` has titles) | bracketed prefixes; `VOD 4K` has no separator |
| `Acción`, `Comedia`, `Drama` | hidden | no prefixes |
| `NETFLIX - Series`, `NETFLIX - Kids`, `HBO - Series`, `HBO - Docs` | hidden | the prefixes are shared but not recognised; the filter is not called "Idioma" for platforms |
| `Sci-Fi`, `Spider-Man Collection` | no prefix | the hyphen is not spaced |
| `4K - Movies`, `2023 - Estrenos` | no prefix | digits |
| `Star Wars: Saga`, `ES - Drama`, `EN - Drama` | Español, Inglés, Otros | `Star Wars` is longer than 3 letters before `:` → no prefix → Otros |
| `ES - Drama` (90% of titles), `Netflix` (10%) | hidden | only 1 recognised option |
| `ES - Drama` (30%), `EN - Drama` (15%), `Netflix` / `HBO` / `4K` (55%) | hidden | recognised coverage 45% < 50% |
| `UK - Drama`, `US - Drama`, `ES - Drama` | Inglés (UK + US merged), Español | one option can cover several prefixes |
| `ES/EN - Dual` plus `ES - x`, `EN - y` | Español, Inglés, Otros | `ES/EN` is not recognised → Otros |

**How it combines with the category chips.** While a language is selected:

- the category chips list only categories of that language ("Otros" → the
  unrecognised and unprefixed ones), keeping "Todas" first;
- labels are shown **without the prefix and separator** (`ESPAÑA - Acción` → `Acción`),
  unless two visible chips would end up with the same label (e.g. `UK - Drama` and
  `US - Drama` under Inglés). In that case all chips keep their full names;
- selecting a language that does not contain the selected category resets the
  category to "Todas" in the same state update, so language plus category can
  never produce an impossible empty list.

### 3.6 Genre unification (EN → ES)

**Tokenising.** Split the raw string on `/`, `,`, `|`, `;`. Trim each token and
collapse inner whitespace (`"Crime /  Drama /  Thriller"` → `Crime`, `Drama`,
`Thriller`). Normalise for lookup to lowercase, accent-folded, with `-` and `_`
replaced by a space. Look the whole token up in the table first. Only if it is not
found and it contains `&`, split on `&` and look up each part. Drop tokens that are
blank, `n/a`, `na`, `null`, `none`, `-`, `0`, digits only, or longer than 30
characters.

**Unknown tokens pass through.** They are deduplicated case- and
accent-insensitively. The label is the most frequent original spelling, with its
first letter uppercased (`anime` / `Anime` → `Anime`).

A title's genres are a **set** of canonical ids: `Action & Adventure` + `Acción`
counts once under Acción and once under Aventura.

| Canonical (label key) | Spanish label | Lookup keys (normalised) |
|---|---|---|
| `genre_action` | Acción | action, accion |
| `genre_adventure` | Aventura | adventure, aventura, aventuras |
| *(compound)* | Acción + Aventura | action & adventure, action and adventure, accion y aventura |
| `genre_animation` | Animación | animation, animated, animacion, dibujos animados |
| `genre_comedy` | Comedia | comedy, comedia |
| `genre_crime` | Crimen | crime, crimen, policiaco, policiaca |
| `genre_documentary` | Documental | documentary, documental, documentales, docu |
| `genre_drama` | Drama | drama |
| `genre_family` | Familia | family, familia, familiar |
| `genre_fantasy` | Fantasía | fantasy, fantasia |
| `genre_history` | Historia | history, historical, historia, historico |
| `genre_horror` | Terror | horror, terror |
| `genre_music` | Música | music, musica |
| `genre_musical` | Musical | musical |
| `genre_mystery` | Misterio | mystery, misterio |
| `genre_romance` | Romance | romance, romantic, romantica, romantico |
| `genre_scifi` | Ciencia ficción | science fiction, sci fi, scifi, ciencia ficcion |
| *(compound)* | Ciencia ficción + Fantasía | sci fi & fantasy, ciencia ficcion y fantasia |
| `genre_tv_movie` | Película de TV | tv movie, pelicula de tv, pelicula de television, telefilm |
| `genre_thriller` | Suspense | thriller, suspense, suspenso, intriga |
| `genre_war` | Bélica | war, belica, belico, guerra |
| `genre_politics` | Política | politics, politica |
| *(compound)* | Bélica + Política | war & politics, belica y politica, guerra y politica |
| `genre_western` | Western | western, oeste, del oeste |
| `genre_kids` | Infantil | kids, children, infantil, ninos |
| `genre_news` | Noticias | news, noticias |
| `genre_reality` | Reality | reality, reality show, reality tv |
| `genre_soap` | Telenovela | soap, telenovela, culebron |
| `genre_talk` | Entrevistas | talk, talk show, entrevistas |
| `genre_biography` | Biografía | biography, biografia |
| `genre_sport` | Deporte | sport, sports, deporte, deportes |
| `genre_game_show` | Concurso | game show, concurso |

"Thriller" is shown as **Suspense**, the established Spanish genre name used by
the es-ES catalogues the provider data comes from. Compound rows map to two
canonical ids. They have no label of their own.

### 3.7 Movie detail

**Entry point.** On a movie poster cell (grid) and a `MovieRow` (list), switch
`clickable` to `combinedClickable(onClick = play, onClickLabel = "Reproducir",
onLongClick = openMovieDetail, onLongClickLabel = "Ver ficha")`. The long-press
gives the default haptic. The star keeps its own click target. Series cells and
rows are unchanged (tap already opens the series screen) and get no long-press.
Flat channel rows (Todo, search-all, no catalogue) have no detail.

**Discoverability hint.** On the Movies tab, while no movie detail has ever been
opened, the notice slot shows `movie_detail_hint` with an **"Entendido"** button
(priority in §4.3). It disappears for good, persisted, after the first long-press
or a tap on "Entendido".

**Container: a full-screen route** (`MovieDetailScreen`), not a bottom sheet:
- it matches `SeriesDetailScreen`, so back, the top bar and error banners behave
  the same;
- plot plus cast at a large font scale need a full scroll, and a sheet with
  partial/expanded states and nested scroll handles that badly;
- `OpenTvApp` already layers player > series > favourites > playlist. The new
  branch goes **after `channel` and before `series`**, so Play from the detail opens
  the player and back returns to the detail, exactly as episodes do.

**Anatomy** (`Scaffold`, `contentWindowInsets = WindowInsets.systemBars`, content in a
`verticalScroll` `Column`, horizontal padding 20 dp, bottom padding 24 dp):

```
 ←  Título de la película (titleLarge, 1 line, ellipsis)          [top app bar, background]
 ┌────────┐  2016 · 1 h 52 min · ★ 7,4        ← labelMedium, onSurface, maxLines 2
 │ poster │  Acción, Aventura, Suspense        ← labelMedium, secondary #A78BFA, maxLines 2
 │120×180 │  Dirección: Nombre Apellido       ← bodySmall, onSurfaceVariant, maxLines 2
 │  12dp  │  [FHD]                             ← QualityBadge, if any
 └────────┘  ⟳ Cargando ficha…                 ← only while loading (16dp spinner + bodySmall)
 [ ▶  Reproducir                     ]  [☆]   ← Button weight(1f), 48dp min; FavoriteButton 48dp
 (error banner, if any)
 Plot text in full …                           ← bodyMedium, onSurface, 16dp above
 Reparto: A, B, C, D, E, F, G, H               ← bodySmall, onSurfaceVariant, 12dp above
```

- Poster: `PosterThumb` 120 × 180 dp, 12 dp radius, generated initial as fallback
  (existing component). Gap of 16 dp to the column.
- Facts line, joined with ` · `, each part omitted when unknown. Year comes from
  `info.releasedate` (any format in §3.3), then the list's release field. Duration
  from `duration_secs`, then `duration`, shown as `1 h 52 min` / `52 min`. Rating from
  `info.rating`, then the list rating, one decimal with a Spanish comma, `,0`
  dropped (`★ 7`, `★ 7,4`). If every part is unknown, the line is not rendered.
- Genres: canonical Spanish labels from §3.6 (pass-through for unknowns), joined
  with `, `.
- Cast: split on `,`, first 8 names, joined with `, `.
- **Play uses the catalogue `playbackUrl` only.** Nothing from `get_vod_info`
  (`movie_data`, `container_extension`, `stream_url`...) ever affects playback.
- Play is **enabled from the first frame**: it never waits for metadata.
- The top-app-bar title is the cleaned name. When the plot is missing, the title is
  not repeated in the body.
- Not shown: the trailer (`youtube_trailer` would open another app; out of scope),
  TMDB links, or any third-party data.

Lazy fetch: `get_vod_info&vod_id=<streamId>` runs on open. It is memoised per
session in an LRU of 16 movies (like the 8 series), never written to disk, and
cancelled when the user leaves. Errors go through the existing redaction and
`PlaylistException.userMessage()` path.

### 3.8 Series header metadata

Changes to `SeriesHeader` only. The poster stays at 92 × 138 dp, and the season pills
and episode list are unchanged.

```
 ┌───────┐  2015 – 2021 · ★ 8,1                 ← line A: labelMedium, onSurface, maxLines 1
 │poster │  72 episodios · 45 min por episodio  ← line B: labelMedium, onSurfaceVariant (existing count, extended)
 │92×138 │  Crimen, Drama, Suspense             ← line C: labelMedium, secondary #A78BFA, maxLines 1, ellipsis
 └───────┘  Plot … (maxLines 5 → 4)              ← existing bodySmall
            [☆]                                  ← existing star
```

- **Years.** After `get_series_info` loads: min and max year over all episodes'
  `info.releaseDate` (falling back to the episode `added`, §3.3 rules). Equal years
  give one year (`2019`); different years give a range (`2015 – 2021`, en dash with
  spaces). Before it loads, or if no episode has a date: the year from
  `get_series` `releaseDate`/`release_date`/`year` if present, as a single year. If
  neither exists, the years are omitted. The range never says "ongoing": we cannot
  know that.
- **Rating** from the card (`★ 8,1`), omitted if unknown. Line A is not rendered
  when it would be empty.
- **Line B**: the episode count as today, plus `· 45 min por episodio` from the
  card's `episode_run_time` when known. If the count is unknown (still loading),
  only the runtime is shown.
- **Line C**: genres from the card (`get_series` `genre`), unified as in §3.6.
- When there is no plot, the existing "title in body" fallback stays and sits above
  line A.
- Loading and error: lines A to C show what the card knows immediately. The
  existing "Cargando episodios…" row and the error banner already explain the
  episode fetch. No second spinner or error is added for metadata.

## 4. Every state

### 4.1 Catalogue tab

| State | What the user sees |
|---|---|
| Default, nothing selected | Exactly today's screen, except: the toolbar row is 48 dp and holds [Ordenar][Filtros][layout]; the layout toggle has left the chip row. Count `13.609 títulos` |
| Sorted | Ordenar icon in mint, count `… · Mejor valoradas`, list scrolled to the top |
| Filtered | Active-filter chip row visible, Filtros badge = count, count `1.234 de 13.609 títulos` |
| Empty after filtering | §4.2 |
| Loading (Actualizar in progress) | Controls stay usable on the current catalogue (the refresh never destroys it). When the new catalogue arrives, capabilities are recomputed; valid choices are kept and invalid ones are reset with a notice (§4.3, "degraded") |
| Catalogue absent / failed | No catalogue tab, so no toolbar buttons and no filter row. The existing `catalog_missing` / `catalog_unavailable` notices stand (unchanged) |
| **Degraded: catalogue cached by an older app version** | The cached catalogue lacks the new fields, so detection hides Recientes / Estreno / Género (rating still works from the old field). Notice `catalog_outdated` with an **Actualizar** button (ACTION_REFRESH). Detected by a `schemaVersion` field on `VodCatalog` that defaults to 0 when decoding an old cache |
| Degraded: a persisted choice is no longer available after a refresh or for this list | The choice is reset (to provider order / filter off) and the stored value is updated. A transient notice `controls_reset` is shown until the user opens the sort menu or the sheet, changes tab, or leaves the list. Never a silent change of order |
| Degraded: data present but below thresholds | Option hidden, plus the footnote in the menu or sheet (§3.3) |
| Offline | Sort and filter are purely local and keep working. Only the movie detail fetch needs the network (§4.4) |
| Search all on | Flat list, so the toolbar buttons and the active-filter row disappear (as the layout toggle does today), and the existing `catalog_search_all` notice shows. Filter and sort state is kept and comes back when search-all is turned off |
| Plain-M3U list, Directos, Todo | Unchanged |
| Very large catalogue (100k titles) | Same UI. Genre chips capped at 40 with a note. See §7 for cost |
| Tab with < 2 titles | Ordenar and Filtros hidden; layout toggle as today |
| Rotation / back from player or detail | State lives in the ViewModel and the Activity is not recreated: nothing changes, and scroll is kept (§5.1) |

### 4.2 Empty results: always with a way out

Replaces the catalogue branch of `EmptyState` (the 56 dp search circle stays). It
adds a `TextButton` under the message, so there is one remedy at a time and each
one peels off one layer of constraint:

| Condition (first match) | Message | Button → effect |
|---|---|---|
| ≥ 1 sheet filter and a query | `empty_search_filters` | **Quitar filtros** → clears language, genre and rating; the query stays |
| ≥ 1 sheet filter | `empty_filters` | **Quitar filtros** |
| Category selected | `empty_category` (existing) | **Ver todas las categorías** → category = Todas |
| Query only | `empty_search_titles` | **Borrar búsqueda** → query = "" |
| Otherwise | `empty_tab` (existing) | none |

If the first remedy still leaves the list empty, the next row of the table applies.
Non-catalogue tabs keep today's messages with no button.

### 4.3 Notice-line priority (extends `catalogNotice`, stays a pure function)

With a catalogue on a catalogue tab, first match wins:

1. Decode warning, INFO (existing, unchanged)
2. `catalog_outdated`, ACTION_REFRESH (**new**)
3. `controls_reset`, INFO (**new**, transient, session only)
4. Unreachable posters, INFO (existing, unchanged)
5. `movie_detail_hint`, ACTION_DISMISS (**new**, Movies tab only, until it has been seen)

`NoticeLine`'s action needs its own label (today it is hard-coded to "Actualizar"):
ACTION_REFRESH uses `refresh`, ACTION_DISMISS uses `got_it`. Rules 1 to 6 of
`no-catalogue-notice.md` are unchanged.

### 4.4 Movie detail states

| State | Header column | Below |
|---|---|---|
| Loading | poster, list rating, quality; then `⟳ Cargando ficha…` | Play enabled; no plot yet |
| Loaded, complete | facts, genres, director, quality | plot, cast |
| Loaded, partial | only the known parts; missing lines are not rendered | whatever exists |
| **Loaded, empty** (`info` is `{}`, `[]`, `null`, or every field unknown) | poster, list rating, quality | `movie_detail_empty` (bodyMedium, onSurfaceVariant) |
| **Error** (timeout, HTTP 401/403/404/5xx, invalid JSON, no network) | as in loading, without the spinner | error banner (same component as `SeriesErrorBanner`): `movie_detail_error` + the mapped specific message + `movie_detail_error_play` · [Reintentar] [Cancelar]. Play stays enabled |
| Offline | same as error, with the mapper's "cannot reach the server" message | — |
| Large font (2.0×) | the header column grows past the poster, which is fine because the page scrolls | — |

A failed or empty fetch never blocks playback and never changes the grid.
Metadata loaded lazily **never** feeds back into sorting or filtering (that would
make the list reorder under the user's finger).

## 5. Interaction details

### 5.1 Scroll and focus
- Any change of sort, language, genre, rating or category scrolls the grid **and**
  the list to item 0. Otherwise the user lands in the middle of the new order.
  Typing a search keeps today's behaviour.
- **Required fix that comes with the detail:** the list layout's `LazyListState`
  for Movies and Series must be hoisted next to `vodGridState` in `OpenTvApp`.
  Without that, back from a movie detail (or from a series) in list layout jumps to
  the top. The grid already keeps its position.
- When the sort menu closes, focus returns to the Ordenar button. When the sheet
  closes, focus returns to the Filtros button (M3 default). On opening the detail,
  TalkBack starts at the top bar, and the first action after "Volver" is
  "Reproducir".

### 5.2 Back
| Where | Back does |
|---|---|
| Sort menu open | closes the menu |
| Filter sheet open | closes the sheet (filters already applied) |
| Movie detail | returns to the grid/list at the same scroll position |
| Player opened from detail | returns to the detail (fullscreen still exits first, unchanged) |
| Playlist | unchanged: back to "Mis listas" |

### 5.3 Persistence

| State | Scope | Store | Survives restart | Reset by |
|---|---|---|---|---|
| Sort | **per list × per tab** | DataStore `v1_sort_<listId>_movie`, `v1_sort_<listId>_series` (enum name; unknown or invalid → provider) | yes | user; deleting the list removes the keys; unavailable after refresh → reset + `controls_reset` |
| Language | **per list** (shared by Movies and Series) | DataStore `v1_lang_<listId>` (option id, e.g. `es`, `en`, `other`) | yes | user; list deletion; it applies only on a tab whose detected options include it (otherwise that tab shows "Todos" and nothing is filtered) |
| Genre, Nota ≥ 7 | per tab, session | ViewModel | no | tab change (like category chips today), list switch, "Quitar filtros" |
| Category chip | per tab, session (as today) | ViewModel | no | as today; also language change (§3.5) |
| Layout | global (as today) | existing `v1_vod_layout` | yes | unchanged |
| Hint seen | global | DataStore `v1_movie_detail_hint_seen` | yes | never |
| Movie info | session LRU 16 | memory | no | — |

Why per list: capabilities and prefixes differ between panels. A global "Estreno
más reciente" chosen on one list would fall back on another list every time, and
the `controls_reset` notice would fire on every switch. Language is the one filter
that is a *preference* ("I watch in Spanish"), so it persists. Genre and rating are
*browsing*, so they are forgotten like the category chip.

### 5.4 Rotation
The Activity is not recreated (project rule). The menu's `expanded` state and the
sheet's open state are local UI chrome (`rememberSaveable`, one instance each,
never per row). Everything else lives in the ViewModel.

## 6. Accessibility

| Control | Semantics |
|---|---|
| Ordenar button | `contentDescription = "Ordenar. Orden actual: Mejor valoradas"` (`sort_cd`) |
| Sort items | `selectable`, `Role.RadioButton`; A-Z reads as `sort_az_cd` ("Alfabético, de la A a la Z") |
| Filtros button | `filters` or the plural `filters_cd_active` ("Filtros, 2 activos"); the badge itself is `clearAndSetSemantics {}` so it is not read twice |
| Active-filter chip | `contentDescription = "Quitar filtro: Comedia"` (`filter_remove_cd`); the trailing ✕ is decorative |
| Sheet chips | `FilterChip` exposes `selected`; section headers are `heading()` |
| Rating switch row | `toggleable(role = Role.Switch)` on the row; the Switch has `onCheckedChange = null` so it is announced once |
| Movie cell / row | `onClickLabel = "Reproducir"`, `onLongClickLabel = "Ver ficha"` (`movie_details`); TalkBack offers "double-tap and hold" plus the actions menu |
| `★ 7,4` | the facts `Text` gets `contentDescription` built from `rating_cd` ("Nota 7,4 de 10"); the year range uses `series_years_range_cd` |
| Play | text button "Reproducir", 18 dp leading icon with `contentDescription = null` |

- **Targets.** Every new control is ≥ 48 dp: IconButtons, menu items, M3 chips and
  InputChips (via `minimumInteractiveComponentSize`), the switch row (56 dp), and
  footer buttons with `heightIn(min = 48.dp)`.
- **Contrast** on `#070B12` / `#0D1420`: `#94A3B8` ≈ 7.7:1; mint `#5EEAD4` > 10:1;
  violet `#A78BFA` ≈ 6.9:1 on the background and about 6:1 on its own 18% tint.
  `onPrimary` `#042F2E` on mint > 9:1. All pass AA at `labelSmall`.
- **Large font (1.3× to 2.0×).** The count text wraps to 2 lines while the three 48 dp
  buttons stay fixed. Chip labels ellipsise at 220 dp. The sheet scrolls and its
  footer stays visible. The detail scrolls. Series header lines A and C ellipsise
  at 1 line and the plot drops to 4 lines. In landscape at 2.0× the series header
  can take most of the height (residual risk, §9).

## 7. Performance notes (tens of thousands of titles)

- **Parse once.** `added` (Long), release key (Int), normalised rating (Float),
  runtime (Int) and genre ids (small `IntArray` indexing a per-catalogue genre
  table) are derived while the catalogue is built, on `Dispatchers.Default`, and
  stored on `VodItem`/`SeriesCard` as **defaulted** fields so an old `XtreamCache`
  still decodes. Raw genre strings are not kept per item. Language is resolved per
  **category** (a map of tens of entries), not per item.
- **Capabilities once.** Coverage, distinct and dominant-share counts are one pass
  per tab per catalogue, cached with the catalogue identity.
- **Sort once per (catalogue, sort).** Build an `IntArray` permutation lazily the
  first time a sort is chosen and memoise it with the catalogue identity. Filtering
  then walks the permutation (O(n) predicates, no re-sort per keystroke or chip
  tap). 100k titles: a sort of a few tens of ms, once.
- **Filtering** stays in the existing `scheduleFilter` (cancel-previous on
  Default). The new predicates (language via a category lookup, genre via an
  `IntArray` contains, rating via a float compare) are constant time. The
  unchanged-snapshot check must include sort and the three filters.
- **No per-row state.** Cells get no new `remember`. `combinedClickable` replaces
  `clickable`. Keys (`movie:<id>`, `series:<id>`), `contentType`, Coil `size()` and the
  prefetch window of 8 are unchanged. A new order changes the list identity, which
  already restarts `PosterPrefetch` correctly.
- **No faceted counts** on chips in v1: per-option counts under the other active
  filters would cost O(n × options) per change (100k × 40). The live "Ver N títulos"
  footer gives the user the only count that matters.
- Formatting (`★ 7,4`, dates) happens only on visible detail and header text, never
  for the whole list.

## 8. Strings (add to `app/src/main/res/values/strings.xml`)

```xml
<!-- Catálogo: ordenar -->
<string name="sort">Ordenar</string>
<string name="sort_cd">Ordenar. Orden actual: %1$s</string>
<string name="sort_menu_title">Ordenar por</string>
<string name="sort_provider">Orden del proveedor</string>
<string name="sort_recent">Añadidas recientemente</string>
<string name="sort_release">Estreno más reciente</string>
<string name="sort_rating">Mejor valoradas</string>
<string name="sort_az">A-Z</string>
<string name="sort_az_cd">Alfabético, de la A a la Z</string>
<string name="sort_unavailable_hint">Solo se muestran los órdenes para los que esta lista tiene datos.</string>
<string name="items_count_sorted">%1$s · %2$s</string>
<string name="items_count_of">%1$s de %2$s títulos</string>

<!-- Catálogo: filtros -->
<string name="filters">Filtros</string>
<plurals name="filters_cd_active">
    <item quantity="one">Filtros, %1$d activo</item>
    <item quantity="other">Filtros, %1$d activos</item>
</plurals>
<string name="filters_title_movies">Filtrar películas</string>
<string name="filters_title_series">Filtrar series</string>
<string name="filter_language">Idioma</string>
<string name="filter_genre">Género</string>
<string name="filter_all">Todos</string>
<string name="filter_rating">Valoración</string>
<string name="filter_rating_min">Solo nota 7 o más</string>
<string name="filter_rating_help">Se ocultan los títulos sin nota.</string>
<string name="filter_rating_chip">Nota ≥ 7</string>
<string name="filter_genre_capped">Se muestran los %1$d géneros más frecuentes.</string>
<string name="filters_unavailable_hint">Solo se muestran los filtros para los que esta lista tiene datos.</string>
<string name="filters_clear">Quitar filtros</string>
<plurals name="filters_show_results">
    <item quantity="one">Ver %1$s título</item>
    <item quantity="other">Ver %1$s títulos</item>
</plurals>
<string name="filter_remove_cd">Quitar filtro: %1$s</string>
<string name="empty_filters">Ningún título cumple los filtros.</string>
<string name="empty_search_filters">Ningún título coincide con la búsqueda y los filtros.</string>
<string name="empty_search_titles">No hay títulos que coincidan con la búsqueda.</string>
<string name="empty_category_action">Ver todas las categorías</string>
<string name="clear_search">Borrar búsqueda</string>
<string name="controls_reset">Algunas opciones de orden o filtro ya no están disponibles en esta lista y se han quitado.</string>
<string name="catalog_outdated">Este catálogo se guardó con una versión anterior de la app: actualiza la lista para ordenar por fecha y filtrar por género.</string>

<!-- Idiomas detectados en las categorías ("Otros" reutiliza @string/other) -->
<string name="lang_es">Español</string>
<string name="lang_latino">Latino</string>
<string name="lang_en">Inglés</string>
<string name="lang_fr">Francés</string>
<string name="lang_de">Alemán</string>
<string name="lang_it">Italiano</string>
<string name="lang_pt">Portugués</string>
<string name="lang_pt_br">Portugués (Brasil)</string>
<string name="lang_ca">Catalán</string>
<string name="lang_gl">Gallego</string>
<string name="lang_eu">Euskera</string>
<string name="lang_ar">Árabe</string>
<string name="lang_tr">Turco</string>
<string name="lang_nl">Neerlandés</string>
<string name="lang_pl">Polaco</string>
<string name="lang_ru">Ruso</string>
<string name="lang_ro">Rumano</string>
<string name="lang_hi">Hindi</string>
<string name="lang_vose">V.O. subtitulada</string>

<!-- Géneros unificados -->
<string name="genre_action">Acción</string>
<string name="genre_adventure">Aventura</string>
<string name="genre_animation">Animación</string>
<string name="genre_comedy">Comedia</string>
<string name="genre_crime">Crimen</string>
<string name="genre_documentary">Documental</string>
<string name="genre_drama">Drama</string>
<string name="genre_family">Familia</string>
<string name="genre_fantasy">Fantasía</string>
<string name="genre_history">Historia</string>
<string name="genre_horror">Terror</string>
<string name="genre_music">Música</string>
<string name="genre_musical">Musical</string>
<string name="genre_mystery">Misterio</string>
<string name="genre_romance">Romance</string>
<string name="genre_scifi">Ciencia ficción</string>
<string name="genre_tv_movie">Película de TV</string>
<string name="genre_thriller">Suspense</string>
<string name="genre_war">Bélica</string>
<string name="genre_politics">Política</string>
<string name="genre_western">Western</string>
<string name="genre_kids">Infantil</string>
<string name="genre_news">Noticias</string>
<string name="genre_reality">Reality</string>
<string name="genre_soap">Telenovela</string>
<string name="genre_talk">Entrevistas</string>
<string name="genre_biography">Biografía</string>
<string name="genre_sport">Deporte</string>
<string name="genre_game_show">Concurso</string>

<!-- Ficha de película -->
<string name="movie_details">Ver ficha</string>
<string name="play">Reproducir</string>
<string name="movie_detail_loading">Cargando ficha…</string>
<string name="movie_detail_empty">El proveedor no tiene más información sobre esta película.</string>
<string name="movie_detail_error">No se pudo cargar la ficha.</string>
<string name="movie_detail_error_play">Puedes reproducir la película igualmente.</string>
<string name="movie_detail_hint">Mantén pulsada una película para ver su ficha.</string>
<string name="got_it">Entendido</string>
<string name="movie_director">Dirección: %1$s</string>
<string name="movie_cast">Reparto: %1$s</string>
<string name="duration_hours_minutes">%1$d h %2$d min</string>
<string name="duration_minutes">%1$d min</string>
<string name="rating_short">★ %1$s</string>
<string name="rating_cd">Nota %1$s de 10</string>

<!-- Cabecera de serie -->
<string name="series_years_range">%1$d – %2$d</string>
<string name="series_years_range_cd">Emitida de %1$d a %2$d</string>
<string name="series_runtime">%1$d min por episodio</string>
```

The error banner text is `movie_detail_error` + a space + the mapped specific
message (`PlaylistException.userMessage()`) + a line break + `movie_detail_error_play`.
The generic fallback for a non-`PlaylistException` must also be a resource (not a
Kotlin literal, unlike today's series fallback).

## 9. What must not change, and residual risks

**Must not change**
- **Tap on a movie plays it** (grid, list, favourites). Tap on a series opens the
  drill-down. The star keeps its own target in every cell.
- Live, Todo, search-all and plain-M3U lists, and Xtream lists without a
  catalogue: identical UI, no new API calls (plain-M3U lists never call the API).
- The default view: provider order, no filters, and the same count text.
- A failed refresh never destroys the list or the catalogue. Sort and filter work
  on whatever catalogue is on screen.
- `catalogNotice` rules 1 to 6 and the existing notice texts. The function stays
  pure and unit-tested (new rows: outdated, controls_reset, hint, priorities).
- Grid performance rules (3 fixed columns, stable keys, one `contentType`, Coil
  `size()`, prefetch 8, no `SubcomposeAsyncImage`/`BoxWithConstraints` per cell,
  no per-row state). The 150 ms search debounce.
- The disk catalogue cache stays backward compatible (defaulted fields,
  `schemaVersion` defaulting to 0). Playback URLs are never built from
  `container_extension` or from `get_vod_info`.
- Favourites screen: no sort, no filters, same order.
- No third-party metadata, no bundled lists or providers, and no host or
  credentials in errors or logs (redaction on the new `get_vod_info` path).

**Deliberate changes to existing behaviour:** the layout toggle moves from the
category row to the results toolbar. The catalogue-tab empty states gain a remedy
button and say "títulos" instead of "canales".

**Optional (do not block the feature):** a list-row subtitle showing the active sort
key instead of the constant "Películas"/"Series" (e.g. `★ 7,4`, `Añadida el 12 mar
2025`, `2016`); long-press for detail from the Favourites screen; multi-select genre;
faceted chip counts.

**Residual risks**
- Long-press discoverability depends on the hint. If an unreachable-posters notice
  is permanently on the Movies tab of some panel, the hint (lowest priority) never
  shows there. TalkBack users always get the custom action.
- The language token table is a heuristic: a panel using an unlisted language or
  invented prefixes gets "Otros" or no filter (safe failure). Two-letter `AR` is
  read as Árabe even where a provider meant Argentina.
- The "Thriller → Suspense" label may surprise some users. It is a wording choice
  and easy to change.
- Thresholds (0.50 / 2 / 0.95) are reasoned, not measured on many panels. They
  need tests on synthetic catalogues, and possibly tuning after real use.
- The series header at 2.0× font in landscape can crowd the episode list. This is
  accepted for v1.
- Only one real account was measured. Parsing tolerance has to be proven by
  synthetic fixtures covering every shape listed in §3.3.
