---
name: stream-probe
description: Probe an IPTV playlist or stream URL from the command line before blaming the app. Use to check HTTP status, content type, encoding, channel counts, Live/Movie/Series classification, HLS master/media manifests, first segment bytes, and MPEG-TS sync. Bundles probe.mjs, which redacts credentials by default.
---

# Stream probe

`probe.mjs` answers "is the playlist broken, or is the player broken?" without
opening the app. It is read-only, makes a small number of requests, and redacts
credentials by default.

## Usage

```bash
# A local playlist (use your own; never commit it)
node .pi/skills/stream-probe/probe.mjs local/list.m3u --limit 5

# A URL (put it in local/ or quote it; do not commit it)
node .pi/skills/stream-probe/probe.mjs "http://provider/get.php?username=...&password=...&type=m3u_plus&output=m3u8"

# Public demo
node .pi/skills/stream-probe/probe.mjs samples/demo.m3u

# Machine-readable
node .pi/skills/stream-probe/probe.mjs local/list.m3u --json --limit 0

# Check the first HLS segment / raw TS bytes
node .pi/skills/stream-probe/probe.mjs samples/demo.m3u --probe-stream
```

Options: `--limit N` (default 5), `--timeout MS` (default 20000), `--max-bytes N`
(default 30000000), `--json`, `--probe-stream`, `--show-urls` (DANGER), `--help`.

## What it reports

- HTTP status, final URL (redacted), content type, body size.
- UTF-8 / Latin-1 decoding, BOM/CRLF handling.
- `m3u` entries, Live/Movie/Series/Other counts, group count, duplicate names,
  parse warnings.
- Sample channels (redacted) with type, group, quality tag.
- HLS master vs media, variant count, segment count, target duration, AES-128,
  VOD (`#EXT-X-ENDLIST`), first segment, optional first-variant probe.
- Optional first-segment/raw-TS probe: status, content type, bytes read, TS sync
  (`0x47`), fMP4 (`ftyp`).

Expected result on a large mixed list: a total entry count and per-type counts.
Use a synthetic fixture for regression tests; never commit a real provider list.


## Safety and etiquette

- Only probe playlists/streams the user owns or public test streams.
- The script limits redirects to 5 and applies a per-request timeout. Do not run
  it in a loop against a provider.
- `--probe-stream` fetches only the first 1 KB with a `Range` request.
- Basic auth in the URL is sent only to the original origin; redirects to another
  host do not receive it.
- `--show-urls` prints credentials. Use it only in a private terminal and never
  paste the output into an issue, commit, or screenshot.
- The script never downloads a full channel stream.

## When to use it

| Symptom | Probe command | What to look for |
|---|---|---|
| "Lista vacía" | `probe.mjs local/list.m3u` | entries=0, HTML kind, warnings |
| "No carga la lista" | `probe.mjs "<url>"` | HTTP 401/403/404/5xx, timeout |
| "Solo salen películas" | `probe.mjs local/list.m3u` | live/movie/series counts |
| "Un canal no reproduce" | `probe.mjs "<playlist>" --probe-stream` | segment status/TS sync |
| "Va lento" | `probe.mjs local/list.m3u --json` | size, entries, groups |
| "Pantalla negra" | probe the channel's HLS manifest | variant/segment 403/404 |

## Interpreting common failures

- **HTML kind**: wrong URL, expired login, captive portal, or proxy error page.
- **HTTP 403 on a channel but playlist OK**: provider rejects the player UA,
  subscription limits, or the channel is offline. Try a different variant.
- **HLS master with 0 variants**: malformed manifest or an HTML error body.
- **Segment 200 but no TS sync / no `ftyp`**: server returned an error page with
  a 200 status, or the segment is encrypted in a way Media3 does not support.
- **Huge body with few entries**: the list is padded with huge logo URLs; the
  app should still stream-parse it.
- **`--probe-stream` timeout**: the provider is slow or blocked; raise the
  timeout once, do not hammer it.

Do not paste raw provider URLs or credentials into the final report. Report the
redacted host, status, and counts.
