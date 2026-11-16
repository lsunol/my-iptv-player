#!/usr/bin/env node
// Fake Xtream panel for QA of catalogue artwork (no dependencies, no real data).
//
// It reproduces the artwork shapes seen on real panels:
//   - a PHP-serialized relative path leaked into the JSON: `s:NN:/images/a.png`
//   - a root-relative path: `/images/c.png`
//   - an absolute URL on the panel itself
//   - an absolute URL on a host that refuses connections (a firewalled
//     Plex `:32400` server on the real account)
//   - an empty cover
// Every image is a generated PNG with a label-free colour pattern, so a
// screenshot shows at a glance which shapes the app managed to load.
//
// Usage: node scripts/fake-xtream-artwork.mjs [port]   (default 8787)
// From the Android emulator the server is http://10.0.2.2:<port>, any
// username/password. Stop with Ctrl+C.
import http from 'node:http';
import net from 'node:net';
import zlib from 'node:zlib';

const port = Number(process.argv[2] || 8787);
const hostForClients = process.env.FAKE_XTREAM_HOST || '10.0.2.2';
const base = `http://${hostForClients}:${port}`;

// A port that is guaranteed closed: bind, read the number, release it.
const refusedPort = await new Promise((resolve) => {
  const s = net.createServer().listen(0, '127.0.0.1', () => {
    const p = s.address().port;
    s.close(() => resolve(p));
  });
});

// ---------------------------------------------------------------- PNG maker
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
/** 200x300 poster: diagonal stripes in two colours. */
function poster([r1, g1, b1], [r2, g2, b2]) {
  const w = 200, h = 300;
  const raw = Buffer.alloc((w * 3 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 3 + 1)] = 0;
    for (let x = 0; x < w; x++) {
      const on = Math.floor((x + y) / 20) % 2 === 0;
      const o = y * (w * 3 + 1) + 1 + x * 3;
      raw[o] = on ? r1 : r2; raw[o + 1] = on ? g1 : g2; raw[o + 2] = on ? b1 : b2;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 2; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0)),
  ]);
}
const images = {
  '/images/php-serialized.png': poster([46, 204, 113], [20, 90, 50]),   // green
  '/images/root-relative.png': poster([155, 89, 182], [60, 30, 80]),    // purple
  '/art/absolute.png': poster([243, 156, 18], [120, 70, 0]),            // orange
};

// ---------------------------------------------------------------- catalogue
const php = (s) => `s:${Buffer.byteLength(s)}:${s}`;
const series = [
  { series_id: 101, name: 'A · PHP serializada', cover: php('/images/php-serialized.png') },
  { series_id: 102, name: 'B · Ruta relativa', cover: '/images/root-relative.png' },
  { series_id: 103, name: 'C · URL absoluta', cover: `${base}/art/absolute.png` },
  { series_id: 104, name: 'D · Servidor caído', cover: `http://${hostForClients}:${refusedPort}/photo/x.jpg` },
  { series_id: 105, name: 'E · Sin portada', cover: '' },
].map((s) => ({ ...s, plot: 'Serie de prueba', category_id: '1', rating: 7 }));
const movies = [
  { stream_id: 201, name: 'Peli PHP serializada', stream_icon: php('/images/php-serialized.png'), category_id: '2' },
  { stream_id: 202, name: 'Peli absoluta', stream_icon: `${base}/art/absolute.png`, category_id: '2' },
];

function m3u(user, pass) {
  const lines = ['#EXTM3U'];
  lines.push('#EXTINF:-1 tvg-id="" tvg-name="Canal prueba" group-title="Prueba",Canal prueba',
    `${base}/live/${user}/${pass}/1.m3u8`);
  for (const m of movies) {
    lines.push(`#EXTINF:-1 tvg-name="${m.name}" group-title="Pelis",${m.name}`,
      `${base}/movie/${user}/${pass}/${m.stream_id}.m3u8`);
  }
  for (const s of series) {
    const epId = s.series_id * 10 + 1;
    lines.push(`#EXTINF:-1 tvg-name="${s.name} S01 E01" group-title="Series",${s.name} S01 E01`,
      `${base}/series/${user}/${pass}/${epId}.m3u8`);
  }
  return lines.join('\n') + '\n';
}

function seriesInfo(id) {
  const s = series.find((x) => x.series_id === id);
  if (!s) return { seasons: [], info: {}, episodes: {} };
  return {
    seasons: [{ season_number: 1, name: 'Temporada 1', cover: '' }],
    info: { name: s.name, cover: s.cover, plot: s.plot },
    episodes: { 1: [{ id: String(id * 10 + 1), episode_num: 1, title: `${s.name} - S01E01 - Piloto`,
      container_extension: 'mkv', info: { movie_image: s.cover } }] },
  };
}

const json = (res, body) => { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(body)); };

http.createServer((req, res) => {
  const url = new URL(req.url, base);
  const q = url.searchParams;
  console.log(new Date().toISOString().slice(11, 19), req.method, url.pathname, q.get('action') || '');
  if (images[url.pathname]) {
    res.writeHead(200, { 'Content-Type': 'image/png' });
    return res.end(images[url.pathname]);
  }
  if (url.pathname === '/get.php') {
    res.writeHead(200, { 'Content-Type': 'audio/x-mpegurl' });
    return res.end(m3u(q.get('username'), q.get('password')));
  }
  if (url.pathname === '/player_api.php') {
    switch (q.get('action')) {
      case 'get_series': return json(res, series);
      case 'get_series_categories': return json(res, [{ category_id: '1', category_name: 'Prueba portadas' }]);
      case 'get_vod_streams': return json(res, movies);
      case 'get_vod_categories': return json(res, [{ category_id: '2', category_name: 'Prueba pelis' }]);
      case 'get_series_info': return json(res, seriesInfo(Number(q.get('series_id'))));
      default: return json(res, { user_info: { auth: 1, status: 'Active' }, server_info: {} });
    }
  }
  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('not found');
}).listen(port, () => {
  console.log(`fake Xtream panel on ${base} (refused artwork port ${refusedPort})`);
});
