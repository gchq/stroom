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
// Record a corpus from a LIVE Stroom, by driving the GWT UI through its whole main menu.
//
//   URL=http://localhost:8080 USER=admin PASS=a node stroom-gwt/stroom-gwt-suite/record.mjs
//   ... GROUPS="Monitoring,Security" node stroom-gwt/stroom-gwt-suite/record.mjs     # refresh part of it
//
// Writes:
//   corpus/<name>/api/   the REST exchanges
//   corpus/<name>/app/   the compiled UI, so the suite can run with no Stroom at all
//   corpus/<name>/baseline/<slug>.png   what each screen looked like
//   corpus/<name>/coverage.json         what was visited, and what it cost
//
// The menu is walked LIVE rather than from a hard-coded list, so a screen added to Stroom appears in
// the next recording instead of being silently uncovered. Leaves that end the session or mutate
// state are skipped by name — everything else runs behind the read-only guard.
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { attachReadOnlyGuard } from './compare/lib/readonly-guard.mjs';
import { clickMenuItem, closeDialogs, dismissMenus, readVisibleMenuItems } from './compare/lib/structure.mjs';
import { settleExplorer } from './compare/lib/settle.mjs';
import { attachRecorder, freezeClock } from './lib/corpus.mjs';
import { settleAndShoot } from './lib/shoot.mjs';
import { ACTIONS } from './lib/actions.mjs';
import { clickEditorTab, readEditorTabs, tabSlug } from './lib/tabs.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const URL = env('URL', 'http://localhost:8080');
const NAME = env('NAME', 'default');
const DIR = join(`${SUITE}/corpus`, NAME);
const VW = Number(env('VW', '1600'));
const VH = Number(env('VH', '1000'));
/** Pinned `now`, so relative-time cells ("5 days ago") are reproducible. */
export const CLOCK_MS = Number(env('CLOCK_MS', String(Date.UTC(2026, 7, 15, 12, 0, 0))));

/**
 * Leaves that must not be driven automatically.
 *
 * Sign Out ends the session the rest of the run depends on; the tab-session and favourites items
 * write user state; Change Password would invalidate the credentials. Everything else is safe behind
 * `attachReadOnlyGuard`, which fails the run rather than letting a mutation through.
 */
const SKIP = new Set([
  'Sign Out',
  'Sign Out Other Sessions',
  'Change Password',
  'Save Tab Session',
  'Delete Tab Session',
  'Add Current Item to Favourites',
]);

const slug = (g, l) => `${g}-${l}`.replace(/[^a-z0-9]+/gi, '-').toLowerCase();

/**
 * Documents to open in addition to one-per-type.
 *
 * The per-type sweep takes the FIRST document of each type it meets, which is not always the
 * interesting one: for `Lucene Index` that is "Example Dynamic Index", while "Example Index" is the
 * representative index. Both entries carry a type because the explorer holds FOUR things called
 * "Example Index" — a Folder, a Pipeline, an XSL Translation and the index itself — so a name alone
 * opens whichever comes first.
 */
const PINNED_DOCS = [
  ['Lucene Index', 'Example Index'],
  ['Dashboard', 'Test Dashboard'],
];

/**
 * Screens whose interesting state needs an action first.
 *
 * An unsearched dashboard is an empty frame; the state worth testing is after the search returns —
 * a 108-row table, a rendered doughnut, ten series on a line chart and three visualisation iframes
 * that had to load their scripts and be handed data. Almost every defect in that pipeline is
 * invisible on the screen as it first opens.
 */
