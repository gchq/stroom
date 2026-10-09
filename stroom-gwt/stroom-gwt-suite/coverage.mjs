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
// The coverage ledger: which of the GWT UI's presenters the crawl has actually observed.
//
//   node stroom-gwt/stroom-gwt-suite/coverage.mjs                       # read stroom-gwt-suite/out/crawl/*.json
//   IN=some/other/dir node stroom-gwt/stroom-gwt-suite/coverage.mjs
//
// "Full coverage" was unfalsifiable while the only outputs were per-run node counts: 173 places is a
// number, not a fraction, and nobody could say of what. So progress is a percentage that can go up,
// and the gap is a worklist with names in it.
//
// THE DENOMINATOR IS `oracles/reachability-graph.json` — every class the inventory classifies as a
// Screen/Dialog or Shared Screen/Dialog presenter, with the route that reaches it. It used to be
// whatever `capability-specs.json` happened to mine, which was the wrong list in both directions:
// 121 of its 421 entries were plugins, REST clients, view impls, models and cells that no crawl can
// reach as a surface, while a presenter that IS one could be missing, because specs are mined from
// `SvgPresets` buttons and `DataGridUtil` headings and a form-only tab declares neither. The new
// Analytic Rule `Settings` tab was exactly that: a real presenter, invisible to the ledger.
//
// Reported as three numbers rather than one, because they answer different questions:
//   DOORS      what the crawler can actually target — a dialog, a screen, an editor, plus UNKNOWNs
//   EMBEDDED   panels covered when their parent screen is; a tab is not something you navigate to
//   ALL        every presenter bar the abstract bases, which are never instantiated by anything
//
// It merges every crawl file in the directory: coverage is cumulative across runs and areas, which
// is the only way a suite built area-by-area can report a total.
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { classifyAlert, knownBug, knownFail, recordedOutcome } from './lib/alerts.mjs';
import { feedbackShown, isFilterAction, pagerVerdict, refreshVerdict, sortVerdict, togglesRoundTrip } from './lib/checks.mjs';
import { illegalVerdict } from './lib/illegal.mjs';
import { ctrlEnterVerdict, escapeVerdict } from './lib/keys.mjs';
import { dirtyVerdict } from './lib/dirty.mjs';
import { staleBy } from './lib/vintage.mjs';
import { join } from 'node:path';
import { fromSuite, oracle } from './lib/paths.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const IN = fromSuite(env('IN', `${SUITE}/out/crawl`));
const OUT = fromSuite(env('OUT', `${SUITE}/out`));

const specs = JSON.parse(readFileSync(oracle('capability-specs.json'), 'utf8')).specs;

const reach = JSON.parse(readFileSync(oracle('reachability-graph.json'), 'utf8')).presenters;
/** A door is a place you can navigate to. UNKNOWN is counted as one: it is an unrouted door, not a
 *  panel, and hiding it in the wide total is how a research backlog stops being visible. */
const DOORS = new Set(['dialog', 'screen/dialog via plugin', 'document editor', 'screen', 'UNKNOWN']);
const isAbstract = (p) => p.how.startsWith('abstract');
/** Every presenter bar the abstract bases — the widest honest denominator. */
const denominator = new Map(reach.filter((p) => !isAbstract(p)).map((p) => [p.name, p]));
const doorNames = new Set(reach.filter((p) => DOORS.has(p.how)).map((p) => p.name));
const embeddedNames = new Set(reach.filter((p) => p.how.startsWith('embedded')).map((p) => p.name));
const placedNames = new Set(reach.filter((p) => p.how.startsWith('place-based')).map((p) => p.name));

/**
 * Caption -> presenter, from the same source mining that produced the routes.
 *
 * Without this the ledger is nearly blind. A crawl node carries a `presenter` only when a RECIPE
 * seeded it, so editors and their sub-tabs are named and the 156 dialogs and 60 alerts a single doc
 * shard reaches are not — and dialogs are exactly where the uncovered presenters live (the
 * reachability graph counts 112 classes that show themselves as one). Three hours of crawling moved
 * the coverage number by ZERO for this reason alone: the crawl was reaching places it could not name.
 */
/**
 * caption -> the SET of presenters that show it. A set, not a value, because a caption is not
 * unique: 9 of the 74 the miner knows belong to more than one site, and `Confirm` is every delete
 * dialog in the application. Collapsing that to one presenter picks a winner at random; keeping the
 * set means an ambiguous caption can still be used as a discriminator within a known parent, and is
 * never used as a global name.
 */
const captionOf = new Map();
const addCaption = (caption, presenter) => {
  const key = normaliseCaption(caption);
  if (!captionOf.has(key)) captionOf.set(key, new Set());
  captionOf.get(key).add(presenter);
};
for (const c of JSON.parse(readFileSync(oracle('route-recipes.json'), 'utf8')).captionOf ?? []) {
  addCaption(c.caption, c.presenter);
}

/**
 * Captions resolved from the source by `name-captions.mjs`, kept separate from the miner's own so
 * the provenance of every name in this ledger stays visible: `captionOf` is mined from the one
 * `ShowPopupEvent…caption("…")` shape, these were looked up because the crawl actually saw them.
 * Optional — the ledger works without the file, it just names fewer dialogs.
 */
let resolvedCount = 0;
try {
  for (const r of JSON.parse(readFileSync(`${SUITE}/out/caption-names.json`, 'utf8')).resolved ?? []) {
    if (!captionOf.has(normaliseCaption(r.caption))) {
      addCaption(r.caption, r.presenter);
      resolvedCount += 1;
    }
  }
} catch { /* not generated yet */ }

/** Captions carry the document's name — `Save 'Seed Report' as` — so quoted text is a wildcard. */
function normaliseCaption(s) {
  return String(s ?? '').replace(/'[^']*'/g, "'…'").replace(/\s+/g, ' ').trim();
}

/**
 * A mined caption with a `…` in it is a TEMPLATE: the miner read `"Copy " + node.getDisplayValue()`
 * and knows every caption from that site reads `Copy <something>`. Matching one is weaker evidence
 * than an exact caption and is used only when no exact caption matches, because a template is
 * deliberately broad: `New …` is CreateDocumentPresenter, but `New Field` is an exact caption shared
 * by four field editors, and the specific reading has to win or the ledger silently claims three
 * nodes it has no business claiming.
 */
/** A mined caption with gaps, as a regex over a normalised observed caption. */
const templateRe = (key) => new RegExp(`^${key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/…/g, '.+')}$`);

