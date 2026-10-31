---
name: iptv-playlists
description: Parse and normalize IPTV M3U/M3U8 and Xtream get.php playlists for Open IPTV. Use for playlist fetching, channel classification, group filters, search normalization, huge-list performance, and parse diagnostics. Covers BOM/CRLF, Latin-1, attributes, duplicates, relative URLs, and credential redaction.
---

# IPTV playlists

This skill is about turning a user-supplied playlist into a fast, searchable
channel list without leaking credentials or blocking the UI.

## Non-negotiables

1. Parse **off the main thread**, line by line. A large list can be several
   megabytes and tens of thousands of entries. `readText()` + regex on the whole
   file is a bug.
2. Treat the playlist as untrusted input. It can be HTML, a stream manifest, a
   truncated file, or 100k malformed lines.
3. Never log the playlist URL, channel URLs, `username`, `password`, `token`, or
   provider host in full. Use `redactUrl()`.
4. Show the user what happened: bytes read, entries parsed, groups found, and
   the first parse warning. Silent failure is not acceptable.
5. Do not silently deduplicate. Duplicate names with `FHD`/`HEVC`/`HDR` are
   normal and sometimes the only working variant.

## Accepted inputs

| Input | Example | Handling |
|---|---|---|
| Raw M3U/M3U8 URL | `https://host/list.m3u` | fetch, parse as text |
| Xtream HLS | `.../get.php?username=U&password=P&type=m3u_plus&output=m3u8` | fetch, parse; channel URLs end `.m3u8` |
| Xtream TS | same with `output=ts` | fetch, parse; channel URLs end `.ts` |
| Local file | `local/list.m3u` | debug/testing only |
| Single stream URL | `.../live/U/P/123.m3u8` | **not a playlist**; explain and offer to play it directly |

The app only asks for the playlist URL. Do not ask for username/password
separately in v1; they are already inside the Xtream URL.

## Canonical model

```kotlin
enum class SourceType { LIVE, MOVIE, SERIES, OTHER }

data class Channel(
    val id: String,            // stable UI key: hash(url + "|" + name)
    val name: String,          // display name, trimmed
    val url: String,           // absolute, resolved against playlist URL
    val group: String?,        // group-title or #EXTGRP
    val logoUrl: String?,      // tvg-logo, may be empty
    val tvgId: String?,        // tvg-id
    val tvgName: String?,      // tvg-name
    val sourceType: SourceType,
    val searchKey: String,     // normalized once, see below
    val qualityTag: String?    // extracted FHD/HD/HEVC/HDR/4K, optional
)
```

A `ParseResult` should carry `channels`, `warnings`, `groups`, `bytesRead`, and
`elapsedMs`. The ViewModel exposes the counts to the UI.

## Fetching

- OkHttp with a 10 MB disk cache, 20 s connect / 45 s read timeout, redirects
  enabled, and a browser-like `User-Agent`.
- Send `Accept: application/x-mpegURL, application/vnd.apple.mpegurl, text/plain, */*`.
- Use the final response URL as the base for relative channel URLs.
- Read the body as a stream. If `Content-Length` is known, report progress.
- Cancel the OkHttp `Call` when the user replaces the URL or leaves the screen.
- A 200 HTML page is not a playlist. Detect `<!DOCTYPE`, `<html`, or a JSON
  error body and report "the provider returned a web page, not a playlist".
- A media playlist (`#EXT-X-TARGETDURATION`, `#EXT-X-STREAM-INF`, no `#EXTINF`
  channel entries) is a stream, not a channel list. Report that distinction.

## Encoding

1. Honor the `charset` in `Content-Type`.
2. Otherwise decode UTF-8.
3. If decoding produces replacement characters, retry as ISO-8859-1.
4. Strip a UTF-8 BOM (`\uFEFF`) from the first line.
5. Normalize `\r\n` and `\r`; `BufferedReader.readLine()` already handles both.

## Line parser

State machine, not a regex over the whole file:

```kotlin
var pending: ExtInf? = null
var lineNo = 0
while (true) {
    val raw = reader.readLine() ?: break
    lineNo++
    val line = raw.trim().removePrefix("\uFEFF")
    when {
        line.isEmpty() -> Unit
        line.startsWith("#EXTM3U") -> Unit
        line.startsWith("#EXTINF:") -> pending = parseExtInf(line, lineNo)
        line.startsWith("#EXTGRP:") ->
            pending = pending?.copy(group = line.substringAfter(':').trim().ifBlank { null })
        line.startsWith("#EXTVLCOPT:") -> Unit // v1: ignore, optionally warn
        line.startsWith("#") -> Unit           // comments, VLC/Kodi props
        else -> {
            val url = resolveUrl(line, baseUrl)
            if (pending == null) {
                warnings += Warning(lineNo, "URL without #EXTINF; ignored")
            } else if (!url.isHttpUrl()) {
                warnings += Warning(lineNo, "Non-HTTP channel URL; ignored")
            } else {
                emit(buildChannel(pending, url))
            }
            pending = null
        }
    }
    if (lineNo % 500 == 0) onProgress(lineNo, count)
}
```

`parseExtInf` rules:

- Format: `#EXTINF:<duration> [key="value" ...],<display name>`.
- Find the comma that separates attributes from the display name **outside
  quotes**. Do not use `split(",")`; names and logo URLs can contain commas.
- Parse attributes with `Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")`.
- If the display name is blank, fall back to `tvg-name`, then `tvg-id`, then
  `"Canal sin nombre"`.
- Quote-aware comma scan:

