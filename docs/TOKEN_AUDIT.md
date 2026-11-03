# Auditoría de consumo de tokens y enrutado a modelos gratuitos

Fecha: 2026-11-03. Autor: sesión principal de Pi (evidencia medida, no estimada).

Todo lo de este documento sale de datos reales: los `usage.cost` que Pi escribe en
los JSONL de sesión (`~/.pi/agent/sessions/--C--Users-lluis-projects-my-iptv-player--/`),
los `*_meta.json` de `subagent-artifacts/`, el catálogo `GET /api/v1/models` de
OpenRouter y el contador de cuenta `GET /api/v1/key`.

## 1. Consumo real del proyecto

Total acumulado del repositorio: **7.99 USD**.

| Bloque | USD | % | Llamadas |
|---|---|---|---|
| Sesiones principales (orquestador) | 6.05 | 76% | ~540 |
| Subagentes (25 runs) | 1.93 | 24% | — |

### 1.1 Sesiones principales por modelo

| Modelo | Llamadas | Entrada | Salida | cacheRead | USD |
|---|---|---|---|---|---|
| `deepseek/deepseek-v4-pro` | 139 | 222.675 | 152.997 | 21.354.752 | **5.7686** |
| `deepseek/deepseek-v4.1-flash` | 265 | — | — | — | 0.2376 |
| `z-ai/glm-5.3-flash` | 60 | — | — | — | 0.0441 |

Una sola sesión (`2026-11-01T16-28`, 3 h de trabajo) costó **5.72 USD**, el 72% del
proyecto. De sus 5.77 USD, **5.34 USD (92%) son lecturas de caché**: 21,35 M tokens
a 0,25 USD/M. El coste no lo genera el trabajo nuevo, lo genera **un contexto muy
largo releído turno tras turno**.

### 1.2 Subagentes por agente

| Agente | Runs | Turnos | Entrada | Salida | cacheRead | USD | USD/run |
|---|---|---|---|---|---|---|---|
| `iptv-engineer` | 2 | 114 | 241.636 | 103.895 | 8.048.550 | 0.7169 | 0.3584 |
| `qa-reviewer` | 6 | 153 | 359.129 | 138.587 | 6.539.099 | 0.6697 | 0.1116 |
| `android-dev` | 7 | 412 | 1.795.336 | 431.123 | 33.398.333 | 0.4066 | 0.0581 |
| `ui-designer` | 1 | 11 | 36.587 | 17.066 | 306.240 | 0.0475 | 0.0475 |
| `apk-smith` | 5 | 59 | 192.488 | 26.925 | 867.840 | 0.0327 | 0.0065 |
| `researcher` | 1 | 20 | 179.083 | 13.517 | 872.256 | 0.0312 | 0.0312 |
| `android-architect` | 1 | 7 | 24.169 | 7.766 | 122.304 | 0.0210 | 0.0210 |
| `scout` | 2 | 16 | 52.383 | 14.015 | 222.644 | 0.0091 | 0.0046 |

### 1.3 Tarifas implícitas reales (derivadas del coste registrado)

| Modelo | Entrada | Salida | cacheRead |
|---|---|---|---|
| `deepseek/deepseek-v4-pro` | 0,90 | 1,50 | 0,25 |
| `deepseek/deepseek-v4.1-flash` | 0,0075 | 0,67 | 0,0017 |
| `z-ai/glm-5.3` | 0,070 | 1,40 | 0,069 |
| `z-ai/glm-5.3-flash` | 0,025 | 0,113 | 0,023 |

USD por 1 M tokens. Corrige la tabla de `AGENTS.md`: `deepseek-v4-pro` **no** se
facturaba a precio Flash (era ~100× más caro en entrada que `v4.1-flash`).

**Conclusión del bloque 1:** el gasto no está en los subagentes (1,93 USD en 25
runs), sino en el orquestador con contexto largo. Mover agentes a modelos
gratuitos ahorra céntimos; lo que ahorra euros es compactar/reiniciar sesión y no
orquestar sobre `v4-pro`.

## 2. Qué hay disponible gratis (OpenRouter, 2026-11-03)

14 modelos con sufijo `:free`. Con `tools` (imprescindible para un agente) y
contexto útil:

