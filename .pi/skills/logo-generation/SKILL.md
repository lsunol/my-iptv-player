---
name: logo-generation
description: Generate and evaluate app-icon concepts for Open IPTV with the OpenRouter image API. Use when creating or revising the launcher icon, the in-app header mark, or a brand concept. Covers the generation script, palette, prompt rules, adaptive-icon safe zones, and licensing caveats.
---

# Logo generation

The app's visual identity is already fixed:

| Role | Hex |
|---|---|
| Background | `#070B12` |
| Surface | `#0D1420` |
| Primary / mint | `#5EEAD4` |
| Secondary / violet | `#A78BFA` |
| Live accent | `#F87171` |

The logo must read as a play/streaming/broadcast mark, not as a specific TV
brand. Do not use text, letters, brand logos, 3D, shadows, or fine detail.

## Generate concepts

`tools/generate-logo.mjs` calls OpenRouter's `POST /api/v1/images` endpoint and
writes base64 image data to `design/logo-options/`.

```bash
node tools/generate-logo.mjs \
  --model google/gemini-3.1-flash-image \
  --name concept-1 \
  --out design/logo-options \
  --prompt "Full-bleed square Android app icon for Open IPTV. Deep navy #070B12 background. A bold play triangle merged with two broadcast signal arcs, mint #5EEAD4 to violet #A78BFA gradient. Flat vector, centered, generous safe margin, no text, no letters, no brand logos, no 3D, no shadows, high contrast, works at 48dp."
```

The script reads `OPENROUTER_API_KEY` or `pi auth print-api-key --provider
openrouter`; it never prints the key. The key stays local. The script defaults
to `google/gemini-3.1-flash-image`; other image models are in
`/api/v1/images/models`.

## Evaluate

- Build a contact sheet for quick comparison (see `design/logo-options/`).
- Judge at full size and at 48dp. The mark must survive the launcher mask.
- Adaptive icons crop the outer area: keep the mark inside the central ~66%
  safe zone, or make a full-bleed background with the mark centered.
- Pick one. Copy it to `design/logo-final.png` and write the brief to
  `design/logo-brief.md`.

## Integration (done by the Android agent, not this skill)

- Legacy launcher: `mipmap-*/ic_launcher.png` + `ic_launcher_round.png`.
- Adaptive: `mipmap-anydpi-v26/ic_launcher.xml` with a `#070B12` background and
  the mark as foreground.
- In-app header: `drawable-nodpi/logo_mark.png`, clipped to a rounded square.
- Manifest: `android:icon="@mipmap/ic_launcher"`,
  `android:roundIcon="@mipmap/ic_launcher_round"`.

## Licensing

AI-generated images are subject to the image model/provider terms. Record the
model and prompt in `design/logo-brief.md`. Do not bundle third-party brand
logos (Movistar, DAZN, etc.); the app uses provider-supplied `tvg-logo` URLs only.
