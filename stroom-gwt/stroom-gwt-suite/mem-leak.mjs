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
 * Is the 5.1 GB renderer a GWT LEAK, or the cost of the walker's one-session design?
 *
 * docs7c's renderer was OOM-killed at `anon-rss: 5,120,996 kB` in the Pipeline editor after 575
 * nodes in one 4.5-hour session. Those are two different claims and only a measurement separates
 * them: a leak keeps what it should have dropped, whereas a long session that merely ACCUMULATES
 * live state would hold flat per cycle and only grow with what is still open.
 *
 * Method: open a document, optionally visit its sub-tabs, close the tab, force GC, sample. Repeat.
 * Closing a tab should return the renderer to where it started. Whatever survives a forced GC after
 * the tab is gone is RETAINED, and retention that grows per cycle is a leak.
 *
 * GC is not optional here: without `HeapProfiler.collectGarbage` ordinary uncollected garbage reads
 * as a leak, which would indict GWT for doing nothing wrong.
 *
 * Controls, both of which this reports:
 *   - an IDLE round (no document) separates the app's own background growth from the open/close
 *   - DOCTYPE=Dictionary vs Pipeline separates a Pipeline-specific leak from any tab open/close
 *
 *   DOCTYPE=Pipeline ROUNDS=8 TABS=1 node stroom-gwt/stroom-gwt-suite/mem-leak.mjs
 */
import { execSync } from 'node:child_process';
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { attachReadOnlyGuard } from './compare/lib/readonly-guard.mjs';
import { settleExplorer, waitUntil } from './compare/lib/settle.mjs';
import { clickMenuItem, dismissMenus, readDialogs, rightClickByText } from './compare/lib/structure.mjs';
import { grown, heapCounts, javaNames, retainingPaths } from './lib/heap-growth.mjs';

const env = (k, d) => process.env[k] ?? d;
const URL = env('URL', 'http://localhost:8080');
const DOCTYPE = env('DOCTYPE', 'Pipeline');
const ROUNDS = Number(env('ROUNDS', '8'));
const TABS = env('TABS', '1') !== '0';
const IDLE = Number(env('IDLE', '2'));
/**
 * What to cycle. A document open/close is only a few dozen of a shard's nodes; a DIALOG open/close
 * and a MENU open/dismiss are what the walk does hundreds of times each, so they dominate any
 * per-session total. 5.1 GB needs the dominant term, not the worst single one.
 */
const MODE = env('MODE', 'doc');
/**
 * CLASSES=1: also take a heap snapshot after round 2 and after the last round, and list the Java
 * classes whose instances grew in between, i.e. what each cycle leaves behind.
 */
const CLASSES = env('CLASSES', '0') === '1';
let classesBefore = null;
const MENU_GROUP = env('MENU_GROUP', 'Navigation');
const MENU_LEAF = env('MENU_LEAF', 'Find');

const browser = await chromium.launch({ headless: env('HEADLESS', '1') !== '0' });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
attachReadOnlyGuard(page, { enabled: true });
gwt.baseUrl = URL;
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());

const cdp = await page.context().newCDPSession(page);
await cdp.send('HeapProfiler.enable');
await cdp.send('Performance.enable').catch(() => {});

/**
 * The page's renderer is the biggest chrome process descended from THIS node process — the one the
 * kernel killed in docs7c. `browser.process()` is not available on this Playwright build, so the
 * subtree is walked from our own pid instead, which also keeps another session's browser out.
 */
const rendererKb = () => {
  try {
    const ps = execSync('ps -eo pid,ppid,rss,comm --no-headers').toString().trim().split('\n')
      .map((l) => l.trim().split(/\s+/)).map(([pid, ppid, rss, comm]) => ({ pid: +pid, ppid: +ppid, rss: +rss, comm }));
    const kin = new Set([process.pid]);
    for (let i = 0; i < 6; i += 1) for (const p of ps) if (kin.has(p.ppid)) kin.add(p.pid);
    const mine = ps.filter((p) => kin.has(p.pid) && /chrome|chromium/i.test(p.comm));
    return Math.max(0, ...mine.map((p) => p.rss));
  } catch { return 0; }
};