| Modelo | Contexto | Códec | Notas del proveedor |
|---|---|---|---|
| `nvidia/nemotron-3.5-lightning:free` | 1.000.000 | 30B/3B activos | alto rendimiento agéntico |
| `nvidia/nemotron-3-ultra-550b-a55b:free` | 1.000.000 | 550B/55B activos | razonamiento frontier + orquestación |
| `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free` | 256.000 | 30B/3B | multimodal, sub-agente |
| `thinkingmachines/inkling:free` / `inkling-small:free` | 1.048.576 | 975B/41B · 276B/12B | coding y tool-use |
| `cohere/north-mini-code:free` | 256.000 | 30B/3B | primer modelo agéntico de código de Cohere |
| `google/gemma-4-26b-a4b-it:free`, `gemma-4-31b-it:free` | 262.144 | densos | multimodal |
| `dots-studio/dots-3-note-preview:free` | 512.000 | 280B/16B | — |
| `xingchen-agi/xing4.0-29b-a4b:free` | 262.144 | 29B/4B | — |
| `recursive/flint-v1.0:free` | 262.144 | 40B/2,5B | construido para agentes de código |
| `liquid/lfm-2.5-2.6b:free` | 65.536 | 2,6B | **el propio proveedor desaconseja coding agéntico** |

Además existe `openrouter/free`, un router que elige al azar entre modelos
gratuitos (no es un modelo físico).

### 2.1 Límites del tier gratuito (respuesta a "¿se agota la cuota?")

| Créditos comprados (histórico) | Requests/minuto | Requests/día |
|---|---|---|
| < 10 | 20 | **50** |
| ≥ 10 | 20 | **1000** |

Estado de esta cuenta (`GET /api/v1/key`): `is_free_tier: false`,
`free_model_daily_requests.limit: 1000`.

Medición de esfuerzo real: los 5 modelos probados en paralelo (sección 3) más 4
arranques del router consumieron **49 requests** de los 1000 del día. Un run de
agente completo son 6-10 requests, así que el techo diario da para ~100 runs.
Aun así, con la cuota baja (50/día) **un solo run largo podría agotarla**, de ahí
que el enrutado vaya con failover (sección 4) y no a pelo.

Comprobación rápida sin imprimir la clave:

```bash
K=$(python -c "import json;print(json.load(open('$HOME/.pi/agent/auth.json'))['openrouter']['key'])")
curl -s -H "Authorization: Bearer $K" https://openrouter.ai/api/v1/key \
  | python -c "import json,sys;d=json.load(sys.stdin)['data'];print(d['free_model_daily_requests'], round(d['limit_remaining'],2))"
```

## 3. Prueba empírica: ¿sirven para este repo?

Misma tarea, cinco modelos distintos, agente `android-dev` (herramientas
read/find/bash/write), directorio aislado y verdad de referencia verificada
(`PlaylistParser.kt` = 206 líneas, 8 líneas con `fun `). Todos debían leer,
contar con bash, escribir un informe y responder una línea exacta.

| Modelo | Resultado | Turnos | Entrada/Salida | Coste |
|---|---|---|---|---|
| `nvidia/nemotron-3-ultra-550b-a55b:free` | correcto | 7 | 30.589 / 657 | 0 USD |
| `nvidia/nemotron-3.5-lightning:free` | correcto | 7 | 20.765 / 1.800 | 0 USD |
| `cohere/north-mini-code:free` | correcto | 6 | 72.616 / 1.626 | 0 USD |
| `thinkingmachines/inkling:free` | correcto | 6 | 13.812 / 248 | 0 USD |
| `openrouter/free` (router) | correcto | 10 | 91.849 / 1.768 | 0 USD |

Los cinco (exit 0, sin dañar el repo: `git status` limpio). No distingue calidad
fina — una tarea mecánica no prueba juicio de diseño — pero sí prueba lo que
importa para delegar: bucle multi-turno con tool calls, seguir instrucciones
exactas y escribir ficheros.

## 4. Failover: que ninguna tarea quede desatendida

