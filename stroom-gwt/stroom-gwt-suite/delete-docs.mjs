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
// Delete documents by name, through the UI.
//
//   URL=… USER=admin PASS=… NAMES="Probe View,Probe Report" node stroom-gwt/stroom-gwt-suite/delete-docs.mjs
//   ... PREFIX="Probe " …                                   # everything starting with this
//
// Exists to clean up after the probes, which deliberately leave what they created so a failure can be
// inspected. MUTATES: it deletes documents. It will not delete anything whose name it was not given —
// there is no "delete everything under" mode, on purpose.
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { clickMenuItem, closeDialogs, dismissMenus, rightClickByText } from './compare/lib/structure.mjs';
import { settleExplorer } from './compare/lib/settle.mjs';

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const URL = env('URL', 'http://localhost:8080');
const NAMES = env('NAMES') ? env('NAMES').split(',').map((s) => s.trim()).filter(Boolean) : null;
const PREFIX = env('PREFIX');
if (!NAMES && !PREFIX) {
  console.error('Set NAMES="A,B" or PREFIX="Probe " — this script refuses to guess what to delete.');
  process.exit(2);
}

const browser = await chromium.launch({ headless: env('HEADLESS', '1') !== '0' });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
gwt.baseUrl = URL;
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());
await closeDialogs(page);

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

await freshTree();
const tree = await readTree();
const targets = [
  ...new Set(
    tree
      .map((t) => t.label)
      .filter((l) => (NAMES ? NAMES.includes(l) : l.startsWith(PREFIX))),
  ),
];
console.log(`${targets.length} to delete: ${targets.join(', ') || '(none)'}`);

let done = 0;
for (const name of targets) {
  try {
    await freshTree();
    await dismissMenus(page).catch(() => {});
    if (!(await rightClickByText(page, '.explorerCell', name))) throw new Error('row not found');
    if (!(await clickMenuItem(page, 'Delete'))) throw new Error('no Delete item');
    await page.waitForTimeout(800);
    // A confirm dialog: OK to proceed.
    const d = page.locator('.resizableDialog-popup, .dialog-popup').last();
    if (await d.isVisible().catch(() => false)) {
      await d.getByText('OK', { exact: true }).first().click().catch(() => {});
      await page.waitForTimeout(1500);
    }
    console.log(`  ✓ ${name}`);
    done += 1;
  } catch (e) {
    console.log(`  ✗ ${name} — ${String(e?.message ?? e).slice(0, 100)}`);
  }
}

await freshTree();
const left = (await readTree()).map((t) => t.label).filter((l) => targets.includes(l));
console.log(`\ndeleted ${done}/${targets.length}${left.length ? `; STILL PRESENT: ${left.join(', ')}` : '; none left'}`);
await browser.close();
