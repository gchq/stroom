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

// How is every GWT screen and dialog REACHED? Written to oracles/reachability-graph.md.
//
// `gwt-inventory.csv` says WHAT exists — 414 presenters in Screen/Dialog and Shared Screen/Dialog.
// It does not say how you get to one, and a test suite cannot photograph a screen it cannot reach.
// The suite's 184 recorded targets are all NAVIGABLE destinations (a menu leaf, a document, a tab);
// everything else is reached by doing something, and until now "everything else" was an estimate with
// a tilde in front of it.
//
// This derives the entry points from the GWT client source, so it is a measurement rather than a
// list somebody maintains:
//
//   - `extends DocumentPlugin`     -> a document editor, opened from the explorer
//   - `extends ContentTabPlugin`   -> a screen, opened from the main menu
//   - `ShowPopupEvent.builder(x)`  -> a DIALOG, and the file raising it is its PARENT
//   - `ShowMenuEvent`              -> a context menu, itself an entry point to more
//
// The `ShowPopupEvent` edges are the interesting part: they form a directed graph whose roots are
// screens and whose depth is how many clicks from a destination a dialog sits. That graph is the
// shape a branching test plan has to follow, and it cannot be guessed from the React side because
// the port collapses some presenters into one component.
//
// Run: node stroom-stroom-gwt-suite/tools/build-reachability-graph.mjs
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { SOURCE, oracle } from '../lib/paths.mjs';

const GWT = SOURCE;
// EVERY module holding GWT client code, not just the obvious two. Statistics lives in its own module
// (stroom-statistics-client), and scanning only stroom-core-client made its five presenters look
// unrouted when in fact StatisticsPlugin reaches them — a whole area misreported as a research
// backlog because the file scan was too narrow. Derived by `find . -name '*Presenter.java'`.
const ROOTS = [
  'stroom-core-client/src/main/java',
  'stroom-core-client-widget/src/main/java',
  'stroom-statistics/stroom-statistics-client/src/main/java',
  'stroom-app-gwt/src/main/java',
];
const OUT = oracle('reachability-graph.md');

/** Every .java file under the client source roots. */
function* javaFiles(dir) {
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return;
  }
  for (const e of entries) {
    const p = join(dir, e);
    const s = statSync(p);
    if (s.isDirectory()) yield* javaFiles(p);
    else if (e.endsWith('.java')) yield p;
  }
}

const files = [];
for (const r of ROOTS) files.push(...javaFiles(join(GWT, r)));

const cls = (p) => p.split('/').pop().replace(/\.java$/, '');
/**
 * Read a file with its comments removed. FeedDependencyListPresenter's `ShowPopupEvent.builder(this)`
 * is COMMENTED OUT, and reading it raw made an embedded list pane a self-shown dialog with no opener
 * — a door nothing could ever reach. Same reader as build-route-recipes.mjs, for the same reason.
 */
const readSrc = (f) => readFileSync(f, 'utf8')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .split('\n').map((l) => l.replace(/^\s*\/\/.*$/, '')).join('\n');

const isPlaceBased = new Set();     // GWTP: revealed by a place/proxy, not opened by a parent
const isAbstract = new Set();       // abstract base classes are never instantiated: not doors
const embeddedIn = new Map();       // presenter -> presenters that hold it as a field/param
const documentPlugins = [];
const contentPlugins = [];
const showsMenu = [];
/** parent class -> Set of dialog expressions it shows */
const edges = new Map();
/**
 * Classes that ARE dialogs but whose popup site names no parent. Two shapes:
 *   - `ShowPopupEvent.builder(this)`     — shows itself
 *   - `void setupDialog(.., ShowPopupEvent.Builder b)` — RECEIVES a builder and configures it, so the
 *     caller does the showing. Missed entirely at first, which left ContentStoreCredentialsDialog
 *     looking unrouted when it is simply opened a different way (11 classes use this shape).
 * Either way the parent is whoever holds a Provider for them, resolved below.
 */
const selfShown = new Set();