Dato duro: **`pi-subagents` no hace fallback de modelo por diseño**
(`pi-subagents/docs/models.md`: "each launch resolves one model… to try another
model, the parent or operator must issue a later explicit launch"), y el campo
`fallbackModels` fue eliminado de los perfiles.

Solución implementada: un **modelo virtual** de Pi (`.pi/extensions/free-first-router.ts`)
llamado `openrouter/free-first`:

- Turno nuevo de usuario → intenta la cadena gratuita
  (`nemotron-3.5-lightning` → `north-mini-code` → `nemotron-3-ultra` → `inkling`).
- Continuaciones (tool results) → se quedan en el modelo físico que respondió
  (no rompe la caché de prompt ni la coherencia del turno).
- Fallo por cuota/rate-limit/404/5xx/proveedor caído → prueba el siguiente
  gratuito; si ya lo había intentado o el fallo es de cuenta, **escala a
  `deepseek/deepseek-v4.1-flash`** y se queda ahí durante el resto de la rama de
  sesión (sobrevive a compactación y forks).
- Requests directos (resúmenes de compactación) → directos al de pago.

### Verificado

| Prueba | Resultado |
|---|---|
| Sesión nueva con `--model openrouter/free-first` | despacha a `nemotron-3.5-lightning:free`, coste 0 |
| Cadena gratuita inexistente | rutó a `deepseek-v4.1-flash` y terminó (exit 0) |
| Gratuito que no soporta tools (inyección de fallo) | escaló a `deepseek-v4.1-flash` y terminó (exit 0) |
| Hijo `scout` con `model: openrouter/free-first` | `nemotron-3.5-lightning:free`, coste 0 |
| Hijos `scout`/`apk-smith` con el modelo **de config** | `nemotron-3.5-lightning:free`, coste 0 |

### Límites conocidos

- No detecta fallo de **calidad**: si el gratuito responde mal pero responde, no
  escala. Eso se resuelve cambiando de modelo a mano o subiendo la tarea a un
  agente de pago.
- Los hijos **no heredan** modelos virtuales: hay que cargar la extensión en el
  hijo con `subagents.defaultSubagentOnlyExtensions`. Estas rutas se resuelven
  contra el **cwd del hijo** (`getBaseDirForScope` de pi devuelve `cwd` para el
  scope `temporary`, que es como `pi-subagents` las pasa), no contra `.pi/`:
  `.pi/extensions/free-first-router.ts` funciona con cwd = raíz del repo, y desde
  otro cwd (worktree, subdirectorio) hay que usar ruta absoluta. Si la extensión
  no carga, el hijo envía `free-first` crudo a OpenRouter y falla con
  `400 free-first is not a valid model ID` (fallo ruidoso, no silencioso).
- Coste de caché: los modelos gratuitos de OpenRouter no facturan caché, así que
  aquí no aplica, pero tampoco la aprovechan igual (visto: `cacheRead: 0` en
  `north-mini-code`).

## 5. Decisión de enrutado

| Rol | Antes | Ahora | Motivo |
|---|---|---|---|
| `scout` | glm-5.3-flash | **`openrouter/free-first`** (low) | recon read-only, verificable, barato de repetir |
| `researcher` | glm-5.3-flash | **`openrouter/free-first`** (medium) | síntesis web; contexto de 1 M en los gratuitos |
| `delegate` (builtin) | deepseek-v4.1-flash | **`openrouter/free-first`** (medium) | delegación ligera |
| `evidence-auditor` (builtin) | glm-5.3-flash | **`openrouter/free-first`** (low) | comprobación de fuentes |
| `apk-smith` | glm-5.3-flash | **`openrouter/free-first`** (medium) | bucle mecánico de build/install; con failover |
| `worker` (builtin) | deepseek-v4.1-flash | sin cambio | implementación genérica, no se arriesga calidad |
| `android-dev`, `iptv-engineer`, `qa-reviewer`, `android-architect`, `ui-designer`, `brand-designer`, `reviewer`, `oracle` | — | sin cambio | juicio, corrección crítica o revisión independiente |
| Sesión principal | deepseek-v4-pro | sin cambio (opcional `free-first`) | el coste real es el contexto, no el modelo |

Presupuesto de referencia si se quiere ir más allá: pasar `android-dev` a
`free-first` es la siguiente prueba natural (7 runs, 0,41 USD de los 1,93 USD de
subagentes), pero conviene medir antes calidad de código real, no una tarea
mecánica.

## 6. Qué haría más por el gasto que cambiar de modelo

1. **Compactar o reiniciar sesión** cuando el contexto pase de ~100 k tokens: 92%
   del coste del orquestador son cache reads de contexto largo.
2. **No orquestar sobre `v4-pro`** en sesiones largas: la entrada cuesta ~100×
   más que `v4.1-flash`. Un `openrouter/free-first` de sesión principal, con
   escalada automática, es viable para días de exploración.
3. **Delegar en subagentes con contexto acotado** en vez de leer todo en la
   sesión principal: cada token que entra al orquestador se paga en cada turno
   siguiente.
