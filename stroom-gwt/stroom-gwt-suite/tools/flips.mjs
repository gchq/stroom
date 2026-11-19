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
// What did a round change? For every place an earlier run failed, what the round said there.
//
//   node stroom-gwt-suite/tools/flips.mjs <round>
//     reads out/checks.json (written by coverage.mjs) and out/crawl/*-<round>.json
//     -> out/flips-<round>.md
//
// The ledger lists every run's fails side by side and never retires one, so a fixed bug does not
// show there as a fail that disappears. It shows here: a place that failed before and passes in the
// round.
//
// Fails are matched by PLACE (lib/places.mjs), not by the node's full path, which records the
// route the walk took and changes whenever anything ahead of it changes.
//
// Each earlier fail gets one of:
//   now passes          the round passed the same check there
//   still fails         the round failed it there (at least once)
//   now <verdict>       the round checked it with another verdict (unchecked, unstable)
//   reached, unchecked  the round reached the place but made no such check
//   not reached         the round never got there
//

import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { knownBug, knownFail } from '../lib/alerts.mjs';
import { OUT } from '../lib/paths.mjs';
import { placeOf } from '../lib/places.mjs';
import { staleBy } from '../lib/vintage.mjs';

const round = process.argv[2];
if (!round) {
  console.error('usage: node tools/flips.mjs <round>');
  process.exit(1);
}
const inRound = (file) => file.endsWith(`-${round}.json`);

const bugOf = (c) => knownFail(`${c.node} › ${c.label} — ${c.why}`) ?? knownBug(c.why) ?? (staleBy(c) ? 'stale' : 'NEW');
const checkKey = (c) => `${c.kind}|${placeOf(c.node)}|${c.label}`;

const checks = JSON.parse(readFileSync(join(OUT, 'checks.json'), 'utf8'));
const roundFiles = [...new Set(checks.map((c) => c.file))].filter(inRound);
if (!roundFiles.length) {
  console.error(`no checks from a *-${round}.json crawl — merge the round first`);
  process.exit(1);
}

// What the round said, check by check, and every place it reached at all.
const verdicts = new Map();
for (const c of checks.filter((c) => inRound(c.file))) {
  verdicts.set(checkKey(c), [...(verdicts.get(checkKey(c)) ?? []), c.verdict]);
}
const crawlDir = join(OUT, 'crawl');
const reached = new Set(readdirSync(crawlDir).filter(inRound).flatMap((f) =>
  (JSON.parse(readFileSync(join(crawlDir, f), 'utf8')).nodes ?? [])
    .filter((n) => n.status === 'reached' || n.status === 'duplicate')
    .map((n) => placeOf(n.id))));

const outcomeOf = (c) => {
  const v = verdicts.get(checkKey(c));
  if (v?.includes('fail')) return 'still fails';
  if (v?.includes('pass')) return 'now passes';
  if (v?.length) return `now ${v[0]}`;
  return reached.has(placeOf(c.node)) ? 'reached, unchecked' : 'not reached';
};

// Each earlier fail once per place, under the bug it is filed against.
const earlier = new Map();
for (const c of checks.filter((c) => !inRound(c.file) && c.verdict === 'fail')) {
  if (!earlier.has(checkKey(c))) earlier.set(checkKey(c), c);
}
const byBug = new Map();
for (const c of earlier.values()) {
  const bug = bugOf(c);
  const row = byBug.get(bug) ?? new Map();
  const outcome = outcomeOf(c);
  row.set(outcome, [...(row.get(outcome) ?? []), c]);
  byBug.set(bug, row);
}
// Fails the round found at a place no earlier run had failed.
const firsts = checks.filter((c) => inRound(c.file) && c.verdict === 'fail' && !earlier.has(checkKey(c)));

const OUTCOMES = ['now passes', 'still fails', 'reached, unchecked', 'not reached'];
const bugs = [...byBug.keys()].sort((a, b) => (a === 'stale') - (b === 'stale') || a.localeCompare(b, 'en', { numeric: true }));
const line = (c) => `- **${c.kind}** ${c.node} › ${c.label}`;
const lines = [
  `# Flips in round ${round}`,
  '',
  `_Every place an earlier run failed, and what round ${round} said there (${roundFiles.length} crawl`
  + ` file(s)). Places are matched by kind, area and the last two steps of the path, so a changed route`
  + ' still matches. Written by `tools/flips.mjs`._',
  '',
  `| bug | places | ${OUTCOMES.join(' | ')} | other |`,
  `| --- | ---: | ${OUTCOMES.map(() => '---:').join(' | ')} | ---: |`,
  ...bugs.map((bug) => {
    const row = byBug.get(bug);
    const total = [...row.values()].reduce((n, l) => n + l.length, 0);
    const other = [...row].filter(([o]) => !OUTCOMES.includes(o)).reduce((n, [, l]) => n + l.length, 0);
    return `| ${bug} | ${total} | ${OUTCOMES.map((o) => row.get(o)?.length ?? 0).join(' | ')} | ${other} |`;
  }),
  '',
  `## First fails in round ${round} (${firsts.length})`,
  '',
  ...(firsts.length ? firsts.map((c) => `${line(c)} — ${bugOf(c)}: ${String(c.why).slice(0, 160)}`) : ['_none_']),
  '',
  ...bugs.filter((bug) => bug !== 'stale').flatMap((bug) => [
    `## ${bug}`,
    '',
    ...[...byBug.get(bug)].flatMap(([outcome, list]) => [`### ${outcome} (${list.length})`, '', ...list.map(line), '']),
  ]),
];
writeFileSync(join(OUT, `flips-${round}.md`), lines.join('\n'));

console.log(`flips in ${round}: ${bugs.map((bug) => {
  const row = byBug.get(bug);
  return `${bug} ${[...row].map(([o, l]) => `${l.length} ${o}`).join(', ')}`;
}).join(' · ')}`);
console.log(`  first fails: ${firsts.length} -> ${join(OUT, `flips-${round}.md`)}`);