const INTERACTIONS = [
  {
    type: 'Dashboard',
    label: 'Test Dashboard',
    action: 'runDashboardSearch',
    suffix: 'searched',
    // Captured and useful, but NOT yet reproducible through the simulation, so it is reported
    // separately rather than failing the suite.
    //
    // The replay gets the structure right — same panels, same columns, pager reading "1 to 100 of
    // 108" and "1 to 10 of 10" — and leaves the table CELLS blank.
    //
    // An earlier note here blamed a client-minted query key. That was WRONG and is recorded because
    // it was asserted: the request carries only stable document UUIDs (dashboard, data source,
    // extraction pipeline, visualisations) and the query key is minted by the SERVER and appears
    // only in the response, so there is nothing to map. The `result-store/v1/find` polls that carry
    // the values now match EXACTLY (46 of them, the whole recorded sequence) and the cells are still
    // empty. Cause not yet identified.
    replayLimitation: 'replay reproduces structure, row counts and pager text but not cell VALUES; cause not yet identified',
  },
];

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: VW, height: VH } });
attachReadOnlyGuard(page, { allowMutations: false });
await freezeClock(page, CLOCK_MS);
const recorder = await attachRecorder(page, DIR);

gwt.baseUrl = URL;
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());
await mkdir(join(DIR, 'baseline'), { recursive: true });

/**
 * Enumerate the menu as it actually is, rather than trusting a list in this file — a screen added to
 * Stroom then shows up in the next recording instead of being silently uncovered.
 *
 * Uses the harness's `readVisibleMenuItems` rather than a raw selector: the menu is rendered through
 * several widget classes and a hand-written `.menuItem` query returns nothing.
 */
const labels = async () =>
  (await readVisibleMenuItems(page)).map((i) => i.label ?? i.text ?? String(i)).filter(Boolean);

async function readMenu() {
  await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await gwt.waitForApp(page);
  await closeDialogs(page);
  await gwt.openMainMenu(page);
  await page.waitForTimeout(600);
  const groups = await labels();
  await dismissMenus(page).catch(() => {});
  const menu = {};
  for (const g of groups) {
    await dismissMenus(page).catch(() => {});
    await gwt.openMainMenu(page);
    await page.waitForTimeout(500);
    if (!(await clickMenuItem(page, g))) continue;
    await page.waitForTimeout(700);
    menu[g] = (await labels()).filter((x) => !groups.includes(x));
    await dismissMenus(page).catch(() => {});
  }
  return menu;
}

console.log('reading the live menu…');
const menu = await readMenu();
const only = env('GROUPS') ? new Set(env('GROUPS').split(',').map((s) => s.trim())) : null;
const targets = [];
for (const [g, leaves] of Object.entries(menu)) {
  if (only && !only.has(g)) continue;
  for (const l of leaves) targets.push([g, l]);
}
console.log(`${Object.keys(menu).length} groups, ${targets.length} leaves`);

const coverage = [];
for (const [group, leaf] of targets) {
  const name = slug(group, leaf);
  if (SKIP.has(leaf)) {
    coverage.push({ kind: 'menu', group, leaf, slug: name, status: 'skipped', why: 'ends the session or writes state' });
    console.log(`  - ${name} (skipped)`);
    continue;
  }
  try {
    await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await gwt.waitForApp(page);
    await closeDialogs(page);
    await dismissMenus(page).catch(() => {});
    await gwt.openMainMenu(page);
    await page.waitForTimeout(500);
    if (!(await clickMenuItem(page, group))) throw new Error('group not found');
    await page.waitForTimeout(700);
    if (!(await clickMenuItem(page, leaf))) throw new Error('leaf not found');
    await settleAndShoot(page, join(DIR, 'baseline', `${name}.png`), {
      viewportHeight: VH,
      domFile: join(DIR, 'dom', `${name}.html.gz`),
    });
    coverage.push({ kind: 'menu', group, leaf, slug: name, status: 'recorded' });
    console.log(`  ✓ ${name}`);
  } catch (e) {
    coverage.push({ kind: 'menu', group, leaf, slug: name, status: 'failed', why: String(e?.message ?? e).slice(0, 120) });
    console.log(`  ✗ ${name} — ${String(e?.message ?? e).slice(0, 80)}`);
  }
}

// ── Documents ────────────────────────────────────────────────────────────────
// One per document TYPE (so every editor is covered without opening thousands of documents), plus
// the pinned ones. The list is discovered from the live explorer, like the menu, so a new document
// type shows up in the next recording rather than being missed.
async function freshTree() {
  await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await gwt.waitForApp(page);
  await closeDialogs(page);
  await dismissMenus(page).catch(() => {});
  await settleExplorer(page, { label: 'reload' }).catch(() => {});
  await page.locator('button[title="Expand All"]').first().click({ timeout: 10_000 }).catch(() => {});
  await settleExplorer(page, { label: 'expand' }).catch(() => {});
}