for (const f of files) {
  const src = readSrc(f);
  const name = cls(f);

  if (/\babstract\s+class\s+/.test(src)) isAbstract.add(name);
  // GWTP place-based: the presenter declares a Proxy and is revealed by navigation rather than by
  // anybody calling show(). Login, AuthenticationError and ResetPassword are all of this shape — they
  // appear because of the SESSION's state, which is why no parent references them and why the crawler
  // will never find them by clicking.
  if (/extends\s+MyPresenter\b/.test(src) && /\bProxy\b/.test(src)) isPlaceBased.add(name);
  if (/extends\s+DocumentPlugin\b/.test(src)) documentPlugins.push(name);
  if (/extends\s+(ContentTabPlugin|MonitoringPlugin|NodeToolsPlugin)\b/.test(src)) contentPlugins.push(name);
  if (/ShowMenuEvent/.test(src)) showsMenu.push(name);
  // The builder-as-parameter shape: this class is a dialog, shown by whoever passes the builder.
  if (/ShowPopupEvent\.Builder\s+[a-z]/.test(src)) selfShown.add(name);

  // Resolve a shown VARIABLE to its declared type. The call site says
  // `ShowPopupEvent.builder(settingsPresenter)`, which names a field; the field's declaration says
  // what it actually is, including through `Provider<X>`. Without this the graph is a list of local
  // variable names and cannot be joined to anything.
  const typeOf = new Map();
  for (const d of src.matchAll(
    /(?:private|protected|public)?\s*(?:final\s+)?(?:Provider<)?([A-Za-z_][A-Za-z0-9_]*)>?\s+([a-zA-Z_][A-Za-z0-9_]*)\s*[;=)]/g)) {
    typeOf.set(d[2], d[1]);
  }

  // `new ShowPopupEvent.Builder(editAssetDialog)` is the same site spelt as a constructor — the
  // visualisation assets editor is shown ONLY this way and had no parent edge.
  for (const m of src.matchAll(/ShowPopupEvent\s*\.\s*(?:builder|Builder)\(\s*([A-Za-z_][A-Za-z0-9_.]*)/g)) {
    const shown = m[1];
    if (shown === 'this') {
      selfShown.add(name);
    } else {
      const base = shown.split('.')[0];
      const resolved = typeOf.get(base) ?? base;
      if (!edges.has(name)) edges.set(name, new Set());
      edges.get(name).add(resolved);
    }
  }
}

