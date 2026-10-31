#!/usr/bin/env node
/**
 * Open IPTV playlist/stream probe.
 *
 * Usage:
 *   node probe.mjs <url|file> [options]
 *
 * Options:
 *   --limit N          sample channels to print (default 5, max 100)
 *   --timeout MS       per-request timeout (default 20000)
 *   --max-bytes N      refuse bodies larger than N bytes (default 30000000)
 *   --json             machine-readable output
 *   --probe-stream     request the first HLS segment / TS bytes
 *   --show-urls        print real URLs (DANGER: may expose credentials)
 *   --help
 *
 * This script never writes files and never follows a URL it was not given
 * (except HLS variant/segment URLs declared by the manifest).
 */

import { readFile } from 'node:fs/promises';
import { basename } from 'node:path';

const UA = 'OpenIPTV-Probe/1.0 (+https://github.com/)';
const SENSITIVE_PARAMS = new Set([
  'username', 'user', 'password', 'pass', 'token', 'key', 'auth', 'apikey',
  'api_key', 'secret', 'sig', 'signature', 'expires', 'session', 'sessionid',
]);

function usage() {
  console.error(`Usage: node probe.mjs <url|file> [options]

Options:
  --limit N          sample channels to print (default 5, max 100)
  --timeout MS       per-request timeout (default 20000)
  --max-bytes N      refuse bodies larger than N bytes (default 30000000)
  --json             machine-readable output
  --probe-stream     request the first HLS segment / TS bytes
  --show-urls        print real URLs (DANGER: may expose credentials)
  --help`);
}

function parseArgs(argv) {
  const opts = {
    input: null,
    limit: 5,
    timeout: 20_000,
    maxBytes: 30_000_000,
    json: false,
    probeStream: false,
    showUrls: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--help' || a === '-h') { usage(); process.exit(0); }
    else if (a === '--limit') opts.limit = clampInt(argv[++i], 0, 100, 5);
    else if (a === '--timeout') opts.timeout = clampInt(argv[++i], 1000, 120_000, 20_000);
    else if (a === '--max-bytes') opts.maxBytes = clampInt(argv[++i], 1024, 200_000_000, 30_000_000);
    else if (a === '--json') opts.json = true;
    else if (a === '--probe-stream') opts.probeStream = true;
    else if (a === '--show-urls') opts.showUrls = true;
    else if (a.startsWith('-')) { console.error(`Unknown option: ${a}`); usage(); process.exit(2); }
    else if (!opts.input) opts.input = a;
    else { console.error(`Unexpected argument: ${a}`); usage(); process.exit(2); }
  }
  if (!opts.input) { usage(); process.exit(2); }
  return opts;
}

function clampInt(value, min, max, fallback) {
  const n = Number.parseInt(value, 10);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(max, Math.max(min, n));
}

function isHttpUrl(value) {
  return /^https?:\/\//i.test(value);
}

function redactUrl(raw, show = false) {
  if (show) return raw;
  try {
    const u = new URL(raw);
    if (u.username) u.username = '***';
    if (u.password) u.password = '***';
    for (const key of [...u.searchParams.keys()]) {
      if (SENSITIVE_PARAMS.has(key.toLowerCase())) u.searchParams.set(key, '***');
    }
    const parts = u.pathname.split('/');
    for (let i = 0; i < parts.length; i++) {
      const current = (parts[i] || '').toLowerCase();
      // Xtream embeds username/password as the two segments after /live/,
      // /movie/, or /series/. Redact both.
      if (['live', 'movie', 'series'].includes(current)) {
        if (parts[i + 1]) parts[i + 1] = '***';
        if (parts[i + 2]) parts[i + 2] = '***';
      }
      if (parts[i].length >= 24 && /^[A-Za-z0-9._~-]+$/.test(parts[i]) && !parts[i].includes('.')) {
        parts[i] = '***';
      }
    }
    u.pathname = parts.join('/');
    return u.toString();
  } catch {
    return '<invalid-url>';
  }
}

function sameOrigin(a, b) {
  try { return new URL(a).origin === new URL(b).origin; } catch { return false; }
}

function headersFor(url, origin, extra = {}) {
  const headers = {
    'User-Agent': UA,
    Accept: 'application/x-mpegURL, application/vnd.apple.mpegurl, text/plain, */*',
    ...extra,
  };
  // Do not leak basic auth to a different origin after a redirect.
  if (origin && sameOrigin(url, origin.originalUrl)) {
    headers.Authorization = origin.authorization;
  }
  return headers;
}

