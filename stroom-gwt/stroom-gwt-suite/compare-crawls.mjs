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
// Did two crawls of the same area find the same things?
//
//   node stroom-gwt/stroom-gwt-suite/compare-crawls.mjs stroom-gwt-suite/out/crawl/crawl-monitoring.json /tmp/walk-mon/crawl-monitoring.json
//
// The check every crawler change has to pass (COVERAGE-PLAN.md, P1): an optimisation that changes an
// answer is a bug in a test suite. So this compares PLACES reached (by id and by signature-level
// status), and AFFORDANCE OUTCOMES per (node, label), and prints every difference by name rather than
// a percentage — a difference is either a crawler defect or a genuine change, and only reading it
// says which.
import { readFileSync } from 'node:fs';

const [a, b] = process.argv.slice(2).map((p) => JSON.parse(readFileSync(p, 'utf8')));
if (!a || !b) {
  console.error('usage: compare-crawls.mjs <baseline.json> <candidate.json>');
  process.exit(2);
}

const byId = (d) => new Map(d.nodes.map((n) => [n.id, n]));
const A = byId(a);
const B = byId(b);
const ids = new Set([...A.keys(), ...B.keys()]);

const reachedA = [...A.values()].filter((n) => n.status === 'reached').map((n) => n.id);
const reachedB = [...B.values()].filter((n) => n.status === 'reached').map((n) => n.id);
const onlyA = reachedA.filter((id) => !B.has(id) || B.get(id).status !== 'reached');
const onlyB = reachedB.filter((id) => !A.has(id) || A.get(id).status !== 'reached');

console.log(`baseline  ${a.summary.area}: ${reachedA.length} reached / ${a.nodes.length} nodes, ${a.summary.affordances} affordances, ${Math.round(a.summary.wallMs / 1000)}s`);
console.log(`candidate ${b.summary.area}: ${reachedB.length} reached / ${b.nodes.length} nodes, ${b.summary.affordances} affordances, ${Math.round(b.summary.wallMs / 1000)}s`);
console.log(`ids in common: ${[...ids].filter((id) => A.has(id) && B.has(id)).length} of ${ids.size}\n`);

// Status differences for nodes both know.
const statusDiff = [...ids].filter((id) => A.has(id) && B.has(id) && A.get(id).status !== B.get(id).status);
console.log(`STATUS changed between the two (${statusDiff.length}): `
  + statusDiff.slice(0, 10).map((id) => `${id}: ${A.get(id).status} -> ${B.get(id).status}`).join(', ')
  + (statusDiff.length > 10 ? ` … ${statusDiff.length - 10} more` : ''));
console.log(`REACHED only in baseline (${onlyA.length}):`);
console.log(onlyA.map((id) => `  - ${id}${B.has(id) ? `  [candidate: ${B.get(id).status}${B.get(id).why ? ' — ' + B.get(id).why : ''}]` : ''}`).join('\n') || '  (none)');
console.log(`\nREACHED only in candidate (${onlyB.length}):`);
console.log(onlyB.map((id) => `  + ${id}${A.has(id) ? `  [baseline: ${A.get(id).status}]` : ''}`).join('\n') || '  (none)');

// Per-node surface: button counts and disabled sets, where both reached it.
const surface = [];
for (const id of ids) {
  const x = A.get(id);
  const y = B.get(id);
  if (!x || !y || x.status !== 'reached' || y.status !== 'reached') continue;
  const dx = (x.disabledAtRest ?? []).slice().sort().join('|');
  const dy = (y.disabledAtRest ?? []).slice().sort().join('|');
  if (x.buttons !== y.buttons || dx !== dy) {
    surface.push(`  ~ ${id}: buttons ${x.buttons} -> ${y.buttons}${dx !== dy ? `, disabled [${dx}] -> [${dy}]` : ''}`);
  }
}
console.log(`\nSURFACE differs on nodes both reached (${surface.length}):`);
console.log(surface.join('\n') || '  (none)');

// Affordance outcomes per (node, label).
const key = (f) => `${f.node} :: ${f.label}`;
const FA = new Map(a.affordances.map((f) => [key(f), f]));
const FB = new Map(b.affordances.map((f) => [key(f), f]));
const moved = [];
for (const [k, f] of FA) {
  const g = FB.get(k);
  if (!g) continue;
  if (f.outcome !== g.outcome) moved.push(`  ~ ${k}: ${f.outcome} -> ${g.outcome}${g.detail ? ` (${g.detail.slice(0, 60)})` : ''}`);
}
const affOnlyA = [...FA.keys()].filter((k) => !FB.has(k));
const affOnlyB = [...FB.keys()].filter((k) => !FA.has(k));
console.log(`\nAFFORDANCE outcomes that MOVED (${moved.length} of ${[...FA.keys()].filter((k) => FB.has(k)).length} in common):`);
console.log(moved.join('\n') || '  (none)');
console.log(`\naffordances only in baseline: ${affOnlyA.length}${affOnlyA.length ? '\n' + affOnlyA.slice(0, 20).map((k) => `  - ${k}`).join('\n') : ''}`);
console.log(`affordances only in candidate: ${affOnlyB.length}${affOnlyB.length ? '\n' + affOnlyB.slice(0, 20).map((k) => `  + ${k}`).join('\n') : ''}`);

const tally = (d) => Object.entries(d.summary.outcomes ?? {}).sort().map(([k, v]) => `${k}=${v}`).join(' ');
console.log(`\noutcome tallies\n  baseline : ${tally(a)}\n  candidate: ${tally(b)}`);
if (b.findings?.length) {
  console.log(`\ncandidate pop findings (${b.findings.length}):`);
  for (const f of b.findings.slice(0, 15)) console.log(`  ‼ ${f.node} :: ${f.label} — ${f.detail}`);
}
