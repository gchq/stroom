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
// Seed the missing document types by DRIVING THE UI.
//
//   URL=http://localhost:8080 USER=admin PASS=… node stroom-gwt/stroom-gwt-suite/seed.mjs
//   ... ONLY="Documentation,View" node stroom-gwt/stroom-gwt-suite/seed.mjs      # a subset
//   ... DRYRUN=1 node stroom-gwt/stroom-gwt-suite/seed.mjs                        # report, create nothing
//
// The corpus can only record documents that EXIST. This instance held 16 of the 26 creatable
// document types, so ten editors — a third of the document surface — could not be recorded at all,
// not because the harness could not reach them but because there was nothing to open.
// `probe-doctypes.mjs` derives that list live rather than trusting this comment.
//
// All ten are created here. Four of them — View, Analytic Rule, Report and Data Generator — used to
// answer 403 (`stroom-gwt/ISSUES.md` #31) and had to be imported instead; that is fixed as of
// 2026-08-16, so `seed-import.mjs` is only needed against a server predating the fix. A create that
// is refused is still reported rather than silently passing, because the failure mode was an Alert
// behind the New dialog that read as success.
//
// THIS SCRIPT MUTATES THE INSTANCE. It is the one script in the suite that runs WITHOUT
// `attachReadOnlyGuard`, deliberately and visibly. It is re-runnable: every document is created only
// if a document of that name is not already in the seed folder, so an interrupted run resumes.
//
// Creating documents changes the explorer tree, which invalidates every baseline recorded before it
// — this suite's and `compare/`'s. A full re-record follows a successful seed.
import { mkdir, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { clickMenuItem, closeDialogs, dismissMenus, rightClickByText } from './compare/lib/structure.mjs';
import { settleExplorer } from './compare/lib/settle.mjs';
import { FILLS, saveDocument } from './lib/fills.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const URL = env('URL', 'http://localhost:8080');
const FOLDER = env('FOLDER', 'Seed Content');
const DRYRUN = env('DRYRUN', '') === '1';
const ONLY = env('ONLY') ? new Set(env('ONLY').split(',').map((s) => s.trim())) : null;

/**
 * What to create, and where the New menu keeps it.
 *
 * The category matters: Stroom groups the New menu under Data Processing / Transformation / Search /
 * Indexing / Configuration, so a type is two clicks deep and the category cannot be guessed from the
 * type name (`Query` is under Search, `Plan B` under Indexing).
 *
 * Order is deliberate — Elastic Cluster before Elastic Index, because the index editor wants a
 * cluster to point at.
 */
const SEEDS = [
  ['Configuration', 'Documentation', 'Seed Documentation'],
  ['Configuration', 'Elastic Cluster', 'Seed Elastic Cluster'],
  ['Configuration', 'OpenAI Model', 'Seed OpenAI Model'],
  ['Configuration', 'S3 Configuration', 'Seed S3 Configuration'],
  ['Indexing', 'Elastic Index', 'Seed Elastic Index'],
  ['Indexing', 'Pathways', 'Seed Pathways'],
  ['Indexing', 'View', 'Seed View'],
  ['Search', 'Analytic Rule', 'Seed Analytic Rule'],
  ['Search', 'Data Generator', 'Seed Data Generator'],
  ['Search', 'Report', 'Seed Report'],
];

const browser = await chromium.launch({ headless: env('HEADLESS', '1') !== '0' });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
gwt.baseUrl = URL;
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());
await closeDialogs(page);
await mkdir(`${SUITE}/out/seed`, { recursive: true });

/** Reload the explorer and expand it, so a freshly created document is visible to the next step. */
async function freshTree() {
  await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await gwt.waitForApp(page);
  await closeDialogs(page);
  await dismissMenus(page).catch(() => {});
  await settleExplorer(page, { label: 'reload' }).catch(() => {});
  await page.locator('button[title="Expand All"]').first().click({ timeout: 10_000 }).catch(() => {});
  await settleExplorer(page, { label: 'expand' }).catch(() => {});
}

const readTree = () =>
  page.evaluate(() => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    return [...document.querySelectorAll('.explorerCell')].map((c) => ({
      type: norm(c.querySelector('.explorerCell-icon')?.getAttribute('title')),
      label: norm(c.textContent),
    }));
  });

const dialog = () => page.locator('.resizableDialog-popup, .dialog-popup').last();

