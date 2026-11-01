#!/usr/bin/env node
// Verifica la configuración de pi: settings, agentes, skills y modelos.
// Uso: node scripts/check-config.mjs [raiz-del-proyecto]
// Sin dependencias. Exit 1 si hay FAIL.

import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const proj = path.resolve(process.argv[2] ?? ".");
const home = path.join(os.homedir(), ".pi", "agent");
const results = [];
const check = (ok, where, msg) => results.push({ ok, where, msg });

// --- catálogo de modelos (models-store.json) ---
let catalogIds = new Set();
try {
  const store = JSON.parse(fs.readFileSync(path.join(home, "models-store.json"), "utf8"));
  const walk = (o) => {
    if (!o || typeof o !== "object") return;
    if (o.type === "chat" && o.id) catalogIds.add(o.id);
    for (const v of Object.values(o)) if (v && typeof v === "object") walk(v);
  };
  walk(store);
} catch {
  check(false, "catalog", "no se pudo leer ~/.pi/agent/models-store.json");
}

const modelResolves = (ref) => {
  // Formatos válidos: "provider/vendor/model" o "vendor/model" (bare, openrouter)
  if (catalogIds.has(ref)) return true;
  const bare = ref.includes("/") ? ref.slice(ref.indexOf("/") + 1) : ref;
  return catalogIds.has(bare);
};

// --- settings.json (global y proyecto) ---
const readJson = (p) => {
  try {
    return JSON.parse(fs.readFileSync(p, "utf8"));
  } catch (e) {
    check(false, path.relative(proj, p), `JSON inválido: ${e.message}`);
    return null;
  }
};
const gs = readJson(path.join(home, "settings.json"));
const ps = readJson(path.join(proj, ".pi", "settings.json"));

// defaultModel de sesión principal: ID PELADO (sin prefijo de proveedor)
for (const [label, s] of [["global", gs], ["proyecto", ps]]) {
  if (!s) continue;
  const dm = s.defaultModel;
  if (dm === undefined) continue;
  const bareOk = !dm.startsWith(s.defaultProvider + "/");
  check(bareOk, `settings(${label}).defaultModel`,
    bareOk ? `"${dm}" (formato pelado correcto)` : `"${dm}" lleva prefijo de proveedor: pi NO lo resuelve y cae al fallback (kimi-k2.6)`);
  check(modelResolves(dm), `settings(${label}).defaultModel`, `resuelve en catálogo: "${dm}"`);
  const lv = s.defaultThinkingLevel;
  if (lv && !["off", "minimal", "low", "medium", "high", "xhigh", "max"].includes(lv))
    check(false, `settings(${label}).defaultThinkingLevel`, `nivel inválido: ${lv}`);
}

// --- agentes definidos en el proyecto (.pi/agents/*.md) ---
const THINKING = ["off", "minimal", "low", "medium", "high", "xhigh", "max"];
const agentFiles = fs.existsSync(path.join(proj, ".pi", "agents"))
  ? fs.readdirSync(path.join(proj, ".pi", "agents")).filter((f) => f.endsWith(".md"))
  : [];
const agentNames = new Set(agentFiles.map((f) => f.replace(/\.md$/, "")));
// builtins conocidos de pi/pi-subagents
const builtins = new Set(["scout", "researcher", "worker", "reviewer", "oracle", "delegate", "evidence-auditor"]);

for (const f of agentFiles) {
  const src = fs.readFileSync(path.join(proj, ".pi", "agents", f), "utf8");
  const fm = src.match(/^---\n([\s\S]*?)\n---/);
  if (!fm) { check(false, `agent:${f}`, "sin frontmatter"); continue; }
  const get = (k) => fm[1].match(new RegExp(`^${k}:\\s*(.+)$`, "m"))?.[1]?.trim();
  const model = get("model");
  if (model && model !== "inherit") {
    check(modelResolves(model), `agent:${f}`, `model "${model}" ${modelResolves(model) ? "resuelve" : "NO está en el catálogo"}`);
    if (model.split("/").length < 3)
      check(false, `agent:${f}`, `model "${model}" sin prefijo de proveedor (los agentes requieren provider/vendor/model)`);
  }
  const thinking = get("thinking");
  if (thinking && !THINKING.includes(thinking)) check(false, `agent:${f}`, `thinking inválido: ${thinking}`);
  const skills = get("skills");
  if (skills) {
    for (const s of skills.split(",").map((x) => x.trim()).filter(Boolean)) {
      const found =
        fs.existsSync(path.join(proj, ".pi", "skills", s, "SKILL.md")) ||
        fs.existsSync(path.join(home, "skills", s, "SKILL.md")) ||
        fs.existsSync(path.join(os.homedir(), ".agents", "skills", s, "SKILL.md"));
      check(found, `agent:${f}`, `skill "${s}" ${found ? "encontrada" : "NO existe en .pi/skills, ~/.pi/agent/skills ni ~/.agents/skills"}`);
    }
  }
}

// --- agentOverrides: nombres y modelos ---
for (const [label, s] of [["global", gs], ["proyecto", ps]]) {
  const ov = s?.subagents?.agentOverrides;
  if (!ov) continue;
  for (const [name, o] of Object.entries(ov)) {
    check(agentNames.has(name) || builtins.has(name),
      `settings(${label}).agentOverrides.${name}`,
      agentNames.has(name) || builtins.has(name) ? "agente existe" : "NO hay agente con ese nombre (override muerto)");
    if (o.model) check(modelResolves(o.model), `settings(${label}).agentOverrides.${name}`, `model "${o.model}"`);
    if (o.model && o.model.split("/").length < 3)
      check(false, `settings(${label}).agentOverrides.${name}`, `model "${o.model}" sin prefijo de proveedor`);
    if (o.thinking && !THINKING.includes(o.thinking)) check(false, `settings(${label}).agentOverrides.${name}`, `thinking inválido: ${o.thinking}`);
  }
}

// --- subagents.defaultModel: pelado o fully-qualified, ambos válidos ---
for (const [label, s] of [["global", gs], ["proyecto", ps]]) {
  const m = s?.subagents?.defaultModel;
  if (m) check(modelResolves(m), `settings(${label}).subagents.defaultModel`, `"${m}"`);
}

// --- informe ---
let fails = 0;
for (const r of results) {
  if (!r.ok) fails++;
  console.log(`${r.ok ? "OK  " : "FAIL"}  ${r.where.padEnd(46)} ${r.msg}`);
}
console.log(`\n${results.length - fails} OK, ${fails} FAIL`);
process.exit(fails ? 1 : 0);