async function sample(label) {
  // Collect twice: one pass can leave a just-dropped graph for the next.
  await cdp.send('HeapProfiler.collectGarbage');
  await page.waitForTimeout(300);
  await cdp.send('HeapProfiler.collectGarbage');
  await page.waitForTimeout(500);
  const dom = await cdp.send('Memory.getDOMCounters');
  const { metrics } = await cdp.send('Performance.getMetrics').catch(() => ({ metrics: [] }));
  const m = (n) => metrics.find((x) => x.name === n)?.value ?? 0;
  return { label, nodes: dom.nodes, listeners: dom.jsEventListeners, documents: dom.documents,
    heapMb: +(m('JSHeapUsedSize') / 1048576).toFixed(1), rssMb: +(rendererKb() / 1024).toFixed(0) };
}

const rows = [];
const show = (r) => {
  rows.push(r);
  const f = rows[0];
  const d = (k) => (r === f ? '' : `${r[k] - f[k] >= 0 ? '+' : ''}${+(r[k] - f[k]).toFixed(1)}`);
  console.log(`  ${String(r.label).padEnd(14)} nodes ${String(r.nodes).padStart(7)} ${d('nodes').padStart(8)}`
    + ` · listeners ${String(r.listeners).padStart(6)} ${d('listeners').padStart(7)}`
    + ` · docs ${String(r.documents).padStart(3)}`
    + ` · heap ${String(r.heapMb).padStart(7)}MB ${d('heapMb').padStart(8)}`
    + ` · rss ${String(r.rssMb).padStart(5)}MB ${d('rssMb').padStart(7)}`);
};

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

/** Open the main menu and dismiss it: the walk's commonest gesture after a click. */
async function menuCycle() {
  await gwt.openMainMenu(page).catch(() => {});
  await page.waitForTimeout(250);
  await dismissMenus(page).catch(() => {});
  await page.waitForTimeout(250);
  return true;
}

/** Open a dialog off the main menu and close it again. */
async function dialogCycle() {
  await gwt.openMainMenu(page).catch(() => {});
  await page.waitForTimeout(200);
  if (MENU_GROUP !== 'Main Menu') { await clickMenuItem(page, MENU_GROUP).catch(() => {}); await page.waitForTimeout(200); }
  if (!(await clickMenuItem(page, MENU_LEAF).catch(() => false))) { await dismissMenus(page).catch(() => {}); return false; }
  await waitUntil(page, async () => ((await readDialogs(page)).length ? true : null),
    { budget: 6_000, label: 'dialog-open', kind: 'dialog' }).catch(() => {});
  const up = (await readDialogs(page)).length;
  if (!up) return false;
  await page.keyboard.press('Escape');
  await page.waitForTimeout(400);
  if ((await readDialogs(page)).length) {
    await page.locator('.dialog-popup button, .resizableDialog-popup button')
      .filter({ hasText: /^(Cancel|Close)/ }).first().click({ timeout: 3_000 }).catch(() => {});
    await page.waitForTimeout(400);
  }
  return !(await readDialogs(page)).length;
}

console.log(`\nprobe-leak · mode=${MODE}`
  + `${MODE === 'doc' ? ` · ${DOCTYPE} · sub-tabs ${TABS ? 'on' : 'off'}` : ''}`
  + `${MODE === 'dialog' ? ` · ${MENU_GROUP} > ${MENU_LEAF}` : ''}`
  + ` · ${ROUNDS} rounds\n`);

show(await sample('baseline'));

// Control: the app sitting still. Growth here is NOT the open/close cycle's fault.
for (let i = 1; i <= IDLE; i += 1) {
  await page.waitForTimeout(3_000);
  show(await sample(`idle ${i}`));
}

let name = null;
if (MODE === 'doc') {
  name = await findDoc(DOCTYPE);
  if (!name) { console.error(`no ${DOCTYPE} document in the explorer`); await browser.close(); process.exit(2); }
  console.log(`\n  document: "${name}"\n`);
}

