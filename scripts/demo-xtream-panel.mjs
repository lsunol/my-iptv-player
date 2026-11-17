#!/usr/bin/env node
// Demo Xtream panel for the website screenshots. Everything it serves is legal
// to show: Blender Foundation open movies (CC BY) and public HLS test streams.
// No dependencies, no real provider, no credentials (any user/pass works).
//
//   node scripts/demo-xtream-panel.mjs [--port 8789]
//
// From the Android emulator: server http://10.0.2.2:8789, any user/password.
// Plain M3U variant of the same list: http://10.0.2.2:8789/demo.m3u
// Local video (optional, git-ignored): the real streams are low-res test patterns, so for a
// sharp player screenshot download the Sintel trailer and cut it into HLS:
//   curl -L -o scripts/demo-assets/video/sintel.mp4 https://download.blender.org/durian/trailer/sintel_trailer-1080p.mp4
//   cd scripts/demo-assets/video && mkdir sintel && ffmpeg -i sintel.mp4 -c copy -f hls -hls_time 4 \
//     -hls_playlist_type vod -hls_segment_filename 'sintel/seg%03d.ts' sintel/index.m3u8
// Posters: scripts/demo-assets/posters (Wikimedia Commons, CC BY 3.0/4.0,
// © Blender Foundation | blender.org). Stop with Ctrl+C.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const args = process.argv.slice(2);
const port = Number(args[args.indexOf('--port') + 1]) || 8789;
const host = process.env.DEMO_HOST || '10.0.2.2';
const base = `http://${host}:${port}`;
const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), 'demo-assets', 'posters');

// The only real streams: public HLS test streams (Big Buck Bunny, Tears of Steel, Apple's bipbop).
const STREAMS = {
  // Local HLS of the Sintel trailer (CC BY 3.0, © Blender Foundation); see "Local video" above.
  sintel: `${base}/hls/sintel/index.m3u8`,
  bbb: `${base}/hls/bbb/index.m3u8`, // local: a Big Buck Bunny frame as a still video (CC BY 3.0)
  tos: 'https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8',
  bipbop: 'https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8',
  mux: 'https://test-streams.mux.dev/test_001/stream.m3u8',
};

const DAY = 86400;
const NOW = Math.floor(Date.now() / 1000);
const CHANNELS = [
  ['Sintel HD', 'ESPAÑA - Animación', 'sintel'], ['Sintel FHD', 'ESPAÑA - Animación', 'sintel'],
  ['Big Buck Bunny HD', 'ESPAÑA - Animación', 'bbb'], ['Big Buck Bunny FHD', 'ESPAÑA - Animación', 'bbb'],
  ['Tears of Steel HD', 'ESPAÑA - Ciencia ficción', 'tos'], ['Tears of Steel HEVC', 'ESPAÑA - Ciencia ficción', 'tos'],
  ['Elephants Dream', 'ESPAÑA - Cortometrajes', 'bbb'], ['Cosmos Laundromat', 'ESPAÑA - Cortometrajes', 'tos'],
  ['Coffee Run', 'ESPAÑA - Cortometrajes', 'bbb'], ['Charge SD', 'ESPAÑA - Cortometrajes', 'mux'],
  ['Apple BipBop', 'DEMO - Pruebas HLS', 'bipbop'], ['Mux Test Stream', 'DEMO - Pruebas HLS', 'mux'],
  ['Mux Test Stream HD', 'DEMO - Pruebas HLS', 'bbb'], ['Open Movies Mix', 'DEMO - Internacional', 'bbb'],
  ['Blender Studio', 'DEMO - Internacional', 'tos'], ['Open Source Cinema', 'DEMO - Internacional', 'mux'],
];
const MOVIE_CATS = [{ category_id: '1', category_name: 'Animación' }, { category_id: '2', category_name: 'Ciencia ficción' }, { category_id: '3', category_name: 'Cortometrajes' }];
const MOVIES = [
  ['Sintel', 'sintel', 2010, '1', 7.6, 'sintel', 'Una joven guerrera busca a su dragón perdido.'],
  ['Big Buck Bunny', 'bbb', 2008, '1', 7.1, 'bbb', 'Un conejo gigante se enfrenta a tres roedores.'],
  ['Tears of Steel', 'tos', 2012, '2', 6.4, 'tos', 'Un grupo de guerreros y científicos intenta salvar el mundo.'],
  ['Elephants Dream', 'ed', 2006, '1', 5.8, 'bbb', 'Dos extraños personajes exploran una máquina inmensa.'],
  ['Cosmos Laundromat', 'cosmos', 2015, '3', 7.2, 'tos', 'Una oveja deprimida recibe una oferta inesperada.'],
  ['Spring', 'spring', 2019, '3', 8.0, 'bbb', 'Una pastora y su perro se enfrentan a los espíritus.'],
  ['Coffee Run', 'coffee', 2020, '3', 7.0, 'mux', 'Una carrera por un café con consecuencias cósmicas.'],
  ['Charge', 'charge', 2022, '2', 7.3, 'tos', 'Un hombre con una misión en un futuro incierto.'],
].map(([name, poster, year, cat, rating, stream, plot], i) => ({
  num: i + 1, name: `${name} (${year})`, stream_type: 'movie', stream_id: i + 1, stream_icon: `${base}/poster/${poster}.jpg`,
  category_id: cat, container_extension: 'm3u8', rating: String(rating), rating_5based: rating / 2,
  added: String(NOW - (i * 9 + 3) * DAY), year: String(year), releasedate: `${year}-06-01`, genre: ['Animación', 'Ciencia ficción', 'Cortometraje'][Number(cat) - 1],
  _stream: stream, _plot: plot,
}));
const pub = (m) => Object.fromEntries(Object.entries(m).filter(([k]) => !k.startsWith('_')));

