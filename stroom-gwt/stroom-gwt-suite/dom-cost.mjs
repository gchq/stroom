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
/**
 * What an OPEN document costs: for each document type, heap-snapshots before opening it and
 * once it is open (with its sub-tabs visited), and reports the DOM nodes it added (in the page, and
 * in all, i.e. including widgets built but not shown) and the classes it made the most of, so that
 * heavy widgets built eagerly (e.g. a date picker per expression term) stand out.
 *
 *   STROOM_USER=admin STROOM_PASS=… DOCTYPES="Dashboard,Pipeline" node stroom-gwt/stroom-gwt-suite/dom-cost.mjs
 */
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { attachReadOnlyGuard } from './compare/lib/readonly-guard.mjs';
import { settleExplorer, waitUntil } from './compare/lib/settle.mjs';
import { clickMenuItem, dismissMenus, readDialogs, rightClickByText } from './compare/lib/structure.mjs';
import { grown, heapCounts, javaNames } from './lib/heap-growth.mjs';

const env = (k, d) => process.env[k] ?? d;
const URL = env('URL', 'http://localhost:8080');
const DOCTYPES = env('DOCTYPES', 'Dashboard,Pipeline,Query,Analytic Rule,View,Feed,XSLT').split(',').map((t) => t.trim());
const TOP = Number(env('TOP', '15'));

const browser = await chromium.launch({ headless: env('HEADLESS', '1') !== '0' });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
attachReadOnlyGuard(page, { enabled: true });
gwt.baseUrl = URL;
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());
const cdp = await page.context().newCDPSession(page);
await cdp.send('HeapProfiler.enable');

async function expandExplorer() {
  await page.locator('button[title="Expand All"]').first().click({ timeout: 10_000 }).catch(() => {});
  await settleExplorer(page, { label: 'expand', budget: 5_000 }).catch(() => {});
}

/** The walker's own docType lookup: a document of this type already in the explorer. */
async function findDoc(type) {
  await expandExplorer();
  return page.evaluate((t) => {
    const norm = (s) => String(s ?? '').replace(/\s+/g, ' ').trim();
    const squash = (s) => s.toLowerCase().replace(/[^a-z0-9]/g, '');
    const rows = [...document.querySelectorAll('.explorerCell')].filter((e) => e.offsetWidth > 0)
      .map((e) => ({ title: norm(e.querySelector('.explorerCell-icon')?.getAttribute('title')), label: norm(e.textContent) }))
      .filter((r) => r.title);
    for (const test of [(x) => x === t, (x) => squash(x) === squash(t), (x) => squash(x).startsWith(squash(t))]) {
      const hit = rows.find((r) => test(r.title));
      if (hit) return hit.label;
    }
    return null;
  }, type);
}

const contentTabs = () => page.evaluate(() => [...document.querySelectorAll('.curveTab')]
  .filter((e) => e.offsetWidth > 0).map((e) => e.textContent.replace(/\s+/g, ' ').trim()));

/**
 * The walker's own close: right-click the tab, then `Close`. A synthetic `click` on the tab's own
 * close glyph does nothing -- the first version of this probe used one, every round after the first
 * measured the SAME still-open document, and the frozen numbers read as a clean bill of health.
 */
async function closeTab(label) {
  const before = (await contentTabs()).length;
  if (!(await rightClickByText(page, '.curveTab', label).catch(() => false))) return false;
  if (!(await clickMenuItem(page, 'Close').catch(() => false))) { await dismissMenus(page).catch(() => {}); return false; }
  for (let round = 0; round < 3; round += 1) {
    const r = await waitUntil(page, async () => {
      if ((await contentTabs()).length < before) return 'closed';
      if ((await readDialogs(page)).some((d) => /confirm/i.test(d.caption ?? ''))) return 'confirm';
      return null;
    }, { budget: 5_000, label: 'tab-close', kind: 'restore' }).catch(() => ({ value: null }));
    if (r.value === 'closed') return true;
    if (r.value !== 'confirm') return false;
    // A dirty document asks before it goes. Nothing here edited anything, so this is GWT's own.
    await page.locator('.dialog-popup button, .resizableDialog-popup button').filter({ hasText: /^(OK|Yes)/ })
      .first().click({ timeout: 3_000 }).catch(() => {});
    await page.waitForTimeout(500);
  }
  return (await contentTabs()).length < before;
}


const attached = () => page.evaluate(() => {
  let n = 0;
  const w = document.createTreeWalker(document, NodeFilter.SHOW_ALL);
  while (w.nextNode()) n += 1;
  return n;
});
const domNodes = async () => (await cdp.send('Memory.getDOMCounters')).nodes;
const heapMb = async () => {
  const m = Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map((x) => [x.name, x.value]));
  return m.JSHeapUsedSize / 1048576;
};
await cdp.send('Performance.enable');

for (const type of DOCTYPES) {
  const name = await findDoc(type);
  if (!name) { console.log(`\n${type}: no document`); continue; }
  const before = await heapCounts(cdp);
  const b = { attached: await attached(), nodes: await domNodes(), heap: await heapMb() };
  await gwt.openDocByName(page, name, type).catch((e) => console.error(`   open failed: ${e.message}`));
  await page.waitForTimeout(2_000);
  const tabs = await page.evaluate(() => [...document.querySelectorAll('.editorTab, [class*=editorTab]')]
    .filter((e) => e.offsetWidth > 0).map((e) => e.textContent.replace(/\s+/g, ' ').trim()).filter(Boolean));
  for (const t of tabs.slice(0, 10)) {
    await page.locator('.editorTab, [class*=editorTab]').filter({ hasText: t }).first().click({ timeout: 4_000 }).catch(() => {});
    await page.waitForTimeout(400);
  }
  const after = await heapCounts(cdp);
  const a = { attached: await attached(), nodes: await domNodes(), heap: await heapMb() };
  const added = grown(before, after, 1);
  const names = await javaNames(page, added.map(([k]) => k));
  const widgets = added.map(([k, d]) => [names[k] || k, d])
    .filter(([n]) => /^(stroom|com\.google\.gwt\.user|edu)\./.test(n));
  console.log(`\n${type} ("${name}")`);
  console.log(`  DOM nodes +${a.nodes - b.nodes} in all, +${a.attached - b.attached} in the page · heap +${(a.heap - b.heap).toFixed(1)} MB`);
  for (const [n, d] of widgets.slice(0, TOP)) console.log(`  ${String(d).padStart(7)}  ${n}`);
  await closeTab(name);
  await page.waitForTimeout(1_000);
}
await browser.close();