for (let i = 1; i <= ROUNDS; i += 1) {
  if (MODE !== 'doc') {
    const ok = MODE === 'menu' ? await menuCycle() : await dialogCycle();
    show(await sample(`round ${i}`));
    if (!ok) {
      console.error(`\n  !! round ${i}: the ${MODE} cycle did not complete — a frozen reading looks`);
      console.error('     like no leak, so this aborts rather than reporting one.');
      await browser.close(); process.exit(2);
    }
    continue;
  }
  await gwt.openDocByName(page, name, DOCTYPE).catch((e) => console.error(`   open failed: ${e.message}`));
  await page.waitForTimeout(1_500);
  if (TABS) {
    const tabs = await page.evaluate(() => [...document.querySelectorAll('.editorTab, [class*=editorTab]')]
      .filter((e) => e.offsetWidth > 0).map((e) => e.textContent.replace(/\s+/g, ' ').trim()).filter(Boolean));
    for (const t of tabs.slice(0, 8)) {
      await page.locator(`.editorTab, [class*=editorTab]`).filter({ hasText: t }).first()
        .click({ timeout: 4_000 }).catch(() => {});
      await page.waitForTimeout(350);
    }
  }
  const openNow = await contentTabs();
  const closed = await closeTab(name);
  const left = (await contentTabs()).filter((t) => t && !/Welcome/.test(t));
  show(await sample(`round ${i}`));
  if (CLASSES && i === 2) classesBefore = await heapCounts(cdp);
  if (!closed || left.length) {
    console.error(`\n  !! round ${i}: the tab did NOT close (${left.length} open: ${left.join(' | ')}, was ${openNow.length}).`);
    console.error('     Every later round would re-measure the same open document, and a frozen');
    console.error('     reading looks like no leak. Aborting rather than reporting that.');
    await browser.close(); process.exit(2);
  }
}

// ── Verdict ─────────────────────────────────────────────────────────────────
const first = rows.find((r) => r.label === 'round 1');
const last = rows[rows.length - 1];
const n = ROUNDS - 1;
console.log(`\n=== per ${MODE} cycle, after round 1 (round 1 carries the first-open cost) ===`);
if (first && last && n > 0) {
  const per = (k) => +((last[k] - first[k]) / n).toFixed(1);
  console.log(`  nodes ${per('nodes') >= 0 ? '+' : ''}${per('nodes')}/cycle · listeners ${per('listeners') >= 0 ? '+' : ''}${per('listeners')}/cycle`
    + ` · heap ${per('heapMb') >= 0 ? '+' : ''}${per('heapMb')}MB/cycle · rss ${per('rssMb') >= 0 ? '+' : ''}${per('rssMb')}MB/cycle`);
  const leaky = per('nodes') > 50 || per('listeners') > 50 || per('heapMb') > 2;
  console.log(`\n  ${leaky ? 'RETAINED growth per cycle survives a forced GC with the tab CLOSED — a leak' : 'no material retention per cycle: the tab gives back what it took'}`);
  if (per('rssMb') > 5 && !leaky) console.log('  (rss grows while the heap does not — allocator//GPU-side, not retained JS)');
  const toFive = per('rssMb') > 0.5 ? Math.round((5120 - last.rssMb) / per('rssMb')) : null;
  if (toFive) console.log(`  at ${per('rssMb')}MB/cycle, 5.1 GB is ~${toFive} more cycles (docs7c walked 575 nodes)`);
}
// RETAIN=<java class>: what keeps that class's instances alive once every tab is closed
if (env('RETAIN', '')) {
  console.log(`\n=== what keeps ${env('RETAIN')} ===`);
  for (const { count, path } of (await retainingPaths(cdp, page, env('RETAIN'), Number(env('DEPTH', '24')))).slice(0, 4)) {
    console.log(`  ${count} instance(s):\n      ${path.split('\n').join('\n      ')}`);
  }
}
if (CLASSES && classesBefore && ROUNDS > 2) {
  const cycles = ROUNDS - 2;
  const growth = grown(classesBefore, await heapCounts(cdp), cycles);
  const names = await javaNames(page, growth.map(([k]) => k));
  console.log(`\n=== objects kept per cycle (rounds 3-${ROUNDS}) ===`);
  for (const [k, d] of growth.slice(0, Number(env('TOP', '40')))) {
    console.log(`  ${(d / cycles).toFixed(1).padStart(8)}  ${names[k] || k}`);
  }
}
console.log('');
await browser.close();
