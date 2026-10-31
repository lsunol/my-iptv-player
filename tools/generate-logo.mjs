#!/usr/bin/env node
/**
 * Generate logo concepts with OpenRouter's image API.
 *
 * Usage:
 *   node tools/generate-logo.mjs --prompt "..." --out design/logo-options --name concept-1
 *
 * The API key is read from OPENROUTER_API_KEY, or from the local Pi auth store
 * (`pi auth print-api-key --provider openrouter`). It is never printed.
 */
import { execSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { extname, join } from 'node:path';

function arg(name, fallback = null) {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 ? process.argv[i + 1] : fallback;
}

const model = arg('model', 'google/gemini-3.1-flash-image');
const prompt = arg('prompt');
const outDir = arg('out', 'design/logo-options');
const name = arg('name', 'logo');
const aspect = arg('aspect', '1:1');

if (!prompt) {
  console.error('Missing --prompt');
  process.exit(2);
}

function apiKey() {
  if (process.env.OPENROUTER_API_KEY) return process.env.OPENROUTER_API_KEY.trim();
  try {
    return execSync('pi auth print-api-key --provider openrouter', {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch {
    throw new Error('No OPENROUTER_API_KEY and `pi auth print-api-key --provider openrouter` failed.');
  }
}

const body = {
  model,
  prompt,
  n: 1,
  aspect_ratio: aspect,
};

const response = await fetch('https://openrouter.ai/api/v1/images', {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${apiKey()}`,
    'Content-Type': 'application/json',
    'HTTP-Referer': 'https://github.com/lsunol/my-iptv-player',
    'X-Title': 'Open IPTV logo generation',
  },
  body: JSON.stringify(body),
});

const text = await response.text();
if (!response.ok) {
  throw new Error(`OpenRouter ${response.status}: ${text.slice(0, 500)}`);
}

const json = JSON.parse(text);
if (!json.data?.length) throw new Error(`No image data: ${text.slice(0, 500)}`);

await mkdir(outDir, { recursive: true });
const saved = [];
for (let i = 0; i < json.data.length; i++) {
  const item = json.data[i];
  if (!item.b64_json) continue;
  const media = item.media_type || 'image/png';
  const ext = media.includes('svg') ? '.svg' : media.includes('webp') ? '.webp' : media.includes('jpeg') ? '.jpg' : '.png';
  const path = join(outDir, `${name}${json.data.length > 1 ? `-${i + 1}` : ''}${ext}`);
  await writeFile(path, Buffer.from(item.b64_json, 'base64'));
  saved.push(path);
}

console.log(JSON.stringify({ model, saved, usage: json.usage ?? null }, null, 2));