console.log('reading the explorer…');
await freshTree();
const explorer = await page.evaluate(() => {
  const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
  return [...document.querySelectorAll('.explorerCell')].map((c) => ({
    type: norm(c.querySelector('.explorerCell-icon')?.getAttribute('title')),
    label: norm(c.textContent),
  }));
});
const NOT_A_DOC = new Set(['System', 'Favourites', 'Folder', '']);
const byType = new Map();
for (const it of explorer) {
  if (NOT_A_DOC.has(it.type) || !it.label) continue;
  if (!byType.has(it.type)) byType.set(it.type, it.label);
}
for (const [type, label] of PINNED_DOCS) byType.set(`${type}\u0000pinned`, label);
const docTargets = [];
for (const [k, label] of byType) docTargets.push([k.split('\u0000')[0], label]);
// De-duplicate: a pinned document may also be the first of its type.
const seen = new Set();
const docs = docTargets.filter(([t, l]) => (seen.has(`${t}|${l}`) ? false : seen.add(`${t}|${l}`)));
console.log(`${docs.length} document types`);

for (const [type, label] of docs) {
  const name = `doc-${slug(type, label)}`;
  try {
    await freshTree();
    await gwt.openDocByName(page, label, type);
    // Some editors raise an Alert as they open — Pathways reports `Remote node '' has no URL set`
    // on an instance with no pathways node. It is modal, so it would sit over every sub-tab shot.
    await closeDialogs(page).catch(() => {});
    await settleAndShoot(page, join(DIR, 'baseline', `${name}.png`), {
      viewportHeight: VH,
      domFile: join(DIR, 'dom', `${name}.html.gz`),
    });
    coverage.push({ kind: 'doc', type, label, slug: name, status: 'recorded' });
    console.log(`  ✓ ${name}`);

    // …and each of its sub-tabs. The editor is photographed as it OPENS, which is one pane of
    // several; without this the Fields grid, the Documentation pane and the Permissions screen of
    // every document type go unrecorded.
    // A document's tabs are not one flat list, and reading them once then clicking through fails
    // on 29 of 220 targets — every one reported as "tab not found". Two different causes, measured:
    //
    //   * a DASHBOARD's component tabs (Params, Query, Table, …) exist only while the tab the
    //     document opened on is showing. Click anything else and they are gone; return to it and
    //     they are back.
    //   * a FEED's or PIPELINE's stream-preview tabs (Info, Data Preview, Meta) are in the strip
    //     from the start but are NOT CLICKABLE — the pane is inert until a stream is selected, so
    //     the click fails Playwright's actionability check rather than the lookup.
    //
    // So: try each tab as it comes; if that fails, restore the home tab AND select a row, then try
    // once more. The home tab is photographed before any row is selected, so its baseline is
    // unchanged. (`Error` and `Context` stay out of reach — they need a stream that HAS an error
    // and a context sub-stream, which is a property of the data, not of the driver.)
    const arrival = await readEditorTabs(page, label);
    const home = arrival[0];
    const restoreContext = async () => {
      await clickEditorTab(page, home, { timeout: 5_000 }).catch(() => {});
      await page.waitForTimeout(1_200);
      const at = await page.evaluate(() => {
        const row = [...document.querySelectorAll('.dataGridWidget tbody tr, [role=row]')]
          .find((e) => e.offsetHeight > 0 && e.textContent.trim());
        if (!row) return null;
        const cell = [...row.querySelectorAll('td, [role=gridcell]')]
          .find((c) => c.offsetWidth > 0 && !c.querySelector('a, .tickBox, button, svg')) ?? row;
        const r = cell.getBoundingClientRect();
        return { x: r.x + Math.min(r.width / 2, 60), y: r.y + r.height / 2 };
      }).catch(() => null);
      if (at) { await page.mouse.click(at.x, at.y); await page.waitForTimeout(2_000); }
    };

    for (const tab of arrival) {
      const tname = tabSlug(name, tab);
      const shoot = async () => {
        await settleAndShoot(page, join(DIR, 'baseline', `${tname}.png`), {
          viewportHeight: VH,
          domFile: join(DIR, 'dom', `${tname}.html.gz`),
        });
        coverage.push({ kind: 'doctab', type, label, tab, slug: tname, status: 'recorded' });
        console.log(`    ✓ ${tname}`);
      };
      try {
        if (!(await clickEditorTab(page, tab, { timeout: 5_000 }))) throw new Error('tab not found');
        await shoot();
      } catch (first) {
        try {
          await restoreContext();
          if (!(await clickEditorTab(page, tab, { timeout: 5_000 }))) throw first;
          await shoot();
        } catch (e) {
          coverage.push({ kind: 'doctab', type, label, tab, slug: tname, status: 'failed', why: String(e?.message ?? e).slice(0, 120) });
          console.log(`    ✗ ${tname} — ${String(e?.message ?? e).slice(0, 70)}`);
        }
      }
    }
  } catch (e) {
    coverage.push({ kind: 'doc', type, label, slug: name, status: 'failed', why: String(e?.message ?? e).slice(0, 120) });
    console.log(`  ✗ ${name} — ${String(e?.message ?? e).slice(0, 80)}`);
  }
}

