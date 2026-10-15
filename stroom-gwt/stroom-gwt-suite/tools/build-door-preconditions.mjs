#!/usr/bin/env node
/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//
// How do you get to every presenter the walk has NOT reached — and what has to be true first?
//
//   node stroom-gwt-suite/tools/build-door-preconditions.mjs
//     -> oracles/door-preconditions.json
//     -> oracles/door-preconditions.md
//
// The walk (stroom-gwt-suite/walk.mjs) reaches what a click reaches. The doors it has not reached are, by
// construction, the ones behind a PRECONDITION it does not know: a selected row, a permission, a
// mutation the read-only guard aborts, design mode, a feature switched off on this instance. A blind
// walk finds those by accident; the source states them. So for each unreached door this reads every
// opener site the recipe miner knows (`showEdges`: builder calls, single-handler events, `show(…)`
// helpers) and classifies the code around it:
//
//   selection   the show sits under `getSelected() != null`, `!selection.isMatchNothing()`,
//               `getSelectionModel().getSelected…`, `selectedItems.size() == 1`
//   create      a `create()`/`onNew()`/`add…()` path that builds a fresh entity and shows the editor —
//               reachable from a New/Add button with no selection
//   mutation    a REST create/save/delete/update happens BEFORE the show, so the guard aborts the
//               path before the dialog appears
//   permission  `hasAppPermission(…)` / `hasDocumentPermission(…)` / `securityContext` around it
//   design-mode `designMode` / `isDesignMode()` (dashboard components)
//   event       opened by an event whose firer is not a presenter the walk can stand on
//   plain       no guard found — the walk should have reached it, so it is a crawler or attribution
//               gap to look at, not a precondition
//
// Each class has a different remedy, and that is the point: seeding a selection is a walker feature;
// a mutation-gated dialog needs a mutation-permitted pass on scratch content; a permission-gated one
// is a fact about this instance's user; a feature switched off comes OFF the denominator.
//
// MINING PROPOSES, EXECUTION DISPOSES: a class here is a claim about the source, to be tested by
// seeding the precondition and walking. Read-only over the sibling source; no server involved.
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { SOURCE, oracle, outPath } from '../lib/paths.mjs';

const GWT = SOURCE;
const ROOTS = [
  'stroom-core-client/src/main/java/stroom',
  'stroom-core-client-widget/src/main/java/stroom',
  'stroom-statistics/stroom-statistics-client/src/main/java/stroom',
];

function* javaFiles(dir) {
  let entries;
  try { entries = readdirSync(dir); } catch { return; }
  for (const e of entries) {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) yield* javaFiles(p);
    else if (e.endsWith('.java')) yield p;
  }
}
const cls = (p) => p.split('/').pop().replace(/\.java$/, '');
const files = [];
for (const r of ROOTS) files.push(...javaFiles(join(GWT, r)));
const fileOf = new Map(files.map((f) => [cls(f), f]));
// A simple class name is NOT unique: `RulePresenter` is the dashboard's conditional-formatting rule
// editor AND the data-receipt rule editor, and `fileOf` kept whichever came last — so the dashboard
// RulePresenter's `customRowStylePresenter.show(…)` was never read and CustomRowStylePresenter sat
// as `opener-not-mined`. Five presenter names collide (TableFilter, EditExpression, Rule, Text,
// ProcessorTask); a shower's sites are read from EVERY file of that name.
const filesOf = new Map();
for (const f of files) { if (!filesOf.has(cls(f))) filesOf.set(cls(f), []); filesOf.get(cls(f)).push(f); }
const readSrc = (f) => readFileSync(f, 'utf8')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .split('\n').map((l) => l.replace(/^\s*\/\/.*$/, '')).join('\n');

// ── Inputs: the doors, what has been reached, and the edges ──────────────────
const reach = JSON.parse(readFileSync(oracle('reachability-graph.json'), 'utf8')).presenters;
const DOORS = new Set(['dialog', 'screen/dialog via plugin', 'document editor', 'screen', 'UNKNOWN']);
const coverageMd = readFileSync(outPath('coverage.md'), 'utf8');
const reached = new Set([...coverageMd.matchAll(/^- `([A-Za-z0-9_]+)` — \d+ place/gm)].map((m) => m[1]));
const recipes = JSON.parse(readFileSync(oracle('route-recipes.json'), 'utf8'));
const edges = recipes.showEdges ?? [];
const missing = reach.filter((p) => DOORS.has(p.how) && !reached.has(p.name));

