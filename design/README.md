# Design assets

`brand/` holds the **current** app icon, concept B «Pantalla», made by a
designer (derived from the Lluís Suñol `ls` seal; not AI-generated):

| File | Use |
|---|---|
| `open-iptv-icon-rounded.svg` | master, rounded square |
| `open-iptv-icon-round.svg` | master, circle |
| `open-iptv-icon-square.svg` | master, full-bleed square |
| `open-iptv-adaptive-108.svg` | adaptive-icon foreground on the 108dp canvas |
| `open-iptv-mark-transparent.svg` | mark only, transparent background |
| `play-store-icon-512.png` | Google Play store icon (512×512) |
| `open-iptv-icon-1024.png` | 1024 px raster |

In the APK it is `drawable/ic_launcher_foreground.xml`,
`drawable/ic_launcher_monochrome.xml`, `drawable/logo_mark.xml` (in-app header),
`mipmap-*/ic_launcher*.png` (API 24–25) and the `#0F1A2B` icon background.
Icon palette: navy `#0F1A2B`, white `#F8FAFD`, blue `#3B82F6`.

Superseded: `logo-options/` contains the earlier AI-generated concepts and
`logo-final.png` the previous mark; `logo-brief.md` records how they were made.

The app's palette is defined in `app/src/main/java/dev/lluissunol/iptv/ui/theme/Theme.kt`:
deep navy `#070B12`, mint `#5EEAD4`, violet `#A78BFA`, live red `#F87171`.

Do not add brand logos (Movistar, DAZN, etc.) to this repository or the APK.
The app only displays `tvg-logo` URLs supplied by the user's playlist.