// ── Interactions ─────────────────────────────────────────────────────────────
// Each runs in a FRESH page. By this point the run has opened forty-odd screens and eighteen
// documents in one session, and the visualisation iframes stop drawing: the same dashboard that
// renders all three charts from a clean page comes back with empty panels at the end of a long one.
// A clean session per interaction is cheap and removes a whole class of "why is this screen
// different" that has nothing to do with the UI.
for (const it of INTERACTIONS) {
  const name = `doc-${slug(it.type, it.label)}-${it.suffix}`;
  const ipage = await browser.newPage({ viewport: { width: VW, height: VH } });
  attachReadOnlyGuard(ipage, { allowMutations: false });
  await freezeClock(ipage, CLOCK_MS);
  const irec = await attachRecorder(ipage, DIR);
  try {
    await ipage.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await gwt.signInFully(ipage, credentials());
    await settleExplorer(ipage, { label: 'reload' }).catch(() => {});
    await ipage.locator('button[title="Expand All"]').first().click({ timeout: 10_000 }).catch(() => {});
    await settleExplorer(ipage, { label: 'expand' }).catch(() => {});
    await gwt.openDocByName(ipage, it.label, it.type);
    await ipage.waitForTimeout(5000);
    const { settled } = await ACTIONS[it.action](ipage);
    await settleAndShoot(ipage, join(DIR, 'baseline', `${name}.png`), {
      wait: 1000,
      viewportHeight: VH,
      domFile: join(DIR, 'dom', `${name}.html.gz`),
    });
    coverage.push({ kind: 'interaction', ...it, slug: name, status: 'recorded', settled });
    console.log(`  ✓ ${name}${settled ? '' : ' (did not settle — captured anyway)'}`);
  } catch (e) {
    coverage.push({ kind: 'interaction', ...it, slug: name, status: 'failed', why: String(e?.message ?? e).slice(0, 120) });
    console.log(`  ✗ ${name} — ${String(e?.message ?? e).slice(0, 80)}`);
  } finally {
    irec.save();
    await ipage.close();
  }
}

const saved = recorder.save();
await writeFile(
  join(DIR, 'coverage.json'),
  JSON.stringify({ url: URL, clockMs: CLOCK_MS, viewport: { width: VW, height: VH }, menu, targets: coverage }, null, 1),
);
await browser.close();

const ok = coverage.filter((c) => c.status === 'recorded').length;
console.log(`\nrecorded ${ok}/${coverage.length} targets (${coverage.filter((c) => c.kind === 'menu' && c.status === 'recorded').length} menu, ${coverage.filter((c) => c.kind === 'doc' && c.status === 'recorded').length} documents)`);
console.log(`corpus: ${saved.api} api exchanges, ${saved.app} app assets -> ${DIR}`);
