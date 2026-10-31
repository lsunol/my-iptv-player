# Open IPTV logo brief

**Chosen file:** `design/logo-options/test-1.png` → copied to `design/logo-final.png` (1024×1024, byte-identical).

> Generated with `google/gemini-3.1-flash-image` via OpenRouter on 2026-10-31.
> The image is **AI-generated**. It is subject to the terms of the image model/provider that produced it; check them before any public or store release. The app must **not** bundle third-party brand logos (Movistar, DAZN, etc.). Channel logos only come from `tvg-logo` URLs in the user's playlist.

## Why it wins

A solid rounded play triangle with three concentric broadcast arcs. It reads as "play + streaming signal" with no TV brand cue, uses only two shapes, and has no text, 3D, shadow or fine detail.

- **48dp:** the triangle is a large solid mass. The arcs are thick (~45px of 1024, about 2dp at 48dp) with generous gaps, so they stay separate. Mint on navy has very high contrast.
- **Safe zone:** the mark spans roughly x 230–790 and y 210–810, about 55–59% of the canvas and centered. That is inside the central ~66% (adaptive-icon safe zone). A circle, squircle or square mask will not clip any of it.
- **Header (38dp):** it still reads as a clean glyph on a rounded square, with no thin outlines to fill in.
- **Palette:** mint→violet gradient on the mark over deep navy. The gradient is the brand pair, and the play shape stays dominant.

## Why the others lose

- `concept-2.png`: TV frame with a play button and Wi-Fi arcs. The rounded-rectangle "video" frame is very close to the YouTube mark, so it risks reading as a brand. The mark spans ~70% of the canvas, so the arcs sit near the crop edge. The outline strokes and the tiny inner triangle thin out at 48dp, and the Wi-Fi arcs read as "network", not "broadcast".
- `concept-3.png`: ring with a play triangle and a dot. The ring spans ~83% of the canvas, which breaks the safe zone. The satellite dot at the top-right falls inside the cropped corner and vanishes under a circle mask. It reads like a generic media-player or camera icon, with nothing that signals streaming.
- `concept-4.png`: striped play shape with one arc. It is the most distinctive but the least clear. The horizontal bars read as "menu/speed lines" more than play, and the split-triangle shape is fragile at 48dp. The background also has visible noise/speckle, so it is not clean flat navy.

## Palette usage

| Role | Colour | Use in the mark |
|---|---|---|
| Background | `#070B12` | Adaptive-icon background layer and header backdrop |
| Surface | `#0D1420` | Rounded-square header chip behind the mark, if needed |
| Primary mint `#5EEAD4` | Play triangle bottom-left and the lower arc ends |
| Secondary violet `#A78BFA` | Upper arcs and the gradient end |
| Live red `#F87171` | Not used in the logo. Reserve it for the LIVE badge in the UI. |

Note: the generated background sampled as about `#070F17`, slightly lighter than `#070B12`. Do not use the PNG background as-is. Use a flat `#070B12` layer and cut the mark out onto transparency.

## Adaptive-icon safe zone and integration specs (for the implementer, not done here)

- **Adaptive foreground:** the mark only, on a transparent background, in the 108dp canvas. Keep it inside the central 66dp (≈61%), so scale the mark's longest side to ≤ ~60dp. The outer ~18dp on each side can be cropped by the launcher mask. Currently the mark covers ~59% of the source image, so place the source at about 100–105% of 108dp, or crop tightly and scale to fit.
- **Adaptive background:** solid `#070B12` color layer, with no image or gradient.
- **Monochrome (themed icon, Android 13+, optional):** a single-colour silhouette of the same triangle plus arcs.
- **Legacy mipmaps:** `ic_launcher.png` and `ic_launcher_round.png` at mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192. Flatten the mark onto `#070B12`. The round version is clipped to a circle, and the mark must stay inside the circle.
- **In-app header:** `drawable-nodpi/logo_mark.png` (e.g. 256×256, mark on `#070B12` or transparent), displayed at ~38dp and clipped to a rounded square (~22% corner radius). Keep the mark inside ~80% of that square.
- **Play Store icon (later):** 512×512 on `#070B12`.
- Check the result at 48dp and under circle and squircle masks before committing.

## Reproducing prompt (one line)

`Flat vector app icon: a bold rounded play triangle with three concentric broadcast arcs to its right, mint #5EEAD4 to violet #A78BFA gradient on a flat deep navy #070B12 background, centered with wide margins, no text, no letters, no 3D, no shadows, no brand logos, minimal, high contrast.`

## Residual notes

- The source is a 1024px raster with a slightly off-navy background and soft anti-aliasing. For crisp launcher assets, either cut out the mark and re-export per density, or rebuild it as a vector (an Android VectorDrawable with a linear gradient) from the three shapes.
- Verify the image provider's terms for commercial or store use before publishing.