const m3u = () => {
  const out = ['#EXTM3U'];
  CHANNELS.forEach(([name, group], i) => {
    out.push(`#EXTINF:-1 tvg-id="live${i + 1}" tvg-name="${name}" tvg-logo="" group-title="${group}",${name}`, `${base}/live/demo/demo/${i + 1}.m3u8`);
  });
  for (const m of MOVIES) {
    const group = MOVIE_CATS[Number(m.category_id) - 1].category_name;
    out.push(`#EXTINF:-1 tvg-id="${m.stream_id}" tvg-name="${m.name}" tvg-logo="${m.stream_icon}" group-title="${group}",${m.name}`, `${base}/movie/demo/demo/${m.stream_id}.m3u8`);
  }
  return out.join('\n') + '\n';
};
// The plain-M3U list: direct public URLs, no panel.
const plainM3u = () => ['#EXTM3U', ...CHANNELS.flatMap(([name, group, s], i) => [`#EXTINF:-1 tvg-id="demo${i}" tvg-name="${name}" tvg-logo="" group-title="${group}",${name}`, STREAMS[s]])].join('\n') + '\n';

const json = (res, body, status = 200) => { const s = JSON.stringify(body); res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(s) }); res.end(s); };
const redirect = (res, url) => { res.writeHead(302, { Location: url }); res.end(); };

http.createServer((req, res) => {
  const url = new URL(req.url, base);
  const p = url.pathname;
  let m;
  if ((m = p.match(/^\/poster\/(\w+)\.jpg$/))) {
    const file = path.join(dir, `${m[1]}.jpg`);
    if (!fs.existsSync(file)) { res.writeHead(404); return res.end(); }
    const buf = fs.readFileSync(file);
    res.writeHead(200, { 'Content-Type': 'image/jpeg', 'Content-Length': buf.length });
    return res.end(buf);
  }
  if ((m = p.match(/^\/hls\/(\w+)\/([\w.]+)$/))) {
    const file = path.join(path.dirname(dir), 'video', m[1], m[2]);
    if (!fs.existsSync(file)) { res.writeHead(404); return res.end(); }
    const buf = fs.readFileSync(file);
    res.writeHead(200, { 'Content-Type': m[2].endsWith('.m3u8') ? 'application/vnd.apple.mpegurl' : 'video/mp2t', 'Content-Length': buf.length });
    return res.end(buf);
  }
  if (p === '/get.php' || p === '/demo.m3u') {
    const body = p === '/demo.m3u' ? plainM3u() : m3u();
    res.writeHead(200, { 'Content-Type': 'audio/mpegurl', 'Content-Length': Buffer.byteLength(body) });
    return res.end(body);
  }
  if (p === '/player_api.php') {
    switch (url.searchParams.get('action') || 'user_info') {
      case 'user_info': return json(res, { user_info: { auth: 1, status: 'Active' }, server_info: { url: host, port: String(port) } });
      case 'get_vod_categories': return json(res, MOVIE_CATS);
      case 'get_series_categories': return json(res, []);
      case 'get_vod_streams': return json(res, MOVIES.map(pub));
      case 'get_series': return json(res, []);
      case 'get_vod_info': {
        const mv = MOVIES[Number(url.searchParams.get('vod_id')) - 1];
        if (!mv) return json(res, { info: [] });
        return json(res, { info: { movie_image: mv.stream_icon, plot: mv._plot, genre: mv.genre, releasedate: mv.releasedate, rating: mv.rating, duration: '00:14:48', director: 'Blender Foundation', cast: '' }, movie_data: { stream_id: mv.stream_id, name: mv.name, container_extension: 'm3u8' } });
      }
      default: return json(res, []);
    }
  }
  if ((m = p.match(/^\/live\/[^/]+\/[^/]+\/(\d+)\.\w+$/))) return redirect(res, STREAMS[CHANNELS[Number(m[1]) - 1]?.[2]] ?? STREAMS.bbb);
  if ((m = p.match(/^\/movie\/[^/]+\/[^/]+\/(\d+)\.\w+$/))) return redirect(res, STREAMS[MOVIES[Number(m[1]) - 1]?._stream] ?? STREAMS.bbb);
  res.writeHead(404); res.end();
}).listen(port, '0.0.0.0', () => console.log(`Demo panel on ${base}`));