/**
 * Drive `New > [category >] type` from a right-click on `parent`, name the document and confirm.
 *
 * The name box is addressed as "the full-width TextBox that is not the parent-picker's quick filter"
 * — the dialog holds three text inputs and only that negation separates them by role.
 */
async function createDoc(parentLabel, category, type, name) {
  await dismissMenus(page).catch(() => {});
  if (!(await rightClickByText(page, '.explorerCell', parentLabel))) throw new Error(`no explorer row "${parentLabel}"`);
  if (!(await clickMenuItem(page, 'New'))) throw new Error('no New item');
  await page.waitForTimeout(600);
  if (category) {
    if (!(await clickMenuItem(page, category))) throw new Error(`no New category "${category}"`);
    await page.waitForTimeout(600);
  }
  if (!(await clickMenuItem(page, type))) throw new Error(`no New type "${type}"`);
  const d = dialog();
  await d.waitFor({ state: 'visible', timeout: 20_000 });
  await page.waitForTimeout(500);
  const nameBox = d.locator('input.gwt-TextBox.w-100:not(.quickFilter-textBox)').first();
  await nameBox.waitFor({ state: 'visible', timeout: 10_000 });
  await nameBox.fill(name);
  await d.getByText('OK', { exact: true }).first().click();
  await d.waitFor({ state: 'hidden', timeout: 30_000 }).catch(() => {});
  await page.waitForTimeout(1500);

  // A failed create leaves the New dialog open behind an Alert, and the first version of this script
  // read that as success: four document types reported ✓ and none of them existed. Read the Alert
  // and fail on it — the server's own message is far more useful than "it did not appear in the
  // tree" ten minutes later. See stroom-gwt/ISSUES.md #31.
  const alert = await page.evaluate(() => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const p = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].find(
      (x) => norm(x.querySelector('.dialog-titleText')?.textContent) === 'Alert',
    );
    return p ? norm(p.querySelector('.dialog-content')?.textContent).slice(0, 300) : null;
  });
  if (alert) throw new Error(`create rejected: ${alert.replace(/(Show|Hide) Detail.*/, '').trim()}`);
}

const saveDoc = () => saveDocument(page);

// ── The seed folder ──────────────────────────────────────────────────────────
await freshTree();
let tree = await readTree();
const haveFolder = tree.some((t) => t.type === 'Folder' && t.label === FOLDER);
if (!haveFolder) {
  if (DRYRUN) console.log(`would create folder "${FOLDER}"`);
  else {
    console.log(`creating folder "${FOLDER}"…`);
    await createDoc('System', null, 'Folder', FOLDER);
    await freshTree();
    tree = await readTree();
  }
}

const present = new Set(tree.map((t) => `${t.type}|${t.label}`));
const results = [];
for (const [category, type, name] of SEEDS) {
  if (ONLY && !ONLY.has(type)) continue;
  if (present.has(`${type}|${name}`)) {
    console.log(`  = ${type} "${name}" already exists`);
    results.push({ type, name, status: 'present' });
    continue;
  }
  if (DRYRUN) {
    console.log(`  + would create ${type} "${name}" under ${FOLDER} (New > ${category})`);
    results.push({ type, name, status: 'dryrun' });
    continue;
  }
  try {
    await freshTree();
    await createDoc(FOLDER, category, type, name);
    const fill = FILLS[type];
    const filled = fill ? await fill(page, { name, saveDoc }) : null;
    await saveDoc();
    await page.screenshot({ path: `${SUITE}/out/seed/${type.replace(/\W+/g, '-').toLowerCase()}.png` }).catch(() => {});
    console.log(`  ✓ ${type} "${name}"${filled ? ` — ${filled}` : ' (created, not filled)'}`);
    results.push({ type, name, status: 'created', filled });
  } catch (e) {
    console.log(`  ✗ ${type} "${name}" — ${String(e?.message ?? e).slice(0, 140)}`);
    await page.screenshot({ path: `${SUITE}/out/seed/FAIL-${type.replace(/\W+/g, '-').toLowerCase()}.png` }).catch(() => {});
    results.push({ type, name, status: 'failed', why: String(e?.message ?? e).slice(0, 200) });
  }
}

await writeFile(`${SUITE}/out/seed/results.json`, JSON.stringify({ url: URL, folder: FOLDER, results }, null, 1));
await browser.close();
const ok = results.filter((r) => r.status === 'created').length;
console.log(`\nseeded ${ok}/${results.length}; ${results.filter((r) => r.status === 'failed').length} failed`);
console.log('the explorer tree has changed — re-record the corpus (npm run gwt:record) before running the suite');
