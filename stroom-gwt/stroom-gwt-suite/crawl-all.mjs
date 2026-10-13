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
// Run several crawls at once, one child process per shard.
//
//   USER=admin PASS=… node stroom-gwt/stroom-gwt-suite/crawl-all.mjs
//   AREAS="Monitoring,Security,Tools" WORKERS=4 MAXNODES=60 node stroom-gwt/stroom-gwt-suite/crawl-all.mjs
//
// Nodes are independent by construction — every one is reached from a fresh app — so the only thing
// standing between this crawl and a linear speed-up was that it ran in one browser. Sharding by AREA
// needs no changes to `crawl.mjs` at all: each child writes its own `crawl-<area>.json`, and
// `coverage.mjs` already merges every file in the directory, because coverage was always meant to be
// cumulative across runs.
//
// Sharding by AREA rather than by node keeps each child's queue self-contained: a shard that dies
// costs one area, and re-running it is one command. It also means shards are UNEVEN — an area with
// 60 nodes and one with 6 finish far apart — which is the price of not sharing a queue between
// processes. `WORKERS` therefore matters less than which areas are in the list.
import { spawn } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import { fromSuite, oracle } from './lib/paths.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
/** The main menu's groups — the crawl's own entry points. */
const AREAS = env('AREAS', 'Monitoring,Security,Tools,Administration,Annotations')
  .split(',').map((s) => s.trim()).filter(Boolean);
const WORKERS = Number(env('WORKERS', '3'));
// Inherited by every child. Named here because it is NOT obvious from this file that a wide run
// takes crawl.mjs's per-shard default of 40 — which silently truncates every shard.
const MAXNODES = env('MAXNODES', '');
/**
 * Which crawler each shard runs. `walk` is walk.mjs — one session, depth-first, no reloads — and
 * is the default since 2026-09-04, when it matched crawl.mjs on the Monitoring shard with every
 * difference named (compare-crawls.mjs: 1 of 64 shared outcomes moved, on a live-data screen) at
 * 3.1x the speed. `crawl` keeps crawl.mjs available for comparison.
 */
const WALKER = env('WALKER', 'walk') === 'crawl' ? `${SUITE}/crawl.mjs` : `${SUITE}/walk.mjs`;
const OUT = fromSuite(env('OUT', `${SUITE}/out/crawl`));

await mkdir(OUT, { recursive: true });

const started = Date.now();
const queue = [...AREAS];
const results = [];

/**
 * A shard is a main-menu AREA, or `recipes:<kind>` for the source-mined route recipes — which is
 * where the document editors and their sub-tabs come from, and therefore most of the coverage.
 */
function envForShard(shard) {
  // `directed:dashboard` runs walk.mjs's directed seeds — preconditions the source names that no
  // blind walk hits (design mode, column menus, a component of each type).
  if (shard.startsWith('directed:')) return { DIRECTED: shard.slice('directed:'.length) };
  if (!shard.startsWith('recipes:')) return { AREA: shard };
  // `recipes:doc:AnalyticRule|Report` walks only those document types, so the 26 editors — the
  // longest shard by far, at ~2 nodes/min — can be split across workers instead of run in series.
  const [, kind, types] = shard.split(':');
  return { RECIPES: fromSuite(env('RECIPES', oracle('route-recipes.json'))), RECIPEKIND: kind,
    ...(types ? { DOCTYPES: types.split('|').join(',') } : {}) };
}

/** One child, inheriting the environment (URL/USER/PASS/MAXNODES/DEPTH) with its shard overridden. */
function runArea(area) {
  return new Promise((resolve) => {
    const t0 = Date.now();
    const child = spawn(process.execPath, [WALKER],
      { env: { ...process.env, ...envForShard(area), OUT }, stdio: ['ignore', 'pipe', 'pipe'] });
    let tail = '';
    const keep = (buf) => { tail = (tail + buf.toString()).slice(-4000); };
    child.stdout.on('data', keep);
    child.stderr.on('data', keep);
    child.on('close', (code) => {
      const ms = Date.now() - t0;
      const nodes = /nodes (\d+)\/(\d+) reached/.exec(tail);
      // A shard that CAPPED is a shard that did not finish, and the default MAXNODES is 40 — small
      // enough that a wide run can cap every shard and still look like eight ticks. It cost a full
      // pass on 2026-09-04 before anyone read the per-shard tail, so say it on the summary line.
      const capped = /CAPPED at MAXNODES=(\d+)[\s\S]*?(\d+) queued/.exec(tail);
      console.log(`${code === 0 ? '✓' : '✗'} ${area.padEnd(16)} ${(ms / 1000).toFixed(0)}s`
        + `${nodes ? `  ${nodes[1]} nodes` : ''}${code === 0 ? '' : `  exit ${code}`}`
        + `${capped ? `  !! CAPPED at ${capped[1]}, ${capped[2]} still queued` : ''}`);
      if (code !== 0) console.log(tail.split('\n').slice(-6).map((l) => `    ${l}`).join('\n'));
      resolve({ area, code, ms });
    });
  });
}

console.log(`crawling ${AREAS.length} area(s) across ${WORKERS} worker(s) → ${OUT} with ${WALKER}`);
console.log(MAXNODES
  ? `  MAXNODES=${MAXNODES} per shard\n`
  : '  MAXNODES unset — each shard stops at crawl.mjs\'s default of 40. Set it for a wide pass.\n');
const workers = Array.from({ length: Math.min(WORKERS, AREAS.length) }, async () => {
  while (queue.length) results.push(await runArea(queue.shift()));
});
await Promise.all(workers);

const wall = Date.now() - started;
const serial = results.reduce((s, r) => s + r.ms, 0);
const failed = results.filter((r) => r.code !== 0);
console.log(`\nwall ${(wall / 1000).toFixed(0)}s · summed ${(serial / 1000).toFixed(0)}s`
  + ` · speed-up ${(serial / wall).toFixed(1)}x`);
if (failed.length) {
  console.log(`${failed.length} shard(s) FAILED: ${failed.map((f) => f.area).join(', ')} — re-run those areas alone`);
  process.exit(1);
}
console.log('next: node stroom-gwt/stroom-gwt-suite/coverage.mjs');
