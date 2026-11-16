#!/usr/bin/env node
// Fake Xtream panel for QA of catalogue sorting, filters and detail metadata.
// No dependencies, no real data, no credentials: any username/password works and
// this script never reads a credentials file. Titles and categories are invented.
//
// Usage:
//   node scripts/fake-xtream-panel.mjs --variant <measured|rich|bare|malformed>
//        [--port 8788] [--scale N]
//
// Variants (the golden rule: the app must work with ANY panel):
//   measured   fields filled like one real account: bulk-import `added`, integer
//              ratings, series genres mixing English/Spanish with irregular `/`
//              spacing, empty `releaseDate`, `LANG - Genre` categories.
//   rich       release dates under rotating names (releasedate / release_date /
//              year), comma genres, `added` in milliseconds, finer ratings,
//              bracketed category prefixes.
//   bare       only name, id, icon and category. No metadata anywhere.
//   malformed  garbage in every field (see MALFORMED below).
//
// --scale N multiplies the base sizes (1,400 movies / 240 series): --scale 10
// gives the sizes of a large real account.
// From the Android emulator the panel is http://10.0.2.2:<port> (override the
// host the M3U advertises with FAKE_XTREAM_HOST). Stream URLs answer 404 on
// purpose, which exercises the player's actionable error. Stop with Ctrl+C.
import http from 'node:http';
import zlib from 'node:zlib';

// -------------------------------------------------------------------- args
const args = process.argv.slice(2);
const opt = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && i + 1 < args.length ? args[i + 1] : fallback;
};
const variant = opt('variant', 'measured');
if (!['measured', 'rich', 'bare', 'malformed'].includes(variant)) {
  console.error('--variant must be measured, rich, bare or malformed');
  process.exit(2);
}
const port = Number(opt('port', 8788));
const scale = Math.max(1, Number(opt('scale', 1)) || 1);
const host = process.env.FAKE_XTREAM_HOST || '10.0.2.2';
const base = `http://${host}:${port}`;
const MOVIES = 1400 * scale;
const SERIES = 240 * scale;

// ------------------------------------------------------------ seeded random
function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rand = mulberry32(20241107);
const pick = (list) => list[Math.floor(rand() * list.length)];
const pad = (n, w = 5) => String(n).padStart(w, '0');

const NOW = Math.floor(Date.UTC(2024, 10, 7) / 1000);
const DAY = 86400;

// --------------------------------------------------------------- categories
const ES = ['Acción', 'Comedia', 'Drama', 'Terror', 'Familia', 'Crimen', 'Aventura', 'Documental'];
const EN = ['Action', 'Comedy', 'Drama', 'Horror', 'Family', 'Crime', 'Adventure'];

const CATEGORIES = {
  measured: {
    movies: [...ES.slice(0, 7).map((g) => `ESPAÑA - ${g}`), ...EN.map((g) => `ENGLISH - ${g}`)],
    series: [...ES.map((g) => `ESPAÑA - ${g}`), ...EN.map((g) => `ENGLISH - ${g}`)],
  },
  rich: {
    movies: ['|ES| Drama', '|ES| Comedia', '|EN| Drama', '|EN| Comedy', '|FR| Comédie', '[DE] Krimi', '[DE] Komödie', 'VOD 4K'],
    series: ['|ES| Drama', '|ES| Comedia', '|EN| Drama', '|EN| Comedy', '|FR| Comédie', '[DE] Krimi', 'VOD 4K'],
  },
  bare: {
    movies: ['Acción', 'Comedia', 'Drama'],
    series: ['Acción', 'Comedia', 'Drama'],
  },
  malformed: {
    // `null` is a category without a name; the rest are names that must not be
    // mistaken for a language prefix (digits, an unspaced hyphen, a long token).
    movies: [null, 'NETFLIX - Series', '4K - Movies', 'Sci-Fi', 'Star Wars: Saga', 'ES/EN - Dual'],
    series: [null, 'NETFLIX - Series', '4K - Movies', 'Sci-Fi', 'Star Wars: Saga', 'ES/EN - Dual'],
  },
};
const categoryList = (names) =>
  names.map((name, i) => ({ category_id: String(i + 1), category_name: name, parent_id: 0 }));