async function fetchWithRedirects(rawUrl, { timeout, maxBytes, origin }) {
  let current = rawUrl;
  for (let i = 0; i < 6; i++) {
    const headers = headersFor(current, origin);
    const response = await fetch(current, {
      redirect: 'manual',
      headers,
      signal: AbortSignal.timeout(timeout),
    });
    if ([301, 302, 303, 307, 308].includes(response.status)) {
      const location = response.headers.get('location');
      if (!location) throw new Error(`HTTP ${response.status} without Location`);
      current = new URL(location, current).toString();
      continue;
    }
    const length = Number.parseInt(response.headers.get('content-length') || '0', 10);
    if (length > maxBytes) {
      throw new Error(`Body is ${(length / 1e6).toFixed(1)} MB, over --max-bytes (${(maxBytes / 1e6).toFixed(1)} MB)`);
    }
    return { response, finalUrl: current };
  }
  throw new Error('Too many redirects');
}

function decodeBody(buffer, contentType = '') {
  const charset = /charset=([^;]+)/i.exec(contentType)?.[1]?.trim().toLowerCase();
  const decode = (enc) => new TextDecoder(enc, { fatal: false }).decode(buffer);
  if (charset && charset !== 'utf-8' && charset !== 'utf8') {
    try { return decode(charset); } catch { /* fall through */ }
  }
  const utf8 = decode('utf-8');
  if (utf8.includes('\uFFFD')) {
    try { return decode('iso-8859-1'); } catch { return utf8; }
  }
  return utf8;
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xFEFF ? text.slice(1) : text;
}

function splitExtInf(line) {
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') inQuotes = !inQuotes;
    else if (c === ',' && !inQuotes) return [line.slice(0, i), line.slice(i + 1)];
  }
  return [line, ''];
}

