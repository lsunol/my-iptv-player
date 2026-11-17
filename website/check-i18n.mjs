#!/usr/bin/env node
// Checks that every data-i18n key used in index.html exists in each locales/*.json
// (and that no locale has keys the page does not use).  node website/check-i18n.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const dir = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(dir, 'index.html'), 'utf8');
const keys = new Set();
for (const m of html.matchAll(/data-i18n="(\w+)"/g)) keys.add(m[1]);
for (const m of html.matchAll(/data-i18n-attr="([^"]+)"/g)) m[1].split(',').forEach((p) => keys.add(p.split(':')[1]));
const extra = new Set(['date_locale', 'og_locale']);
let bad = false;
for (const f of fs.readdirSync(path.join(dir, 'locales')).filter((n) => n.endsWith('.json'))) {
  const d = JSON.parse(fs.readFileSync(path.join(dir, 'locales', f), 'utf8'));
  for (const k of keys) if (typeof d[k] !== 'string') { console.error(`${f}: missing ${k}`); bad = true; }
  for (const k of Object.keys(d)) if (!keys.has(k) && !extra.has(k)) console.warn(`${f}: unused ${k}`);
}
process.exit(bad ? 1 : 0);
