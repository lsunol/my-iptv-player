# Aviso de catálogo ausente — estados degradado y de primera ejecución (listas Xtream)

Spec: `design/ui/no-catalogue-notice.md` · Pantalla: `PlaylistScreen` (pestañas
Películas / Series) · Author: ui-designer · Estado: propuesta lista para implementar.

---

## 1. El problema

Síntoma real (dispositivo físico, release 0.3.0, lista `PlaylistKind.XTREAM`
con 13.609 películas y ~75.000 episodios en el M3U): al abrir una lista Xtream
**desde caché sin catálogo** — el caso de toda instalación pre-existente, porque
la caché de catálogo (`XtreamCache`) es nueva y aún no existe — las pestañas
Películas y Series muestran en silencio la lista plana del M3U: filas con
miniatura de 40 dp, contador "13.609 canales", pills de `group-title` del M3U,
sin rejilla de carteles, sin botón de diseño, y Series con 75.000 episodios
sueltos. Solo tras pulsar **Actualizar** aparece el catálogo (rejilla, toggle,
chips de categorías del proveedor, "13.609 títulos", Series colapsa a 2.400
series reales).

Causa: el catálogo (`player_api.php`) solo se construye durante una descarga de
red (`loadWithCatalog`). En el arranque desde caché, `XtreamCache.read`
devuelve `null` (archivo ausente o corrupto) y `applyPlaylist` se queda con
`catalog = null`, `catalogWarning = null` → `isCatalogTab = false` → lista plana
**sin ningún texto que lo explique**. Nada dice que la rejilla y el agrupado
de series existen y requieren un Actualizar.