const captionTemplates = [];
const compileTemplates = () => {
  captionTemplates.length = 0;
  for (const [key, presenters] of captionOf) {
    if (!key.includes('…')) continue;
    captionTemplates.push({ key, re: templateRe(key), presenters });
  }
};
/** Every template that matches, as one set — several matching is ambiguity, not a shortlist. */
function templateNames(caption) {
  const out = new Set();
  for (const t of captionTemplates) if (t.re.test(caption)) for (const p of t.presenters) out.add(p);
  return out.size ? out : null;
}

// Compiled once both caption sources are loaded — the miner's own, and the ones `name-captions.mjs`
// resolved from what the crawl actually saw.
compileTemplates();

/**
 * Menu `group > leaf` -> presenter, from the recipes' own single-step menu routes.
 *
 * A screen reached by clicking a menu leaf IS the presenter that leaf opens — the recipes say so
 * themselves. Without this, a screen the crawl found by walking the menu (rather than by being
 * seeded from its recipe) is anonymous, which is how 41 screens scored nothing.
 */
const leafOf = new Map();
for (const r of JSON.parse(readFileSync(oracle('route-recipes.json'), 'utf8')).recipes ?? []) {
  const [step, ...rest] = r.route ?? [];
  if (!rest.length && step?.via === 'menu' && r.presenter) {
    leafOf.set(`${step.group} > ${step.leaf}`, r.presenter);
  }
}

/**
 * Node kinds that ARE a presenter when reached, so failing to name one is a real gap.
 *
 * A `menu` node is deliberately not among them: a menu is a route, not a screen — GWT raises it
 * from a presenter rather than being one — so counting all 396 of them as "anonymous" conflated a
 * hole in the miner with a node that was never going to name a presenter in the first place, and
 * made the gap look twice its real size.
 */
const PRESENTER_BEARING = new Set(['screen', 'editor', 'editor-tab', 'dialog', 'alert']);

/**
 * Who opens what: `shower` -> the dialogs it shows. The miner already records this and the ledger
 * was ignoring it in favour of the caption.
 */
const showsFrom = new Map();
{
  const recipes = JSON.parse(readFileSync(oracle('route-recipes.json'), 'utf8'));
  // `showEdges` where available — every `ShowPopupEvent.builder(X)` site, caption or not. The
  // miner used to record only those with a literal caption, which discarded 68 of 153 edges and
  // forced this ledger onto caption evidence for dialogs whose parent was known all along.
  for (const e of recipes.showEdges ?? recipes.captionOf ?? []) {
    if (!e.shower) continue;
    if (!showsFrom.has(e.shower)) showsFrom.set(e.shower, []);
    showsFrom.get(e.shower).push({ presenter: e.presenter, caption: normaliseCaption(e.caption) });
  }
}

/**
 * presenter -> the presenters it embeds, from the capability specs.
 *
 * A parent screen is rarely the thing that shows its dialogs. `FeedPresenter` opens nothing; it
 * embeds `FeedSettingsPresenter`, `MetaPresenter` and two more, and THOSE hold the
 * `ShowPopupEvent.builder(...)` calls. Without this the edge list and the crawl's attributions
 * simply do not meet: 136 distinct showers against parents named `FeedPresenter`, and the join
 * finds nothing.
 *
 * One level only. Deeper is available but each level widens the candidate set, and a wide candidate
 * set is resolved by caption — which is the weak evidence this is all trying to get away from.
 */
const embedsOf = new Map(specs.map((s) => [s.presenter, s.embeds ?? []]));
/** presenter -> its ancestors, from the capability specs' `inheritsFrom`. */
const inheritsOf = new Map(specs.map((s) => [s.presenter, s.inheritsFrom ?? []]));

/**
 * (editor presenter, tab label) -> the presenter that tab embeds, from `addTab(TAB, provider)`.
 *
 * The walk records `editor-tab / Feed (document) / Settings` under the EDITOR's presenter, so every
 * settings pane, list pane and permissions tab it visited scored nothing — 190 embedded panels
 * reachable only this way, 2 of them credited. The tab bar's label plus the editor names the pane.
 */
const tabPresenter = new Map();
for (const t of JSON.parse(readFileSync(oracle('route-recipes.json'), 'utf8')).tabsOf ?? []) {
  tabPresenter.set(`${t.owner} :: ${t.tab}`, t.presenter);
}

/** Route -> the presenters attributed to the node at that route. Filled as nodes are read, in order. */
const byRoute = new Map();
const routeKey = (route) => JSON.stringify(route ?? []);

/**
 * Every presenter a node is evidence for.
 *
 * ROUTE FIRST, caption second. A caption is a label, not an identity: 9 of the 74 the miner knows
 * are used by more than one site, `Confirm` is every delete dialog in the application, and asking
 * the source which presenter says `Save '…' as` returns three. The route is not an inference at
 * all — the crawl clicked a known button on a known screen, and the miner knows which presenter
 * that screen shows from there. So a dialog is named by WHERE IT WAS OPENED FROM, and its caption
 * is used only to choose between the children of that parent.
 */
/**
 * A chrome TOGGLE's post-action state IS a presenter: `after Ask Stroom AI` is the AI dock, which is
 * AskStroomAiPresenter, and everything opened from it (Configure, Conversation History, Download)
 * narrows from there. The toggle is app chrome, so no route step names its presenter.
 */
const CHROME_TOGGLE_PRESENTER = new Map([['Ask Stroom AI', 'AskStroomAiPresenter']]);

