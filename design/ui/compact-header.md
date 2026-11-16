# Compact header for the playlist screen (phase 1)

Spec: `design/ui/compact-header.md` · Screens: `PlaylistScreen` (header, search,
group/category filter), `ListsScreen` (back behaviour only), app start in
`OpenTvApp` / `PlaylistViewModel` · Author: ui-designer · Branch:
`feature/compact-header` (rollback tag `checkpoint/pre-compact-header`) ·
Status: ready to implement.

Scope is the council outcome the owner accepted (phase 1). Not in scope: a header
that scrolls away (phase 2), a navigation drawer (phase 3, only if needed).

Measurements below come from a `uiautomator` dump of the current build on
`emulator-5554` (1080x2400, 420 dpi, 411 dp wide, font scale 1.0). Only bounds
and the app's own strings were read. No screenshot was taken: the lists screen
shows the provider host, and the playlist shows the real account's group names.

---

## 1. The problem

On the main playlist screen the list starts **259 dp** below the status bar on
Live and **280 dp** below it on Movies. That stack is the top bar (64), a 4 dp
spacer, the tabs (48), a permanent search field (68), the group or category pills
(48), and a count row (26 on Live, 48 for the results toolbar on Movies). On a
phone in portrait this leaves about 6 to 7 channel rows visible. In landscape it
leaves fewer than 3. Despite all that chrome, three things are broken or hidden:

- The header subtitle is `name · 32.905 canales · 16/11 · 18:33` on one
  ellipsized line, so the user sees "…32.905 canal…" and **never sees when the
  list was last updated**.
- `GroupPills` renders `state.groups.take(12)`, so **every group after the 12th
  cannot be reached**. A provider with 300 groups exposes 12 of them.
- Back from the playlist goes to the lists screen ("requirement 2"). The app
  also always starts on that screen, so the user taps their only list on every
  launch.

## 2. Options considered

### 2.1 Switching lists

