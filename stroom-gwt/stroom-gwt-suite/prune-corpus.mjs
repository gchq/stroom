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
// Drop corpus bodies nothing references any more.
//
//   node stroom-gwt/stroom-gwt-suite/prune-corpus.mjs              # DRY RUN: say what would go
//   DELETE=1 node stroom-gwt/stroom-gwt-suite/prune-corpus.mjs     # actually remove them
//
// `record.mjs` writes a new body file per response and rewrites the manifest, but never removes the
// bodies the previous recording referenced — so every re-record leaves the old ones behind. After
// the 2026-09-24 re-record: 3,149 files on disk, 1,226 referenced, 1,923 dead (8.9 MB of 17.8 MB).
// The same thing happened at the previous re-record and was cleaned up by hand.
//
// A body is LIVE if the manifest names it. That is the only reference: `serve.mjs` answers a
// request by looking the exchange up in the manifest and reading the body it names, so a file no
// manifest entry mentions can never be served. The replay is re-run after pruning to prove it.
import { readFileSync, readdirSync, statSync, unlinkSync } from 'node:fs';
import { join } from 'node:path';
import { fromSuite } from './lib/paths.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const CORPUS = fromSuite(env('CORPUS', `${SUITE}/corpus/default`));
const DELETE = env('DELETE', '') === '1';

let removed = 0;
let freed = 0;
for (const part of ['api', 'app']) {
  const dir = join(CORPUS, part);
  let manifest;
  try { manifest = JSON.parse(readFileSync(join(dir, 'manifest.json'), 'utf8')); } catch { continue; }
  // Every string the manifest holds, whatever shape it takes — a body id, a path, an asset name.
  const referenced = new Set();
  const walk = (o) => {
    if (o === null || o === undefined) return;
    if (typeof o === 'string') { referenced.add(o); referenced.add(o.split('/').pop()); return; }
    if (typeof o !== 'object') return;
    if (Array.isArray(o)) { o.forEach(walk); return; }
    for (const [k, v] of Object.entries(o)) { referenced.add(k); referenced.add(String(k).split('/').pop()); walk(v); }
  };
  walk(manifest);

  const onDisk = readdirSync(dir).filter((f) => f !== 'manifest.json');
  const dead = onDisk.filter((f) => !referenced.has(f));
  const bytes = dead.reduce((s, f) => s + statSync(join(dir, f)).size, 0);
  console.log(`${part}: ${onDisk.length} file(s), ${onDisk.length - dead.length} referenced, ${dead.length} dead (${(bytes / 1048576).toFixed(1)} MB)`);
  if (DELETE) for (const f of dead) { unlinkSync(join(dir, f)); }
  removed += dead.length;
  freed += bytes;
}

console.log(DELETE
  ? `\nremoved ${removed} file(s), freeing ${(freed / 1048576).toFixed(1)} MB — re-run the replay (npm run gwt:test) to prove nothing was in use`
  : `\nDRY RUN: ${removed} file(s) would go, freeing ${(freed / 1048576).toFixed(1)} MB. DELETE=1 to do it.`);
