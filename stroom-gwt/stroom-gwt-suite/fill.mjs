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
// Put test data into the seeded documents.
//
//   URL=http://localhost:8080 USER=admin PASS=… node stroom-gwt/stroom-gwt-suite/fill.mjs
//   ... ONLY="Elastic Index" node stroom-gwt/stroom-gwt-suite/fill.mjs
//
// `seed.mjs` fills each document as it creates it; this does the same job for documents that
// already exist, which is the case after an interrupted seed, after `seed-import.mjs`, or when a
// fill in `lib/fills.mjs` is changed and needs re-applying. Re-running is harmless — every fill
// sets the same values.
//
// MUTATES: it opens each document, edits it and saves. Like `seed.mjs` it runs without the
// read-only guard, deliberately.
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { closeDialogs, dismissMenus } from './compare/lib/structure.mjs';
import { settleExplorer } from './compare/lib/settle.mjs';
import { FILLS, saveDocument } from './lib/fills.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const URL = env('URL', 'http://localhost:8080');
const ONLY = env('ONLY') ? new Set(env('ONLY').split(',').map((s) => s.trim())) : null;

/** The documents `seed.mjs` creates, and the name it gives each. */
const SEEDED = [
  ['Documentation', 'Seed Documentation'],
  ['Elastic Cluster', 'Seed Elastic Cluster'],
  ['OpenAI Model', 'Seed OpenAI Model'],
  ['S3 Configuration', 'Seed S3 Configuration'],
  ['Elastic Index', 'Seed Elastic Index'],
  ['Pathways', 'Seed Pathways'],
];

const browser = await chromium.launch({ headless: env('HEADLESS', '1') !== '0' });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
gwt.baseUrl = URL;
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());
await closeDialogs(page);

const results = [];
for (const [type, label] of SEEDED) {
  if (ONLY && !ONLY.has(type)) continue;
  const fill = FILLS[type];
  if (!fill) {
    results.push({ type, label, status: 'no fill defined' });
    console.log(`  · ${type} — no fill defined`);
    continue;
  }
  try {
    await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await gwt.waitForApp(page);
    await closeDialogs(page);
    await dismissMenus(page).catch(() => {});
    await settleExplorer(page, { label: 'reload' }).catch(() => {});
    await page.locator('button[title="Expand All"]').first().click({ timeout: 10_000 }).catch(() => {});
    await settleExplorer(page, { label: 'expand' }).catch(() => {});
    await gwt.openDocByName(page, label, type);
    await page.waitForTimeout(3000);
    // Some editors raise an Alert as they open — Pathways reports `Remote node '' has no URL set`
    // on an instance with no pathways node configured. It is modal, so it swallows the first click
    // of the fill and the failure reads as a tab that would not respond.
    await closeDialogs(page).catch(() => {});

    const did = await fill(page, { name: label });
    const saved = await saveDocument(page);
    await page.screenshot({ path: `${SUITE}/out/seed/filled-${type.replace(/\W+/g, '-').toLowerCase()}.png` }).catch(() => {});
    const ok = saved === 'saved' || saved === 'nothing to save';
    results.push({ type, label, status: ok ? 'filled' : 'unsaved', did, saved });
    console.log(`  ${ok ? '✓' : '!'} ${type} — ${did ?? 'nothing to fill'} (${saved})`);
  } catch (e) {
    await page.screenshot({ path: `${SUITE}/out/seed/FAIL-fill-${type.replace(/\W+/g, '-').toLowerCase()}.png` }).catch(() => {});
    results.push({ type, label, status: 'failed', why: String(e?.message ?? e).slice(0, 200) });
    console.log(`  ✗ ${type} — ${String(e?.message ?? e).slice(0, 140)}`);
  }
}

await writeFile(`${SUITE}/out/seed/fill-results.json`, JSON.stringify({ url: URL, results }, null, 1));
await browser.close();
console.log(`\nfilled ${results.filter((r) => r.status === 'filled').length}/${results.length}`);