function presentersOf(node) {
  const out = new Set();
  if (node.presenter) out.add(node.presenter);
  if (node.kind === 'post-action') {
    const last = (node.route ?? []).at(-1);
    const p = last?.via === 'button' && CHROME_TOGGLE_PRESENTER.get(last.label);
    if (p) out.add(p);
  }
  if (node.kind === 'editor-tab') {
    const tab = String(node.path?.at(-1) ?? '').replace(/ #\d+$/, '');
    // The OWNER of the tab bar. `node.presenter` is only set on a SEEDED node, so a tab reached by
    // exploration — Ask Stroom AI › Configure › General, say — had no owner to look up and the whole
    // `tabsOf` map went unused for it. The owner is whatever the node this tab hangs off was
    // attributed to, which `byRoute` already holds because depth-first reads the parent first.
    const owners = new Set(node.presenter ? [node.presenter] : []);
    if (!owners.size) {
      let up = (node.route ?? []).slice(0, -1);
      // Walk up past steps that name nothing of their own (a menu, a post-action state).
      while (up.length && !byRoute.get(routeKey(up))?.size) up = up.slice(0, -1);
      for (const p of byRoute.get(routeKey(up)) ?? []) owners.add(p);
    }
    for (const o of owners) {
      const embedded = tabPresenter.get(`${o} :: ${tab}`);
      if (embedded) { out.add(embedded); attributedBy.tab = (attributedBy.tab ?? 0) + 1; }
    }
  }
  for (const step of node.route ?? []) {
    if (step?.via !== 'menu') continue;
    const named = leafOf.get(`${step.group} > ${step.leaf}`);
    if (named) out.add(named);
  }

  const captions = (node.dialogs ?? []).map((d) => normaliseCaption(typeof d === 'string' ? d : d?.caption));

  // The parent is this node's route with the last step removed — the screen the click came from.
  //
  // Only a POPUP can be named this way. A `shower -> presenter` edge says "clicking here opens that
  // dialog", so applying it to a node that is not a dialog attributes an editor's sub-tab to
  // whatever dialog its editor happens to open. Ungated, that named 1,119 of 1,241 nodes by
  // "route" — nearly all of them, which is the tell: an evidence class that explains everything
  // is not evidence.
  const isPopup = (node.kind === 'dialog' || node.kind === 'alert')
    && ['button', 'context'].includes((node.route ?? []).at(-1)?.via);
  let parentRoute = (node.route ?? []).slice(0, -1);
  // A MENU is a route, not a screen: a dialog opened from a menu item was opened from the screen
  // that opened the menu (the same reading the explorer's context menu gets below). A menu node
  // names no presenter of its own, so without this every dialog behind a menu — the whole column
  // menu of a dashboard table: Rename, Expression, Format, Filter — had an empty parent set and
  // fell back to caption evidence. Walk up through menu steps until a step that names something.
  // A POST-ACTION state that names nothing of its own (`after Execute Query`, `after select row`)
  // is the same: the dialog was opened from the screen underneath it.
  if (isPopup) {
    while (parentRoute.length && (parentRoute.at(-1)?.opens === 'menu' || parentRoute.at(-1)?.post)
      && !byRoute.get(routeKey(parentRoute))?.size) {
      parentRoute = parentRoute.slice(0, -1);
    }
  }
  let parents = isPopup ? new Set(byRoute.get(routeKey(parentRoute)) ?? []) : new Set();
  // The parent's OWN presenters, not what it inherited: a route's menu step names the screen on every
  // descendant, so a dialog two deep (`Edit` → `New` on Data Volumes) had parents {screen, editor}
  // and three candidates where the editor alone gives one. When the parent adds nothing of its own
  // (an alert, a Confirm) the inherited set is all there is, and stays.
  if (isPopup && parentRoute.length > 1) {
    const inherited = new Set(byRoute.get(routeKey(parentRoute.slice(0, -1))) ?? []);
    const own = [...parents].filter((p) => !inherited.has(p));
    if (own.length) parents = new Set(own);
  }
  // A dialog opened from the EXPLORER'S CONTEXT MENU has a known shower, and it is not a presenter:
  // `DocumentPluginEventManager` builds that menu and fires every event behind it (Create, Rename,
  // Copy, Move, Info, Edit/Remove Tags, Import). Without this the parent set is empty — the context
  // node names no presenter, because a menu is a route rather than a screen — and `New Folder`,
  // `Copy …` and the rest fall back to caption evidence that cannot separate the explorer's create
  // dialog from the visualisation assets one. It is never added to `out`, so it cannot affect
  // coverage: it exists only to be the parent an edge hangs off.
  if (isPopup && parentRoute.at(-1)?.via === 'context') parents.add('DocumentPluginEventManager');
  // The parent, plus what it embeds, plus what it INHERITS — the dialog was opened from somewhere
  // in that subtree, and the site may live in a base class: every dashboard component's `Settings`
  // is shown by AbstractComponentPresenter, not by TablePresenter, so a `Settings` dialog under a
  // Table tab found no candidate and stayed ambiguous (16 of them).
  // Embedding is ONE level, on purpose. Tried transitive on 2026-09-18: TablePresenter's closure is
  // half the application (through QueryPresenter → QueryDoc → …), `Settings` then had two candidates
  // and the Table's own Settings dialog lost its name. The dashboard's `Download Options` stays
  // unnamed under a whole-dashboard route because DashboardPresenter reaches TablePresenter through
  // the component registry, not an embed — the walk's node under the TABLE tab is what names it.
  const subtree = new Set();
  for (const parent of parents) {
    subtree.add(parent);
    for (const child of embedsOf.get(parent) ?? []) subtree.add(child);
    for (const base of inheritsOf.get(parent) ?? []) subtree.add(base);
  }
  // A door that shows ITSELF carries a `P -> P` edge (its real openers call its `show(`). That edge
  // is not a candidate for something opened FROM P: a dialog does not open itself from itself, and
  // leaving it in made `{FsVolumeGroupEditPresenter, FsVolumeEditPresenter}` two candidates for the
  // `New` volume dialog under the volume-group editor, so neither was named.
  const candidates = [...subtree].flatMap((parent) => (showsFrom.get(parent) ?? [])
    .filter((c) => !(parents.has(c.presenter) && c.presenter === parent)));
  let namedByRoute = false;
  if (process.env.DEBUG_CAPTION && captions.some((c) => c.includes(process.env.DEBUG_CAPTION))) {
    console.error('DEBUG', node.path.join(' / '), '\n  parents', [...parents], '\n  subtree', [...subtree], '\n  candidates', candidates.map((c) => `${c.presenter}<-${c.shower}:${c.caption ?? ''}`));
  }
  // Several edges to ONE presenter (its `New` and `Edit` call sites) are one candidate, not two.
  const distinct = new Set(candidates.map((c) => c.presenter));
  // A lone candidate whose every edge carries a caption must still FIT the caption observed: the
  // Pathways editor's `Save 'Seed Pathways' as` had PathwayEditPresenter (`New Pathway` / `Edit
  // Pathway`) as its only candidate and was credited to it six times, the same false credit as
  // ItemSelectionPresenter's had been. An uncaptioned edge stays route-only evidence.
  const fits = (c) => !c.caption || captions.includes(c.caption)
    || (c.caption.includes('…') && captions.some((cap) => templateRe(c.caption).test(cap)));
  if (distinct.size === 1 && (!captions.length || candidates.some(fits))) {
    out.add(candidates[0].presenter);
    attributedBy.route += 1;
    namedByRoute = true;
  } else if (distinct.size > 1) {
    // Several children of the same parent: NOW the caption earns its keep, as a discriminator
    // within a known parent rather than as a global key.
    // A candidate's caption may be a TEMPLATE — `Copy …`, `New …` — so match it the same way the
    // global caption pass does, or the narrowing silently fails on exactly the dialogs that needed
    // a route to name them.
    // An exact caption BEATS a template here too (the global pass below has the same rule): with
    // embedding transitive, `New Field` under the index field list also meets the explorer popup's
    // `New …` two embeds down, and treating them as equals un-named a dialog the exact caption names.
    const exactly = candidates.filter((c) => c.caption && captions.includes(c.caption));
    const matched = exactly.length ? exactly : candidates.filter((c) => c.caption
      && captions.some((cap) => c.caption.includes('…') && templateRe(c.caption).test(cap)));
    if (new Set(matched.map((c) => c.presenter)).size === 1) {
      out.add(matched[0].presenter);
      attributedBy.routeAndCaption += 1;
      namedByRoute = true;
    }
  }

  // Then every caption on the node, ALWAYS — not gated on whether the route named something.
  // Dialogs stack: `['Query Favourites', 'Create New Favourite']` is one node showing two, and the
  // route names only the outer. Gating this cost `NamePresenter` and `HttpTlsConfigPresenter`,
  // both of which were sitting in a caption the ledger had chosen not to read.
  //
  // A caption is used as a global name ONLY when it is unambiguous. Where several presenters share
  // it, the route above was its one chance to be resolved, and an unresolved one is left unnamed
  // rather than guessed.
  for (const cap of captions) {
    // An exact caption BEATS a template, because it is the more specific reading of the same source.
    // `Rename Tab` is mined exactly, from the dashboard, and also matches the `Rename …` template
    // that NameDocumentPresenter contributes; unioning them made a genuinely-named dialog ambiguous.
    // Ambiguity between two TEMPLATES is different and still counts: `New Folder` matches `New …`
    // from the explorer's create dialog and `New …` from the visualisation assets dialog, neither
    // more specific than the other, so it stays unnamed.
    const exact = captionOf.get(cap);
    const named = exact ?? templateNames(cap);
    if (!named) continue;
    if (named.size === 1) {
      const only = [...named][0];
      if (!out.has(only)) {
        out.add(only);
        if (exact) attributedBy.captionOnly += 1;
        else attributedBy.captionTemplate += 1;
      }
    } else if (!namedByRoute) {
      ambiguousCaptions.set(cap, (ambiguousCaptions.get(cap) ?? 0) + 1);
    }
  }
  return out;
}

/** Every crawl run in the directory, newest last — later runs win on conflicting status. */
const files = readdirSync(IN).filter((f) => f.startsWith('crawl-') && f.endsWith('.json')).sort();
if (!files.length) {
  console.error(`no crawl-*.json in ${IN} — run stroom-gwt-suite/crawl.mjs first`);
  process.exit(2);
}

/** presenter -> {reached, unreachable, places:Set, runs:Set} */
const seen = new Map();
/** Dialog captions nothing can name — a miner worklist, not noise. */
const unnamedCaptions = new Map();
let nodesTotal = 0;
let nodesAttributed = 0;
/** Nodes that OUGHT to name a presenter, and those of them that could not. */
let bearing = 0;
const anonymousByKind = new Map();
/** Which evidence named things — route is strong, caption alone is weak. */
const attributedBy = { route: 0, routeAndCaption: 0, captionOnly: 0, captionTemplate: 0 };
/** Captions the route could not resolve and that name more than one presenter — left unnamed. */
const ambiguousCaptions = new Map();
/**
 * What the alerts SAID — BEHAVIOUR-PLAN.md § B0. Every `alert` outcome with a body, by class, and
 * the `exception` ones by message with every route that produced them: a server or client failure
 * the walk caused is a GWT bug candidate, and the node id is its repro. Alerts recorded before the
 * body was kept count as `unread`.
 */
const alerts = { byClass: new Map(), unread: 0, exceptions: new Map(), environment: new Map() };
/** run -> node id -> the presenters the ledger attributes to it. Written to node-presenters.json. */
const nodePresenters = {};
/**
 * Checker verdicts (BEHAVIOUR-PLAN.md § B2, lib/checks.mjs) by kind and verdict, with every fail by
 * route; and the selection-rule nodes, for the offline check against the specs' disabled-at-rest.
 */
// ── B4: the write paths, from stroom-gwt-suite/cycles.mjs ──────────────────────────
// A cycle proves a write by UNDOING it, so the ledger reports two things: whether each phase held,
// and — louder — whether anything was LEFT BEHIND. A leaked row makes every later baseline wrong,
// so it is reported before the passes.
const CYCLES = fromSuite(env('CYCLES', `${SUITE}/out/cycles/cycles.json`));
const cycles = existsSync(CYCLES) ? JSON.parse(readFileSync(CYCLES, 'utf8')) : null;
const cyclesReport = () => {
  if (!cycles) {
    return ['_No cycles have been run. `MUTATE=1 node stroom-gwt/stroom-gwt-suite/cycles.mjs` drives them; until then'
      + ' every OK that writes is untested and the ledger says so._'];
  }
  const rs = cycles.results ?? [];
  const leaked = rs.filter((r) => r.leaked);
  const out = [];
  if (leaked.length) {
    out.push(`> **${leaked.length} cycle(s) LEAKED a row — the instance is dirty.** Clean up before`
      + ' recording a corpus or trusting a later run:', '',
    ...leaked.map((r) => `> - \`${r.name}\``), '');
  }
  out.push(`_Run ${cycles.generated ?? '?'} against ${cycles.url ?? '?'}._`, '',
    '| cycle | verdict | phases | wrote | undeclared |', '| --- | --- | --- | --- | --- |');
  for (const r of rs) {
    const bad = (r.phases ?? []).filter((p) => !p.ok);
    out.push(`| \`${r.name}\` | ${r.leaked ? '**LEAKED**' : r.ok ? 'pass' : 'fail'} `
      + `| ${(r.phases ?? []).length - bad.length}/${(r.phases ?? []).length}`
      + `${bad.length ? ` — ${bad.map((p) => `${p.what}: ${p.why}`).join('; ').slice(0, 160)}` : ''} `
      + `| ${(r.writes ?? []).join(', ') || '_nothing_'} | ${(r.undeclared ?? []).join(', ') || '—'} |`);
  }
  return out;
};

const checks = { by: new Map(), fails: [], unstable: [] };
const selectionNodes = []; // { run, id, enabled: Set(labels), disabledAtRest: [...] of the parent }
for (const f of files) {
  const data = JSON.parse(readFileSync(join(IN, f), 'utf8'));
  const run = data.summary?.area ?? f;
  const affByNode = new Map();
  for (const a of data.affordances ?? []) {
    affByNode.set(a.node, [...(affByNode.get(a.node) ?? []), a]);
    if (a.check) {
      // Re-judged from the recorded captures under the CURRENT checker, like alerts are re-classed.
      let c = a.check;
      if (c.before !== undefined || c.after !== undefined) {
        if (c.kind === 'sort') c = { ...c, ...sortVerdict(c.before, c.after) };
        else if (c.kind === 'pager') c = { ...c, ...pagerVerdict(a.label, c.before, c.after) };
        else if (c.kind === 'refresh') c = { ...c, ...refreshVerdict({ grids: c.before }, { grids: c.after }) };
        else if (c.kind === 'dirty') c = { ...c, ...dirtyVerdict({ before: c.before, after: c.after, field: a.field }) };
        else if (c.kind === 'key' && a.label === 'Escape') c = { ...c, ...escapeVerdict({ before: c.before, after: c.after, said: a.said, aceFocused: c.aceFocused ?? a.aceFocused }) };
        // An `ok` that recorded a feedback capture is re-judged on PRESENCE, so correcting that
        // rule reaches captures already taken.
        else if (c.kind === 'ok' && c.after && typeof c.after === 'object' && 'texts' in c.after) {
          const inline = feedbackShown(c.after);
          if (inline) c = { ...c, verdict: 'pass', why: `validation, inline: ${String(inline).slice(0, 80)}` };
        }
      }
      // The Filter check applies only to a dialog that FILTERS. The walker matched any opener with
      // the word in it, so `Add XPath Filter` — which adds a row by design — was recorded as a
      // filter that grew the grid. The opener is the node's last path segment.
      // `Expand All` / `Collapse All` are absolute, not inverses — the round-trip does not apply.
      // A round-trip that only GAINED buttons was surveyed mid-settle, not broken.
      if (c.kind === 'toggle' && c.verdict === 'fail' && (c.gained?.length ?? 0) && !(c.lost?.length ?? 0)) {
        c = { ...c, verdict: 'unstable', why: `the node gained ${c.gained.join(', ')} while the round-trip ran — it was still settling when surveyed` };
      }
      if (c.kind === 'toggle' && !togglesRoundTrip(a.label)) {
        c = { ...c, verdict: 'unchecked', why: `"${a.label}" is absolute, not a toggle — the round-trip does not apply (was: ${c.why})` };
      }
      if (c.kind === 'filter') {
        const opener = String(a.node ?? '').split(' / ').at(-1);
        if (!isFilterAction(opener)) {
          c = { ...c, verdict: 'unchecked', why: `"${opener}" is not a filter — the check does not apply (was: ${c.why})` };
        }
      }
      // B3's two drivers re-judge from what they recorded as well: the illegal values and what the
      // form answered, and Ctrl+Enter against what the OK button then did.
      if (c.kind === 'illegal') {
        const said = a.said ? { ...a.said, class: classifyAlert({ ...a.said, blocked: a.blocked ?? [] }) } : null;
        c = { ...c, ...illegalVerdict({ outcome: recordedOutcome(a.outcome, a.said), said, blocked: a.blocked ?? [],
          plan: c.plan ?? [], landed: c.landed !== false, inline: c.inline ?? null }) };
      } else if (c.kind === 'key' && a.label === 'Ctrl+Enter' && a.okOutcome) {
        c = { ...c, ...ctrlEnterVerdict({ keyOutcome: recordedOutcome(a.outcome, a.said), okOutcome: a.okOutcome, keySaid: a.said,
          aceFocused: a.aceFocused, okBlocked: false, keyBlocked: (a.blocked ?? []).length > 0 }) };
      }
      const k = `${c.kind}:${c.verdict}`;
      checks.by.set(k, (checks.by.get(k) ?? 0) + 1);
      if (c.verdict === 'fail') {
        checks.fails.push({ run, node: a.node, label: a.label, kind: c.kind, why: c.why,
          walkerVersion: data.summary?.walkerVersion });
      }
      if (c.verdict === 'unstable') checks.unstable.push({ run, node: a.node, label: a.label, why: c.why });
    }
  }
  {
    const byId = new Map((data.nodes ?? []).map((n) => [n.id, n]));
    for (const n of data.nodes ?? []) {
      if (n.status !== 'reached' || n.kind !== 'post-action' || !/after select row/.test(String(n.path.at(-1)))) continue;
      const parent = byId.get(n.id.slice(0, n.id.lastIndexOf(' / ')));
      selectionNodes.push({ run, id: n.id, enabled: new Set((affByNode.get(n.id) ?? []).map((a) => a.label)), parentDisabled: parent?.disabledAtRest ?? [] });
    }
  }
  for (const a of data.affordances ?? []) {
    if (a.outcome !== 'alert') continue;
    if (!a.said) { alerts.unread += 1; continue; }
    // Re-classed from what was recorded (text, detail, icon, blocked) under the CURRENT rules, so a
    // rule corrected in lib/alerts.mjs applies to every alert already read, not only the next run's.
    const cls = a.said.text !== undefined ? classifyAlert({ ...a.said, blocked: a.blocked ?? [] }) : (a.said.class ?? 'unread');
    alerts.byClass.set(cls, (alerts.byClass.get(cls) ?? 0) + 1);
    if (cls === 'exception' || cls === 'environment') {
      const bucket = alerts[cls === 'exception' ? 'exceptions' : 'environment'];
      // Keyed by the message with the variable parts (ids, uuids, numbers) blanked, so one defect
      // hit from ten places is one entry with ten routes.
      const key = `${a.said.text}${a.said.detail ? ` — ${a.said.detail.slice(0, 160)}` : ''}`
        .replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, '<uuid>').replace(/\b\d{3,}\b/g, '<n>');
      const e = bucket.get(key) ?? { icon: a.said.icon, routes: new Set(), runs: new Set(), walkers: new Set(), kinds: new Set() };
      e.routes.add(`${a.node} › ${a.label}`);
      e.runs.add(run);
      // The WALKER each occurrence came from, so an exception that only ever happened under an older
      // driver can be told from one that is still happening. Fails have had this since `vintage.mjs`;
      // exceptions did not, so a `FailedResponseException` the Enter probe caused by answering a
      // confirm — fixed in a49bc79 — read "1 not yet filed" for ever, inviting a bug report for a
      // defect that was the suite's own.
      e.walkers.add(String(data.summary?.walkerVersion ?? ''));
      e.kinds.add(a.kind);
      bucket.set(key, e);
    }
  }
  for (const n of data.nodes ?? []) {
    nodesTotal += 1;
    if (n.status === 'reached') {
      for (const d of n.dialogs ?? []) {
        const cap = normaliseCaption(typeof d === 'string' ? d : d?.caption);
        // A template counts as named — otherwise the worklist reports work that is already done.
        if (cap && !captionOf.has(cap) && !templateNames(cap)) {
          unnamedCaptions.set(cap, (unnamedCaptions.get(cap) ?? 0) + 1);
        }
      }
    }
    const names = presentersOf(n);
    if (names.size) {
      const key = routeKey(n.route);
      byRoute.set(key, new Set([...(byRoute.get(key) ?? []), ...names]));
      // The attribution, per run and node, for the tools downstream that join the walk's
      // observations to a presenter (the handler inventory, the capability diff) — they used to
      // re-derive a weaker join of their own from recipe seeds and unambiguous captions.
      (nodePresenters[run] ??= {})[n.id] = [...names];
    }
    if (PRESENTER_BEARING.has(n.kind)) {
      bearing += 1;
      if (!names.size) anonymousByKind.set(n.kind, (anonymousByKind.get(n.kind) ?? 0) + 1);
    }
    if (!names.size) continue;
    nodesAttributed += 1;
    for (const name of names) {
      const e = seen.get(name) ?? { reached: 0, unreachable: 0, places: new Set(), runs: new Set() };
      if (n.status === 'unreachable') e.unreachable += 1;
      else { e.reached += 1; e.places.add(n.id ?? n.slug ?? ''); if (process.env.DEBUG_PLACES === name) console.error(`PLACE ${run}: ${n.kind} ${n.id}`); }
      e.runs.add(run);
      seen.set(name, e);
    }
  }
}