// ── The method enclosing an offset, and the guards it carries ────────────────
/** The source of the method that contains `at`: from the nearest preceding method signature to it. */
function enclosingMethod(src, at) {
  const head = src.slice(0, at);
  // The last method signature before the site. Java: modifiers, return type, name, params, `{`.
  // Java methods here always carry an access modifier; without requiring one, `if (…) {` read as a
  // method called `if` and every class came out as `plain`.
  const sigs = [...head.matchAll(/\n[ \t]*(?:public|private|protected)\s+(?:static\s+|final\s+|synchronized\s+)*[A-Za-z_][A-Za-z0-9_<>[\], ?.]*\s+([a-zA-Z_][A-Za-z0-9_]*)\s*\([^;{)]*\)\s*(?:throws [^{]+)?\{/g)];
  const sig = sigs[sigs.length - 1];
  if (!sig) return { name: '?', body: head.slice(-1500) + src.slice(at, at + 200) };
  return { name: sig[1], body: src.slice(sig.index, at + 200) };
}

// SvgPresets: constant -> { title, enabled-at-rest }. A trigger that is a preset resolves to its
// title, and a preset declared `disabled(…)` is a button that needs a selection to light up.
const preset = new Map();
{
  const f = fileOf.get('SvgPresets');
  if (f) for (const m of readSrc(f).matchAll(/public\s+static\s+final\s+Preset\s+([A-Z0-9_]+)\s*=\s*(enabled|disabled)\([^,]+,\s*"([^"]*)"/g)) preset.set(m[1], { title: m[3], enabled: m[2] === 'enabled' });
}

/**
 * The button behind a method, through its handler VARIABLE:
 *   editButton = view.addButton(SvgPresets.EDIT.title("Edit Volume"));   … later …
 *   registerHandler(editButton.addClickHandler(event -> edit()));
 * The preset sits in the constructor and the handler in onBind(), hundreds of lines apart, which is
 * why a lookback from the call site found nothing for 34 of 45 doors.
 */
function triggerViaHandler(src, methodName) {
  const call = new RegExp(`([a-zA-Z_][A-Za-z0-9_]*)\\s*\\.\\s*add(?:Click|Selection|DoubleClick)?Handler\\(\\s*[^)]*?(?:->|::)\\s*${methodName}\\b`, 'g');
  for (const m of src.matchAll(call)) {
    const v = m[1];
    const decl = src.match(new RegExp(`${v}\\s*=[^;]*?SvgPresets\\s*\\.\\s*([A-Z0-9_]+)(?:\\s*\\.\\s*title\\(\\s*"([^"]*)"\\s*\\))?`));
    if (decl) {
      const pr = preset.get(decl[1]);
      return { label: decl[2] ?? pr?.title ?? `SvgPresets.${decl[1]}`, viaMenu: false, disabledAtRest: pr ? !pr.enabled : undefined };
    }
    const txt = src.match(new RegExp(`${v}\\s*=[^;]*?\\.text\\(\\s*"([^"]+)"`));
    if (txt) return { label: txt[1], viaMenu: true };
    if (/DoubleClick/.test(m[0])) return { label: 'double-click row', viaMenu: false };
  }
  return null;
}

/**
 * A UiHandler method — `onShowHistory()`, `onChangeConfig()` — is called from the VIEW:
 *   @UiHandler("history") public void onHistory(ClickEvent e) { getUiHandlers().onShowHistory(); }
 * and the button's words are in the ui.xml: `<b:Button ui:field="history" text="Conversation History"/>`.
 * So the presenter's method resolves through its ViewImpl to a ui:field to a label.
 */
const uiXml = new Map();
for (const r of ['stroom-core-client/src/main/resources', 'stroom-statistics/stroom-statistics-client/src/main/resources']) {
  for (const f of (function* walk(d) { let es; try { es = readdirSync(d); } catch { return; } for (const e of es) { const q = join(d, e); if (statSync(q).isDirectory()) yield* walk(q); else if (e.endsWith('.ui.xml')) yield q; } })(join(GWT, r))) {
    uiXml.set(f.split('/').pop().replace(/\.ui\.xml$/, ''), readFileSync(f, 'utf8'));
  }
}
function triggerViaView(shower, methodName) {
  const viewName = shower.replace(/Presenter$/, 'ViewImpl');
  const vf = fileOf.get(viewName);
  if (!vf) return null;
  const vsrc = readSrc(vf);
  const call = new RegExp(`getUiHandlers\\(\\)\\s*\\.\\s*${methodName}\\s*\\(`);
  const m = call.exec(vsrc);
  if (!m) return null;
  const before = vsrc.slice(Math.max(0, m.index - 400), m.index);
  const field = [...before.matchAll(/@UiHandler\(\s*"([^"]+)"\s*\)/g)].pop()?.[1]
    ?? [...before.matchAll(/([a-zA-Z_][A-Za-z0-9_]*)\s*\.\s*addClickHandler\(/g)].pop()?.[1];
  if (!field) return null;
  const xml = uiXml.get(viewName) ?? '';
  const tag = new RegExp(`<[^>]*ui:field="${field}"[^>]*>`).exec(xml)?.[0] ?? '';
  const label = /\btext="([^"]+)"/.exec(tag)?.[1] ?? /\btitle="([^"]+)"/.exec(tag)?.[1] ?? null;
  return { label: label ?? `ui:field ${field}`, viaMenu: false };
}

/** Bounded lookback for the trigger — the button or menu item whose handler leads to the show. */
function triggerNear(src, at, methodName, shower) {
  const viaHandler = triggerViaHandler(src, methodName);
  if (viaHandler) return viaHandler;
  const viaView = shower ? triggerViaView(shower, methodName) : null;
  if (viaView) return viaView;
  // A handler that calls the method by name: `addButton(SvgPresets.NEW).addClickHandler(e -> create())`
  // or `.command(this::create)` or `.command(() -> create())` — find the preset/text just before it.
  // `-> create()`, `::create`, `addClickHandler(e -> create())`, and a double-click on a row
  // (`onOpen` / `addDoubleClickHandler`) which is a selection in disguise.
  const call = new RegExp(`(?:->\\s*|::|\\.)\\s*${methodName}\\s*\\(?`, 'g');
  let best = null;
  for (const m of src.matchAll(call)) {
    const before = src.slice(Math.max(0, m.index - 400), m.index);
    const preset = [...before.matchAll(/SvgPresets\s*\.\s*([A-Z0-9_]+)(?:\s*\.\s*title\(\s*"([^"]*)"\s*\))?/g)].pop();
    const text = [...before.matchAll(/\.text\(\s*"([^"]+)"\s*\)/g)].pop();
    const title = [...before.matchAll(/\.title\(\s*"([^"]+)"\s*\)/g)].pop();
    const menu = /IconMenuItem|MenuItem\.Builder|addMenuItem|ShowMenuEvent/.test(before);
    const dbl = /DoubleClick|onOpen|OpenEvent|CellPreview/.test(before);
    const label = text?.[1] ?? title?.[1] ?? preset?.[2] ?? (preset ? `SvgPresets.${preset[1]}` : (dbl ? 'double-click row' : null));
    if (label) { best = { label, viaMenu: menu }; break; }
  }
  if (best) return best;
  const before = src.slice(Math.max(0, at - 600), at);
  const preset = [...before.matchAll(/SvgPresets\s*\.\s*([A-Z0-9_]+)(?:\s*\.\s*title\(\s*"([^"]*)"\s*\))?/g)].pop();
  const text = [...before.matchAll(/\.text\(\s*"([^"]+)"\s*\)/g)].pop();
  const label = text?.[1] ?? preset?.[2] ?? (preset ? `SvgPresets.${preset[1]}` : null);
  return label ? { label, viaMenu: /IconMenuItem|addMenuItem|ShowMenuEvent/.test(before) } : null;
}

const SIGNALS = [
  ['selection', /getSelected\(\)\s*!=\s*null|!\s*[a-zA-Z_.]*isMatchNothing\(\)|getSelectionModel\(\)\s*\.\s*getSelected|selectedItems?\s*\.\s*size\(\)\s*[=!<>]|getSelectedItems\(\)|hasSelected|selection\.(size|is)|\bselected\s*!=\s*null/],
  ['permission', /hasAppPermission\(|hasDocumentPermission\(|securityContext\.|isAdmin\(\)|AppPermission\./],
  ['design-mode', /designMode|isDesignMode\(\)|DesignMode/],
  ['mutation', /\.method\(\s*res\s*->\s*res\s*\.\s*(create|save|update|delete|remove|import|upload|move|copy|rename)\w*\(/],
  ['create', /\b(create|onNew|onCreate|add(?:New)?[A-Z]\w*|newItem|onAdd)\s*\(/],
  ['event', /\.fire\(\s*(this|[A-Za-z]+\.this)\s*,/],
  ['enabled-gate', /setEnabled\(\s*(false|[a-z][A-Za-z]*\s*!=\s*null|[a-z][A-Za-z]*\.size\(\))|\.enabled\(\s*(false|[a-z])/],
];

function classify(body, methodName) {
  const found = [];
  for (const [name, re] of SIGNALS) if (re.test(body)) found.push(name);
  // A create/new METHOD name is a strong signal even without a call inside it.
  if (/^(create|onNew|onCreate|add[A-Z]?\w*|newItem)$/i.test(methodName) && !found.includes('create')) found.unshift('create');
  // Priority: what BLOCKS the walk today decides the class.
  for (const c of ['mutation', 'permission', 'design-mode', 'selection', 'create', 'event', 'enabled-gate']) if (found.includes(c)) return { primary: c, signals: found };
  return { primary: 'plain', signals: found };
}

// ── Walk every opener site of every missing door ─────────────────────────────
/** Declared variable -> type, with Provider<T> -> T and generics stripped; plus `a = b;` aliases. */
function typesIn(src) {
  const typeOf = new Map();
  for (const d of src.matchAll(/(?:private|protected|public)?\s*(?:final\s+)?(?:Provider\s*<\s*([A-Za-z_][A-Za-z0-9_]*)\s*>|([A-Za-z_][A-Za-z0-9_]*)(?:\s*<[^<>;=()]*>)?)\s+([a-zA-Z_][A-Za-z0-9_]*)\s*[;=),]/g)) {
    typeOf.set(d[3], d[1] ?? d[2]);
  }
  for (const a of src.matchAll(/([a-zA-Z_][A-Za-z0-9_]*)\s*=\s*([a-zA-Z_][A-Za-z0-9_]*)\s*;/g)) {
    if (!typeOf.has(a[1]) || !/Presenter$/.test(typeOf.get(a[1]) ?? '')) { const t = typeOf.get(a[2]); if (t) typeOf.set(a[1], t); }
  }
  return typeOf;
}

/** Every site in `src` that shows `door`: builder(x)/x.show(…) with x typed as the door, or its event. */
function sitesFor(src, door, handlerEvents) {
  const typeOf = typesIn(src);
  // `getChangeStatusPresenter().show(list)` — the receiver is a same-file GETTER (a lazy
  // `if (x == null) x = provider.get(); return x;`), typed by its return type. AnnotationManager
  // opens ChangeStatus and ChangeAssignedTo this way and neither site was ever found.
  for (const g of src.matchAll(/(?:private|protected|public)?\s*(?:static\s+)?([A-Z][A-Za-z0-9_]*)\s+([a-z][A-Za-z0-9_]*)\s*\(\s*\)\s*\{/g)) typeOf.set(`${g[2]}()`, g[1]);
  const out = [];
  // `x.show(`, `x().show(`, `provider.get().show(`, `builder(x)`, `builder(provider.get())`, `XEvent.fire(`.
  const re = /ShowPopupEvent\s*\.\s*(?:builder|Builder)\(\s*([A-Za-z_][A-Za-z0-9_.]*?)(?:\s*\.\s*get\(\s*\))?\s*\)|([a-zA-Z_][A-Za-z0-9_]*(?:\(\s*\))?)(?:\s*\.\s*get\(\s*\))?\s*\.\s*(?:show|open|setupPopup)\(|([A-Za-z0-9_]+Event)\s*\.\s*fire\(/g;
  // `provider.get().show(…)` and `builder(provider.get())` resolve through the provider variable.
  const resolves = (name) => {
    if (!name) return false;
    const base = name.split('.')[0].replace(/\s+/g, '');
    if (base === 'this') return false;
    return typeOf.get(base) === door || base === door;
  };
  for (const m of src.matchAll(re)) {
    let concerns = false;
    if (m[1]) concerns = resolves(m[1]);
    else if (m[2]) concerns = resolves(m[2]);
    else if (m[3]) concerns = handlerEvents.has(m[3]);
    if (concerns) out.push(m.index);
  }
  return out;
}

// Events the door handles: `implements XEvent.Handler` in the door's own file.
const handlerEventsOf = (door) => {
  const f = fileOf.get(door);
  if (!f) return new Set();
  const src = readSrc(f);
  const ev = new Set();
  for (const m of src.matchAll(/implements\s+([A-Za-z0-9_.\s,<>]+?)\s*\{/g)) for (const part of m[1].split(',')) { const h = /^([A-Za-z0-9_]+)\.Handler$/.exec(part.trim()); if (h) ev.add(h[1]); }
  return ev;
};

const out = [];
for (const door of missing) {
  const sites = [];
  // Show edges first; failing those, the reachability graph's own openers (`via` — whoever holds a
  // Provider for the door), which is how 23 doors with no mined edge still get their sites read.
  let showers = [...new Set(edges.filter((e) => e.presenter === door.name).map((e) => e.shower))];
  if (!showers.length && door.via) showers = door.via.split(',').map((v) => v.trim()).filter((v) => fileOf.has(v));
  // A door that shows ITSELF has its real opener in whoever calls its `show(…)`; the self edge
  // says nothing about the precondition. Drop it when there are others, else go and find callers.
  const external = showers.filter((s) => s !== door.name);
  if (external.length) showers = external;
  else if (showers.length) {
    showers = files.filter((f) => cls(f) !== door.name && new RegExp(`\\b${door.name}\\b`).test(readSrc(f))
      && /\.(?:show|open|setupPopup)\(|ShowPopupEvent/.test(readSrc(f))).map(cls);
  }
  const handlerEvents = handlerEventsOf(door.name);
  for (const shower of showers) {
    for (const f of filesOf.get(shower) ?? []) {
    const src = readSrc(f);
    for (const at of sitesFor(src, door.name, handlerEvents)) {
      const { name, body } = enclosingMethod(src, at);
      const c = classify(body, name);
      const trigger = triggerNear(src, at, name, shower);
      let label = trigger?.label ?? null;
      let disabledAtRest = trigger?.disabledAtRest;
      const pm = label && /^SvgPresets\.([A-Z0-9_]+)$/.exec(label);
      if (pm && preset.has(pm[1])) { label = preset.get(pm[1]).title; disabledAtRest = !preset.get(pm[1]).enabled; }
      // A button disabled at rest is a selection gate in disguise.
      if (disabledAtRest && c.primary === 'plain') { c.primary = 'selection'; c.signals.push('disabled-at-rest'); }
      sites.push({ shower, method: name, ...c, trigger: label, viaMenu: trigger?.viaMenu ?? false, disabledAtRest });
    }
    }
  }
  // Dedupe by (shower, method).
  const seen = new Set();
  const uniq = sites.filter((s) => { const k = `${s.shower}#${s.method}`; if (seen.has(k)) return false; seen.add(k); return true; });
  // The door's overall class is the EASIEST of its sites: one create path makes it reachable.
  const order = ['plain', 'create', 'selection', 'enabled-gate', 'event', 'design-mode', 'permission', 'mutation'];
  const easiest = uniq.map((s) => s.primary).sort((a, b) => order.indexOf(a) - order.indexOf(b))[0];
  // A door with no show SITE but whose only openers are PLUGINS is not shown at all — it is a
  // screen a plugin creates: a menu screen (reached by its menu leaf), a document editor, or a
  // dashboard COMPONENT type, which exists only where a dashboard holds a component of that type.
  let klass = uniq.length ? easiest : (showers.length ? 'opener-not-mined' : 'no-opener');
  if (!uniq.length && showers.length && showers.every((s) => /Plugin$/.test(s))) {
    const src = showers.map((s) => readSrc(fileOf.get(s))).join('\n');
    // A `*TabPresenterPlugin` opens a content tab in answer to an EVENT, not a menu leaf: the
    // stream preview/source tabs come from `ShowDataEvent` (a `View Source` label under a selected
    // stream, `Open Stream` on a server task with a stream, a stream hyperlink in a result). Filing
    // them as menu screens said "attribution gap" for two doors that needed a seed.
    klass = /ComponentRegistry|registerComponent/.test(src) ? 'needs-component'
      : /extends\s+DocumentPlugin/.test(src) ? 'document-editor'
        : /extends\s+AbstractTabPresenterPlugin/.test(src) ? 'event' : 'menu-screen';
  }
  out.push({ door: door.name, how: door.how, area: door.area, via: door.via, reachedParent: showers.filter((s) => reached.has(s)),
    class: klass, sites: uniq });
}

// ── Report ───────────────────────────────────────────────────────────────────
const byClass = {};
for (const d of out) byClass[d.class] = (byClass[d.class] ?? 0) + 1;
const lines = [];
lines.push('# Door preconditions — how to reach every presenter the walk has not');
lines.push('');
lines.push('**Generated** by `stroom-gwt-suite/tools/build-door-preconditions.mjs` from the Stroom source, the');
lines.push('reachability graph, the mined show edges and the current coverage ledger. Do not hand-edit.');
lines.push('');
lines.push(`${missing.length} doors unreached. Each is classified by the EASIEST of its opener sites — one create path`);
lines.push('makes a door reachable however many selection-gated paths it also has.');
lines.push('');
lines.push('| class | doors | what it means | remedy |');
lines.push('| --- | ---: | --- | --- |');
const meaning = {
  'plain': ['no guard found around the show', 'a walker or attribution gap — look at it'],
  'create': ['a New/Add path builds a fresh entity and shows the editor', 'the walk\'s New/Delete chrome rule hides screen-level New buttons; scope chrome to the explorer pane'],
  'selection': ['shown only when a row is selected', 'seed a selection (post-action) before the click'],
  'enabled-gate': ['the button is disabled at rest', 'usually a selection in disguise'],
  'event': ['opened by an event the walk cannot stand on', 'find the firer\'s trigger'],
  'design-mode': ['dashboard design mode', 'enter design mode as a seeded post-action'],
  'permission': ['gated by an app or document permission', 'a fact about this user — walk as an admin or accept'],
  'mutation': ['a create/save/delete happens BEFORE the show', 'needs a mutation-permitted pass on scratch content'],
  'opener-not-mined': ['edges name a shower whose site could not be re-found', 'miner gap'],
  'needs-component': ['a dashboard component TYPE; exists only where a dashboard holds one', 'seed a dashboard with every component type (design mode is client-side, no mutation needed until Save)'],
  'menu-screen': ['a screen a plugin opens from a menu leaf', 'walked already if its leaf is — an attribution gap'],
  'document-editor': ['a document editor', 'walked if a document of the type exists'],
  'no-opener': ['no show edge at all', 'reachability research, or unwired (denominator)'],
};
for (const [c, n] of Object.entries(byClass).sort((a, b) => b[1] - a[1])) lines.push(`| ${c} | ${n} | ${meaning[c]?.[0] ?? ''} | ${meaning[c]?.[1] ?? ''} |`);
lines.push('');
for (const c of Object.keys(byClass).sort((a, b) => byClass[b] - byClass[a])) {
  lines.push(`## ${c} (${byClass[c]})`);
  lines.push('');
  for (const d of out.filter((d) => d.class === c).sort((a, b) => a.area.localeCompare(b.area) || a.door.localeCompare(b.door))) {
    const s = d.sites[0];
    lines.push(`* **${d.door}** _(${d.area}, ${d.how})_${d.reachedParent.length ? ` — parent reached: ${d.reachedParent.join(', ')}` : ''}`);
    for (const site of d.sites.slice(0, 3)) {
      lines.push(`  - ${site.shower}.${site.method}() → ${site.primary}${site.signals.length > 1 ? ` [${site.signals.join(', ')}]` : ''}${site.trigger ? ` · trigger: \`${site.trigger}\`${site.viaMenu ? ' (menu)' : ''}` : ''}`);
    }
    if (!d.sites.length) lines.push(`  - via: ${d.via || '(none)'}`);
    void s;
  }
  lines.push('');
}
writeFileSync(oracle('door-preconditions.md'), lines.join('\n'));
writeFileSync(oracle('door-preconditions.json'), JSON.stringify({ generated: new Date().toISOString(), byClass, doors: out }, null, 1));
console.log(`${missing.length} unreached doors classified:`, JSON.stringify(byClass), '-> oracles/door-preconditions.md');
