/**
 * free-first — modelo virtual con failover a un modelo de pago.
 *
 * Objetivo: usar modelos gratuitos de OpenRouter (`:free`) sin que una tarea se
 * quede a medias cuando el tier gratuito se agota o el proveedor falla.
 *
 * Cómo funciona:
 * - Cada turno nuevo de usuario intenta un modelo gratuito de la cadena.
 * - Las continuaciones (tool results) se quedan en el modelo físico que ya
 *   respondió: no se rompe la caché de prompt ni la coherencia del turno.
 * - Si un request falla por cuota / rate limit / proveedor caído, se prueba el
 *   siguiente gratuito; y si el fallo apunta a límite de cuenta o a un modelo ya
 *   de pago, se escala a `PAID_FALLBACK` y **se queda ahí** durante el resto de
 *   la rama de sesión (sobrevive a compactación y a forks).
 *
 * Límite conocido: no puede detectar un fallo de *calidad* (el gratuito responde
 * mal pero responde). Eso se resuelve cambiando de modelo a mano.
 *
 * Uso:
 *   pi -e ./.pi/extensions/free-first-router.ts --model openrouter/free-first
 * o como `model: openrouter/free-first` en un agente de .pi/agents/.
 */

import type {
	ExtensionAPI,
	ExtensionContext,
	ModelRoute,
	ModelRouteRequest,
} from "@earendil-works/pi-coding-agent";

const PROVIDER = "openrouter";

/** Cadena de modelos gratuitos, en orden de preferencia. */
const FREE_CHAIN = [
	"nvidia/nemotron-3.5-lightning:free",
	"cohere/north-mini-code:free",
	"nvidia/nemotron-3-ultra-550b-a55b:free",
	"thinkingmachines/inkling:free",
];

/** Red de seguridad cuando lo gratuito no está disponible. */
const PAID_FALLBACK = "deepseek/deepseek-v4.1-flash";

/** Fallos que significan "esto no se arregla reintentando gratis". */
const ESCALATE_PATTERN =
	/rate.?limit|429|too many requests|insufficient credits|402|403|404|\b400\b|quota|no provider|unknown model|not found|overload|capacity|unavailable|timed? ?out|timeout|ECONNRESET|socket hang up|\b5\d\d\b/i;

interface FreeFirstState {
	/** Ya se escaló a pago: no volver a intentar gratuito en esta rama. */
	escalated: boolean;
	/** Índice del siguiente candidato gratuito a probar. */
	next: number;
	/** Ídice del modelo que acaba de fallar, para no reintentarlo en bucle. */
	failedId?: string;
	/** Último error visto, para diagnóstico. */
	reason?: string;
}

type FreeFirstRequest = ModelRouteRequest<FreeFirstState>;
type Level = ModelRoute<FreeFirstState>["thinkingLevel"];

function resolve(ctx: ExtensionContext, id: string) {
	return ctx.modelRegistry.find(PROVIDER, id) ?? null;
}

function routeTo(
	ctx: ExtensionContext,
	id: string,
	thinkingLevel: Level,
	state: FreeFirstState,
): ModelRoute<FreeFirstState> {
	const model = resolve(ctx, id);
	if (!model) throw new Error(`free-first: ${PROVIDER}/${id} no está en el catálogo`);
	return { model, thinkingLevel, state };
}

/** Primer gratuito disponible del catálogo a partir de `start`, o null. */
function pickFree(ctx: ExtensionContext, start: number) {
	for (let i = 0; i < FREE_CHAIN.length; i++) {
		const index = (((start + i) % FREE_CHAIN.length) + FREE_CHAIN.length) % FREE_CHAIN.length;
		const model = resolve(ctx, FREE_CHAIN[index]);
		if (model) return { model, index };
	}
	return null;
}

export default function (pi: ExtensionAPI) {
	pi.registerVirtualModel<FreeFirstState>({
		provider: PROVIDER,
		id: "free-first",
		name: "Free-first (fallback a pago)",
		thinkingLevels: ["low", "medium", "high"],
		// Solo informativos: en cuanto responde un físico, mandan sus límites.
		contextWindow: 200_000,
		maxTokens: 32_000,
		route(request: FreeFirstRequest, ctx: ExtensionContext): ModelRoute<FreeFirstState> {
			const state: FreeFirstState = request.state ?? { escalated: false, next: 0 };

			// Continuaciones: seguir en el modelo físico que ya respondió.
			if (request.reason === "continuation" && request.previous) {
				return {
					model: request.previous.model,
					thinkingLevel: request.previous.thinkingLevel ?? request.thinkingLevel,
					state,
				};
			}

			// Reintento tras fallo: siguiente gratuito o escalada a pago.
			if (request.reason === "retry") {
				const error = request.failed?.message?.errorMessage ?? "";
				const failedId = request.failed?.model?.id ?? "";
				const failedWasPaid = failedId === PAID_FALLBACK;
				const transient = !ESCALATE_PATTERN.test(error);
				// Si ya reintentamos este mismo modelo, no insistir: a pago.
				const alreadyRetried = state.failedId === failedId;

				if (failedWasPaid || !transient || alreadyRetried) {
					return routeTo(ctx, PAID_FALLBACK, request.thinkingLevel, {
						...state,
						escalated: true,
						reason: error.slice(0, 200),
					});
				}

				const picked = pickFree(ctx, state.next + 1);
				if (picked && picked.model.id !== failedId) {
					return routeTo(ctx, picked.model.id, request.thinkingLevel, {
						...state,
						next: picked.index + 1,
						failedId,
						reason: error.slice(0, 200),
					});
				}
				return routeTo(ctx, PAID_FALLBACK, request.thinkingLevel, {
					...state,
					escalated: true,
					reason: error.slice(0, 200),
				});
			}

			// Requests fuera del bucle del agente (compactación, clasificadores).
			if (request.reason === "direct") {
				return routeTo(ctx, PAID_FALLBACK, request.thinkingLevel, state);
			}

			// Turno de usuario: gratuito, salvo que ya hayamos escalado.
			if (state.escalated) {
				return routeTo(ctx, PAID_FALLBACK, request.thinkingLevel, state);
			}
			const picked = pickFree(ctx, state.next);
			if (!picked) {
				return routeTo(ctx, PAID_FALLBACK, request.thinkingLevel, {
					...state,
					escalated: true,
					reason: "ningún modelo gratuito de la cadena está en el catálogo",
				});
			}
			return routeTo(ctx, picked.model.id, request.thinkingLevel, { ...state, next: picked.index + 1 });
		},
	});
}
