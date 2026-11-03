// Generates a synthetic large playlist for manual verification of the loader
// and the UI. Deterministic, no real provider, no real stream: every URL points
// at a placeholder host. Never commit a real provider list — see AGENTS.md.
//
//   node scripts/generate-large-playlist.mjs local/biglist.m3u 165000
//
// The default host is the Android emulator's alias for the machine running the
// emulator, so the file can be served with any static HTTP server and loaded by
// the app as http://10.0.2.2:8099/biglist.m3u.
//
//   node -e "..." or any server; for example:
//   npx --yes serve -l 8099 local
//
// Usage: generate-large-playlist.mjs [outputFile] [entries] [hostPort]

import { createWriteStream } from 'node:fs'
import { once } from 'node:events'

const out = process.argv[2] ?? 'local/biglist.m3u'
const total = Number(process.argv[3] ?? 165000)
const host = process.argv[4] ?? '10.0.2.2:8099'

// Multilingual groups and quality variants, matching what real provider dumps
// contain: accents that are not ASCII, a non-Latin script, and duplicates that
// differ only by variant tag.
const GROUPS = [
  'ES - FUTBOL', 'ESPAÑA - Drama', 'ESPAÑA - Cine', 'LATINO - Novelas',
  'LATINO - Infantil', 'Música', 'Noticias 24h', 'ℝℝ Deportes', 'Документальные'
]
const VARIANTS = ['FHD', 'HD', 'HEVC', 'HDR', 'SD']

const stream = createWriteStream(out)
const write = (line) => stream.write(line) || once(stream, 'drain')

await write('#EXTM3U\n')
for (let i = 0; i < total; i++) {
  const kind = i % 3 === 0 ? 'live' : i % 3 === 1 ? 'movie' : 'series'
  const group = GROUPS[i % GROUPS.length]
  const name = `Canal ${i} ${VARIANTS[i % VARIANTS.length]}`
  await write(
    `#EXTINF:-1 tvg-id="${kind}-${i}" tvg-name="${name}" ` +
    `tvg-logo="http://${host}/logo${i}.png" group-title="${group}",${name}\n` +
    `http://${host}/example/${kind}/u/p/${i}.m3u8\n`
  )
}
stream.end()
await once(stream, 'finish')