// -------------------------------------------------------------------- data
const GENRES_SERIES_MEASURED = [
  'Crime /  Drama / Thriller', 'Comedy', 'Comedia', 'Action & Adventure', 'Crimen / Drama',
  'Documentary', 'Documental', 'Mystery /  Drama', 'Misterio', 'Animation / Family', 'Animación',
];
const GENRES_RICH = ['Action, Comedy', 'Drama', 'Horror, Thriller', 'Sci-Fi & Fantasy', 'Animación', 'Comedy, Romance'];
const RATINGS_MALFORMED = ['N/A', '7,4', [], {}, -1, 85, 'abc', '8'];
const ADDED_MALFORMED = ['', '0', NOW * 1000, '2023-12-05 10:00:00', 99999999999999, 'abc', [], null];
const RELEASE_MALFORMED = ['0000-00-00', '19/10/2016', '10/19/2016', '05/06/2016', 2016, '1800', '2999', {}];
const GENRE_MALFORMED = [['Drama', 'Crime'], 'Action & Adventure', 'Sci-Fi & Fantasy', 'x'.repeat(60), '123', null, {}, 'Drama'];

const catCount = (kind) => CATEGORIES[variant][kind].length;

function buildMovies() {
  const items = [];
  if (variant === 'measured') {
    // Two bulk-import days (55% and 38%), the rest spread over 11 other days.
    const days = Array.from({ length: 13 }, (_, i) => NOW - (400 - i * 3) * DAY);
    for (let i = 0; i < MOVIES; i++) {
      const r = rand();
      const day = r < 0.55 ? days[0] : r < 0.93 ? days[1] : days[2 + Math.floor(rand() * 11)];
      items.push({ added: day + Math.floor(rand() * 40000), rating: rand() < 0.03 ? '' : String(1 + Math.floor(rand() * 10)) });
    }
    items.sort((a, b) => b.added - a.added); // newest first, like the provider order
  } else {
    for (let i = 0; i < MOVIES; i++) items.push({});
  }
  return items.map((extra, idx) => {
    const id = idx + 1;
    const category = String((idx % catCount('movies')) + 1);
    const name = `Película de prueba ${pad(id)}`;
    const common = { num: id, name, stream_type: 'movie', stream_id: id, category_id: category, container_extension: 'mp4' };
    switch (variant) {
      case 'measured':
        return {
          ...common,
          stream_icon: `${base}/poster.png`,
          added: String(extra.added),
          rating: extra.rating,
          rating_5based: extra.rating === '' ? 0 : Number(extra.rating) / 2,
          tmdb_id: String(100000 + id),
        };
      case 'rich': {
        const year = 1985 + Math.floor(rand() * 40);
        const date = `${year}-${pad(1 + Math.floor(rand() * 12), 2)}-${pad(1 + Math.floor(rand() * 28), 2)}`;
        const rotate = idx % 3;
        const rating = Math.floor(rand() * 10) + 1;
        return {
          ...common,
          stream_icon: `${base}/poster.png`,
          // Numbers and strings are both seen in the wild.
          stream_id: idx % 2 ? String(id) : id,
          added: (NOW - Math.floor(rand() * 400) * DAY) * 1000,
          rating: idx % 2 ? rating : String(rating),
          rating_5based: Math.min(5, Math.round(rating * 5) / 10 + (idx % 5) / 20),
          ...(rotate === 0 ? { releasedate: date } : rotate === 1 ? { release_date: date } : { year: idx % 2 ? year : String(year) }),
          genre: pick(GENRES_RICH),
        };
      }
      case 'bare':
        return { name, stream_id: id, stream_icon: '', category_id: category };
      case 'malformed': {
        const m = {
          name: id === 9 ? null : name,
          stream_id: id === 5 ? 6 : id === 7 ? 'abc' : id, // a duplicate id and an unusable id
          stream_icon: null,
          category_id: idx % 4 === 0 ? Number(category) : category, // a numeric category id
          rating: RATINGS_MALFORMED[idx % RATINGS_MALFORMED.length],
          added: ADDED_MALFORMED[idx % ADDED_MALFORMED.length],
          releasedate: RELEASE_MALFORMED[idx % RELEASE_MALFORMED.length],
          genre: GENRE_MALFORMED[idx % GENRE_MALFORMED.length],
        };
        return m;
      }
    }
  });
}