const covered = [...seen].filter(([, e]) => e.reached > 0).map(([p]) => p);
const attempted = [...seen].filter(([, e]) => e.reached === 0 && e.unreachable > 0).map(([p]) => p);
// A presenter the crawl names but source mining does not know about. Not noise: it is either a
// miner gap or a presenter reached under a name the specs spell differently, and both need a human.
const unknownToSpecs = covered.filter((p) => !denominator.has(p)).sort();
const coveredKnown = covered.filter((p) => denominator.has(p));
const never = [...denominator.keys()].filter((p) => !seen.has(p)).sort();
const coveredDoors = coveredKnown.filter((p) => doorNames.has(p));
const coveredEmbedded = coveredKnown.filter((p) => embeddedNames.has(p));
const coveredPlaced = coveredKnown.filter((p) => placedNames.has(p));
/** In the denominator, so a real presenter, but the capability miner has no expectation for it —
 *  the P4 diff can reach it and still have nothing to score it against. */
const noSpec = [...denominator.keys()].filter((p) => !specs.some((s) => s.presenter === p)).sort();

const pct = (n, of = denominator.size) => `${((n / of) * 100).toFixed(1)}%`;
/** The offline selection check: enabled-after-select vs the spec's disabled-at-rest. */
function selectionReport() {
  const disabledBySpec = new Map(specs.map((sp) => [sp.presenter, new Set(sp.disabledAtRest ?? [])]));
  const out = [];
  let checked = 0;
  let clean = 0;
  for (const sn of selectionNodes) {
    const owners = nodePresenters[sn.run]?.[sn.id] ?? nodePresenters[sn.run]?.[sn.id.slice(0, sn.id.lastIndexOf(' / '))] ?? [];
    // UNION every owner's spec, do not take the first. `find` made the answer depend on which owner
    // a Set happens to iterate first — the same fault that let the handler inventory lose an `ok`
    // credit by learning more. A node carries the buttons of every presenter attributed to it, so the
    // expectations are the union; and `expected` is filtered to buttons that were actually disabled on
    // the parent, so a button belonging to a panel that is not on this node cannot create a false
    // MISSING.
    const spec = new Set(owners.flatMap((o) => [...(disabledBySpec.get(o) ?? [])]));
    if (!spec.size) continue;
    checked += 1;
    // What the spec expects to enable is what was disabled at rest AND the spec calls selection-gated.
    const expected = new Set([...spec].filter((l) => sn.parentDisabled.includes(l)));
    const missing = [...expected].filter((l) => !sn.enabled.has(l));
    const extra = [...sn.enabled].filter((l) => !spec.has(l) && sn.parentDisabled.includes(l));
    if (!missing.length && !extra.length) { clean += 1; continue; }
    out.push(`- ${sn.id}${missing.length ? ` — MISSING: ${missing.join(', ')}` : ''}${extra.length ? ` — extra: ${extra.join(', ')}` : ''}`);
  }
  return [`_${checked} selection state(s) checked against a spec, ${clean} agree._`, '', ...(out.length ? out : ['_none disagree_'])];
}

