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
// Name the dialogs the crawl reached but nothing could identify.
//
//   STROOM=<a Stroom checkout> node stroom-gwt/stroom-gwt-suite/name-captions.mjs
//
// `build-route-recipes.mjs` mines captions from ONE shape —
// `ShowPopupEvent.builder(X).caption("literal")` — and the dialogs it misses are not exotic:
//
//   final String caption = "User Preferences";      a local, resolved later
//   chooser.setCaption("Choose Dashboard");         set on a child widget
//   .caption("Save Pipeline: " + name)              built at runtime
//   show(..., "New Credentials")                    passed to a helper
//
// Chasing those patterns one regex at a time is how a miner accumulates false positives. This works
// the other way round: take the captions the crawl ACTUALLY SAW and look each one up in the source.
// A caption found in exactly one presenter names it; a caption found in several is reported as
// AMBIGUOUS and named by nobody, because a guess in a coverage ledger is worse than a gap in it.
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { join } from 'node:path';
import { SOURCE, fromSuite, oracle } from './lib/paths.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const STROOM = env('STROOM', SOURCE);
const IN = fromSuite(env('IN', `${SUITE}/out/crawl`));
const OUT = fromSuite(env('OUT', `${SUITE}/out/caption-names.json`));

const normalise = (s) => String(s ?? '').replace(/'[^']*'/g, "'…'").replace(/\s+/g, ' ').trim();

const known = new Set(
  (JSON.parse(readFileSync(oracle('route-recipes.json'), 'utf8')).captionOf ?? [])
    .map((c) => normalise(c.caption)),
);

/** Every caption the crawl reached that the miner cannot name, with how often it was seen. */
const wanted = new Map();
for (const f of readdirSync(IN).filter((x) => x.startsWith('crawl-') && x.endsWith('.json'))) {
  for (const n of JSON.parse(readFileSync(join(IN, f), 'utf8')).nodes ?? []) {
    if (n.status !== 'reached') continue;
    for (const d of n.dialogs ?? []) {
      const cap = normalise(typeof d === 'string' ? d : d?.caption);
      if (cap && !known.has(cap)) wanted.set(cap, (wanted.get(cap) ?? 0) + 1);
    }
  }
}

/**
 * The literal to search for. A runtime-built caption keeps only its fixed prefix — `Save '…' as`
 * searches for `Save '`, which is what appears in the source either side of the concatenation.
 */
function needle(caption) {
  const cut = caption.indexOf("'…'");
  return (cut > 0 ? caption.slice(0, cut) : caption).trim();
}

const resolved = [];
const ambiguous = [];
const unfound = [];
for (const [caption, count] of [...wanted].sort((a, b) => b[1] - a[1])) {
  const lit = needle(caption);
  if (lit.length < 4) { unfound.push({ caption, count, why: 'too short to search for' }); continue; }
  let hits = [];
  try {
    // Presenters only: a caption in a ViewImpl or a plugin is not the thing being shown.
    //
    // The WHOLE quoted string, not a substring — `"New Folder"` must not match
    // `.text("Add New Folder")`, which is a menu item in a presenter that has nothing to do with the
    // dialog. That was a real false positive from the first version, and it is the failure mode this
    // whole tool exists to avoid.
    const out = execSync(
      `grep -rn ${JSON.stringify(`"${lit}"`)} --include='*Presenter.java' ${JSON.stringify(STROOM)} || true`,
      { encoding: 'utf8', maxBuffer: 1 << 26 });
    hits = out.split('\n').filter(Boolean)
      // A caption is shown, not labelled: `.text(` is a menu item, `.title(`/`.tooltip(` a hover.
      .filter((line) => !/\.(text|title|tooltip)\s*\(/.test(line))
      .map((line) => line.split(':')[0].split('/').pop().replace(/\.java$/, ''));
  } catch { /* grep found nothing */ }
  const uniq = [...new Set(hits)];
  if (uniq.length === 1) resolved.push({ caption, presenter: uniq[0], count, via: 'source-literal' });
  else if (uniq.length > 1) ambiguous.push({ caption, count, candidates: uniq.slice(0, 6) });
  else unfound.push({ caption, count, why: 'no presenter contains this literal' });
}

writeFileSync(OUT, JSON.stringify({ resolved, ambiguous, unfound }, null, 1));
console.log(`captions the miner could not name: ${wanted.size}`);
console.log(`  resolved to one presenter : ${resolved.length}  (${new Set(resolved.map((r) => r.presenter)).size} distinct)`);
console.log(`  ambiguous, left unnamed   : ${ambiguous.length}`);
console.log(`  not found in any presenter: ${unfound.length}`);
console.log(`written: ${OUT}`);