function parseAttrs(head) {
  const attrs = {};
  const re = /([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"/g;
  let m;
  while ((m = re.exec(head))) attrs[m[1].toLowerCase()] = m[2];
  // Tolerate unquoted attributes as a fallback.
  const unquoted = /([A-Za-z0-9_-]+)\s*=\s*([^"\s]+)/g;
  while ((m = unquoted.exec(head))) {
    if (!(m[1].toLowerCase() in attrs)) attrs[m[1].toLowerCase()] = m[2];
  }
  return attrs;
}

function classify(url, group) {
  let path = '';
  try { path = new URL(url).pathname.toLowerCase(); } catch { path = url.toLowerCase(); }
  if (path.includes('/live/')) return 'live';
  if (path.includes('/movie/')) return 'movie';
  if (path.includes('/series/')) return 'series';
  const g = (group || '').toLowerCase();
  if (/(vod|pel[ií]cula|movie|cine|film)/.test(g)) return 'movie';
  if (/(serie|novela|anime|docuserie)/.test(g)) return 'series';
  if (/(tv|live|en vivo|directo|canal|deporte|futbol|fútbol|noticia|infantil|música|musica)/.test(g)) return 'live';
  return 'other';
}

function qualityTag(name) {
  const m = /(4K|UHD|FHD|HD|SD|HEVC|HDR|H\.265|H265)/i.exec(name || '');
  return m ? m[1].toUpperCase() : null;
}

function baseFor(url) {
  try { return new URL('.', url).toString(); } catch { return null; }
}

function parseM3u(text, baseUrl, limit, showUrls) {
  const lines = text.split(/\r?\n/);
  const stats = { entries: 0, live: 0, movie: 0, series: 0, other: 0, groups: 0, duplicates: 0, warnings: 0 };
  const groupCount = new Map();
  const nameCount = new Map();
  const sample = [];
  let firstRawUrl = null;
  let pending = null;
  let lineNo = 0;
  for (const raw of lines) {
    lineNo++;
    const line = raw.trim();
    if (!line) continue;
    if (line.startsWith('#EXTM3U')) continue;
    if (line.startsWith('#EXTINF:')) {
      const [head, nameRaw] = splitExtInf(line.slice(8));
      const attrs = parseAttrs(head);
      const name = (nameRaw || attrs['tvg-name'] || attrs['tvg-id'] || 'Canal sin nombre').trim();
      pending = {
        name,
        group: attrs['group-title'] || attrs['group'] || null,
        logo: attrs['tvg-logo'] || null,
        tvgId: attrs['tvg-id'] || null,
        tvgName: attrs['tvg-name'] || null,
        lineNo,
      };
      continue;
    }
    if (line.startsWith('#EXTGRP:')) {
      if (pending) pending.group = line.slice(8).trim() || pending.group;
      continue;
    }
    if (line.startsWith('#')) continue;
    if (!pending) { stats.warnings++; continue; }
    let url;
    try { url = baseUrl ? new URL(line, baseUrl).toString() : line; } catch { stats.warnings++; pending = null; continue; }
    if (!isHttpUrl(url)) { stats.warnings++; pending = null; continue; }
    const type = classify(url, pending.group);
    stats.entries++;
    stats[type]++;
    if (pending.group) groupCount.set(pending.group, (groupCount.get(pending.group) || 0) + 1);
    nameCount.set(pending.name, (nameCount.get(pending.name) || 0) + 1);
    if (!firstRawUrl) firstRawUrl = url;
    if (sample.length < limit) {
      sample.push({
        name: pending.name,
        group: pending.group,
        type,
        quality: qualityTag(pending.name),
        url: redactUrl(url, showUrls),
      });
    }
    pending = null;
  }
  stats.groups = groupCount.size;
  stats.duplicates = [...nameCount.values()].filter((n) => n > 1).reduce((a, n) => a + (n - 1), 0);
  const groupsTop = [...groupCount.entries()].sort((a, b) => b[1] - a[1]).slice(0, 10)
    .map(([name, count]) => ({ name, count }));
  const out = { stats, groupsTop, sample };
  Object.defineProperty(out, 'firstUrlRaw', { value: firstRawUrl, enumerable: false });
  return out;
}

function firstUri(lines, start) {
  for (let i = start; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line || line.startsWith('#')) continue;
    return line;
  }
  return null;
}

function parseHls(text, baseUrl, showUrls) {
  const lines = text.split(/\r?\n/);
  const isMaster = lines.some((l) => l.startsWith('#EXT-X-STREAM-INF'));
  const variants = [];
  let targetDuration = null;
  let encrypted = false;
  let endList = false;
  let segments = 0;
  let firstSegment = null;
  let pendingVariant = false;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;
    if (line.startsWith('#EXT-X-STREAM-INF')) { pendingVariant = true; continue; }
    if (pendingVariant && !line.startsWith('#')) {
      try { variants.push(new URL(line, baseUrl).toString()); } catch { /* ignore */ }
      pendingVariant = false;
      continue;
    }
    if (line.startsWith('#EXT-X-TARGETDURATION:')) targetDuration = Number(line.split(':')[1]) || null;
    if (line.startsWith('#EXT-X-KEY:')) encrypted = !/METHOD=NONE/i.test(line);
    if (line.startsWith('#EXT-X-ENDLIST')) endList = true;
    if (line.startsWith('#EXTINF:')) {
      segments++;
      if (!firstSegment) {
        const uri = firstUri(lines, i + 1);
        if (uri) {
          try { firstSegment = new URL(uri, baseUrl).toString(); } catch { /* ignore */ }
        }
      }
    }
  }
  const out = {
    kind: isMaster ? 'master' : 'media',
    variants: variants.map((v) => redactUrl(v, showUrls)),
    variantCount: variants.length,
    targetDuration,
    segments,
    encrypted,
    endList,
    firstSegment: firstSegment ? redactUrl(firstSegment, showUrls) : null,
  };
  // Keep the raw URL for an internal probe, but never serialize it.
  Object.defineProperty(out, 'firstSegmentRaw', { value: firstSegment, enumerable: false });
  return out;
}

async function probeBytes(url, opts, origin) {
  if (!url) return null;
  try {
    const response = await fetch(url, {
      redirect: 'follow',
      headers: headersFor(url, origin, { Range: 'bytes=0-1023' }),
      signal: AbortSignal.timeout(opts.timeout),
    });
    const contentType = response.headers.get('content-type') || '';
    const reader = response.body?.getReader();
    let bytes = 0;
    let first = null;
    if (reader) {
      const chunk = await reader.read();
      if (chunk.value) {
        bytes = chunk.value.byteLength;
        first = chunk.value;
        await reader.cancel().catch(() => {});
      }
    }
    return {
      status: response.status,
      contentType,
      bytesRead: bytes,
      tsSync: first && first.length > 0 ? first[0] === 0x47 : null,
      ftyp: first ? Buffer.from(first.slice(0, 12)).toString('latin1').includes('ftyp') : null,
    };
  } catch (error) {
    return { error: error.message };
  }
}

async function inspectHlsUrl(url, opts, origin, depth = 0) {
  const { response, finalUrl } = await fetchWithRedirects(url, {
    timeout: opts.timeout, maxBytes: opts.maxBytes, origin,
  });
  const buf = Buffer.from(await response.arrayBuffer());
  const text = stripBom(decodeBody(buf, response.headers.get('content-type') || ''));
  const hls = parseHls(text, finalUrl, opts.showUrls);
  const out = { status: response.status, finalUrl: redactUrl(finalUrl, opts.showUrls), ...hls };
  if (depth < 2 && hls.kind === 'master') {
    const raw = parseHls(text, finalUrl, true);
    const first = raw.variants[0];
    if (first) out.variant = await inspectHlsUrl(first, opts, origin, depth + 1);
  }
  if (out.variant?.stream) out.stream = out.variant.stream;
  else if (hls.firstSegmentRaw) out.stream = await probeBytes(hls.firstSegmentRaw, opts, origin);
  return out;
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  const result = {
    ok: false,
    source: opts.input,
    kind: 'unknown',
    sizeBytes: null,
    status: null,
    contentType: null,
    finalUrl: null,
    stats: null,
    groupsTop: [],
    sample: [],
    warnings: [],
    hls: null,
    stream: null,
  };

  let text = '';
  let baseUrl = null;
  let origin = null;

  if (isHttpUrl(opts.input)) {
    const u = new URL(opts.input);
    origin = { originalUrl: u.toString() };
    if (u.username || u.password) {
      origin.authorization = 'Basic ' + Buffer.from(
        `${decodeURIComponent(u.username)}:${decodeURIComponent(u.password)}`
      ).toString('base64');
      u.username = '';
      u.password = '';
    }
    const { response, finalUrl } = await fetchWithRedirects(u.toString(), {
      timeout: opts.timeout,
      maxBytes: opts.maxBytes,
      origin,
    });
    result.status = response.status;
    result.contentType = response.headers.get('content-type') || '';
    result.finalUrl = redactUrl(finalUrl, opts.showUrls);
    const buffer = Buffer.from(await response.arrayBuffer());
    result.sizeBytes = buffer.byteLength;
    if (buffer.byteLength > opts.maxBytes) throw new Error('Body over --max-bytes');
    text = stripBom(decodeBody(buffer, result.contentType));
    baseUrl = finalUrl;
  } else {
    const buffer = await readFile(opts.input);
    result.sizeBytes = buffer.byteLength;
    text = stripBom(decodeBody(buffer, ''));
    baseUrl = new URL(`file:///${opts.input.replace(/\\/g, '/')}`).toString();
    result.source = basename(opts.input);
  }

  const trimmed = text.trimStart().toLowerCase();
  if (trimmed.startsWith('<!doctype') || trimmed.startsWith('<html')) {
    result.kind = 'html';
    result.warnings.push('The source returned HTML, not a playlist. Check the URL, login, or proxy.');
  } else if (text.includes('#EXTM3U') || text.includes('#EXTINF:')) {
    result.kind = 'm3u';
    const parsed = parseM3u(text, baseUrl, opts.limit, opts.showUrls);
    Object.assign(result, parsed);
    if (opts.probeStream && parsed.firstUrlRaw) {
      const channelUrl = parsed.firstUrlRaw;
      const path = (() => { try { return new URL(channelUrl).pathname.toLowerCase(); } catch { return ''; } })();
      if (path.endsWith('.m3u8') || path.endsWith('.m3u')) {
        result.channelProbe = await inspectHlsUrl(channelUrl, opts, origin);
        if (result.channelProbe.stream) result.stream = result.channelProbe.stream;
      } else {
        result.stream = await probeBytes(channelUrl, opts, origin);
      }
    }
  } else if (text.includes('#EXT-X-')) {
    result.kind = 'hls';
    result.hls = parseHls(text, baseUrl, opts.showUrls);
    if (result.hls.kind === 'master' && result.hls.variants.length > 0) {
      // The public variants are redacted; re-parse to get the raw first variant.
      const raw = parseHls(text, baseUrl, true);
      const firstVariant = raw.variants[0];
      if (firstVariant && isHttpUrl(firstVariant)) {
        const { response, finalUrl } = await fetchWithRedirects(firstVariant, {
          timeout: opts.timeout, maxBytes: opts.maxBytes, origin,
        });
        const buf = Buffer.from(await response.arrayBuffer());
        const variantText = stripBom(decodeBody(buf, response.headers.get('content-type') || ''));
        result.hls.variantProbe = {
          status: response.status,
          finalUrl: redactUrl(finalUrl, opts.showUrls),
          ...parseHls(variantText, finalUrl, opts.showUrls),
        };
      }
    }
    if (opts.probeStream && result.hls.firstSegmentRaw) {
      result.stream = await probeBytes(result.hls.firstSegmentRaw, opts, origin);
    }
  } else {
    result.kind = 'text';
    result.warnings.push('No #EXTM3U, #EXTINF, or #EXT-X markers found. This does not look like a playlist.');
    if (opts.probeStream && isHttpUrl(opts.input)) {
      result.stream = await probeBytes(opts.input, opts, origin);
    }
  }

  result.ok = result.kind === 'hls' || (result.kind === 'm3u' && result.stats?.entries > 0);

  if (opts.json) {
    console.log(JSON.stringify(result, null, 2));
  } else {
    printHuman(result, opts);
  }
  process.exit(result.ok ? 0 : 1);
}

function printHuman(r, opts) {
  const size = r.sizeBytes == null ? '?' : `${(r.sizeBytes / 1e6).toFixed(2)} MB`;
  console.log(`Source:  ${r.source}`);
  if (r.finalUrl && r.finalUrl !== r.source) console.log(`Final:   ${r.finalUrl}`);
  console.log(`Kind:    ${r.kind}`);
  console.log(`Size:    ${size}`);
  if (r.status) console.log(`HTTP:    ${r.status} ${r.contentType || ''}`.trim());
  if (r.stats) {
    const s = r.stats;
    console.log(`Entries: ${s.entries} (live ${s.live}, movie ${s.movie}, series ${s.series}, other ${s.other})`);
    console.log(`Groups:  ${s.groups}`);
    console.log(`Dupes:   ${s.duplicates}`);
    console.log(`Warnings:${s.warnings}`);
    if (r.groupsTop.length) {
      console.log('Top groups:');
      for (const g of r.groupsTop) console.log(`  ${String(g.count).padStart(6)}  ${g.name}`);
    }
    if (r.sample.length) {
      console.log(`Sample (${r.sample.length}):`);
      r.sample.forEach((c, i) => {
        console.log(`  ${i + 1}. ${c.name} [${c.type}]${c.group ? ` group=${c.group}` : ''}`);
        if (c.url) console.log(`     ${c.url}`);
      });
    }
  }
  if (r.hls) {
    console.log(`HLS:     ${r.hls.kind}, variants=${r.hls.variantCount}, segments=${r.hls.segments}, target=${r.hls.targetDuration ?? '?'}s, encrypted=${r.hls.encrypted}, vod=${r.hls.endList}`);
    if (r.hls.firstSegment) console.log(`Segment: ${r.hls.firstSegment}`);
    if (r.hls.variantProbe) {
      const v = r.hls.variantProbe;
      console.log(`Variant: HTTP ${v.status}, segments=${v.segments}, target=${v.targetDuration ?? '?'}s`);
    }
  }
  if (r.channelProbe) {
    const c = r.channelProbe;
    console.log(`Channel: HTTP ${c.status}, ${c.kind}, segments=${c.segments}, target=${c.targetDuration ?? '?'}s, encrypted=${c.encrypted}, vod=${c.endList}`);
    if (c.variant) console.log(`Variant: HTTP ${c.variant.status}, ${c.variant.kind}, segments=${c.variant.segments}, target=${c.variant.targetDuration ?? '?'}s`);
    if (c.firstSegment) console.log(`Seg 1:   ${c.firstSegment}`);
    if (c.variant?.firstSegment) console.log(`Seg 1:   ${c.variant.firstSegment}`);
  }
  if (r.stream) {
    if (r.stream.error) console.log(`Stream:  error: ${r.stream.error}`);
    else console.log(`Stream:  HTTP ${r.stream.status}, ${r.stream.contentType}, ${r.stream.bytesRead} bytes, TS sync=${r.stream.tsSync}`);
  }
  if (r.warnings.length) {
    console.log('Warnings:');
    for (const w of r.warnings) console.log(`  - ${w}`);
  }
  if (!r.ok) console.log('Result:  not a usable playlist or stream');
  if (opts.showUrls) console.log('WARNING: --show-urls printed real URLs that may contain credentials.');
}

main().catch((error) => {
  console.error(`probe failed: ${error.message}`);
  process.exit(1);
});