// ── Who opens the dialogs that show themselves? ──────────────────────────────
// A self-shown dialog is raised by whoever holds a Provider for it and calls show()/getView(). The
// source does name that relationship, just not at the ShowPopupEvent site, so it is recoverable
// rather than manual: find every file that declares a Provider<ThatPresenter> or constructs one.
//
// Holding is not opening. IndexFieldEditPresenter holds a DenseVectorFieldPresenter and puts its
// VIEW into its own form (`view.setDenseVectorOptions(p.getView())`); the `show()` that presenter
// also carries is never called by anyone. Counting the holder as an opener made an embedded panel a
// door. So a holder is an opener only when it INVOKES the thing — `x.show(`, `x.open(`,
// `x.setupPopup(`, `builder(x)`, `new Builder(x)`, or through `provider.get()` — and a holder that
// only places the view is recorded as a host, which is what "embedded panel" means.
//
// And a dialog that answers an EVENT is opened by whoever fires it: CreateDocumentPresenter,
// ImportConfigPresenter, DependenciesInfoPresenter and eleven more are `@ProxyEvent` handlers whose
// only "holder" is the Ginjector's `AsyncProvider<X> getX();` — a method, which no variable regex
// reads. An event with exactly one self-showing handler is an edge (the recipe miner's rule).
const handlerOf = new Map();
for (const f of files) {
  const src = readSrc(f);
  const name = cls(f);
  for (const m of src.matchAll(/implements\s+([A-Za-z0-9_.\s,<>]+?)\s*\{/g)) {
    for (const part of m[1].split(',')) {
      const h = /^([A-Za-z0-9_]+)\.Handler$/.exec(part.trim());
      if (h) {
        if (!handlerOf.has(h[1])) handlerOf.set(h[1], new Set());
        handlerOf.get(h[1]).add(name);
      }
    }
  }
}
const openers = new Map();
const hosts = new Map();
for (const f of files) {
  const src = readSrc(f);
  const name = cls(f);
  for (const m of src.matchAll(/([A-Za-z0-9_]+Event)\s*\.\s*fire\(/g)) {
    if (m[1] === 'ShowPopupEvent') continue;
    const hs = [...(handlerOf.get(m[1]) ?? [])].filter((h) => selfShown.has(h) && h !== name);
    if (hs.length !== 1) continue;
    if (!openers.has(hs[0])) openers.set(hs[0], new Set());
    openers.get(hs[0]).add(name);
  }
  for (const d of selfShown) {
    if (d === name) continue;
    const vars = [...src.matchAll(new RegExp(`(?:Provider\\s*<\\s*${d}\\s*>|\\b${d})\\s+([a-z][A-Za-z0-9_]*)\\s*[;=),]`, 'g'))].map((m) => m[1]);
    if (!vars.length) {
      // Held some other way (a Ginjector's `AsyncProvider<X> getX();`) — the old, conservative reading,
      // kept as the last resort so nothing that was routable becomes unrouted by this refinement.
      if (!openers.has(d) && new RegExp(`Provider<\\s*${d}\\s*>`).test(src)) {
        if (!openers.has(d)) openers.set(d, new Set());
        openers.get(d).add(name);
      }
      continue;
    }
    const v = `(?:${vars.join('|')})(?:\\s*\\.\\s*get\\(\\s*\\))?`;
    const invokes = new RegExp(`\\b${v}\\s*\\.\\s*(?:show|open|setupPopup|showEntity)\\(|ShowPopupEvent\\s*\\.\\s*(?:builder|Builder)\\(\\s*${v}\\s*\\)`).test(src);
    const embeds = new RegExp(`\\b${v}\\s*\\.\\s*getView\\(\\)|\\.(?:add|setWidget|setInSlot|addToSlot)\\(\\s*${v}\\s*[,)]`).test(src);
    if (invokes) {
      if (!openers.has(d)) openers.set(d, new Set());
      openers.get(d).add(name);
    } else if (embeds) {
      if (!hosts.has(d)) hosts.set(d, new Set());
      hosts.get(d).add(name);
    } else {
      // Held but neither invoked nor placed in a way this reads — keep the old, conservative reading.
      if (!openers.has(d)) openers.set(d, new Set());
      openers.get(d).add(name);
    }
  }
}

// A presenter held as a field or constructor parameter by ANOTHER presenter is an embedded panel —
// a settings tab, a list pane, a toolbar — not a door. It is covered when its parent screen is
// covered, and its behaviour is Layer 4b's business rather than the crawler's.
for (const f of files) {
  const name = cls(f);
  if (!/Presenter$/.test(name)) continue;
  const src = readSrc(f);
  for (const m of src.matchAll(/\b([A-Z][A-Za-z0-9_]*Presenter)\b/g)) {
    if (m[1] === name) continue;
    if (!embeddedIn.has(m[1])) embeddedIn.set(m[1], new Set());
    embeddedIn.get(m[1]).add(name);
  }
}

// ── Join: for each of the 414 presenters, HOW is it reached? ─────────────────
// This is the crawler's target list. Without it "cover everything" has no denominator and no way to
// tell a screen the crawler missed from one that is not reachable at all.
const isScreen = new Set(contentPlugins);
const isDocEditor = new Set(documentPlugins);
const childOf = new Map();          // dialog -> parents, from explicit edges
for (const [parent, kids] of edges) {
  for (const k of kids) {
    if (!childOf.has(k)) childOf.set(k, new Set());
    childOf.get(k).add(parent);
  }
}

/** Everything a plugin names, so a presenter owned by a Plugin counts as menu-reachable. */
const namedByPlugin = new Map();
for (const f of files) {
  const name = cls(f);
  if (!/Plugin$/.test(name)) continue;
  const src = readSrc(f);
  for (const m of src.matchAll(/\b([A-Z][A-Za-z0-9_]*Presenter)\b/g)) {
    if (!namedByPlugin.has(m[1])) namedByPlugin.set(m[1], new Set());
    namedByPlugin.get(m[1]).add(name);
  }
}

/**
 * Presenters the source proves are NOT doors on any instance, with the line that proves it. Kept
 * as an explicit list because each is a different reason and none is worth a general rule yet.
 */
const NOT_A_DOOR = new Map([
  ['UnknownComponentPresenter', 'fallback for a dashboard component whose type is not registered (Components.java: `componentRegistry.getComponent(type) == null`) — needs corrupt content'],
  ['ContentTabPanePresenter', 'the content tab pane itself — application shell, revealed by the AppModule proxy, not opened'],
]);
/** Doors that exist only when a UI config flag is on; the walk should check the live UiConfig. */
const CONFIG_GATED = new Map([
  ['SplashPresenter', 'stroom.ui.splash.enabled (SplashPresenter.show: `splashConfig.isEnabled()`)'],
  ['ManageActivityPresenter', 'stroom.ui.activity.enabled (NavigationPresenter: `activityConfig.isEnabled()` adds the activity button)'],
  ['ActivityEditPresenter', 'stroom.ui.activity.enabled (opened from ManageActivityPresenter)'],
]);

function routeFor(name) {
  if (NOT_A_DOOR.has(name)) return { how: 'not a door (by construction)', via: NOT_A_DOOR.get(name) };
  if (CONFIG_GATED.has(name)) return { how: 'config-gated (a door only when enabled)', via: CONFIG_GATED.get(name) };
  // An abstract class is never instantiated, whatever names it: DocPresenter, LinkTabPanelPresenter
  // and ContentTabPresenter are mentioned by every plugin and were counted as three doors.
  if (isAbstract.has(name)) return { how: 'abstract base (not a door)', via: '' };
  if (isDocEditor.has(name)) return { how: 'document editor', via: 'explorer' };
  if (isScreen.has(name)) return { how: 'screen', via: 'main menu' };
  const plug = namedByPlugin.get(name);
  if (plug) return { how: 'screen/dialog via plugin', via: [...plug].sort().join(', ') };
  const open = openers.get(name);
  if (open) return { how: 'dialog', via: [...open].sort().join(', ') };
  const par = childOf.get(name);
  if (par) return { how: 'dialog', via: [...par].sort().join(', ') };
  const hostedBy = hosts.get(name);
  if (hostedBy) return { how: 'embedded panel (covered via parent)', via: [...hostedBy].sort().slice(0, 3).join(', ') };
  if (isPlaceBased.has(name)) return { how: 'place-based (session/navigation state)', via: 'GWTP proxy' };
  const host = embeddedIn.get(name);
  if (host) return { how: 'embedded panel (covered via parent)', via: [...host].sort().slice(0, 3).join(', ') };
  return { how: 'UNKNOWN', via: '' };
}

// ── The presenter inventory, for the gap ─────────────────────────────────────
const csv = readFileSync(oracle('gwt-inventory.csv'), 'utf8').split('\n').slice(1).filter(Boolean);
const presenters = [];
for (const line of csv) {
  const c = line.split(',');
  if (c[4] !== 'Presenter') continue;
  if (c[6] !== 'Screen/Dialog' && c[6] !== 'Shared Screen/Dialog') continue;
  presenters.push({ name: cls(c[0]), area: c[7], status: c[8] });
}
const byArea = new Map();
for (const p of presenters) byArea.set(p.area, (byArea.get(p.area) ?? 0) + 1);

const totalEdges = [...edges.values()].reduce((n, s) => n + s.size, 0);
const parents = [...edges.keys()].sort();

const lines = [];
lines.push('# Reachability graph — how every GWT screen and dialog is entered');
lines.push('');
lines.push('**Generated** by `stroom-stroom-gwt-suite/tools/build-reachability-graph.mjs`. Do not hand-edit.');
lines.push('');
lines.push('`gwt-inventory.csv` says what exists. This says how you get to it, which is what a test');
lines.push('suite actually needs: a screen that cannot be reached cannot be photographed.');
lines.push('');
lines.push('## Totals');
lines.push('');
lines.push('| | count |');
lines.push('| --- | ---: |');
lines.push(`| Presenters in Screen/Dialog + Shared | ${presenters.length} |`);
lines.push(`| …across areas | ${byArea.size} |`);
lines.push(`| Document editors (\`extends DocumentPlugin\`) | ${documentPlugins.length} |`);
lines.push(`| Screens (\`extends ContentTabPlugin\` and friends) | ${contentPlugins.length} |`);
lines.push(`| Classes that raise a context menu (\`ShowMenuEvent\`) | ${showsMenu.length} |`);
lines.push(`| Classes that show THEMSELVES as a dialog | ${selfShown.size} |`);
lines.push(`| Parent→child dialog edges | ${totalEdges} (from ${parents.length} parents) |`);
lines.push('');
lines.push('## The dialog graph');
lines.push('');
lines.push('Each line is "this presenter can open these". A test plan walks this: reach the parent,');
lines.push('then each child is one affordance away. Depth beyond 1 is where the long tail lives.');
lines.push('');
for (const p of parents) {
  lines.push(`* **${p}** → ${[...edges.get(p)].sort().join(', ')}`);
}
lines.push('');
lines.push('## Dialogs that show themselves');
lines.push('');
lines.push('These raise `ShowPopupEvent.builder(this)`, so the popup site does not name a parent.');
lines.push('The parent is whoever holds a `Provider` for them, which IS in the source — resolved below.');
lines.push('An entry with no opener is genuinely unreached and needs manual research.');
lines.push('');
const orphans = [];
for (const d of [...selfShown].sort()) {
  const who = openers.get(d);
  if (who && who.size) lines.push(`* **${d}** ← opened by ${[...who].sort().join(', ')}`);
  else orphans.push(d);
}
lines.push('');
lines.push(`### No opener found (${orphans.length}) — manual research needed`);
lines.push('');
lines.push(orphans.length ? orphans.map((s) => `\`${s}\``).join(' · ') : '_none_');
lines.push('');
// ── The target list ──────────────────────────────────────────────────────────
const routed = presenters.map((p) => ({ ...p, ...routeFor(p.name) }));
const byHow = new Map();
for (const r of routed) byHow.set(r.how, (byHow.get(r.how) ?? 0) + 1);
const unknown = routed.filter((r) => r.how === 'UNKNOWN');

// Machine-readable, for `stroom-gwt-suite/coverage.mjs`. The md aggregates and never lists all 412 names,
// so the ledger could not use this list as its denominator and fell back to whatever the capability
// miner happened to produce — which included 121 plugins, clients, view impls and cells that are not
// screens at all, and excluded a presenter that is (a form-only tab declares no button and no grid).
writeFileSync(OUT.replace(/\.md$/, '.json'), JSON.stringify({
  generated: new Date().toISOString(),
  source: 'stroom-stroom-gwt-suite/tools/build-reachability-graph.mjs',
  presenters: routed.map((r) => ({ name: r.name, area: r.area, status: r.status, how: r.how, via: r.via })),
}, null, 1) + '\n');

lines.push(`## The crawler target list — all ${routed.length}, with a route`);
lines.push('');
lines.push('This is the denominator. A presenter the crawl never reaches is either a crawler gap, a');
lines.push('config-gated feature, or an UNKNOWN below that still needs manual research.');
lines.push('');
lines.push('| route | presenters |');
lines.push('| --- | ---: |');
for (const [h, n] of [...byHow.entries()].sort((a, b) => b[1] - a[1])) lines.push(`| ${h} | ${n} |`);
lines.push('');
const doors = routed.filter((r) => r.how === 'dialog' || r.how === 'screen/dialog via plugin' ||
  r.how === 'document editor' || r.how === 'screen');
const placed = routed.filter((r) => r.how.startsWith('place-based'));
const embedded = routed.filter((r) => r.how.startsWith('embedded'));
const abstracts = routed.filter((r) => r.how.startsWith('abstract'));
lines.push(`**${routed.length} is not ${routed.length} doors.** It decomposes:`);
lines.push('');
lines.push(`* **${doors.length} DOORS** — the crawler's actual target list, each with a route above.`);
lines.push(`* **${embedded.length} embedded panels** — settings tabs, list panes, toolbars. Covered when`);
lines.push('  their parent screen is, and their behaviour is the screen-profile layer\'s business.');
lines.push(`* **${placed.length} place-based** — revealed by session or navigation state, not by a click.`);
lines.push('  Reached by manipulating the session (sign out, fail auth), not by crawling.');
lines.push(`* **${abstracts.length} abstract base classes** — never instantiated, not reachable by anything.`);
const notDoors = routed.filter((r) => r.how.startsWith('not a door') || r.how.startsWith('config-gated'));
for (const r of notDoors) lines.push(`* **${r.name}** — ${r.how}: ${r.via}`);
lines.push(`* **${unknown.length} genuinely unrouted** — the research backlog below.`);
lines.push('');
lines.push(`So the crawler's denominator is **${doors.length + unknown.length}**, of which ` +
  `**${doors.length} (${Math.round((doors.length / (doors.length + unknown.length)) * 100)}%) have a ` +
  'known route today.**');
lines.push('');
lines.push(`### UNKNOWN route (${unknown.length}) — the research backlog`);
lines.push('');
lines.push('Grouped by area, because a whole area with no route usually means one missing mechanism');
lines.push('rather than N missing screens.');
lines.push('');
const unkByArea = new Map();
for (const u of unknown) {
  if (!unkByArea.has(u.area)) unkByArea.set(u.area, []);
  unkByArea.get(u.area).push(u.name);
}
for (const [a, names] of [...unkByArea.entries()].sort((x, y) => y[1].length - x[1].length)) {
  lines.push(`* **${a}** (${names.length}) — ${names.sort().join(', ')}`);
}
lines.push('');
lines.push('## Presenters by area');
lines.push('');
lines.push('| area | presenters |');
lines.push('| --- | ---: |');
for (const [a, n] of [...byArea.entries()].sort((x, y) => y[1] - x[1])) lines.push(`| ${a} | ${n} |`);
lines.push('');

writeFileSync(OUT, lines.join('\n'));
console.log(`${presenters.length} presenters, ${totalEdges} dialog edges from ${parents.length} parents, ` +
  `${selfShown.size} self-shown -> ${OUT}`);
