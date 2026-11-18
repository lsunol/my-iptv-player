# Web de Open IPTV

Página única estática (HTML + CSS + un poco de JS, sin build ni dependencias), en
inglés (por defecto), español y catalán, **en una sola URL**.

## Idiomas

- El inglés está escrito directamente en `index.html` (es lo que ven los
  buscadores y quien tenga JS desactivado). Cada texto traducible lleva
  `data-i18n="clave"` (contenido) o `data-i18n-attr="atributo:clave"` (alt,
  aria-label, meta...).
- Las demás lenguas están en `locales/es.json` y `locales/ca.json`; `main.js`
  descarga la que toca y la aplica sobre el HTML. Volver a inglés restaura el
  original, así que no hay `en.json`.
- Orden de elección: `?lang=xx` en la URL → `localStorage.lang` (lo guarda el
  selector EN/ES/CA) → primer idioma soportado de `navigator.languages` → inglés.
- Si el idioma no es inglés, la página se oculta hasta aplicar la traducción
  (evita el parpadeo en inglés); una animación CSS la muestra a los 1,2 s pase lo
  que pase.
- `node website/check-i18n.mjs` comprueba que todas las claves del HTML existen en
  cada `locales/*.json`.
- Para añadir un idioma: copia `locales/es.json`, tradúcelo y añade el código a la
  lista del script de `<head>`, a `SUPPORTED` en `main.js` y un enlace en el
  selector.
- Limitación: los metadatos para redes sociales (`og:*`) y el `<title>` que lee un
  rastreador sin JS siempre son los ingleses.

Se publica en GitHub Pages con `.github/workflows/pages.yml` en cada push a
`main` que toque `website/`. El APK **no** se compila aquí: se compila y firma en
local y se sube a GitHub Releases.

## Probar en local

```bash
cd website && python3 -m http.server 8765   # http://localhost:8765
```

## Enlace de descarga

Los botones apuntan a
`https://github.com/lsunol/my-iptv-player/releases/latest/download/open-iptv.apk`,
así que **cada Release debe llevar un asset llamado exactamente `open-iptv.apk`**:

```bash
./gradlew :app:assembleRelease
cp app/build/outputs/apk/release/app-release.apk /tmp/open-iptv.apk
sha256sum /tmp/open-iptv.apk
gh release create v0.3.0 /tmp/open-iptv.apk --title "v0.3.0" --notes "SHA-256: …"
```

La versión y la fecha que se ven bajo los botones se leen de la API pública de
GitHub (`releases/latest`); si falla, se muestra "Android 7.0 o superior".

## Imágenes

Las capturas de `assets/screens/` (WebP) están hechas en el emulador con el panel
de demostración `scripts/demo-xtream-panel.mjs` (películas abiertas de Blender,
CC BY). `assets/about/avatar.jpg` es el avatar de lluissunol.dev.
Para rehacerlas: `node scripts/demo-xtream-panel.mjs`, en la app crea una lista
Xtream con servidor `http://10.0.2.2:8789` (usuario y contraseña cualquiera) y
una M3U con `http://10.0.2.2:8789/demo.m3u`. Para un vídeo nítido en el
reproductor, ver la cabecera del script (tráiler de Sintel convertido a HLS).

| Fichero | Tamaño | Qué mostrar |
|---|---|---|
| `assets/screens/hero.webp` | 1080×2340 vertical | Pestaña **Directo** con la lista de demo cargada: cabecera, pestañas, varios canales |
| `assets/screens/step-lists.webp` | 1080×2340 vertical | Pantalla **Listas** con 2–3 listas con nombre (una M3U y una Xtream, credenciales enmascaradas) |
| `assets/screens/step-search.webp` | 1080×2340 vertical | Búsqueda con la consulta `espana` mostrando resultados de un grupo `ESPAÑA - …` |
| `assets/screens/step-catalog.webp` | 1080×2340 vertical | Pestaña **Películas** en cuadrícula de pósteres, con los chips de categoría/orden visibles |
| `assets/screens/player-landscape.webp` | 2340×1080 horizontal | Reproductor a **pantalla completa** con Sintel u otro vídeo de la demo |
| `assets/about/avatar.jpg` | 400×400 cuadrada | Tu foto (se recorta en círculo) |
| `assets/og-image.png` | 1200×630 | Ya generada con el logo; cámbiala si quieres una con captura |

Cualquier resolución vertical de 9:19,5 sirve (el marco recorta desde arriba).
Si el emulador da otra (p. ej. 1080×2400), vale tal cual.

### Reglas para las capturas (obligatorias)

- **Solo contenido sintético o de demostración**: `samples/demo.m3u`,
  `scripts/generate-large-playlist.mjs` o `scripts/fake-xtream-panel.mjs`
  (sirve en `10.0.2.2:8787` desde el emulador).
- **Nunca** la cuenta real, un proveedor real, logos de cadenas ni el
  catálogo real (`captures/sort-filter/` es privado y no se puede usar).
- La URL del panel falso no es un secreto, pero mejor que no se lea entera.
- Si usas Sintel, se mantiene la atribución del pie (CC BY 3.0, Blender
  Foundation). Si usas otro vídeo, actualiza ese texto en `index.html`.

### Captura desde el emulador

```bash
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb exec-out screencap -p > website/assets/screens/hero.webp
adb shell am broadcast -a com.android.systemui.demo -e command exit
```

Opcional, para que pesen menos: `magick hero.png -strip -quality 85 hero.webp`
(y cambia la extensión en `index.html`).

## Publicación

La web está publicada en [openiptv.lluissunol.dev](https://openiptv.lluissunol.dev/)
mediante GitHub Pages y el workflow indicado arriba.

Pendiente: forzar la redirección de HTTP a HTTPS y usar una URL absoluta en
`og:image` de `index.html` (algunas redes no aceptan rutas relativas).