function buildSeries() {
  const out = [];
  for (let i = 0; i < SERIES; i++) {
    const id = i + 1;
    const category = String((i % catCount('series')) + 1);
    const name = `Serie de prueba ${pad(id)}`;
    switch (variant) {
      case 'measured':
        out.push({
          num: id, name, series_id: id, cover: `${base}/poster.png`, plot: `Trama inventada de la serie ${id}.`,
          cast: 'Persona Uno, Persona Dos', director: '', genre: pick(GENRES_SERIES_MEASURED),
          releaseDate: '', last_modified: String(NOW - (390 - (i % 5)) * DAY),
          rating: rand() < 0.05 ? '' : String(1 + Math.floor(rand() * 10)),
          episode_run_time: rand() < 0.04 ? '' : String(25 + 5 * Math.floor(rand() * 8)),
          category_id: category,
        });
        break;
      case 'rich': {
        const year = 1990 + Math.floor(rand() * 34);
        const rotate = i % 3;
        out.push({
          num: id, name, series_id: id, cover: `${base}/poster.png`, plot: `Trama inventada ${id}.`,
          genre: pick(GENRES_RICH), rating: String(1 + Math.floor(rand() * 10)),
          ...(rotate === 0 ? { releaseDate: `${year}-05-01` } : rotate === 1 ? { release_date: String(year) } : { year }),
          episode_run_time: 20 + Math.floor(rand() * 40), category_id: category,
        });
        break;
      }
      case 'bare':
        out.push({ name, series_id: id, category_id: category });
        break;
      case 'malformed':
        out.push({
          name, series_id: id, category_id: category,
          rating: RATINGS_MALFORMED[i % RATINGS_MALFORMED.length],
          releaseDate: RELEASE_MALFORMED[i % RELEASE_MALFORMED.length],
          genre: GENRE_MALFORMED[i % GENRE_MALFORMED.length],
          episode_run_time: i % 2 ? '2700' : '45 min',
        });
        break;
    }
  }
  return out;
}

const movies = buildMovies();
const series = buildSeries();

// ----------------------------------------------------------- series detail
function seriesInfo(id) {
  const seasons = 3;
  const episodes = {};
  const year0 = 2010 + (id % 8);
  if (variant === 'malformed' && id === 1) {
    return { info: { name: `Serie de prueba ${pad(id)}` }, episodes: [] }; // an empty series
  }
  for (let s = 1; s <= seasons; s++) {
    episodes[String(s)] = Array.from({ length: 4 }, (_, e) => {
      const epId = id * 1000 + s * 100 + e + 1;
      const ep = { id: String(epId), episode_num: e + 1, title: `Serie de prueba ${pad(id)} - S0${s}E0${e + 1} - Episodio ${e + 1}`, container_extension: 'mp4', season: s };
      if (variant === 'measured' || variant === 'rich') {
        // The episodes' own release date; `added` is a bulk-import stamp.
        ep.info = { releaseDate: `${year0 + (s - 1) * 2}-0${(e % 9) + 1}-1${e}` };
        ep.added = String(NOW - 380 * DAY);
      }
      return ep;
    });
  }
  const info = { name: `Serie de prueba ${pad(id)}`, plot: `Trama inventada ${id}.`, cover: `${base}/poster.png` };
  return {
    info: variant === 'malformed' && id === 2 ? JSON.stringify(info) : info, // `info` as a string
    seasons: Array.from({ length: seasons }, (_, i) => ({ season_number: i + 1 })),
    episodes,
  };
}

// ------------------------------------------------------------- movie detail
function vodInfoReply(id) {
  const genre = variant === 'measured' ? pick(['Action', 'Acción', 'Drama', 'Comedy']) : 'Drama, Crime';
  const info = {
    releasedate: `${1990 + (id % 33)}-10-19`,
    duration_secs: 3600 + (id % 60) * 60,
    duration: '01:52:00',
    genre,
    director: 'Nombre Apellido',
    cast: 'Actor Uno, Actor Dos, Actor Tres, Actor Cuatro',
    plot: `Argumento inventado de la película ${id}. No describe ningún contenido real.`,
    rating: String(1 + (id % 10)),
    movie_image: `${base}/poster.png`,
  };
  const movieData = { stream_id: id, name: `Nombre del proveedor ${id}`, container_extension: 'mkv' };
  if (variant === 'bare') return { status: 200, body: { info: [], movie_data: movieData } };
  if (variant !== 'malformed') return { status: 200, body: { info, movie_data: movieData } };
  switch (id % 7) {
    case 0: return { status: 200, body: { info, movie_data: movieData } };
    case 1: return { status: 200, body: { info: [], movie_data: movieData } };
    case 2: return { status: 200, body: { info: JSON.stringify(info), movie_data: movieData } };
    case 3: return { status: 500, body: 'Internal Server Error', type: 'text/plain' };
    case 4: return { status: 200, body: '<html><body>Panel</body></html>', type: 'text/html' };
    case 5: return { status: 200, body: '{"info": {"plot": ', type: 'application/json' };
    default: return { status: 200, body: { info, movie_data: movieData }, delayMs: 40000 }; // times out
  }
}