Esto viola la regla del proyecto ("the app must make failures understandable; a
blank screen or a generic error is a bug"): es una degradación silenciosa. El
usuario del informe real llegó a la conclusión de que la instalación había
fallado. También cubre el caso permanente de un proveedor con la API
desactivada, donde el usuario vería una lista plana para siempre sin saber por
qué.

Restricción de verificación: en el único dispositivo de pruebas la caché de
catálogo ya existe, y borrar datos de la app destruiría la lista configurada del
usuario. **No** se reproduce con `adb pm clear` ni desinstalando. La lógica de
selección del aviso debe verificarse con tests unitarios de una función pura
(ver §3.4).

## 2. Opciones consideradas

**Opción A — No hacer nada (status quo).** Coste: cero. Compra: nada; la
degradación sigue siendo silenciosa y el fallo de percepción se repite en cada
instalación actualizada. Pierde: incumple la regla de degradaciones
explicables de AGENTS.md.

**Opción B — Aviso pasivo de una línea (reusar `NoticeLine` tal cual).**
Añadir solo el texto "aún no hay catálogo…", sin acción. Coste: mínimo (una
condición + un string). Compra: el usuario sabe *por qué* ve la lista plana.
Pierde frente a C: deja el remedio implícito. En la lista de reproducción la
única acción visible es el icono de Actualizar en la cabecera; un usuario que
acaba de concluir "la instalación falló" necesita el *qué hacer*, no solo el
*por qué*.

**Opción C — Aviso de una línea con acción integrada (recomendada).**
`NoticeLine` ampliado con un `TextButton` "Actualizar" al final (el mismo
patrón fila + mensaje + `TextButton` de reintento que ya usa `ErrorBanner`).
Coste: un composable más en la cabecera de las pestañas Películas/Series, dos
condiciones nuevas en la selección del aviso, un string nuevo. Compra: motivo +
remedio en el mismo sitio, coherente con el lenguaje visual existente, sin
bloquear la lista, sin estado persistido nuevo. **Recomendada**: es el cambio
más pequeño que hace la degradación explicable *y* accionable.

**Opción D — Auto-descargar el catálogo en el arranque si falta.** Descargar
solo `player_api.php` en segundo plano cuando una lista Xtream arranque desde
caché sin catálogo. Compra: arreglaría la causa raíz sin interacción. Pierde:
(1) contradice la garantía documentada de v1 "Startup reads the cache without a
network request" (espíritu: arranque sin red garantizado y predecible);
(2) gasta datos móviles sin intención del usuario; (3) si el proveedor tiene la
API desactivada, el usuario recibe el aviso de fallo en cada arranque en vez de
una vez. Cambiar la garantía de arranque sin red **es una decisión de producto,
no de diseño**; esta spec no la asume. Si se aprueba más adelante, el aviso de
esta spec sigue siendo necesario para el caso API desactivada y para el
intervalo de descarga.

**Opción E — Sheet, diálogo o pantalla intersticial.** Rechazada: bloquearía la
lista que sí funciona; el usuario debe poder ignorar el aviso y usar los canales.
Un aviso inline expansion es lo correcto: contextual a la pestaña, no modal.

## 3. El diseño elegido

### 3.1 Componente: `NoticeLine` accionable

Un único composable, en la **posición exacta donde hoy renderiza `NoticeLine`**
(en `PlaylistContent`, tras `ListHeader`/error banner, antes del contenido).
No es un sheet ni una ruta: es una expansión inline, porque la lista plana debe
seguir siendo utilizable y el contexto es la pestaña activa.

Anatomía (fila horizontal, `fillMaxWidth`):

| Elemento | Especificación |
|---|---|
| Contenedor | `Row`, padding horizontal 20 dp (igual que el resto de la cabecera), vertical 4 dp |
| Icono | `Icons.Default.Info`, 14 dp, `contentDescription = null` (decorativo), tint `onSurfaceVariant` `#94A3B8` (contraste ≈ 7.7:1 sobre `#070B12`, pasa WCAG AA) |
| Separador | `Spacer` 6 dp |
| Texto | `labelSmall`, color `onSurfaceVariant` `#94A3B8`, `modifier = weight(1f)`, `maxLines = 3` (antes 2: da margen a fuentes grandes), `TextOverflow.Ellipsis` |
| Acción | `TextButton` "Actualizar", texto color `primary` `#5EEAD4` (contraste > 10:1), estilo por defecto de M3: visual ~40 dp, touch target 48 dp garantizado por `minimumInteractiveComponentSize`. `enabled = !state.isLoading` |

La variante **pasiva** (avisos informativos actuales: `catalogWarning` de
decodificación parcial, `catalog_search_all`) mantiene la anatomía de hoy
(icono + texto, sin botón, `maxLines` sube a 3 por coherencia). La variante
**accionable** añade el `TextButton`.

### 3.1.1 Texto exacto y claves de recursos

Nuevas en `app/src/main/res/values/strings.xml`:

```xml
<!-- Catálogo Xtream: degradación explicada -->
<string name="catalog_missing">El catálogo de esta lista aún no se ha cargado; sin él no hay cuadrícula de carteles ni series agrupadas.</string>
<string name="catalog_unavailable">No se pudo cargar el catálogo de películas y series; se muestra la lista plana.</string>
<string name="catalog_skipped_items">Se omitieron %1$d elementos del catálogo por datos no válidos.</string>
<string name="catalog_empty_response">El proveedor respondió sin datos del catálogo.</string>
<string name="catalog_invalid_json">El catálogo del proveedor no era JSON válido.</string>
<string name="catalog_unexpected_format">El catálogo del proveedor no tenía el formato esperado.</string>
```

- `catalog_missing` es la única cadena nueva del usuario visible. El botón
  **reutiliza la clave existente `refresh` ("Actualizar")** — no se añade clave
  para el botón.
- `catalog_unavailable` y los cuatro avisos de decodificación son los textos
  hoy **codificados en Kotlin** en `PlaylistRepository.CATALOG_UNAVAILABLE` y
  `XtreamClient` (avisos "El proveedor respondió sin datos…", "…no era JSON
  válido", "…formato esperado", "Se omitieron N elementos…"). AGENTS.md exige
  todo texto visible en recursos; esta spec es la excusa para moverlos a
  claves. Los textos no cambian (salvo el parámetro de `catalog_skipped_items`).

### 3.2 Las dos preguntas de diseño

**P1 — ¿Muestra algo una lista `PlaylistKind.URL`?** **No.** Una lista URL es el
comportamiento documentado y esperado, no una degradación: AGENTS.md fija que
"Plain-M3U (`PlaylistKind.URL`) lists are never augmented with API calls". Un
hint de "una lista Xtream desbloquea la cuadrícula" sería un upsell ruidoso y
a menudo falso: el usuario de una URL M3U compartida no tiene por qué tener
credenciales Xtream con las que reconstruir la conexión. Además exige
persistir un flag "ya visto" (estado nuevo para un beneficio marginal) y
rompe la simetría con el resto de la app, que nunca sugiere contenido ni
proveedores. La educación correcta para el único caso real — un usuario que
pega una URL `get.php` completa como tipo URL — perteneende al **formulario de
lista**, no al reproductor, y es una decisión de producto aparte; se deja
fuera de esta spec. Veredicto: sin hint, ni sutil: **nada en absoluto**.

**P2 — ¿Toggle de diseño oculto o deshabilitado sin catálogo?** **Oculto**
(comportamiento actual, hay que preservarlo). Un toggle deshabilitado es un
control que no explica nada: el usuario ve un icono gris sin saber por qué, y
sugiere que la lista plana *podría* ser una rejilla, lo cual es falso (sin
catálogo no hay carteles). La explicación y el remedio los lleva el aviso
accionable; el toggle aparece solo cuando `isCatalogTab` es cierto, es decir
cuando funciona. Regla M3: un control deshabilitado sin razón visible al lado
es un antipatrón; el aviso es la razón, el toggle la recompensa.

### 3.3 Condición visible del aviso (para el implementador)

El aviso es **derivado**, nunca estado propio: se calcula de `kind` de la
lista activa, `catalog`, `catalogWarning`, `tab`, `searchAll`, `isLoading`.
Nada se persiste. Se muestra **cada arranque** mientras la condición se
mantenga — no es nagging porque la degradación persiste y el aviso es un
estado cierto con remedio, no un recordatorio promocional.

### 3.4 Función pura de selección + matriz de tests (verificación requerida)

Extraer la selección a una función pura (sin Compose, sin Android), p. ej.:

```kotlin
enum class CatalogNoticeKind { NONE, INFO, ACTION_REFRESH }

fun catalogNotice(
    kind: PlaylistKind,
    catalog: VodCatalog?,
    catalogWarning: String?,
    tab: SourceType?,
    searchAll: Boolean,
    isLoading: Boolean
): Pair<CatalogNoticeKind, String?>
```

Prioridad (de arriba abajo, primera coincidencia gana):

1. `tab != MOVIE && tab != SERIES` → `NONE`. (Directos/Todo no cambian.)
2. `searchAll` → comportamiento actual: `INFO(catalog_search_all)` solo si
   `catalog != null`; los avisos de catálogo ausente/fallido se **suprimen**
   (la lista plana es intencionada en búsqueda global).
3. `kind == URL` → `NONE`. (Nunca hay degradación en URL; nunca se llama a la API.)
4. `catalog != null` → `INFO(catalogWarning)` si el aviso no es nulo; si no,
   `NONE`. (Decodificación parcial; pasivo, sin botón.)
5. `catalog == null && catalogWarning != null` → `ACTION_REFRESH(catalog_unavailable)`.
   (La API falló en el último fetch; el M3U funciona.)
6. `catalog == null && catalogWarning == null` → `NONE` si `isLoading`
   (un Actualizar ya está trayendo el catálogo; el aviso sería ruido sobre un
   progreso visible) ; si no → `ACTION_REFRESH(catalog_missing)`.
   **Este es el estado nuevo: primera ejecución / caché de catálogo ausente o
   corrupta.** Cubre también `decodeVodCatalog` devolviendo `null` por archivo
   corrupto: mismo remedio.

`ACTION_REFRESH` renderiza el botón (tapa = `onRefresh`); su `enabled`
sigue a `!isLoading` (la variante de fallo sí permanece visible durante una
descarga porque describe la lista que aún se ve, pero no debe permitir un
doble refresco).

Tests unitarios obligatorios (la restricción del dispositivo impide reproducir
el estado; la función pura sí se puede):

1. XTREAM, `catalog=null`, `warning=null`, tab MOVIE, sin searchAll, sin
   loading → `ACTION_REFRESH(catalog_missing)`. **El caso del defecto.**
2. Igual con tab SERIES → `ACTION_REFRESH(catalog_missing)`.
3. Igual con tab LIVE → `NONE`.
4. Igual con tab null (Todo) → `NONE`.
5. Igual con `searchAll=true` → `NONE`.
6. Igual con `isLoading=true` → `NONE`.
7. XTREAM, `catalog=null`, `warning` no nulo, tab MOVIE → `ACTION_REFRESH(catalog_unavailable)`.
8. XTREAM, `catalog != null`, `warning` no nulo, tab MOVIE, sin searchAll → `INFO(warning)` (idéntico a hoy).
9. XTREAM, `catalog != null`, sin warning, `searchAll=true`, tab MOVIE → `INFO(catalog_search_all)` (idéntico a hoy).
10. URL, cualquier combinación de catalog/warning, tab MOVIE → siempre `NONE`; en particular `catalog=null && warning=null` nunca produce `catalog_missing`.
11. Pureza: la función no lee ViewModel ni DataStore (implícito; sin esta nota un test de la tabla 1 acoplado al estado real no existiría).

## 4. Todos los estados

| Estado | Condición | Qué se ve |
|---|---|---|
| Por defecto (catálogo cargado) | XTREAM, `catalog != null`, tab MOVIE/SERIES, sin searchAll | Sin aviso. Rejilla/toggle/chips según hoy. |
| **Primera ejecución / catálogo ausente** (nuevo) | XTREAM, arranque desde caché, `XtreamCache` ausente **o corrupto** (`decodeVodCatalog → null`), `warning=null` | `catalog_missing` accionable en Películas y Series: lista plana visible y usable, botón "Actualizar". Contadores honestos del M3U ("13.609 canales"), pills de grupo M3U. |
| Catálogo fallido (API caída o desactivada) | `warning=catalog_unavailable`, `catalog=null` | Mismo sitio y anatomía, texto `catalog_unavailable`, botón reintenta. Si el proveedor tiene la API desactivada, este aviso es permanente y correcto: explica el estado. |
| Catálogo parcialmente decodificado | `catalog != null`, `warning` de `catalog_skipped_items`/etc. | `INFO` pasivo (sin botón), igual que hoy pero con clave de recurso y `maxLines=3`. |
| Lista `PlaylistKind.URL` | cualquier tab | **Nada** (P1). La lista plana es el contrato documentado. |
| Pestaña Directos / Todo | cualquier catálogo | Nada (regla 1). El aviso vive solo en MOVIE/SERIES. |
| Búsqueda global (`searchAll`) | XTREAM sin catálogo, tab MOVIE/SERIES | Suprimido (regla 2): la lista plana es intencional; ya existe `catalog_search_all` cuando hay catálogo. |
| Refresco en curso | `isLoading` | Pending oculto (regla 6); `catalog_unavailable` visible con botón deshabilitado. La barra de progreso de refresco sigue en la parte alta. |
| Resultado vacío tras filtrar | query sin coincidencias | `EmptyState` (`empty_search`) **debajo** del aviso; el aviso permanece (su condición no depende del query) — correcto: sigue siendo verdad. |
| Sin conexión en arranque | caché M3U presente, catálogo ausente, sin red | Aviso `catalog_missing` visible (no requiere red). Pulsar "Actualizar" produce el banner de error de red existente y la lista se conserva (semántica Actualizar intacta). Explicable. |
| Catálogo muy grande | decenas de miles de títulos | El aviso es un único composable fuera de la lazy list/grid: coste cero por fila, sin estado por fila, sin tocar las claves estables (`movie:<id>`, `series:<id>`) ni la ventana de prefetch de Coil. |
| Lista recién creada (fetch inicial de red) | XTREAM nuevo | `openList` → sin caché → `fetch` → `loadWithCatalog` trae el catálogo en el primer load: no hay aviso. El aviso solo aparece por arranque desde caché o tras fallo de API. |
| Rotación / volver de listas | — | Estado en ViewModel; el aviso se re-deriva idéntico. Sin trabajo por transición. |

## 5. Detalles de interacción

- **Tipo de contenedor**: expansión inline bajo la cabecera de la pestaña. No
  sheet, no diálogo, no ruta: la lista plana sigue siendo operable y el aviso
  es contextual al tab.
- **Tap**: el `TextButton` es el único target de acción (48 dp por M3). El
  texto no es clickable: evita ambigüedad con los taps de la lista. Acción =
  `onRefresh`, idéntica al icono Actualizar de la cabecera.
- **Back**: sin interacción propia. Back en la playlist vuelve a Mis listas
  (comportamiento actual, intacto). El aviso nunca captura back.
- **Qué persiste**: nada nuevo. Sin flag "no mostrar de nuevo" ni preferencia.
  La condición es derivada; el aviso desaparece solo cuando `XtreamCache`
  exista y decodifique (tras un Actualizar con éxito).
- **Rotación**: sin recreación de Activity (regla del proyecto); el estado vive
  en el ViewModel, el aviso se recalcula. Nada que guardar.
- **Foco**: tras pulsar "Actualizar", el aviso pending desaparece (`isLoading`)
  y el foco cae al contenido de la lista; TalkBack anuncia "Actualizar, botón"
  antes. Orden de traversal: campo de búsqueda → fila de filtros → contador →
  aviso → contenido, coherente con el orden visual.
- **Transición de éxito**: Actualizar con éxito → `applyPlaylist` con
  `catalog != null` → el aviso desaparece, aparecen rejilla + toggle + chips de
  categorías, Series colapsa de episodios a series reales. Un solo frame de
  cambio; sin animación requerida.
- **Transición de fallo**: Actualizar falla (M3U caído) → banner de error
  existente, lista conservada (regla "a failed refresh must never destroy the
  working list"), aviso sin cambios. Fallo solo del catálogo →
  `catalog_unavailable` reemplaza a `catalog_missing` con el mismo botón.

## 6. Accesibilidad

- El aviso es texto: TalkBack lo lee en el traversal natural. El icono Info es
  decorativo (`contentDescription = null`) como en el `NoticeLine` actual.
- El botón se anuncia como "Actualizar, botón"; su etiqueta es la clave
  existente `refresh`.
- Contraste: texto `#94A3B8` sobre fondo `#070B12` ≈ 7.7:1; botón `#5EEAD4`
  sobre `#070B12` > 10:1; ambos superan WCAG AA (4.5:1) incluso a `labelSmall`.
- Fuente grande (≥1.3×, hasta 2.0×): `maxLines` sube de 2 a 3 y el texto
  `weight(1f)` empuja el botón a su línea; la fila crece en vertical sin
  recortar el remedio. A 2.0× se admite `TextOverflow.Ellipsis` tras 3 líneas,
  pero la palabra clave ("Actualizar") está siempre en el botón visible.
- El botón mantiene 48 dp de target táctil a cualquier escala de fuente.

## 7. Lo que no debe cambiar

- **Listas URL nunca llaman a la API** ni muestran el aviso (regla dura de
  AGENTS.md).
- **Arranque desde caché sin petición de red**: la garantía documentada se
  mantiene; esta spec no añade fetch en startup (Opción D requiere decisión de
  producto explícita).
- **Semántica de Actualizar**: la lista visible se conserva y solo se
  reemplaza si tiene éxito; la caché de catálogo solo se reescribe con éxito
  total.
- **Contadores de pills** (`tabCount`): cuentan lo que se muestra; "13.609
  canales" en degradado es honesto para la lista plana y no se toca.
- **Pestañas Directos/Todo**, pills de grupo, búsqueda global y su aviso
  `catalog_search_all`, favoritos y su derivación, debounce de 150 ms.
- **Reglas de rendimiento de la rejilla**: 3 columnas fijas, claves estables,
  `contentType` por grid, `size()` de Coil, prefetch 8, sin `SubcomposeAsyncImage`
  ni `BoxWithConstraints` por celda, sin estado por fila.
- **Toggle de diseño oculto sin catálogo** (respuesta P2) y su aparición solo
  cuando `isCatalogTab`.
- **Fondo de degradado existente**: `PlaylistRepository` ya degrada a lista
  plana cuando la API falla y nunca hace fallar el load por el catálogo; solo
  se añade la capa visible.

## 8. Recomendación resumida para el implementador

1. Añadir `catalog_missing` + mover los 5 avisos de catálogo hoy hardcodeados
   a `strings.xml` con las claves de §3.1.1.
2. Extraer `catalogNotice(...)` como función pura con la prioridad de §3.4 y
   cubrir los 11 casos con tests unitarios (la única verificación posible sin
   borrar los datos del dispositivo).
3. Extender `NoticeLine` con variante accionable (anatomía §3.1) renderizada
   en la posición actual del aviso, solo en las pestañas Películas/Series.
4. No tocar: listas URL, arranque sin red, contadores, rendimiento de la
   rejilla, toggle oculto sin catálogo.
