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
// What SHOULD each presenter offer? Capability specs mined from the GWT source, then diffed against
// what the crawler actually observed.
//
//   node stroom-gwt-suite/tools/build-capability-specs.mjs
//     -> oracles/capability-specs.json   (expected buttons + columns per presenter)
//     -> oracles/capability-specs.md     (the same, plus the expected-vs-observed diff)
//
// Everything the suite holds today is DESCRIPTIVE: the crawler records what it finds and has nothing
// to compare it against. A screen that renders with half its buttons missing passes silently, because
// no artefact says how many there should be. This is the oracle.
//
// Two shapes carry it, both declarative:
//
//   SvgPresets.DELETE.title("Delete selected rule")   a toolbar button; the preset supplies the
//                                                     default title AND the default enabled state,
//                                                     since SvgPresets declares each as
//                                                     enabled(...) or disabled(...)
//   DataGridUtil.headingBuilder("Rule")               a grid column header
//
// The enabled/disabled default is the useful surprise: it is an oracle for "disabled at rest", which
// is the selection-gated behaviour the screen profiles derive but could never predict.
//
// MINING PROPOSES, EXECUTION DISPOSES. Today produced a route recipe from a commented-out line, and a
// live menu registration whose GIN binding was commented out. So a spec is a CLAIM. The diff section
// exists to test the miner against the presenters we can already reach, where observed truth is held.
import { readdirSync, readFileSync, statSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { SOURCE, oracle, outPath } from '../lib/paths.mjs';

const GWT = SOURCE;
const ROOTS = [
  'stroom-core-client/src/main/java/stroom',
  'stroom-core-client-widget/src/main/java/stroom',
  'stroom-statistics/stroom-statistics-client/src/main/java/stroom',
];
const CRAWLS = outPath('crawl');

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

/** Comments stripped: dead code is the one input that looks exactly like live code to a regex. */
const readSrc = (f) => readFileSync(f, 'utf8')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .split('\n').map((l) => l.replace(/^\s*\/\/.*$/, '')).join('\n');

// ── SvgPresets: constant -> { title, enabledByDefault } ──────────────────────
const preset = new Map();
{
  const f = files.find((p) => cls(p) === 'SvgPresets');
  const src = f ? readSrc(f) : '';
  for (const m of src.matchAll(
    /public\s+static\s+final\s+Preset\s+([A-Z0-9_]+)\s*=\s*(enabled|disabled)\([^,]+,\s*"([^"]*)"/g)) {
    preset.set(m[1], { title: m[3], enabled: m[2] === 'enabled' });
  }
}

// Classes that show dialogs, from the recipe miner — so a helper field can be recognised as a child.
const showerClasses = new Set((() => {
  try { return JSON.parse(readFileSync(oracle('route-recipes.json'), 'utf8')).showEdges.map((e) => e.shower); } catch { return []; }
})());

// ── Per-presenter capabilities ───────────────────────────────────────────────
const specs = [];
/** class -> { parent, buttons, columns } before inheritance is applied. */
const own = new Map();
for (const f of files) {
  const src = readSrc(f);
  const name = cls(f);
  // The superclass, generics stripped. Document editors declare almost no buttons of their own:
  // `FeedPresenter extends DocTabPresenter`, and DocTabPresenter is where `SvgPresets.SAVE` lives. The
  // first run mined "no spec" for 14 presenters that observably had toolbars, and reported Save/Save As
  // as `extra` on every editor — both were this.
  const parent = (src.match(new RegExp(`class\\s+${name}\\b[^{]*?\\bextends\\s+([A-Za-z_][A-Za-z0-9_]*)`)) ?? [])[1] ?? null;
  const buttons = new Map();   // title -> enabledByDefault
  // `SvgPresets.X` optionally followed by `.title("override")`.
  for (const m of src.matchAll(/SvgPresets\s*\.\s*([A-Z0-9_]+)\s*(?:\.\s*title\(\s*"([^"]*)"\s*\))?/g)) {
    const p = preset.get(m[1]);
    const title = m[2] || p?.title;
    if (!title) continue;
    // An explicit .title() override keeps the preset's enabled default.
    buttons.set(title, p?.enabled ?? true);
  }
  // A SECOND retitling idiom, assigned then set on the next line:
  //     createButton = view.addButton(SvgPresets.NEW_ITEM);
  //     createButton.setTitle("Create Favourite From Current Query");
  // Only the fluent `.title(...)` form was handled, so QueryFavourites expected New/Edit/Delete while
  // the UI correctly showed Create Favourite From Current Query / Change Favourite Name / Delete
  // Favourite. The observation was right and the expectation was stale — the failure mode an oracle
  // must not have.
  for (const m of src.matchAll(
    /([a-zA-Z_][A-Za-z0-9_]*)\s*=\s*[^;]*?SvgPresets\s*\.\s*([A-Z0-9_]+)[^;]*;\s*\1\s*\.\s*setTitle\(\s*"([^"]*)"\s*\)/g)) {
    const p2 = preset.get(m[2]);
    if (p2 && buttons.has(p2.title) && !src.includes(`.title("${p2.title}")`)) buttons.delete(p2.title);
    buttons.set(m[3], p2?.enabled ?? true);
  }

  const columns = new Set();
  for (const m of src.matchAll(/headingBuilder\(\s*"([^"]+)"\s*\)/g)) columns.add(m[1]);

  // A dialog's button set is declared, not drawn: PopupType says which buttons the shell supplies.
  // This is what gives the ~250 presenters that declare no toolbar of their own an expectation —
  // most are dialogs, and OK/Cancel is exactly what a crawl should observe on them.
  const POPUP_BUTTONS = {
    OK_CANCEL_DIALOG: ['OK', 'Cancel'],
    CREATE_OK_CANCEL_DIALOG: ['OK', 'Cancel'],
    ACCEPT_REJECT_DIALOG: ['Accept', 'Reject'],
    CLOSE_DIALOG: ['Close'],
    POPUP: [],
  };
  //
  // Attributed ONLY to a class that shows ITSELF. A PopupType in a file may describe a dialog that
  // class OPENS rather than what it is: DashboardPresenter then expected a lone `Close` while
  // observing 27 toolbar actions, and QueryPresenter expected OK/Cancel belonging to the processor
  // limits dialog it raises. `ShowPopupEvent.builder(this)` — or receiving a Builder to configure —
  // is the same self-shown test the reachability graph uses.
  const selfShown = /ShowPopupEvent\s*\.\s*builder\(\s*this\s*\)/.test(src)
    || /ShowPopupEvent\.Builder\s+[a-z]/.test(src);
  const popupTypes = new Set();
  if (selfShown) {
    for (const m of src.matchAll(/PopupType\.([A-Z_]+)/g)) popupTypes.add(m[1]);
    for (const t of popupTypes) for (const b of POPUP_BUTTONS[t] ?? []) if (!buttons.has(b)) buttons.set(b, true);
  }

  // Which server resources this presenter talks to. `restFactory.create(X_RESOURCE)` names the
  // resource; the lambda that follows names the method. Gives the blocked-request triage list an
  // expectation to be checked against rather than a bare endpoint.
  const resources = new Set();
  for (const m of src.matchAll(/\.create\(\s*([A-Z][A-Z0-9_]*_RESOURCE)\s*\)([\s\S]{0,200}?res\s*->\s*res\s*\.\s*([a-zA-Z0-9_]+))?/g)) {
    resources.add(m[3] ? `${m[1]}.${m[3]}` : m[1]);
  }
  // Older shape: addResizableColumn(col, "Name", width)
  for (const m of src.matchAll(/addResizableColumn\([\s\S]{0,600}?,\s*"([^"]+)"\s*,\s*\d+\s*\)/g)) {
    columns.add(m[1]);
  }
  // Embedded children. A screen's toolbar is usually its CHILDREN's: DataRetentionPresenter observed
  // `Save rules / Edit rule / Copy rule`, all of which are mined — under DataRetentionPolicyPresenter,
  // the list it embeds. Diffing at presenter granularity without this compares a parent against the
  // union of its children and reports every one of their buttons as unexplained.
  const children = new Set();
  for (const m of src.matchAll(
    /(?:private|protected|public|final|,|\()\s*(?:final\s+)?(?:Provider<\s*)?([A-Za-z_][A-Za-z0-9_]*Presenter)\s*>?\s+[a-z][A-Za-z0-9_]*\s*[;=,)]/g)) {
    if (m[1] !== name) children.add(m[1]);
  }
  // HELPER classes that show dialogs on a presenter's behalf are children too. TablePresenter holds
  // a ColumnsManager, and ColumnsManager is what shows Filter / Format / Rename Column / Expression;
  // with only *Presenter fields counted, a dialog opened from a Table tab had no candidate parent
  // edge and stayed ambiguous. Any field whose class is a known SHOWER counts.
  for (const m of src.matchAll(
    /(?:private|protected|public|final|,|\()\s*(?:final\s+)?(?:Provider<\s*)?([A-Z][A-Za-z0-9_]*(?:Manager|Support|Helper|Handler|Model|Info|Activity|Registry|Client))\s*>?\s+[a-z][A-Za-z0-9_]*\s*[;=,)]/g)) {
    if (m[1] !== name && showerClasses.has(m[1])) children.add(m[1]);
  }
  own.set(name, { parent, buttons, columns, popupTypes: [...popupTypes], resources, children });
}

// ── Inherit ──────────────────────────────────────────────────────────────────
// A presenter offers what it declares PLUS what its ancestors declare. Resolved transitively, with a
// seen-set because a malformed chain must not hang the generator.
function resolved(name, seen = new Set()) {
  const node = own.get(name);
  if (!node || seen.has(name)) {
    return { buttons: new Map(), columns: new Set(), resources: new Set(), embeds: [],
      viaChildren: new Set(), from: [] };
  }
  seen.add(name);
  const up = node.parent
    ? resolved(node.parent, seen)
    : { buttons: new Map(), columns: new Set(), resources: new Set(), from: [] };
  const buttons = new Map([...up.buttons, ...node.buttons]);
  const columns = new Set([...up.columns, ...node.columns]);
  const resources = new Set([...up.resources, ...node.resources]);
  // Children contribute what they draw. One level deep and guarded by `seen`: presenters hold each
  // other in cycles, and an unbounded walk would union half the application into every screen.
  //
  // Kept SEPARATE from `buttons` on purpose. Unioning them into the expectation took exact matches
  // from 14 to 4 and manufactured 35 false `missing`, because a parent embeds many children and shows
  // only some at a time — a tabbed screen offers one child's toolbar, not all of them. So children
  // EXPLAIN what was observed; they never demand it. Anything a child could draw is legitimate to see
  // and is not evidence of a defect either way.
  const embeds = [];
  const viaChildren = new Set();
  for (const c of node.children ?? []) {
    const kid = own.get(c);
    if (!kid || seen.has(c)) continue;
    embeds.push(c);
    for (const t of kid.buttons.keys()) viaChildren.add(t);
  }
  return { buttons, columns, resources, embeds, viaChildren,
    from: node.parent ? [node.parent, ...up.from] : [] };
}
for (const name of own.keys()) {
  const { buttons, columns, resources, embeds, viaChildren, from } = resolved(name);
  // A presenter that only EMBEDS still deserves a spec: with an empty expectation and a viaChildren
  // set, its observations can be explained rather than reported as "nothing mined" — which reads as a
  // hole in the miner when it is really a screen assembled from parts.
  if (!buttons.size && !columns.size && !resources.size && !viaChildren.size) continue;
  specs.push({
    presenter: name,
    inheritsFrom: from,
    embeds: embeds ?? [],
    viaChildren: [...(viaChildren ?? [])],
    popupTypes: own.get(name).popupTypes,
    resources: [...resources],
    buttons: [...buttons.entries()].map(([title, enabled]) => ({ title, enabled })),
    disabledAtRest: [...buttons.entries()].filter(([, e]) => !e).map(([t]) => t),
    columns: [...columns],
  });
}

// ── Diff against what the crawler observed ──────────────────────────────────
// Only for presenters a recipe run actually reached: those nodes carry `presenter`, so the join is by
// name rather than by slug-guessing. This is the miner being tested against ground truth we hold.
// App chrome belongs to the SHELL, not to the screen beside it. `disabledAtRest` is recorded
// unfiltered by the crawler — unlike the affordance loop, which excludes chrome — so the explorer's
// greyed-out New/Delete/Locate Current Item were attributed to every screen they sat next to, and
// showed up as `extra` on a dozen presenters. Same rule the screen profiles use, applied at analysis
// time so it needs no re-crawl.
const CHROME = new Set([
  'Main Menu', 'Show Menu', 'Expand All', 'Collapse All', 'Locate Current Item', 'Find In Content',
  'Toggle Alerts', 'Ask Stroom AI', 'Filter Types', 'Clear Filter', 'Quick Filter Syntax Help',
  'New', 'Delete',
]);
// …but a label is chrome only when the screen does not claim it itself. `New` and `Delete` are the
// explorer's buttons AND genuine buttons on FsVolumeGroup, IndexVolumeGroup, NodeGroup and
// ProcessorProfile, so filtering by label alone stripped four screens' real toolbars and reported them
// as MISSING. The spec is what disambiguates: keep anything this presenter is expected to draw.
const expectedBy = new Map(specs.map((sp) => [sp.presenter, new Set(sp.buttons.map((b) => b.title))]));
const isChrome = (presenter, label) => CHROME.has(label) && !(expectedBy.get(presenter)?.has(label));
const observed = new Map();   // presenter -> Set(labels)
// A dialog names itself, and route-recipes.json says which presenter is shown under that caption.
// Without this the diff only sees RECIPE-SEEDED nodes, because only those carry a `presenter` field —
// which is a limit of the join, not of how much has been crawled. Every dialog reached by clicking
// was already identifiable and was being thrown away.
const byCaption = new Map();
if (existsSync(oracle('route-recipes.json'))) {
  for (const c of JSON.parse(readFileSync(oracle('route-recipes.json'), 'utf8')).captionOf ?? []) {
    const k = c.caption.replace(/\s+/g, ' ').trim().toLowerCase();
    // Ambiguous captions ("Confirm", "Alert") are shown by many presenters; a diff cannot attribute
    // observations to one of them, so they are dropped rather than guessed at.
    if (byCaption.has(k) && byCaption.get(k) !== c.presenter) byCaption.set(k, null);
    else if (!byCaption.has(k)) byCaption.set(k, c.presenter);
  }
}
const presenterOfNode = (n) => n.presenter
  ?? (n.dialogs ?? []).map((d) => byCaption.get(d.replace(/\s+/g, ' ').trim().toLowerCase()))
    .find(Boolean)
  ?? null;
if (existsSync(CRAWLS)) {
  for (const file of readdirSync(CRAWLS).filter((x) => x.endsWith('.json'))) {
    const d = JSON.parse(readFileSync(join(CRAWLS, file), 'utf8'));
    const byId = new Map((d.nodes ?? []).map((n) => [n.id, presenterOfNode(n)]).filter(([, p]) => p));
    for (const n of d.nodes ?? []) {
      const p = presenterOfNode(n);
      if (!p) continue;
      const set = observed.get(p) ?? new Set();
      for (const t of n.disabledAtRest ?? []) if (!isChrome(p, t)) set.add(t);
      observed.set(p, set);
    }
    for (const a of d.affordances ?? []) {
      const p = byId.get(a.node);
      if (!p) continue;
      const set = observed.get(p) ?? new Set();
      if (!isChrome(p, a.label)) set.add(a.label);
      observed.set(p, set);
    }
  }
}

const specByName = new Map(specs.map((s) => [s.presenter, s]));
const diffs = [];
for (const [presenter, seen] of observed) {
  const spec = specByName.get(presenter);
  if (!spec) { diffs.push({ presenter, note: 'no mined spec', seen: [...seen] }); continue; }
  const expected = new Set(spec.buttons.map((b) => b.title));
  const viaKids = new Set(spec.viaChildren ?? []);
  const missing = [...expected].filter((t) => !seen.has(t));
  // An affordance a child could legitimately draw is explained, not unexplained.
  const extra = [...seen].filter((t) => !expected.has(t) && !viaKids.has(t));
  const explained = [...seen].filter((t) => !expected.has(t) && viaKids.has(t)).length;
  diffs.push({ presenter, expected: expected.size, seenCount: seen.size, missing, extra, explained });
}

writeFileSync(oracle('capability-specs.json'), JSON.stringify({ specs, diffs }, null, 1));

const L = [];
L.push('# Capability specs — what each presenter SHOULD offer', '');
L.push('**Generated** by `stroom-gwt-suite/tools/build-capability-specs.mjs`. Do not hand-edit.', '');
L.push('The suite records what it finds; nothing said what it should find. Mined from the two');
L.push('declarative shapes GWT uses: `SvgPresets.X[.title("…")]` for toolbar buttons and');
L.push('`headingBuilder("…")` for grid columns.', '');
L.push('`SvgPresets` declares each preset as `enabled(...)` or `disabled(...)`, which gives an oracle');
L.push('for **disabled at rest** — the selection-gated behaviour the screen profiles derive but cannot');
L.push('predict.', '');
L.push('> **A spec is a CLAIM, not a fact.** Source mining produced a route from a commented-out line');
L.push('> today, and a live menu registration behind a dead GIN binding. The diff below is the miner');
L.push('> being tested against the presenters we can already reach.', '');
L.push('## Totals', '', '| | count |', '| --- | ---: |');
L.push(`| Presenters with a mined capability spec | ${specs.length} |`);
L.push(`| …declaring toolbar buttons | ${specs.filter((s) => s.buttons.length).length} |`);
L.push(`| …declaring grid columns | ${specs.filter((s) => s.columns.length).length} |`);
L.push(`| SvgPresets constants resolved | ${preset.size} |`);
L.push(`| …whose spec includes inherited buttons | ${specs.filter((s2) => s2.inheritsFrom?.length).length} |`);
L.push(`| …declaring a dialog PopupType | ${specs.filter((s2) => s2.popupTypes?.length).length} |`);
L.push(`| …naming server resources | ${specs.filter((s2) => s2.resources?.length).length} |`);
L.push(`| Presenters with observed crawl data to diff | ${diffs.length} |`, '');
L.push('## Expected vs observed', '');
L.push('`missing` = mined but never seen (miner phantom, dead code, or a real defect).');
L.push('`extra` = seen but not mined (the miner is incomplete — chrome, menu items, column headers).', '');
for (const d of diffs.sort((a, b) => (b.missing?.length ?? 0) - (a.missing?.length ?? 0))) {
  if (d.note) { L.push(`* **${d.presenter}** — ${d.note}, saw ${d.seen.length}`); continue; }
  L.push(`* **${d.presenter}** — expected ${d.expected}, saw ${d.seenCount}` +
    `${d.missing.length ? `, MISSING: ${d.missing.join(', ')}` : ''}` +
    `${d.extra.length ? `, extra: ${d.extra.slice(0, 6).join(', ')}` : ''}`);
}
L.push('', '## Specs', '');
for (const s of specs.sort((a, b) => a.presenter.localeCompare(b.presenter))) {
  L.push(`### ${s.presenter}`, '');
  if (s.buttons.length) {
    L.push(`**buttons** (${s.buttons.length}): ` +
      s.buttons.map((b) => `\`${b.title}\`${b.enabled ? '' : ' _(disabled at rest)_'}`).join(' · '));
    L.push('');
  }
  if (s.columns.length) { L.push(`**columns** (${s.columns.length}): ` + s.columns.map((c) => `\`${c}\``).join(' · ')); L.push(''); }
  if (s.resources?.length) { L.push(`**resources** (${s.resources.length}): ` + s.resources.slice(0, 12).map((c) => `\`${c}\``).join(' · ')); L.push(''); }
}
writeFileSync(oracle('capability-specs.md'), L.join('\n'));
console.log(`${specs.length} capability specs (${preset.size} presets resolved), ${diffs.length} diffable`);
