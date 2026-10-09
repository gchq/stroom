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
// Of the 414 presenters, which have we actually REACHED?
//
//   node stroom-stroom-gwt-suite/tools/build-crawl-coverage.mjs   -> oracles/crawl-coverage.md
//
// Every crawl so far has reported numbers about the crawler — "73 distinct places" — which cannot be
// compared to the inventory and so never answered the only question that matters: what is left. This
// joins the two, using the identifiers each side actually publishes:
//
//   dialog captions   a crawl records what a dialog calls itself; `route-recipes.json` says which
//                     presenter is shown under that caption, from the ShowPopupEvent site
//   menu leaves       `Main Menu > Tools > Dependencies` is registered in the source against the
//                     plugin that reveals DependenciesPresenter
//
// Both sides of the miss are reported: presenters with no evidence, AND crawled captions that match no
// presenter. A one-sided report would let a broken join look like thorough coverage — the reason the
// ledger one-liners were wrong in BOTH directions the last time this was attempted.
import { readdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { oracle, outPath } from '../lib/paths.mjs';

const CRAWLS = outPath('crawl');
const OUT = oracle('crawl-coverage.md');
const recipes = JSON.parse(readFileSync(oracle('route-recipes.json'), 'utf8'));

const norm = (s) => String(s ?? '').replace(/\s+/g, ' ').trim().toLowerCase();

// caption -> presenters (a caption can be shown by more than one site)
const byCaption = new Map();
for (const c of recipes.captionOf) {
  const k = norm(c.caption);
  if (!byCaption.has(k)) byCaption.set(k, new Set());
  byCaption.get(k).add(c.presenter);
}
// menu leaf -> presenter
const byLeaf = new Map();
for (const r of recipes.recipes) {
  if (r.via !== 'menu' || !r.presenter) continue;
  const leaf = r.route[0].leaf ?? r.route[0].group;
  byLeaf.set(norm(leaf), r.presenter);
}

// ── What the crawls saw ──────────────────────────────────────────────────────
const seenCaptions = new Map();   // caption -> crawl files that saw it
const files = existsSync(CRAWLS) ? readdirSync(CRAWLS).filter((f) => f.endsWith('.json')) : [];
const runs = files.map((f) => ({ from: f.replace(/^crawl-|\.json$/g, ''),
  nodes: JSON.parse(readFileSync(join(CRAWLS, f), 'utf8')).nodes ?? [] }));
// …plus the append-only evidence log, which is the ONLY place a completed run survives a later capped
// one. The per-area JSON is overwritten every run: a Security pass capped at 90 nodes replaced a
// complete 61-place pass and took About, Preferences, Import and Export down with it.
const LOG = join(CRAWLS, 'evidence.jsonl');
if (existsSync(LOG)) {
  const byArea = new Map();
  for (const line of readFileSync(LOG, 'utf8').split('\n').filter(Boolean)) {
    let n; try { n = JSON.parse(line); } catch { continue; }
    if (!byArea.has(n.area)) byArea.set(n.area, []);
    byArea.get(n.area).push(n);
  }
  for (const [area, nodes] of byArea) runs.push({ from: `${area} (log)`, nodes });
}
for (const { from: f, nodes } of runs) {
  for (const n of nodes) {
    // A node's dialog captions, plus the label that opened it — a menu leaf reaches a screen whose
    // node carries no caption at all.
    for (const cap of [...(n.dialogs ?? []), n.path?.[n.path.length - 1]].filter(Boolean)) {
      const k = norm(cap);
      if (!seenCaptions.has(k)) seenCaptions.set(k, new Set());
      seenCaptions.get(k).add(f);
    }
  }
}

// ── Join ─────────────────────────────────────────────────────────────────────
const reached = new Map();        // presenter -> how we know
for (const [cap, where] of seenCaptions) {
  for (const p of byCaption.get(cap) ?? []) {
    reached.set(p, `caption "${cap}" in ${[...where].join(', ')}`);
  }
  const leafHit = byLeaf.get(cap);
  if (leafHit) reached.set(leafHit, `menu leaf "${cap}" in ${[...where].join(', ')}`);
}

const inventory = readFileSync(oracle('gwt-inventory.csv'), 'utf8').split('\n').slice(1).filter(Boolean);
const presenters = [];
for (const line of inventory) {
  const c = line.split(',');
  if (c[4] !== 'Presenter') continue;
  if (c[6] !== 'Screen/Dialog' && c[6] !== 'Shared Screen/Dialog') continue;
  presenters.push({ name: c[0].split('/').pop().replace(/\.java$/, ''), area: c[7] });
}
const known = new Set(presenters.map((p) => p.name));
const covered = presenters.filter((p) => reached.has(p.name));
const missing = presenters.filter((p) => !reached.has(p.name));

// The other direction: captions the crawl saw that no presenter claims. Either the caption map is
// incomplete (a caption built from a variable rather than a literal) or the crawl is naming things
// that are not presenters at all — both are worth seeing, neither is visible from the first list.
const unclaimed = [...seenCaptions.keys()]
  .filter((c) => !(byCaption.get(c) ?? new Set()).size && !byLeaf.has(c));

const byArea = new Map();
for (const p of presenters) {
  if (!byArea.has(p.area)) byArea.set(p.area, { total: 0, hit: 0 });
  const a = byArea.get(p.area);
  a.total += 1;
  if (reached.has(p.name)) a.hit += 1;
}

const lines = ['# Crawl coverage — how much of the 414 have we reached?', '',
  '**Generated** by `stroom-stroom-gwt-suite/tools/build-crawl-coverage.mjs`. Do not hand-edit.', '',
  'Joins the crawl output to `gwt-inventory.csv` through the identifiers each side publishes: dialog',
  'captions (mined from the `ShowPopupEvent` site) and menu leaves (mined from the menu registration).', '',
  '## Totals', '', '| | count |', '| --- | ---: |',
  `| Presenters in the inventory | ${presenters.length} |`,
  `| …with crawl evidence | **${covered.length}** |`,
  `| …no evidence yet | ${missing.length} |`,
  `| Crawl files joined | ${files.length} |`,
  `| Captions seen that match no presenter | ${unclaimed.length} |`, '',
  '> A low number here is not a defect. Most presenters are embedded panels with no route of their own,',
  '> and the crawls run so far cover three areas of forty-five. The point is that the gap is now',
  '> COUNTABLE and named, rather than being a number about the crawler.', '',
  '## By area', '', '| area | reached | total |', '| --- | ---: | ---: |'];
for (const [area, a] of [...byArea.entries()].sort((x, y) => y[1].hit - x[1].hit)) {
  lines.push(`| ${area} | ${a.hit} | ${a.total} |`);
}
lines.push('', '## Reached', '');
for (const p of covered.sort((a, b) => a.name.localeCompare(b.name))) {
  lines.push(`* **${p.name}** — ${reached.get(p.name)}`);
}
lines.push('', '## Captions seen that match no presenter', '',
  'Either the caption map is incomplete (a caption built from a variable, not a literal) or these are',
  'not presenters. Reported because a one-sided join hides its own failures.', '');
for (const c of unclaimed.sort()) lines.push(`* \`${c}\``);
lines.push('', '## No evidence yet', '');
for (const p of missing.sort((a, b) => a.name.localeCompare(b.name))) lines.push(`* ${p.name} _(${p.area})_`);

writeFileSync(OUT, lines.join('\n'));
console.log(`${covered.length} of ${presenters.length} presenters have crawl evidence ` +
  `(${files.length} crawl files, ${unclaimed.length} unmatched captions) -> ${OUT}`);
void known;
