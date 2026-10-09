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
// Run the suite: boot the simulated backend, drive the GWT UI through its recorded coverage, and
// diff every screen against the baseline captured when the corpus was made.
//
//   node stroom-gwt/stroom-gwt-suite/run.mjs                         # whole suite
//   ONLY="monitoring-jobs,security-users" node stroom-gwt/stroom-gwt-suite/run.mjs
//
// No Stroom is involved. The UI and all its data come from the corpus, so a difference here is a
// change in the UI itself — which is the point: this is a regression suite for the GWT client that
// does not need a cluster, a database, or a deploy to run.
//
// Exit code is non-zero if any screen differs, is missing a baseline, or fails to open.
import { mkdir, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { execFile } from 'node:child_process';
import { join } from 'node:path';
import { promisify } from 'node:util';
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { clickMenuItem, closeDialogs, dismissMenus } from './compare/lib/structure.mjs';
import { settleExplorer } from './compare/lib/settle.mjs';
import { loadCorpus, makeResolver, freezeClock } from './lib/corpus.mjs';
import { settleAndShoot } from './lib/shoot.mjs';
import { ACTIONS } from './lib/actions.mjs';
import { clickEditorTab } from './lib/tabs.mjs';
/**
 * Put the editor back into the state a nested tab lives in, the way record.mjs does: return to the
 * tab the document opened on, and select a row there. A dashboard's component tabs exist only while
 * that tab is showing, and a feed's stream-preview tabs are inert until a stream is selected — so a
 * replay that clicks straight down the recorded list reaches neither, and reports every one of them
 * as `tab "X" not found` even though the recording holds them.
 */
async function restoreTabContext(page, home) {
  if (home) { await clickEditorTab(page, home, { timeout: 5_000 }).catch(() => {}); await page.waitForTimeout(1_200); }
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
}


import { knownLimitation } from './lib/known.mjs';
import { createSimServer } from './serve.mjs';
import { fromSuite } from './lib/paths.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const exec = promisify(execFile);
const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const NAME = env('NAME', 'default');
const DIR = join(`${SUITE}/corpus`, NAME);
const OUT = fromSuite(env('OUT', join(`${SUITE}/out`, NAME)));
const PORT = Number(env('PORT', '9099'));
/** Per-screen tolerance, in differing pixels. 0 = exact. */
const TOLERANCE = Number(env('TOLERANCE', '0'));

const corpus = loadCorpus(DIR);
if (!corpus) {
  console.error(`no corpus at ${DIR} — run: node stroom-gwt/stroom-gwt-suite/record.mjs`);
  process.exit(2);
}
const coverage = JSON.parse(await import('node:fs').then((fs) => fs.readFileSync(join(DIR, 'coverage.json'), 'utf8')));
const only = env('ONLY') ? new Set(env('ONLY').split(',').map((s) => s.trim())) : null;
const targets = coverage.targets.filter((t) => t.status === 'recorded' && (!only || only.has(t.slug)));

const resolver = makeResolver(corpus);
const server = createSimServer(corpus, resolver);
await new Promise((r) => server.listen(PORT, r));
const URL = `http://localhost:${PORT}`;
console.log(`simulated backend on ${URL} (${Object.keys(corpus.api.entries).length} exchanges, ${Object.keys(corpus.app.entries).length} assets)`);

await mkdir(join(OUT, 'actual'), { recursive: true });
await mkdir(join(OUT, 'diff'), { recursive: true });
await mkdir(join(OUT, 'dom'), { recursive: true });

const { width: VW, height: VH } = coverage.viewport;
const browser = await chromium.launch({ headless: true });
const sharedPage = await browser.newPage({ viewport: { width: VW, height: VH } });
await freezeClock(sharedPage, coverage.clockMs);
gwt.baseUrl = URL;
await sharedPage.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
// Best-effort, like `freshPage`. Against the simulation the auth status is replayed, and a corpus
// whose settled answer is "this session is authenticated" produces no sign-in form at all — waiting
// for a password field is then a guaranteed timeout. If the app comes up, we are in.
await gwt.signInFully(sharedPage, credentials()).catch(() => {});
await gwt.waitForApp(sharedPage);

/** ImageMagick's absolute-error count, with a 1% fuzz so antialiasing is not reported as change. */
async function differingPixels(a, b, out) {
  try {
    await exec('compare', ['-metric', 'AE', '-fuzz', '1%', a, b, out]);
    return 0;
  } catch (e) {
    const m = String(e.stderr ?? '').trim().match(/^([\d.]+)/);
    return m ? Math.round(Number(m[1])) : Number.NaN;
  }
}

/**
 * Interactions get a clean session, for the same reason the recorder gives them one.
 *
 * Sign-in is best-effort here. Replaying a SEQUENCE is stateful: the first sign-in of the run
 * consumes the recorded auth exchanges, so a second one is answered with the later entries — which
 * report a session that already exists, and no password field ever appears. Waiting for one is then
 * a guaranteed 45-second timeout. If the app comes up, we are in.
 */
async function freshPage() {
  const pg = await browser.newPage({ viewport: { width: VW, height: VH } });
  await freezeClock(pg, coverage.clockMs);
  await pg.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await gwt.signInFully(pg, credentials()).catch(() => {});
  await gwt.waitForApp(pg);
  return pg;
}

const results = [];

/** Photograph the current screen and score it against its baseline. */
async function shootAndScore(page, t, { wait = 4500 } = {}) {
  const actual = join(OUT, 'actual', `${t.slug}.png`);
  const baseline = join(DIR, 'baseline', `${t.slug}.png`);
  await settleAndShoot(page, actual, { wait, viewportHeight: VH, domFile: join(OUT, 'dom', `${t.slug}.html.gz`) });
  if (!existsSync(baseline)) {
    results.push({ ...t, verdict: 'NO BASELINE' });
    return;
  }
  const px = await differingPixels(baseline, actual, join(OUT, 'diff', `${t.slug}.png`));
  const limitation = knownLimitation(t);
  results.push({ ...t, px, replayLimitation: limitation, verdict: px <= TOLERANCE ? 'ok' : limitation ? 'KNOWN' : 'DIFF' });
}

/**
 * Open a document from the explorer, BY TYPE: several documents share a name — the explorer holds
 * four things called "Example Index" — so a name alone opens whichever comes first.
 */
async function openDoc(page, t) {
  await settleExplorer(page, { label: 'reload' }).catch(() => {});
  await page.locator('button[title="Expand All"]').first().click({ timeout: 10_000 }).catch(() => {});
  await settleExplorer(page, { label: 'expand' }).catch(() => {});
  await gwt.openDocByName(page, t.label, t.type);
  await closeDialogs(page).catch(() => {});
}

/**
 * A document's sub-tab targets, in the order the recorder took them.
 *
 * Order is not cosmetic. The recorder opens a document ONCE and clicks along its tab bar, so tab N
 * was photographed with tabs 1..N-1 already visited. Replaying them in a different order — or one
 * per fresh page — photographs a different state on any editor that carries state across its tabs,
 * and the diff reads as a UI regression. The recorded sequence is the contract.
 *
 * The parent is recomputed from type+label with the recorder's own slug rule rather than stored, so
 * this works against corpora recorded before the field existed.
 */
const parentSlug = (t) => `doc-${`${t.type}-${t.label}`.replace(/[^a-z0-9]+/gi, '-').toLowerCase()}`;
const tabsFor = new Map();
for (const t of coverage.targets) {
  if (t.kind !== 'doctab' || t.status !== 'recorded') continue;
  const key = parentSlug(t);
  if (!tabsFor.has(key)) tabsFor.set(key, []);
  tabsFor.get(key).push(t);
}
const wanted = new Set(targets.map((t) => t.slug));
const handled = new Set();

for (const t of targets) {
  if (handled.has(t.slug)) continue;
  const own = t.kind === 'interaction' ? await freshPage() : null;
  const page = own ?? sharedPage;
  try {
    await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await gwt.waitForApp(page);
    await closeDialogs(page);
    await dismissMenus(page).catch(() => {});
    if (t.kind === 'doc' || t.kind === 'doctab' || t.kind === 'interaction') {
      await openDoc(page, t);
      if (t.kind === 'doctab') {
        // Only reached when a sub-tab is run WITHOUT its parent (an `ONLY=` selection). The tab strip
        // is not present the instant the document opens, so wait as long as the shot would have.
        await page.waitForTimeout(4500);
        if (!(await clickEditorTab(page, t.tab, { timeout: 5_000 }).catch(() => false))) {
          await restoreTabContext(page, null);
          if (!(await clickEditorTab(page, t.tab, { timeout: 5_000 }))) throw new Error(`tab "${t.tab}" not found`);
        }
      }
      if (t.kind === 'interaction') {
        await page.waitForTimeout(5000);
        await ACTIONS[t.action](page);
      }
    } else {
      await gwt.openMainMenu(page);
      await page.waitForTimeout(500);
      if (!(await clickMenuItem(page, t.group))) throw new Error('group not found');
      await page.waitForTimeout(700);
      if (!(await clickMenuItem(page, t.leaf))) throw new Error('leaf not found');
    }
    await shootAndScore(page, t, { wait: t.kind === 'interaction' ? 1000 : 4500 });

    // Still on the open document: walk its remaining tabs here rather than re-opening it per tab.
    if (t.kind === 'doc') {
      // The document's own first tab is the context its NESTED tabs live in (record.mjs records
      // them from there); everything is recorded in strip order, so the first is the home tab.
      const home = (tabsFor.get(t.slug) ?? [])[0]?.tab ?? null;
      for (const tab of tabsFor.get(t.slug) ?? []) {
        if (!wanted.has(tab.slug)) continue;
        handled.add(tab.slug);
        try {
          if (!(await clickEditorTab(page, tab.tab, { timeout: 5_000 }))) throw new Error(`tab "${tab.tab}" not found`);
          await shootAndScore(page, tab);
        } catch (first) {
          try {
            await restoreTabContext(page, home);
            if (!(await clickEditorTab(page, tab.tab, { timeout: 5_000 }))) throw first;
            await shootAndScore(page, tab);
          } catch (e) {
            results.push({ ...tab, verdict: 'ERROR', why: String(e?.message ?? e).slice(0, 90) });
          }
        }
      }
    }
  } catch (e) {
    results.push({ ...t, verdict: 'ERROR', why: String(e?.message ?? e).slice(0, 90) });
  } finally {
    if (own) await own.close();
  }
}

await browser.close();
server.close();

// A KNOWN target still reports its number — the point is to watch it, not to hide it — but it does
// not fail the run, because the cause is understood and recorded against the target itself.
const bad = results.filter((r) => r.verdict !== 'ok' && r.verdict !== 'KNOWN');
const known = results.filter((r) => r.verdict === 'KNOWN');
console.log(`\n=== ${results.length} screens, tolerance ${TOLERANCE}px ===`);
for (const r of results.sort((a, z) => (z.px ?? 0) - (a.px ?? 0))) {
  if (r.verdict === 'ok') continue;
  console.log(`  ${r.verdict.padEnd(12)} ${r.slug}${r.px != null ? ` — ${r.px}px` : ''}${r.why ? ` — ${r.why}` : ''}`);
}
console.log(`  ok: ${results.length - bad.length - known.length}/${results.length}${known.length ? `, ${known.length} known-limited` : ''}`);
for (const r of known) console.log(`    known: ${r.slug} — ${r.replayLimitation}`);
const { exact, noBody, path, miss } = resolver.stats;
console.log(`  corpus: exact=${exact} relaxed=${noBody + path} miss=${miss}`);
for (const m of [...new Set(resolver.misses)].slice(0, 20)) console.log(`    miss ${m}`);

await writeFile(join(OUT, 'results.json'), JSON.stringify({ tolerance: TOLERANCE, corpus: resolver.stats, results }, null, 1));
process.exit(bad.length ? 1 : 0);