// ---------------------------------------------------------------- M3U text
function m3u() {
  const lines = ['#EXTM3U'];
  for (let i = 1; i <= 20; i++) {
    lines.push(`#EXTINF:-1 tvg-id="live${i}" tvg-name="Canal de prueba ${i}" tvg-logo="" group-title="TV",Canal de prueba ${i}`);
    lines.push(`${base}/live/user/pass/${i}.m3u8`);
  }
  for (const m of movies) {
    const id = Number(m.stream_id);
    if (!Number.isFinite(id) || id <= 0) continue;
    const group = CATEGORIES[variant].movies[Number(m.category_id) - 1] ?? 'VOD';
    lines.push(`#EXTINF:-1 tvg-id="${id}" tvg-name="${m.name ?? 'Sin nombre'}" tvg-logo="" group-title="${group}",${m.name ?? 'Sin nombre'}`);
    lines.push(`${base}/movie/user/pass/${id}.m3u8`);
  }
  return lines.join('\n') + '\n';
}

// ------------------------------------------------------------------- PNG
const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
const crc32 = (buf) => {
  let c = 0xffffffff;
  for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type, data) => {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
};
function posterPng() {
  const w = 200, h = 300;
  const raw = Buffer.alloc((w * 3 + 1) * h);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const on = Math.floor((x + y) / 20) % 2 === 0;
      const o = y * (w * 3 + 1) + 1 + x * 3;
      raw[o] = on ? 30 : 12; raw[o + 1] = on ? 120 : 60; raw[o + 2] = on ? 110 : 90;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0)),
  ]);
}
const POSTER = posterPng();

// ------------------------------------------------------------------ server
const stamp = () => new Date().toISOString().slice(11, 19);
const json = (res, body, status = 200, type = 'application/json') => {
  const payload = typeof body === 'string' ? body : JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': type, 'Content-Length': Buffer.byteLength(payload) });
  res.end(payload);
};

http.createServer((req, res) => {
  const url = new URL(req.url, base);
  const path = url.pathname;

  if (path === '/poster.png') {
    res.writeHead(200, { 'Content-Type': 'image/png', 'Content-Length': POSTER.length });
    return res.end(POSTER);
  }
  if (path === '/get.php') {
    console.log(stamp(), 'get.php');
    const body = m3u();
    res.writeHead(200, { 'Content-Type': 'audio/mpegurl', 'Content-Length': Buffer.byteLength(body) });
    return res.end(body);
  }
  if (path === '/player_api.php') {
    const action = url.searchParams.get('action') || 'user_info';
    // Only the action and numeric ids are logged: never the query string.
    const ids = ['vod_id', 'series_id'].map((k) => url.searchParams.get(k)).filter((v) => /^\d+$/.test(v ?? ''));
    console.log(stamp(), action, ids.join(' '));
    switch (action) {
      case 'user_info':
        return json(res, { user_info: { auth: 1, status: 'Active' }, server_info: { url: host, port: String(port) } });
      case 'get_vod_categories': return json(res, categoryList(CATEGORIES[variant].movies));
      case 'get_series_categories': return json(res, categoryList(CATEGORIES[variant].series));
      case 'get_vod_streams': return json(res, movies);
      case 'get_series': return json(res, series);
      case 'get_series_info': return json(res, seriesInfo(Number(url.searchParams.get('series_id')) || 0));
      case 'get_vod_info': {
        const reply = vodInfoReply(Number(url.searchParams.get('vod_id')) || 0);
        const send = () => json(res, reply.body, reply.status, reply.type ?? 'application/json');
        return reply.delayMs ? setTimeout(send, reply.delayMs) : send();
      }
      default: return json(res, []);
    }
  }
  const stream = path.match(/^\/(live|movie|series)\/[^/]+\/[^/]+\/(\d+)\.\w+$/);
  if (stream) {
    // Credentials in the path are never logged; the player shows its own error.
    console.log(stamp(), `/${stream[1]}/***/***/${stream[2]}`);
    return json(res, 'Not found', 404, 'text/plain');
  }
  return json(res, 'Not found', 404, 'text/plain');
}).listen(port, '127.0.0.1', () => {
  console.log(`fake Xtream panel [${variant}] x${scale}: ${MOVIES} movies, ${SERIES} series`);
  console.log(`listening on :${port}; from the emulator use ${base} with any username/password`);
});