```kotlin
fun splitExtInf(line: String): Pair<String, String> {
    var inQuotes = false
    for (i in line.indices) {
        when (line[i]) {
            '"' -> inQuotes = !inQuotes
            ',' -> if (!inQuotes) return line.substring(0, i) to line.substring(i + 1)
        }
    }
    return line to ""
}
```

- Tolerate unquoted attributes (`tvg-id=abc`) as a fallback, but prefer quoted.
- `#EXTGRP:` sets the group if `group-title` is absent.
- A second `#EXTINF` before a URL replaces the pending entry and adds a warning.

## Classification

Primary signal: URL path. Xtream paths look like `/live/U/P/123.m3u8`,
`/movie/U/P/456.mp4`, `/series/U/P/789.mkv`.

```kotlin
fun classify(url: String, group: String?): SourceType {
    val path = url.substringAfter("://", "").substringAfter('/', "").lowercase()
    return when {
        "/live/" in path -> LIVE
        "/movie/" in path -> MOVIE
        "/series/" in path -> SERIES
        else -> fallbackFromGroup(group)
    }
}
```

Fallback from group, translated where useful:

- Movie: `VOD`, `PELICULA`, `PELÍCULA`, `MOVIE`, `CINE`, `FILM`.
- Series: `SERIE`, `SERIES`, `NOVELA`, `ANIME`, `DOCUSERIE`.
- Live: `TV`, `LIVE`, `EN VIVO`, `DIRECTO`, `CANAL`, `DEPORTE`, `FUTBOL`,
  `NOTICIA`, `INFANTIL`, `MUSICA`.
- Otherwise `OTHER`. Keep it; do not drop unknown entries.

Use a synthetic fixture (with live/movie/series/other entries) for regression
checks. Never commit a real provider list.

## Search normalization

Precompute once per channel:

```kotlin
fun searchKey(input: String): String =
    Normalizer.normalize(input, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
```

Then `searchKey.contains(normalizedQuery)` or token `all { it in key }`. This
makes `futbol` match `Fútbol`, `LALIGA` match `LaLiga`, and ignores punctuation.
Also search `tvgName` and `group` if the user enables "search all fields".

UI behavior:

- Query is debounced ~150 ms in the ViewModel.
- Filtering runs on `Dispatchers.Default`; the UI only collects the result.
- Active tab filters first (Live/Movies/Series/All), then query, then group.
- Show "X de Y canales" so the user knows the filter worked.
- Empty state must say whether the playlist is empty, the tab has no entries, or
  the search has no matches.

## Performance budgets

- Parse 33k entries in under ~2 s on a mid-range phone; show progress if slower.
- Do not allocate per-line regexes. Compile attribute/search regexes once.
- Avoid `String.format` in the loop; use simple concatenation.
- Keep `Channel` immutable and lean. 33k × ~400 B is acceptable; do not also
  keep the raw multi-megabyte document after parsing.
- The raw playlist may stay in OkHttp's disk cache, not in RAM.
- List: `LazyColumn`, stable `key = channel.id`, `contentType = sourceType`.
  Rows must not create players, fetch images synchronously, or hold `remember`
  state per row.
- Logos: Coil with a small memory/disk cache and a placeholder; do not block the
  list on logo loading. If logos are slow, make them optional.

## Diagnostics and errors

Use a sealed error type and map it to a short user sentence:

| Condition | User message (Spanish) | Detail |
|---|---|---|
| Bad URL | "La URL no es válida. Debe empezar por http:// o https://." | parse error |
| Timeout | "La lista tardó demasiado en responder. Comprueba tu conexión o la URL." | timeout ms |
| 401/403 | "El proveedor rechazó la lista (403). Comprueba usuario/contraseña o que la suscripción esté activa." | status |
| 404 | "La URL no existe (404). Revisa que sea la dirección completa de la lista." | status |
| 5xx | "El servidor del proveedor falló (5xx). Inténtalo más tarde." | status |
| HTML | "La URL devolvió una página web, no una lista M3U." | content-type |
| Empty | "La lista se descargó pero no contiene canales." | entries=0 |
| Partial | "Se cargaron N canales; M líneas no se pudieron leer." | warnings |
| Too large | "La lista es demasiado grande para cargarla en este dispositivo." | bytes |

Keep the technical detail available behind "Ver detalles" but never include the
raw URL. `redactUrl()` must remove userinfo and query values for
`username/password/token/key/auth`, and for Xtream paths log only host + id.

## Tests and fixtures

Committed fixtures must use fake credentials and public streams:

- `samples/demo.m3u` — three public HLS streams.
- `bom-crlf.m3u` — UTF-8 BOM + CRLF.
- `attrs.m3u` — quoted commas in names/logos, missing attributes.
- `relative.m3u` — relative channel URLs.
- `latin1.m3u` — ISO-8859-1 bytes with `Fútbol`.
- `empty.m3u` — header only.
- `html-error.txt` — HTML instead of M3U.
- `hls-media.m3u8` — a media playlist, not a channel list.

Generate a 100k-entry fixture in the test at runtime; never commit a huge file.
Never copy the user's `local/` playlist into tests.

## Checklist

- [ ] Streaming parse, no whole-file regex.
- [ ] BOM/CRLF/Latin-1/relative URL handled.
- [ ] Comma-in-name attribute split is quote-aware.
- [ ] Classification by `/live/`, `/movie/`, `/series/` + fallback.
- [ ] Search key precomputed; accents/case/punctuation insensitive.
- [ ] Counts and groups surfaced in the UI.
- [ ] Parse warnings are visible, not swallowed.
- [ ] No credential in logs, errors, or analytics.
- [ ] 33k-entry list does not ANR, OOM, or jank the list.