| Option | Buys | Costs |
|---|---|---|
| **A. Title = active list name with a ▾ caret, tap opens `ListsScreen` (chosen)** | Reuses the existing screen, which already handles switching, adding, editing and deleting, credential masking and the delete confirmation. Adds no new surface. The title also tells the user which list they are looking at. | The caret has to be visible even with one list, or adding a second list is not discoverable. |
| B. Title opens a `DropdownMenu` of lists plus "Añadir lista" | Switching takes one tap fewer. | It duplicates `ListsScreen` (edit, delete, a broken list's state) in a menu that has no room for the host line and the error. Two places to maintain. |
| C. Navigation drawer | Room for lists and future sections. | It brings a hamburger icon for a single destination and duplicates the lists screen. It was deferred to phase 3. |

A wins because it is the smallest change: one tap target and one back rule.

### 2.2 Search

| Option | Buys | Costs |
|---|---|---|
| **A. Buscar icon turns the top bar into a search field in place (chosen)** | Removes 68 dp of permanent chrome. The tabs, group chip, notices and list stay where they are, so a filtered result is still explained by the same rows. | Search is one tap further away. Search mode has to stick while a query is active, or the filtered list would have no explanation. |
| B. Keep the permanent field | No change. | 68 dp on every visit, for an action most visits do not use. |
| C. M3 `SearchBar` / `ExpandedFullScreenSearchBar` | Standard animation. | It draws its own results surface over the tabs and filters and fights the existing filtered `LazyColumn` and grid. That means a second results renderer for 165k rows. Rejected. |

### 2.3 Groups

| Option | Buys | Costs |
|---|---|---|
| **A. One "Grupo" chip that opens a `ModalBottomSheet` with a filter field and every group (chosen)** | Every group is reachable, each with its count. A filter field makes 300+ groups usable. It replaces two rows (pills and count) with one. | Picking a group takes two taps (chip, then row) instead of one. |
| B. Keep the pills and add a "Más…" pill | Group 1 to 12 stay one tap away. | Still a whole row, still a list of 300 ordered by position, still no filter. |
| C. `ExposedDropdownMenu` | Familiar. | A dropdown with hundreds of entries and an inline filter field is awkward and loses the counts' alignment. |

## 3. The chosen design

### 3.1 Vertical budget (portrait, font scale 1.0)

| Row | Live / All / flat tabs | Movies / Series catalogue |
|---|---|---|
| Top app bar (or search bar) | 64 dp | 64 dp |
| Refresh progress | 0 dp, overlaid on the bar's bottom 2 dp | 0 dp |
| Tab pills | 48 dp (the 4 dp spacer is removed) | 48 dp |
| Filter row / results toolbar | 48 dp | 48 dp (category chip now lives here) |
| **Default chrome** | **160 dp** (was 259) | **160 dp** (was 280) |
| Only when needed | Error banner, notice line | Active-filter chips (48), error banner, notice line |

The council target was about 150 dp. 160 dp is the floor while every control
keeps a 48 dp touch target, and this spec does not go below that floor. Phase 2
(a header that scrolls away) is where the next saving comes from.

### 3.2 Top app bar: default mode

`TopAppBar` (small, single row). Keep today's gradient background
(`0xFF0B2A28` to background `#070B12`), a transparent container and the
status-bar insets exactly as today. Use `expandedHeight = max(64.dp, (24.sp +
16.sp).toDp() + 8.dp)`. That is 64 dp at font scales up to about 1.4 and grows
with the font (88 dp at 2.0), so the two-line title is never clipped.

```
┌──────────────────────────────────────────────────────────┐
│ [■]  Mi lista ▾                      🔍   ★   ⟳   ⋮       │  64 dp
│      Actualizada hoy · 18:33                              │
└──────────────────────────────────────────────────────────┘
  ↑ 32 dp mark   ↑ title block (one tap target)  ↑ 4 × 48 dp actions
```

- **Navigation slot: brand mark.** `R.drawable.logo_mark`, 32 dp, corner radius
  10 dp, centred in a 48 dp box. Decorative: `contentDescription = null`, not
  focusable, not clickable. It replaces the back arrow and keeps the DoD rule
  "header uses the launcher mark".
- **Title block.** One clickable `Row`, `heightIn(min = 48.dp)`, ripple clipped
  to `RoundedCornerShape(12.dp)`, inner padding `horizontal = 4.dp`. Tapping it
  opens `ListsScreen` (`showLists()`, §5.2).
  - Line 1: active list name, `titleMedium`, `onSurface`, `maxLines = 1`,
    ellipsis, in a `weight(1f, fill = false)` slot so the caret is **never**
    ellipsized away. The caret follows it: `Icons.Default.ArrowDropDown`, 20 dp,
    `onSurfaceVariant`, 2 dp gap. The caret shows **always**, including with a
    single list, because it is how the user finds "add a list".
  - Line 2: the last-updated time, `labelSmall`, `onSurfaceVariant`,
    `maxLines = 1`. It is the only thing on the line: the channel count moves to
    the filter row (§3.5) and the tab pills. Pick the text in this order and use
    the first one that fits, measured once with `rememberTextMeasurer()` against
    the title block's width (one instance per screen, never per row):
    1. Long: `Actualizada hoy · 18:33`, `Actualizada ayer · 18:33`,
       `Actualizada 16/11 · 18:33`, or for an older year `Actualizada 16/11/2024`.
    2. Short: the same without the word, capitalised: `Hoy · 18:33`,
       `Ayer · 18:33`, `16/11 · 18:33`, `16/11/2024`.
    3. If the short form still does not fit (large font on a narrow phone),
       move **Favoritos** into the overflow menu (§3.4), then measure again. If
       it still does not fit, ellipsize as a last resort. The full time stays in
       the title's content description and on the lists screen row.
  - `lastUpdated == null` (the list has never downloaded): line 2 reads
    `Aún sin descargar`.
  - The "when" part comes from a new pure, unit-tested formatter
    `updatedWhen(epochMs, nowMs, locale)` returning TODAY / YESTERDAY / DATE /
    OLD_YEAR plus the formatted time or date. Use the device time zone and the
    existing `HH:mm` and `dd/MM` patterns.
- **Actions** (each a 48 dp `IconButton` with a 24 dp icon, `onSurfaceVariant`
  unless stated). They are shown only while `state.playlist != null`, except ⋮,
  which is always shown:
  1. **Buscar**: `Icons.Default.Search`. Enters search mode (§3.3).
  2. **Favoritos**: unchanged. `StarBorder` in `onSurfaceVariant`, or `Star` in
     primary mint `#5EEAD4` when any favourite exists.
  3. **Actualizar**: unchanged. While `isLoading` it is disabled and shows an
     18 dp `CircularProgressIndicator` (stroke 2 dp, primary).
  4. **Más opciones ⋮**: `Icons.Default.MoreVert`. Opens the overflow menu
     (§3.4).
- **Refresh progress.** While `isLoading && playlist != null`, draw a 2 dp
  `LinearProgressIndicator` (primary on `surfaceContainerHigh`, determinate when
  `totalBytes > 0`, as in today's `RefreshProgress`) **overlaid** at the bottom
  edge of the bar. It takes no layout height, so the list never jumps 2 dp when a
  refresh starts or ends. It stays visible in search mode.

### 3.3 Top app bar: search mode

Tapping Buscar replaces the bar's content in place. The height, gradient and
insets stay the same, and the refresh overlay keeps showing.

```
┌──────────────────────────────────────────────────────────┐
│ ←   Buscar canal, grupo…________________   ✕    🌐         │  64 dp
└──────────────────────────────────────────────────────────┘
```

- Navigation: `ArrowBack` 48 dp, "Cerrar búsqueda". It closes search (§5.1).
- Title slot: a `TextField` filling the slot. Transparent container, no
  indicator, `bodyLarge`, cursor in primary, `singleLine`,
  `ImeAction.Search` (on Search, hide the keyboard and stay in search mode).
  Placeholder in `onSurfaceVariant`: `Buscar canal, grupo…` (existing
  `search_hint`). On a catalogue tab (`isCatalogTab`) use `Buscar título…`,
  because there the query matches titles only.
- Actions: **✕ Limpiar** (48 dp, only while the query is not empty; it clears
  the text and keeps search mode and focus). Then **Buscar en todo**: an
  `IconToggleButton`, 48 dp, `Icons.Default.Public`, primary when on and
  `onSurfaceVariant` when off. This is today's toggle, moved. Favoritos,
  Actualizar and ⋮ are hidden while searching.
- On entry: request focus on the field and show the keyboard, only on the user's
  tap (not on recomposition or rotation).
- **Sticky rule:** the bar shows search mode while
  `searchActive || query.isNotEmpty()`. A filtered list therefore always has the
  query visible above it.
- The tabs, filter row, notices and list underneath stay the same. The query
  filters within the current tab and group, exactly as today, and
  `catalog_search_all` still explains "Buscar en todo".

### 3.4 Overflow menu (⋮)

A `DropdownMenu` anchored to ⋮, container `surfaceContainerHigh` (as in the sort
menu), `widthIn(min = 200.dp)`. Each item is 48 dp and `labelLarge`:

| Item | Icon | Action | Shown when |
|---|---|---|---|
| Favoritos | `Star` | `showFavorites()` | only when §3.2 moved it out of the bar |
| Mis listas | `List` (AutoMirrored) | `showLists()` | always. It is a second, explicit path to the title's action. |
| Editar lista | `Edit` | `editActiveList()` (form `returnTo = PLAYLIST`, as today) | always (an active list exists on this screen) |

The pencil icon leaves the bar (owner decision 3). Nothing else goes into the
menu in phase 1.

### 3.5 Filter row (Live, All, and any tab without a catalogue)

This one 48 dp row replaces both `GroupPills` and the count row. It applies
wherever `!isCatalogTab`, which includes Movies/Series on a plain M3U list and
while "Buscar en todo" is on.

```
│ [Grupo: Todos ▾]                              8.450 canales │
│ [ES - FUTBOL ▾](✕)                   312 de 8.450 canales · 2 avisos │
```

- Padding `start = 16.dp, end = 20.dp`, `heightIn(min = 48.dp)`, items
  vertically centred.
- **Group chip.** M3 `FilterChip`, 32 dp visual height inside the 48 dp minimum
  touch target, corner radius 8 dp, `labelLarge`, trailing `ArrowDropDown`
  18 dp. Its colours reuse the `SheetChip` / `GroupPill` language:
  - No group: `selected = false`. Label `Grupo: Todos`. Border outline at 25 %,
    label `onSurfaceVariant`.
  - Group selected: `selected = true`. Label = group name. Container secondary
    violet `#A78BFA` at 18 %, label and icon secondary, border secondary at 50 %.
    Immediately after the chip comes a 48 dp `IconButton` with an 18 dp `Close`
    icon in secondary that clears the group (`onGroupChange(null)`).
  - Tap on the chip opens the group sheet (§3.7).
  - Width: `widthIn(min = 96.dp, max = 240.dp)` with `weight(1f, fill = false)`.
    The chip is what shrinks (the name ellipsizes), never the count.
  - No chip when `state.groups` is empty (a list without `group-title`). The
    count still shows.
- **Count**, right-aligned, measured before the chip so it never truncates.
  `labelMedium`, `onSurfaceVariant`, `maxLines = 2`, `TextAlign.End`:
  - Not narrowed: `8.450 canales` (existing `channels_count`).
  - Narrowed by group or query: `312 de 8.450 canales` (new
    `channels_count_of`). The total is `tabCount(tab)`, or all channels when
    "Buscar en todo" is on.
  - With playlist warnings, append ` · 2 avisos` (existing `warning_count`) as a
    span in error colour. This replaces today's separate icon and text.

### 3.6 Catalogue tabs: category chip inside `ResultsToolbar`

The provider category `LazyRow` (`CatalogPills`) is removed. The toolbar row
(48 dp, unchanged otherwise) becomes:

```
│ [Categoría ▾]  13.609 títulos            ⇅   ⧩   ▦ │
│ [Acción ▾](✕)  412 de 13.609             ⇅   ⧩   ▦ │
```

- **Category chip**: the same component and colours as the group chip. Label
  `Categoría` when none is selected, or the category name (the language-stripped
  name from `visibleVodCategories`) when selected, followed by the 48 dp ✕ that
  calls `onVodCategoryChange(null)`. `widthIn(min = 96.dp, max = 160.dp)`. It is
  shown only when `visibleVodCategories` is not empty, the same rule as today's
  pills.
- **Count text**: today's text (`items_count`, `items_count_of`, plus the
  `items_count_sorted` suffix). If the sorted form does not fit in 2 lines, fall
  back to the form without the sort suffix. The sort button stays tinted primary
  and its content description still states the order. The number never
  ellipsizes.
- Sort, Filtros (with its badge) and the list/grid toggle are unchanged.
- The active-filter chips row (`ActiveFilterChips`) is unchanged and still
  appears only when sheet filters are active.

### 3.7 Group sheet (and category sheet)

One composable, `PickerSheet`, used for groups and for categories.

- `ModalBottomSheet`, `skipPartiallyExpanded = true`, container `surface`
  `#0D1420`, default drag handle. The `showGroupSheet` / `showCategorySheet`
  flags are `rememberSaveable` booleans in `PlaylistContent` (one instance, like
  `showFilters`). Close the sheet with an effect if its data disappears (for
  example the tab changes).
- **Title**: `Grupos (N)` / `Categorías (N)`, `titleLarge`, horizontal padding
  24 dp, `heading()` semantics. N = `formatNumber(items.size)`.
- **Filter field**: below the title with 8 dp vertical gap, horizontal padding
  16 dp, `TextField` 56 dp, container `surfaceContainerHigh`, corner radius
  16 dp, no indicator (today's search-field look). Leading `Search`, placeholder
  `Filtrar grupos` / `Filtrar categorías`, trailing ✕ "Limpiar" while not empty,
  `ImeAction.Done` hides the keyboard. It does **not** auto-focus, so the list
  is visible when the sheet opens. The text is sheet-local (`rememberSaveable`)
  and starts empty on every open.
- **List**: `LazyColumn` with `weight(1f)` inside the sheet and
  `imePadding()`, so the rows stay above the keyboard.
  - Item 0, key `"all"`: `Todos los grupos` / `Todas las categorías`, with the
    tab total as its count for groups.
  - Then every group in `state.groups` order (provider order, as the pills
    were), key = group name (or category id), one `contentType`.
  - Row: `heightIn(min = 56.dp)`, horizontal padding 24 dp. Leading 24 dp slot:
    `Check` icon in secondary when selected, empty otherwise. Name in
    `bodyLarge`, `maxLines = 2`, ellipsis, secondary when selected and
    `onSurface` otherwise. Trailing count `formatNumber(count)` in
    `labelMedium` `onSurfaceVariant` (groups only; categories have no counts in
    phase 1, as today's chips had none).
  - Tap: select (`onGroupChange(name)` / `onVodCategoryChange(id)`), then hide
    the sheet. Tapping the already-selected row also just closes. Tapping
    "Todos" clears.
  - On open, scroll so the selected row is visible:
    `rememberLazyListState(initialFirstVisibleItemIndex = max(0, selectedIndex + 1 - 2))`.
  - Rows carry no `remember` and no state. The row is a plain composable taking
    `(label, count, selected, onClick)`.
- **Filtering**: a new pure function in `data/` (or next to the view model),
  unit-tested:
  ```kotlin
  class PickerEntry(val id: String, val label: String, val count: Int?, val key: String) // key = SearchNormalizer.key(label)
  fun filterPicker(entries: List<PickerEntry>, query: String): List<PickerEntry>
  // empty/blank normalized query -> entries (same instance); else entries whose key contains SearchNormalizer.query(query); order kept
  ```
  Build the entries once per `groups` / `visibleVodCategories` instance
  (`remember(list)` in the sheet). Never per keystroke and never per row.
  Filtering runs on each keystroke without debounce: it is a substring test over
  at most a few thousand short keys.
- **No match**: replace the list with centred text `Ningún grupo coincide con
  «x».` / `Ninguna categoría coincide con «x».` (`bodyMedium`,
  `onSurfaceVariant`) and a `TextButton` `Borrar filtro`.

## 4. Every state

| State | Top bar | Below the bar | Notes |
|---|---|---|---|
| **App starting** (settings read, typically < 100 ms) | none | Background `#070B12` with the 64 dp brand mark centred | New neutral launch state. It replaces today's flash of an empty "0 listas" lists screen. |
| **No lists** (fresh install, or the settings read failed) | (form's own) | Add-list form, as today | Back exits the app, as today. |
| **Opening, reading cache** (`playlist == null`, `isReadingCache`) | Title + caret, line 2 = last-updated or `Aún sin descargar`; ⋮ only | `ListLoadPane`: progress with "Cargando lista guardada…" | The title stays tappable. |
| **First download** (`playlist == null`, `isLoading`, no cache) | same | `ListLoadPane` with bytes and parsed count | This happens at cold start only for a list that never loaded. That is the same as tapping it today. |
| **Load failed, no list in memory** (bad URL, 401, offline and never cached) | same | `ErrorCard` (Reintentar / Cancelar) plus two text buttons: **Editar lista** and **Cambiar de lista** | Replaces "Volver a las listas". Because a broken list can now be the startup screen, both remedies must be one tap away. |
| **Default** | Full bar | Tabs, then the filter row or toolbar, then the list | 160 dp chrome. |
| **Refreshing** (list kept) | Spinner on Actualizar, 2 dp overlay bar, timestamp unchanged | List stays usable | The timestamp keeps saying when the shown data was fetched. |
| **Refresh failed, list kept** | Timestamp unchanged | `ErrorBanner` (message + Reintentar) under the filter row | Unchanged component. It is never hidden in search mode either. |
| **Offline at cold start** | Timestamp of the cache | Cached list, no request | As today. Only Actualizar fails, with the banner. |
| **Degraded catalogue / posters / hints** | — | `NoticeLine` under the banner, unchanged wording and actions | `catalogNotice` logic is untouched. |
| **Search active, empty query** | Search mode, keyboard up | Unfiltered tab | The ✕ is hidden. |
| **Search with query** | Search mode (sticky) | Count `N de M canales` / `N de M títulos` | Leaving the screen and coming back (player, favourites, lists) keeps search mode because the query is not empty. |
| **Search all on** | 🌐 in primary | Existing `catalog_search_all` notice on catalogue tabs | Turned off when search closes (§5.1), so it can never stay on with no visible toggle. |
| **Group selected** | — | Violet chip with the name, then ✕, then `N de M canales` | Cleared by ✕, by "Todos" in the sheet, by a tab change, or by a successful refresh (`applyPlaylist` resets it today; the chip then honestly says "Todos"). |
| **Catalogue tab** | — | Toolbar with category chip, count, sort, filters, layout | The active-filter row appears only while filters are on. |
| **Empty: tab has 0 entries** | — | Existing `EmptyState` text; the count reads `0 canales`; no chip if there are no groups | |
| **Empty: query or group gives 0** | — | `EmptyState`. Flat-list remedies are added: a group is selected, so `Ver todos los grupos`; a query only, so `Borrar búsqueda` (clears the text, keeps search open). | The catalogue remedies are unchanged. |
| **Empty playlist** | — | "Esta lista no contiene canales." | |
| **Very large list** (165k entries, hundreds of groups) | — | Lazy list unchanged; the sheet is lazy with stable keys | No per-row state. Picker keys are built once per groups list. |
| **Sheet open during a refresh that succeeds** | — | Sheet stays open with the new groups; the selection resets to "Todos" | No crash and no stale list. |

## 5. Interaction details

### 5.1 State ownership

- `searchActive: Boolean` goes in `UiState` (not persisted), with
  `openSearch()` and `closeSearch()`. `closeSearch()` sets
  `searchActive = false`, `query = ""` and `searchAll = false`, then refilters
  once. `openList()` resets `searchActive` together with the query, as it
  already does for the query. `setTab()` keeps the query and search mode, as
  today.
- Sheet open flags and sheet filter text: `rememberSaveable` in the playlist
  content, one instance each.
- Overflow open flag: `rememberSaveable`, one instance.

### 5.2 Navigation and back stack

`backToLists()` is replaced by two calls:

- `showLists()`: `screen = LISTS`, `form = null`. It does **not** cancel
  `loadJob`, clear `error`, or touch the playlist, tab, query, group or scroll.
  A refresh running when the user peeks at the lists keeps running.
- `returnToActiveList()`: if the active list's playlist is in memory, or it is
  loading, set `screen = PLAYLIST` (nothing reloads or resets). Otherwise call
  `openList(activeListId)` (cache first, no network if cached).

On `ListsScreen`, tapping the **active** list's row calls `returnToActiveList()`
when it is loaded or loading, and `openList()` otherwise (retry after a failure).
Tapping another list calls `openList()` as today, which resets tab, query,
search and group. The lists top bar gets an `ArrowBack` navigation icon "Volver a
la lista activa" while an active list exists. The active row's tag changes from
"Última usada" to "Activa".

| Where | Condition | Back does |
|---|---|---|
| Player | fullscreen | exits fullscreen (unchanged) |
| Player | — | closes the player, returns to the origin (unchanged) |
| Movie / series detail, Favoritos | — | returns to the playlist (unchanged) |
| Playlist | keyboard visible | hides the keyboard (system) |
| Playlist | overflow, sort menu or any sheet open | closes it and keeps the selection (component default) |
| Playlist | search mode | `closeSearch()`; focus goes to the Buscar icon |
| Playlist | root (also the load and error pane) | **leaves the app**: no `BackHandler` is enabled, so the system default runs (task to background on API 31+, finish on older) |
| Lists | an active list exists | `returnToActiveList()` |
| Lists | no active list | leaves the app |
| Add/edit form | lists exist | `cancelForm()` returns to `form.returnTo` (unchanged) |
| Add/edit form | no lists | leaves the app (unchanged) |

Every `BackHandler` is registered with `enabled =` its condition, so the
predictive back-to-home gesture works at the root.

Deleting the active list from the form opened through ⋮ "Editar lista": the
existing `deleteList()` lands on `ListsScreen` with the next list active. Back
then calls `returnToActiveList()`, which opens that list from its cache. Deleting
the last list shows the add form.

### 5.3 Startup flow (owner decision 1)

1. `UiState.screen` starts in a new `Screen.STARTING` (the brand-mark launch
   state, §4).
2. `init` reads the lists and the active id as today. If the list is empty or
   the read failed: `screen = LISTS` with the add form, as today.
3. Otherwise call `openList(activeId)`, which falls back to the first list as
   today. `openList` already sets `screen = PLAYLIST`, reads the cache without
   any network request, and resets the tab to Live, applying the existing
   start-on-All rule when Live is empty. An invalid URL makes `openList` show
   the edit form with `URL_INVALID` as today; cancelling it lands on the lists
   screen.
4. Process death behaves like a cold start. Nothing new is persisted: the active
   list already is, and query, search mode, group, tab and sheets are not.

### 5.4 Rotation and landscape

- The Activity is not recreated (`configChanges`), so search mode, query,
  keyboard, open sheets, the overflow, the sheet filter text and scroll positions
  all survive rotation. The player is unaffected: fullscreen still forces
  landscape and restores portrait.
- Landscape uses the same layout and the same 160 dp. Content is about 220 dp
  tall on a 411 dp-high phone, which is why phase 2 exists. The title block
  gains width, so the long timestamp form always fits.
- Sheets in landscape: `skipPartiallyExpanded` gives the full height. The list
  scrolls under the title and the field.

### 5.5 Focus

- Opening search: focus to the field and the keyboard shows. Closing: focus to
  the Buscar `IconButton` (a `FocusRequester` on it).
- Opening a sheet: TalkBack focus starts on the sheet title (heading). Closing
  it returns focus to the chip that opened it.
- Overflow: standard menu focus.

## 6. Accessibility

| Control | TalkBack |
|---|---|
| Brand mark | not focusable (decorative) |
| Title block | `clearAndSetSemantics { role = Button; contentDescription = … }`: `Lista activa: Mi lista. Actualizada hoy a las 18:33. Cambiar o añadir lista`. Without a timestamp: `Lista activa: Mi lista. Cambiar o añadir lista`. |
| Buscar | "Buscar" |
| Favoritos | "Favoritos" (unchanged) |
| Actualizar | "Actualizar"; while loading: "Actualizando la lista…" (disabled) |
| ⋮ | "Más opciones" |
| Search back | "Cerrar búsqueda" |
| Search field | placeholder as its label |
| ✕ in search | "Limpiar" (unchanged) |
| Buscar en todo | `IconToggleButton`: "Buscar en todo, activado / desactivado" |
| Group chip | "Grupo: Todos. Elegir grupo" / "Grupo: ES - FUTBOL. Elegir grupo" |
| Group ✕ | "Quitar el grupo ES - FUTBOL" |
| Category chip / ✕ | "Categoría: Acción. Elegir categoría" / "Quitar la categoría Acción" |
| Sheet rows | merged: "ES - FUTBOL, 312 canales", `selected`, `Role.RadioButton` |

- **Contrast**: all colours are the existing palette pairs already used on these
  surfaces. `onSurfaceVariant` on `#070B12` serves the subtitle and count.
  Secondary violet `#A78BFA` on its 18 % tint serves the selected chip, as the
  pills today. Mint on navy serves the primary accents. No new colour pair.
- **Font scale 2.0**:
  - The top bar grows (formula in §3.2). The name stays 1 line with the caret
    always visible. The subtitle switches to the short form, then Favoritos
    moves into ⋮ if needed (§3.2), so the timestamp stays visible on a 360 dp
    phone.
  - Filter row: `heightIn(min = 48.dp)` grows, the count wraps to 2 lines, and
    the chip ellipsizes down to 96 dp.
  - Toolbar: the count drops the sort suffix before it ever ellipsizes.
  - Sheet rows: names wrap to 2 lines, and rows grow from 56 dp.
- Every touch target is at least 48 dp, including the ✕ next to the chips.

## 7. Strings (`app/src/main/res/values/strings.xml`)

New:

| Key | Spanish |
|---|---|
| `header_title_cd` | `Lista activa: %1$s. Cambiar o añadir lista` |
| `header_title_cd_updated` | `Lista activa: %1$s. %2$s. Cambiar o añadir lista` |
| `header_updated` | `Actualizada %1$s` |
| `updated_when_today` | `hoy · %1$s` |
| `updated_when_yesterday` | `ayer · %1$s` |
| `header_never_updated` | `Aún sin descargar` |
| `updated_cd_today` | `Actualizada hoy a las %1$s` |
| `updated_cd_yesterday` | `Actualizada ayer a las %1$s` |
| `updated_cd_date` | `Actualizada el %1$s a las %2$s` |
| `updated_cd_day` | `Actualizada el %1$s` |
| `search` | `Buscar` |
| `search_close` | `Cerrar búsqueda` |
| `search_hint_titles` | `Buscar título…` |
| `more_options` | `Más opciones` |
| `refreshing_cd` | `Actualizando la lista…` |
| `switch_list` | `Cambiar de lista` |
| `back_to_active_list` | `Volver a la lista activa` |
| `group_chip` | `Grupo: %1$s` |
| `group_chip_cd` | `Grupo: %1$s. Elegir grupo` |
| `group_clear_cd` | `Quitar el grupo %1$s` |
| `groups_sheet_title` | `Grupos (%1$s)` |
| `groups_filter_hint` | `Filtrar grupos` |
| `groups_all` | `Todos los grupos` |
| `groups_none_match` | `Ningún grupo coincide con «%1$s».` |
| `category_chip` | `Categoría` |
| `category_chip_cd` | `Categoría: %1$s. Elegir categoría` |
| `category_clear_cd` | `Quitar la categoría %1$s` |
| `categories_sheet_title` | `Categorías (%1$s)` |
| `categories_filter_hint` | `Filtrar categorías` |
| `categories_all` | `Todas las categorías` |
| `categories_none_match` | `Ninguna categoría coincide con «%1$s».` |
| `picker_filter_clear` | `Borrar filtro` |
| `channels_count_of` | `%1$s de %2$s canales` |
| `empty_group_action` | `Ver todos los grupos` |

Changed: `list_last_used` from `Última usada` to `Activa`.

Reused unchanged: `search_hint`, `search_all`, `clear`, `favorites`, `refresh`,
`edit_list`, `lists_title` (overflow "Mis listas"), `all_groups` (in
`Grupo: Todos`), `all_categories` (category chip CD when none is selected),
`channels_count`, `items_count*`, `warning_count`, `clear_search`.

Removed from the playlist screen: the `back_to_lists` usages. Delete the resource
if lint reports it unused. `tagline` stays (it is no longer in the header).

## 8. What must not change

- **A refresh never destroys the working list**: the list stays visible while
  refreshing, and a failure only adds the banner. Navigating to the lists screen
  no longer cancels a running refresh, which is stricter, not looser.
- Cold start from the cache makes **no network request**. Startup still parses
  the cache file in streaming mode.
- Tab pills: their look, counts, the start-on-All rule and its explanation.
- Every `catalogNotice` text and action, the `ErrorBanner`, `ErrorCard` and
  `LoadingProgress`.
- Catalogue: sort menu, filter sheet, active-filter chips, list/grid toggle and
  its persistence, scroll-to-top on a new view (`catalogViewVersion`), and the
  hoisted grid/list scroll state across a detail and back.
- Player: fullscreen forces landscape and back exits fullscreen first, back
  releases the player, rotation keeps the same player, mute.
- Favourites screen and its back. Movie long-press detail. The series
  drill-down.
- Large-list rules: stable keys, `contentType`, no per-row `remember`, a debounced
  query (~150 ms, unchanged), no new dependency (`material-icons-extended` is
  already present for `ArrowDropDown`, `MoreVert` and `List`).
- Edit-form semantics: `returnTo`, and a URL change reloads the list.
- Edge-to-edge: Scaffold `contentWindowInsets = systemBars`, and the gradient
  behind the status bar.

## 9. Acceptance checks (emulator, API 36, 1080x2400 at 420 dpi unless stated)

Use the synthetic list (`scripts/generate-large-playlist.mjs`, served over
`10.0.2.2`) and `scripts/fake-xtream-panel.mjs` (`rich`, `bare`). The generator
has only 9 groups, so the group checks need a fixture with **at least 40
groups**: add an optional group-count argument to the generator, or a
hand-written file under `local/`. Never capture the lists screen with a real
list, because it shows the host. Captures of a real catalogue stay private.

1. **Cold start opens the list.** Force-stop and launch. The first `uiautomator`
   dump after the brand mark contains the tab pills and no `Mis listas` node.
   Repeat with Wi-Fi and data disabled (`svc wifi disable`, `svc data disable`):
   the cached channels show with the timestamp, and no error appears.
2. **Chrome budget.** From the dump, (top of the first list row or poster cell)
   minus (bottom of the status bar) ≤ 164 dp on Live and on the Movies grid
   (baseline 259 dp and 280 dp).
3. **Timestamp visible.** The subtitle node text equals the full expected
   string, not ellipsized. At 411 dp it is the long form. With
   `wm density 480` (360 dp wide) it is the short form. Restore
   `wm density reset`.
4. **Caret with one list.** Delete down to one list. ▾ is still visible and the
   title node's CD equals `header_title_cd_updated`.
5. **Title → lists → back.** Select a group, type a query, scroll down. Tap the
   title, then press back: the same tab, group, query, search mode and scroll
   position, with no reload (the timestamp did not change).
6. **Refresh while peeking.** Tap Actualizar, then tap the title while the
   spinner runs, then press back: the refresh finished (the timestamp advanced).
7. **Search.** Tap Buscar: `dumpsys input_method` shows `mInputShown=true`.
   Type `espana` and confirm the `ESPAÑA - Drama` channels are found. Back once
   hides the keyboard, back again closes search: the query is empty, the
   🌐 toggle is off, and the full count is back. Turn "Buscar en todo" on, close
   search, open Movies on an Xtream list: the grid shows (`isCatalogTab`), not
   the flat list.
8. **Sticky search.** With a query, open a channel and go back: the bar is still
   in search mode with the query.
9. **All groups reachable.** On the 40-group fixture, `Grupos (40)` shows.
   Scroll to the 40th group, select it: the chip shows its name and the count
   reads `N de M canales`. ✕ clears it. Filtering `espana` lists the `ESPAÑA`
   groups. A non-matching filter shows the no-match line and `Borrar filtro`.
   Reopening with a selection scrolls it into view.
10. **Catalogue toolbar.** Fake panel `rich`, Movies: the category row is gone,
    and `Categoría` is in the toolbar with sort, filters and the toggle. Picking a
    category narrows the grid and scrolls it to the top. ✕ clears it. The filter
    sheet still opens. The active-filter chips appear with a filter. Panel
    `bare`: no category chip when there are no categories.
11. **Refresh failure keeps the list.** With the network off, tap Actualizar:
    the spinner, then the `ErrorBanner` with Reintentar. Channels are still
    listed and the timestamp is unchanged.
12. **Broken list at start.** Fake panel answering 401 on a never-cached list,
    set active, cold start: the load pane shows the actionable error with
    `Editar lista` and `Cambiar de lista`, and both work.
13. **Overflow.** ⋮ shows `Mis listas` and `Editar lista`. Editar, then back,
    returns to the playlist. Editing the URL and saving reloads it.
14. **Back at the root.** On the playlist with nothing open, back sends the task
    to the background (`dumpsys activity activities` shows our activity no longer
    resumed). Relaunch: the playlist, not the lists.
15. **Lists back.** From the lists screen, back returns to the playlist. After
    deleting the active list there, back opens the new active list from its
    cache.
16. **Rotation.** Rotate with search open and keyboard up, then with the group
    sheet open and a filter typed: state kept, no `FATAL EXCEPTION`. Player
    fullscreen enter/exit still forces and restores orientation.
17. **Font scale 2.0** (`settings put system font_scale 2.0`, with
    `wm density 480`): the bar grows without clipping, ▾ visible, timestamp
    visible, Favoritos in ⋮. The filter row count is not truncated. Restore
    `font_scale 1.0`.
18. **TalkBack labels**: every icon-only control in §6 has its content
    description in the dump.
19. **Large list.** 165,000 entries: open the sheet, type into its filter, fling
    the channel list. No ANR, no `FATAL EXCEPTION`, Dalvik heap no higher than
    the baseline for the same list.
20. **Unit tests**: `filterPicker` (accent and case folding, punctuation
    `es-futbol` matching `ES - FUTBOL`, blank query returns all, no match returns
    empty, order kept, **more than 12 entries all returned**) and `updatedWhen`
    (today, yesterday, same year, older year, time zone around midnight).