/**
 * An exception is STALE when every occurrence of it was walked before the rule that addressed it.
 * The same test `vintage.mjs` applies to a fail, over the union of the walkers that saw it: one
 * sighting under a current walker and it is live again.
 */
const staleException = (msg, e) => {
  const walkers = [...(e.walkers ?? [])].filter(Boolean);
  if (!walkers.length) return null;
  const rules = [...(e.kinds ?? ['key'])]
    .map((kind) => walkers.map((w) => staleBy({ kind, why: msg, walkerVersion: w })));
  // Every walker, for some kind, must be stale — and by the same rule.
  for (const perWalker of rules) {
    if (perWalker.length === walkers.length && perWalker.every((r) => r && r === perWalker[0])) return perWalker[0];
  }
  return null;
};
const newExceptions = [...alerts.exceptions].filter(([m, e]) => !knownBug(m) && !staleException(m, e)).length;
// Declared ABOVE `lines`: that array is a literal, evaluated where it stands, so a `const` used
// inside it must already be initialised. The same temporal dead zone bit the handler inventory's
// MOUSE_KINDS earlier today.
const lines = [
  '# GWT suite — coverage ledger',
  '',
  `> Generated by \`${SUITE}/coverage.mjs\` from ${files.length} crawl file(s) in \`${IN}\`.`,
  '> Do not hand-edit. The denominator is `oracles/reachability-graph.json` — every class the',
  '> inventory classifies as a Screen/Dialog presenter, minus the abstract bases. See',
  '> stroom-gwt-suite/COVERAGE-PLAN.md for what each phase means.',
  '',
  'A panel is **covered via its parent** — a settings tab is not somewhere you navigate to — so the'
  + ' EMBEDDED row counts only those the crawl named in their own right, and is not the measure of'
  + ' whether they were exercised. DOORS is the number the crawl controls.',
  '',
  '| | reached | of | |',
  '| --- | ---: | ---: | ---: |',
  `| **DOORS — what a crawl can target** | **${coveredDoors.length}** | ${doorNames.size} | **${pct(coveredDoors.length, doorNames.size)}** |`,
  `| EMBEDDED panels — named in their own right | ${coveredEmbedded.length} | ${embeddedNames.size} | ${pct(coveredEmbedded.length, embeddedNames.size)} |`,
  `| place-based — needs session state, not a click | ${coveredPlaced.length} | ${placedNames.size} | ${pct(coveredPlaced.length, placedNames.size)} |`,
  `| all presenters (excluding abstract bases) | ${coveredKnown.length} | ${denominator.size} | ${pct(coveredKnown.length)} |`,
  '',
  '| | count | of denominator |',
  '| --- | ---: | ---: |',
  `| …named by a route but unreachable | ${attempted.length} | ${pct(attempted.length)} |`,
  `| …never seen at all | ${never.length} | ${pct(never.length)} |`,
  `| Presenters the crawl names that are not in the list | ${unknownToSpecs.length} | |`,
  `| In the list but with NO mined capability spec to diff against | ${noSpec.length} | |`,
  '',
  `Captions named by the miner: ${captionOf.size - resolvedCount}. Named by \`name-captions.mjs\``
  + ` looking up what the crawl saw: ${resolvedCount}.`,
  '',
  `Attribution evidence — by ROUTE alone: ${attributedBy.route}, by route narrowed with a caption:`
  + ` ${attributedBy.routeAndCaption}, by caption alone (only where that caption names exactly one`
  + ` presenter): ${attributedBy.captionOnly}, by a caption TEMPLATE — \`Copy …\`, \`Save '…' as\` —`
  + ` where no exact caption matched: ${attributedBy.captionTemplate}.`,
  ...(ambiguousCaptions.size
    ? ['', 'Captions left UNNAMED because several presenters use them and the route could not say'
      + ' which: ' + [...ambiguousCaptions].sort((a, b) => b[1] - a[1])
        .map(([c, n]) => `\`${c}\` (${n}×)`).join(', ') + '.']
    : []),
  '',
  `Nodes across all runs: ${nodesTotal}. ${bearing} of them are presenter-bearing kinds`
  + ` (screen, editor, editor-tab, dialog, alert) and ${[...anonymousByKind.values()].reduce((a, b) => a + b, 0)}`
  + ' of those could not be named — that number, not the raw node count, is the miner\'s real gap.'
  + ' A menu is a route rather than a screen and is excluded.',
  '',
  ...(anonymousByKind.size
    ? ['| anonymous by kind | count |', '| --- | ---: |',
      ...[...anonymousByKind].sort((a, b) => b[1] - a[1]).map(([k, n]) => `| ${k} | ${n} |`)]
    : []),
  '',
  '## Dialog captions nothing can name',
  '',
  '_Reached, but neither the recipes nor `captionOf` can say which presenter they are. Every one is a'
  + ' hole in the miner, and until it is filled these dialogs cannot count towards coverage._',
  '',
  ...[...unnamedCaptions].sort((a, b) => b[1] - a[1]).map(([c, n]) => `- \`${c}\` — seen ${n}×`),
  '',
  '## Reached',
  '',
  ...coveredKnown.sort().map((p) => `- \`${p}\` — ${seen.get(p).places.size} place(s), in ${[...seen.get(p).runs].join(', ')}`),
  '',
  '## Named by a route, never reached',
  '',
  '_Each is either a crawler gap or a real defect, and they are indistinguishable until someone looks._',
  '',
  ...(attempted.length ? attempted.sort().map((p) => `- \`${p}\``) : ['_none_']),
  '',
  '## Named by the crawl, but not a Screen/Dialog presenter',
  '',
  '_Reached and named, but the inventory files it elsewhere, so it cannot count. `CommonAlertPresenter`'
  + ' is the standing example: the alert/confirm/prompt framework is classified as a WIDGET, by an'
  + ' explicit taxonomy decision, so the ~170 alert nodes a crawl reaches will never move this ledger._',
  '',
  ...(unknownToSpecs.length ? unknownToSpecs.map((p) => `- \`${p}\``) : ['_none_']),
  '',
  '## In the presenter list, but with no capability spec',
  '',
  '_P4 diffs observed against expected per presenter. These can be reached and there is nothing to'
  + ' score them against, because specs are mined from `SvgPresets` buttons and `DataGridUtil`'
  + ' headings and a form-only presenter declares neither. `AnalyticSettingsPresenter` — the tab'
  + ' upstream added in #5774 — is one of them._',
  '',
  ...(noSpec.length ? noSpec.map((p) => `- \`${p}\``) : ['_none_']),
  '',
  '## Never seen',
  '',
  ...never.map((p) => `- \`${p}\``),
  '',
  '## Behaviours checked',
  '',
  '_BEHAVIOUR-PLAN.md § B2 and § B3. Checker verdicts from the walk (`check` on an affordance): a sort'
  + ' header sorts, a pager pages, a Refresh changes nothing, a form rejects an `illegal` value with a'
  + ' rule of its own, and a dialog honours the framework\'s `key` contract (Escape closes it,'
  + ' Ctrl+Enter does what OK does — KeyBinding:73/79). A `fail` is a walker bug or a GWT bug and is'
  + ' listed by route; `unstable` is a Refresh that changed the rows, which is the instance (live'
  + ' data) until triage says otherwise._',
  '',
  '| check | verdict | count |',
  '| --- | --- | ---: |',
  ...[...checks.by].sort().map(([k, n]) => `| ${k.split(':')[0]} | ${k.split(':')[1]} | ${n} |`),
  ...(checks.by.size ? [] : ['| _none yet_ | | |']),
  '',
  `### Failed (${checks.fails.length})`,
  '',
  ...(checks.fails.length ? checks.fails.map((f) => {
    // A checker fail whose reason quotes an exception ALREADY FILED is that bug, not a new
    // finding: #38's TypeError reaches the ledger twice — once as an alert the reader classed, and
    // once as the `ok` checker refusing it. Resolve by the fail's own pattern first, then by the
    // message, so filing a bug covers both sides without a second pattern per entry.
    const k = knownFail(`${f.node} › ${f.label} — ${f.why}`) ?? knownBug(f.why);
    if (k) return `- **filed ${k}** · **${f.kind}** ${f.node} › ${f.label} — ${f.why}`;
    // A capture taken before the rule that governs it existed is the wrong VINTAGE, not a finding:
    // the correction needs data the old walker never recorded, so it cannot be re-judged.
    const stale = staleBy(f);
    return `- ${stale ? `**stale** (walked at \`${f.walkerVersion}\`, before "${stale}")` : '**NEW**'}`
      + ` · **${f.kind}** ${f.node} › ${f.label} — ${f.why}`;
  }) : ['_none_']),
  '',
  `### Unstable on Refresh (${checks.unstable.length})`,
  '',
  ...(checks.unstable.length ? checks.unstable.slice(0, 40).map((f) => `- ${f.node} — ${f.why}`) : ['_none_']),
  ...(checks.unstable.length > 40 ? [`- … ${checks.unstable.length - 40} more`] : []),
  '',
  '### Selection enables what the spec says',
  '',
  '_For every `after select row` state the walk reached: the buttons that became enabled against the'
  + ' presenter\'s `disabledAtRest` from the capability specs (mined from `SvgPresets`). `missing` ='
  + ' disabled at rest per the spec and still disabled after a row was selected; `extra` = enabled by'
  + ' the selection but not in the spec (the spec is a claim; a miss here is a spec gap or a button'
  + ' the selection does not gate)._',
  '',
  ...selectionReport(),
  '',
  '## Write paths (compensating cycles)',
  '',
  '_BEHAVIOUR-PLAN.md § B4. Every OK that writes is aborted by the read-only guard, so the only way'
  + ' to test one is to undo it: create, read it back, edit, save, RELOAD, read it back, delete,'
  + ' read it absent. The reload is the point — a presenter that only updated its own view passes'
  + ' without it. Each cycle may make only the requests its own spec declares; an `undeclared`'
  + ' mutation fails the cycle on its own._',
  '',
  ...cyclesReport(),
  '',
  '## What the alerts said',
  '',
  '_BEHAVIOUR-PLAN.md § B0. Every `alert` outcome the walks recorded WITH its body, by class:'
  + ' `guard` is the walker\'s own aborted mutation, `validation` and `info` are behaviours the'
  + ' presenter meant, `environment` is this instance (a node that is down, a value unset), and'
  + ' `exception` is a server or client failure the walk caused — each one a GWT bug candidate'
  + ' whose repro is the route listed. Alerts recorded before the body was kept are `unread`._',
  '',
  '| class | alerts |',
  '| --- | ---: |',
  ...[...alerts.byClass].sort((a, b) => b[1] - a[1]).map(([c, n]) => `| ${c} | ${n} |`),
  ...(alerts.unread ? [`| unread | ${alerts.unread} |`] : []),
  '',
  `### Exceptions (${alerts.exceptions.size} distinct)`,
  '',
  ...(alerts.exceptions.size ? [...alerts.exceptions].map(([msg, e]) =>
    `- ${knownBug(msg) ? `**filed ${knownBug(msg)}** · ` : staleException(msg, e) ? `**stale** (every sighting predates "${staleException(msg, e)}") · ` : '**NEW** · '}${e.icon ? `\`${e.icon}\` ` : ''}${msg}\n  - ${[...e.routes].slice(0, 6).join('\n  - ')}${e.routes.size > 6 ? `\n  - … ${e.routes.size - 6} more` : ''}`)
    : ['_none_']),
  '',
  `### Environment (${alerts.environment.size} distinct)`,
  '',
  ...(alerts.environment.size ? [...alerts.environment].map(([msg, e]) => `- ${msg} — ${e.routes.size} route(s)`) : ['_none_']),
  '',
];
writeFileSync(join(OUT, 'coverage.md'), lines.join('\n'));
writeFileSync(join(OUT, 'node-presenters.json'), JSON.stringify(nodePresenters));
writeFileSync(join(OUT, 'coverage.json'), JSON.stringify({
  denominator: denominator.size, reached: coveredKnown.length, attempted: attempted.length,
  never: never.length, unknownToSpecs, noSpec, files, nodesTotal,
  doors: { reached: coveredDoors.length, of: doorNames.size },
  embedded: { reached: coveredEmbedded.length, of: embeddedNames.size },
  placeBased: { reached: coveredPlaced.length, of: placedNames.size },
  alerts: { byClass: Object.fromEntries(alerts.byClass), unread: alerts.unread,
    exceptions: [...alerts.exceptions].map(([message, e]) => ({ message, icon: e.icon, routes: [...e.routes], runs: [...e.runs] })) },
}, null, 1));

console.log(`coverage: DOORS ${coveredDoors.length}/${doorNames.size} (${pct(coveredDoors.length, doorNames.size)})`
  + ` · embedded ${coveredEmbedded.length}/${embeddedNames.size}`
  + ` · all ${coveredKnown.length}/${denominator.size} (${pct(coveredKnown.length)})`);
console.log(`  ${attempted.length} named but unreachable · ${never.length} never seen`
  + ` · ${unknownToSpecs.length} not in the presenter list · ${noSpec.length} with no spec to diff`);
console.log(`  alerts: ${[...alerts.byClass].map(([c, n]) => `${c}=${n}`).join(' ') || 'none read'}`
  + `${alerts.unread ? ` unread=${alerts.unread}` : ''} · ${alerts.exceptions.size} distinct exception(s), ${newExceptions} not yet filed`);
console.log(`  ${nodesAttributed} node(s) attributed to a presenter`);
console.log(`written: ${join(OUT, 'coverage.md')}`);
