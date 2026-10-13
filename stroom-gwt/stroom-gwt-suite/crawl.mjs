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
// Crawl the GWT UI: reach every screen and dialog by DOING things, not by navigating to a list.
//
//   URL=… USER=admin PASS=… AREA=Annotations node stroom-gwt/stroom-gwt-suite/crawl.mjs
//   ... DEPTH=reach|dom|pixel …            # how much evidence to keep per node (default reach)
//   ... MAXNODES=40 …                      # hard stop, and it is REPORTED when hit
//
// `record.mjs` covers DESTINATIONS — 184 places you can navigate to. This covers everything reached by
// an ACTION: the 203 doors of `oracles/reachability-graph.md`, the dialogs they open, and the dialogs
// those open. It is a crawler rather than a list because a hand-written list of dialogs rots the first
// time somebody adds a button; the suite already walks the live menu for that reason.
//
// TWO AXES, per stroom-gwt-suite/BRANCHING-PLAN.md:
//   depth   reach (JSON only, ~1KB) | dom (+markup) | pixel (+screenshot)
//   replay  NOT captured here at all — a coverage crawl needs no simulation, so this runs against a
//           live server and writes no corpus. Building a replayable corpus is `record.mjs`'s job.
//
// It MUTATES NOTHING: `attachReadOnlyGuard` is armed, which aborts any mutating request. That is also
// how mutating affordances are IDENTIFIED — a blocked request is recorded against the click that
// caused it. A blocked request is a WORKLIST ITEM, not a verdict: it is either "this affordance
// mutates" or "the guard's POST whitelist is missing a read", and only a human can tell which. The
// path is recorded so that triage is possible; the guard's own history has three cases of a read being
// blocked and then misdiagnosed as a UI difference.
import { appendFile, mkdir, writeFile } from 'node:fs/promises';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { attachReadOnlyGuard } from './compare/lib/readonly-guard.mjs';
import {
  clickByText, clickMenuItem, closeDialogs, ctrlClickByText, dismissMenus, readButtons, readDialogs,
  readVisibleMenuItems, rightClickByText,
} from './compare/lib/structure.mjs';
import {
  settleExplorer, waitForDialogs, waitForMenu, waitForStableCount, waitUntil,
} from './compare/lib/settle.mjs';
import { settleAndShoot } from './lib/shoot.mjs';
import { clickEditorTab, readEditorTabs } from './lib/tabs.mjs';
import { fromSuite } from './lib/paths.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const URL = env('URL', 'http://localhost:8080');
const AREA = env('AREA', 'Annotations');
/** Crawl a document editor instead of a menu area: DOC="My Dashboard" [DOCTYPE=Dashboard]. */
const DOC = env('DOC', '');
const DOCTYPE = env('DOCTYPE', '');
/** Explorer rows to right-click, comma-separated. A DOC crawl seeds its own document by default. */
const EXPLORER = (env('EXPLORER', '') || '').split(',').map((x) => x.trim()).filter(Boolean);
/**
 * Rows to select TOGETHER and right-click: EXPLORER_MULTI="Seed Feed|Seed Script".
 *
 * The multi-selection menu is a different menu, not a longer one — `Remove Tags` appears only above
 * one updatable item, and Rename, Info, Dependencies and Permissions all vanish. Semicolons separate
 * groups, pipes separate the rows within one.
 */
const EXPLORER_MULTI = (env('EXPLORER_MULTI', '') || '').split(';')
  .map((g) => g.split('|').map((x) => x.trim()).filter(Boolean)).filter((g) => g.length > 1);
/** Seed from source-mined route recipes: RECIPES=oracles/route-recipes.json [RECIPEKIND=menu|doc]. */
const RECIPES = fromSuite(env('RECIPES', ''));
const RECIPEKIND = env('RECIPEKIND', '');
const DEPTH = env('DEPTH', 'reach');
/**
 * How far to follow POST-ACTION states — a screen after a search, a step, a filter, a selection.
 *
 * 0 disables it and is the behaviour every crawl before 2026-09-04 had. 1 walks one action past a
 * surveyed screen, which is where `the searched dashboard` and its kind live. Higher gets expensive
 * fast: a post-action state can never be restored, so every one of them is a full rebuild.
 */
const POSTDEPTH = Number(env('POSTDEPTH', '1'));
const MAXNODES = Number(env('MAXNODES', '40'));
const OUT = fromSuite(env('OUT', `${SUITE}/out/crawl`));
const VW = 1600;
const VH = 1000;

if (!['reach', 'dom', 'pixel'].includes(DEPTH)) {
  console.error(`DEPTH must be reach|dom|pixel, got "${DEPTH}"`);
  process.exit(2);
}

/**
 * A node's identity, stored as STRUCTURED DATA with the filename derived from it.
 *
 * The right signature scheme is not knowable before building — this one will be wrong in some way.
 * Keeping identity as data means fixing it later is a regeneration rather than a re-record, which is
 * the one identity decision worth making in advance (BRANCHING-PLAN, "target identity").
 */
/**
 * App chrome: affordances that belong to the shell, not to the screen under it.
 *
 * The first run spent 96 of 104 clicks on these — re-opening the main menu and expanding the explorer
 * once per node, which is 92% of the run buying nothing. They are covered ONCE, as their own node, the
 * same rule the screen profiles use for the same reason. Derived from the profile sweep's finding that
 * 13 button titles appear on >=95% of screens.
 */
const CHROME = new Set([
  'Main Menu', 'Show Menu', 'Expand All', 'Collapse All', 'Locate Current Item', 'Find In Content',
  'Toggle Alerts', 'Ask Stroom AI', 'Filter Types', 'Clear Filter', 'Quick Filter Syntax Help',
  'New', 'Delete',
]);
const chromeDone = new Set();
/** Types whose loose explorer-title match has already been reported. */
const looseReported = new Set();

/**
 * Collapse a label GWT has emitted twice.
 *
 * Dialog buttons are `.Button` divs, not `<button title=…>`, so `readButtons` falls back to
 * textContent — and GWT renders the caption in both a visible span and a second copy, giving
 * "Close Close", "OK OK" and "Set As Default For All UsersSet As Default For All Users". The first
 * crawl then looked for `button[title="Close Close"]`, found nothing, and recorded 17 affordances as
 * `vanished` — the crawler reporting its own selector bug as a property of the UI.
 *
 * Exactly the shape of the `.linkTab-label` trap, which also emits a hidden width-reserving copy of
 * its text. Assume any label that is a string repeated twice is one label.
 */
/**
 * A grid column header, and the column it sorts.
 *
 * `readButtons` picks headers up because they are clickable — that is how you sort — and GWT appends
 * the sort-order index to the text, giving "Display Name 1". They are genuine affordances (clicking
 * one sorts the grid, the case the screen profiles derive) but they are not `<button title=…>` or
 * `.Button`, so the click locator missed all five and recorded them as `vanished`.
 */
const SORT_INDEX = /\s+\d+$/;
const isColumnHeader = (label) => SORT_INDEX.test(label);
const columnName = (label) => label.replace(SORT_INDEX, '').trim();

function undouble(label) {
  const t = label.trim();
  if (t.length % 2 === 0) {
    const half = t.slice(0, t.length / 2);
    if (half === t.slice(t.length / 2)) return half.trim();
  }
  const sp = t.match(/^(.+) \1$/);
  return sp ? sp[1] : t;
}

/**
 * Click an affordance however it is built. GWT dialogs use `.Button` divs with no title attribute,
 * screens use real `<button title=…>`; a locator that knows only one of those misses half the UI.
 */
async function clickAffordance(label, timeout = 3000) {
  // A menu item is not a button and is not reachable by any of the locators below. But this must FALL
  // THROUGH rather than commit: routing every click through the menu whenever one happened to be open
  // cost 18 of 44 Security nodes, which failed to navigate with a 10s timeout because the label they
  // wanted was a toolbar button and the menu had no such item. Try the menu, then try the buttons.
  if (await menuIsOpen()) {
    try {
      if (await clickMenuItem(page, label)) return true;
    } catch { /* not a menu item after all — fall through to the button locators */ }
  }
  const candidates = [
    // A sortable column header: match the header cell by its name, without the sort index.
    ...(isColumnHeader(label)
      ? [page.locator('.dataGridHeader, [role=columnheader], th').filter({ hasText: columnName(label) })]
      : []),
    page.locator(`button[title="${label}"]:not([disabled])`),
    page.locator(`button[aria-label="${label}"]:not([disabled])`),
    page.locator('.Button, [role=button]').filter({ hasText: new RegExp(`^\\s*(${label})(\\s*\\1)?\\s*$`) }),
  ];
  for (const c of candidates) {
    if (await c.count().then((n) => n > 0).catch(() => false)) {
      await c.first().click({ timeout });
      return true;
    }
  }
  return false;
}

/**
 * What a node IS, as opposed to how it was reached.
 *
 * The Dashboard crawl reached 60 nodes of which **52 were repeats** — the same `Save As`, `History`,
 * `Favourites` and `Process` dialogs re-opened once per component tab, each costing a full re-explore.
 * 87% of the budget, and it capped with 20 queued because of it. A door reached from seven places is
 * one door; that is what "203 doors" in the reachability graph means.
 *
 * Keyed on the dialog's own CAPTION plus its affordances — not on affordances alone, which would merge
 * `Set Layout Constraints` and `Current Selection` because both offer nothing but `Close`. Falls back
 * to the label that opened it for nodes with no caption (screens, editor tabs).
 */
const signatureOf = (node, state) => [
  node.kind,
  state.dialogs.join('+') || node.path[node.path.length - 1],
  [...state.buttons].sort().join('|'),
  state.fingerprint ?? '',
].join(' :: ');

/** Is a menu popup up? `.menuItem-outer` is the shared "menu is populated" measure. */
const menuIsOpen = () => page.evaluate(() => [...document.querySelectorAll('.menuItem-outer')]
  .some((e) => e.offsetWidth > 0 && e.offsetHeight > 0)).catch(() => false);

const idOf = (node) => [node.kind, ...node.path].join(' / ');
const slugOf = (node) => idOf(node).replace(/[^a-z0-9]+/gi, '-').toLowerCase().slice(0, 120);

const browser = await chromium.launch({ headless: env('HEADLESS', '1') !== '0' });
const page = await browser.newPage({ viewport: { width: VW, height: VH } });
const guard = attachReadOnlyGuard(page, { enabled: true });
gwt.baseUrl = URL;
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
const creds = credentials();
await gwt.signInFully(page, creds);
await mkdir(OUT, { recursive: true });
if (DEPTH !== 'reach') await mkdir(join(OUT, 'evidence'), { recursive: true });

/** Back to a known state. Every node is reached from a fresh app, so order cannot leak between them. */
async function reset() {
  await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await gwt.waitForApp(page);
  // Prove we are still signed in, every time. `waitForApp` swallows its own 30s timeout, so a lost
  // session does not stop a crawl — it makes every remaining node cost 46s instead of 1.6s and
  // record whatever the sign-in page happens to offer. A run of hundreds of nodes is exactly the
  // workload most likely to lose one, and nothing here noticed. Cheap: one `evaluate`.
  try {
    await gwt.assertSignedIn(page, creds);
  } catch {
    // The session went. Re-authenticate and carry on rather than losing every remaining node: a
    // crawl of hundreds of nodes will lose one eventually (expiry, a server restart — or its own
    // Sign Out click, which is how this was found: 16 Navigation nodes died after it).
    signedOutRecoveries += 1;
    await gwt.signInFully(page, creds);
    await gwt.waitForApp(page);
  }
  await closeDialogs(page).catch(() => {});
  await dismissMenus(page).catch(() => {});
  await settleExplorer(page, { label: 'reload' }).catch(() => {});
}

/**
 * Open a document from the explorer.
 *
 * Expands the tree FIRST. `reset()` leaves the explorer as the app opens it, with every folder shut,
 * and `openExplorerDoc` matches only VISIBLE `.explorerCell` rows — so the first `DOC=` run died on
 * `no visible .explorerCell matching "Test Dashboard"` when the document was there all along, one
 * closed folder away. `record.mjs` expands before every open for exactly this reason.
 */
async function openDoc(name, type) {
  await page.locator('button[title="Expand All"]').first().click({ timeout: 10_000 }).catch(() => {});
  await settleExplorer(page, { label: 'expand' }).catch(() => {});
  await gwt.openDocByName(page, name, type);
  await waitForDocOpen();
}

/**
 * A document is open when its editor's tab bar exists. Replaces a flat 2.5s: the editors that are
 * ready in 300ms stop paying for the ones that are not, and an editor that never opens is now a
 * timed-out wait rather than a silent 2.5s followed by a survey of the wrong screen.
 */
async function waitForDocOpen() {
  await waitUntil(page, () => page.evaluate(() => [...document.querySelectorAll('.linkTab-label, .curveTab-label')]
    .some((e) => e.offsetWidth > 0)), { budget: 2_500, label: 'doc-open', kind: 'editor' }).catch(() => {});
}

/**
 * The route currently realised on the page, or null when we do not know where we are.
 * Only ever set after a route was driven successfully — never inferred.
 */
let positionRoute = null;

/**
 * How much of the current position can be reused to reach `route`?
 *
 * Only sub-TAB steps qualify, and that is not a shortcut so much as what a tab bar is: the tabs of
 * one open document are siblings, and clicking from Settings to Permissions is the same act whether
 * or not you re-opened the document first. So when the position and the target differ only in tab
 * steps, the shared prefix (the `doc` open, 4.5s of it) can stand.
 *
 * Deliberately narrow. Menu, button, context and doc steps are NOT reusable: they change what the
 * app is showing in ways the next step may depend on, and this crawl has already shown twice that a
 * shortcut which merely looks equivalent changes the answers.
 */
function reusablePrefix(route) {
  if (!positionRoute) return 0;
  let i = 0;
  while (i < positionRoute.length && i < route.length
    && JSON.stringify(positionRoute[i]) === JSON.stringify(route[i])) i += 1;
  if (i === 0) return 0;
  const restHere = positionRoute.slice(i);
  const restThere = route.slice(i);
  const allTabs = (steps) => steps.every((s) => s.via === 'tab');
  return allTabs(restHere) && allTabs(restThere) && restThere.length ? i : 0;
}

/** Drive the route that reaches a node from a fresh app. */
async function navigate(node) {
  const reuse = reusablePrefix(node.route);
  positionRoute = null;
  if (reuse) {
    // Standing in a sibling tab of the same document: just click across.
    reusedPrefixes += 1;
    for (const step of node.route.slice(reuse)) {
      if (!(await clickEditorTab(page, step.label, { nth: step.nth ?? 0 }))) {
        throw new Error(`no sub-tab "${step.label}"#${step.nth ?? 0}`);
      }
      await waitUntil(page, () => page.evaluate(() => !document.querySelector('.gwt-Label-loading, .loading')),
        { budget: 1_000, label: `tab:${step.label}`, kind: 'tab' }).catch(() => {});
    }
    positionRoute = node.route;
    return;
  }
  await reset();
  for (const step of node.route) {
    if (step.via === 'menu') {
      await gwt.openMainMenu(page);
      // Wait for the thing, not for a number. Each ceiling is the sleep it replaces, so the worst
      // case is what we already paid and the common case is a fraction of it — a menu lands in
      // 200-400ms, not 1500. This file was the last place in the harness still sleeping blind; see
      // the header of compare/lib/settle.mjs for why that is the wrong instrument.
      await waitForMenu(page, { budget: 400, label: 'main-menu' }).catch(() => {});
      if (!(await clickMenuItem(page, step.group))) throw new Error(`no menu group "${step.group}"`);
      await waitUntil(page, () => menuIsOpen(), { budget: 500, label: `menu:${step.group}`, kind: 'menu' })
        .catch(() => {});
      if (!(await clickMenuItem(page, step.leaf))) throw new Error(`no menu leaf "${step.leaf}"`);
      // The leaf's screen has arrived when the menu has gone AND its content has stopped moving.
      // Waiting only for the menu to close is not enough: Monitoring > Nodes populates its grid
      // asynchronously, and arriving sooner than the old 1.5s sleep meant `Refresh` was clicked into
      // a half-filled grid and classified `grid-reordered` — the crawler's timing recorded as the
      // application's behaviour. Wait for READY, not for PRESENT.
      await waitUntil(page, async () => !(await menuIsOpen()), { budget: 1_500, label: `leaf:${step.leaf}`, kind: 'screen' })
        .catch(() => {});
      await waitForStableCount(page, () => page.evaluate(() =>
        document.querySelectorAll('.dataGridRow, .dataGridCell, tr').length),
      { budget: 1_500, poll: 150, stableFor: 2, label: `content:${step.leaf}`, kind: 'content', allowZero: true })
        .catch(() => {});
      // Leave no menu behind. A screen reached through the main menu is the screen, not the screen
      // plus a stale popup — and since a visible menu now captures the click path, a leftover one
      // silently redirects every subsequent step.
      await dismissMenus(page).catch(() => {});
    } else if (step.via === 'button') {
      if (!(await clickAffordance(step.label, 8000))) throw new Error(`no affordance "${step.label}"`);
      // This step exists to open something, so wait for it to be open rather than for 1.2s to pass.
      await waitForDialogs(page, { budget: 1_200, settleBudget: 600, label: `button:${step.label}` })
        .catch(() => {});
    } else if (step.via === 'docType') {
      // Open ANY existing document of this type. Deliberately not `New > … > Type`: creating one is a
      // mutation, the read-only guard aborts `explorer/v2/create`, and that route therefore reaches
      // the create dialog and never the editor behind it. The explorer already carries the type on
      // each row's icon, so an existing document is both reachable and read-only.
      await page.locator('button[title="Expand All"]').first().click({ timeout: 10_000 }).catch(() => {});
      await settleExplorer(page, { label: 'expand' }).catch(() => {});
      // The mined type is the Java constant's stem (`XsltDoc.TYPE` -> `Xslt`); the explorer shows the
      // server's DISPLAY name (`XSL Translation`). Matching the two literally reported "no document of
      // type Xslt" against 49 of them. Exact, then squashed, then squashed-PREFIX, which is what
      // covers `Xslt` -> `XSL Translation`, `KafkaConfig` -> `Kafka Configuration` and
      // `S3Config` -> `S3 Configuration`. The strategy that matched is reported, so a loose match can
      // never be mistaken for an exact one.
      const found = await page.evaluate((type) => {
        const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
        const squash = (s) => s.toLowerCase().replace(/[^a-z0-9]/g, '');
        const rows = [...document.querySelectorAll('.explorerCell')].filter((e) => e.offsetWidth > 0)
          .map((e) => ({ el: e, title: norm(e.querySelector('.explorerCell-icon')?.getAttribute('title')),
            label: norm(e.textContent) }))
          .filter((r) => r.title);
        for (const [how, test] of [
          ['exact', (t) => t === type],
          ['squashed', (t) => squash(t) === squash(type)],
          ['prefix', (t) => squash(t).startsWith(squash(type))],
        ]) {
          const hit = rows.find((r) => test(r.title));
          if (hit) return { label: hit.label, title: hit.title, how };
        }
        return null;
      }, step.type);
      if (!found) throw new Error(`no document of type "${step.type}" in the explorer`);
      // Announce ONCE per type, not once per navigation: every affordance re-navigates, so
      // AnalyticRule reported its own loose match 18 times in a single run.
      if (found.how !== 'exact' && !looseReported.has(step.type)) {
        looseReported.add(step.type);
        console.log(`  (type "${step.type}" matched explorer title "${found.title}" by ${found.how})`);
      }
      await gwt.openDocByName(page, found.label, found.title);
      await waitForDocOpen();
    } else if (step.via === 'context') {
      // The explorer tree is the biggest uncrawled surface in the application: Copy, Move, Rename,
      // Delete, Info and the whole document-type New submenu are reachable ONLY by right-click, so no
      // button-driven pass has ever seen them.
      await page.locator('button[title="Expand All"]').first().click({ timeout: 10_000 }).catch(() => {});
      await settleExplorer(page, { label: 'expand' }).catch(() => {});
      // A multi-selection is built the way a user builds one: CLICK the first row, ctrl-click the
      // rest, then right-click inside the selection.
      //
      // The first version skipped the plain click and went straight to ctrl-clicking the extras,
      // which selected only the extra — and the right-click that followed reset the selection to the
      // target. It reported 14 items and no `Remove Tags`: the single-selection menu wearing a
      // multi-selection label, which is worse than not covering the shape at all.
      if (step.also?.length) {
        if (!(await clickByText(page, '.explorerCell', step.target))) {
          throw new Error(`no explorer row "${step.target}" to select`);
        }
        for (const extra of step.also) {
          if (!(await ctrlClickByText(page, '.explorerCell', extra))) {
            throw new Error(`no explorer row "${extra}" to add to the selection`);
          }
        }
      }
      if (!(await rightClickByText(page, '.explorerCell', step.target))) {
        throw new Error(`no explorer row "${step.target}" to right-click`);
      }
      await page.waitForTimeout(600);
    } else if (step.via === 'tab-context') {
      // The CONTENT TAB's own menu — Close / Close Others / Close Saved / Close All / Save / Save
      // All / Locate / Add To Favourites — which is a separate menu built from `event.getTabData()`
      // and is not reachable from the explorer at all.
      if (!(await rightClickByText(page, '.curveTab', step.label))) {
        throw new Error(`no content tab "${step.label}" to right-click`);
      }
      await page.waitForTimeout(600);
    } else if (step.via === 'doc') {
      await openDoc(step.name, step.type);
    } else if (step.via === 'tab') {
      if (!(await clickEditorTab(page, step.label, { nth: step.nth ?? 0 }))) {
        throw new Error(`no sub-tab "${step.label}"#${step.nth ?? 0}`);
      }
      await waitUntil(page, () => page.evaluate(() => !document.querySelector('.gwt-Label-loading, .loading')),
        { budget: 1_000, label: `tab:${step.label}`, kind: 'tab' }).catch(() => {});
    }
  }
  positionRoute = node.route;
}

/**
 * After a click: can we carry on from here, or must the world be rebuilt?
 *
 * `navigate()` costs a reload plus the whole route — 6.4s measured — and the crawl paid it once per
 * AFFORDANCE, which is where the hours went. Most clicks do not actually destroy the node: a dialog
 * opens, a menu drops, nothing happens. Closing what opened and checking we are still standing in
 * the same place is ~200ms, and the SIGNATURE is what makes that safe. It is the same signature the
 * crawl already trusts to decide two routes reach the same node, so this weakens nothing: it buys
 * the isolation guarantee by verifying it instead of by rebuilding.
 *
 * Returns the fresh survey when the node survived, else null (caller re-navigates).
 */
async function restoreTo(nodeSig, node) {
  try {
    await closeDialogs(page).catch(() => {});
    await dismissMenus(page).catch(() => {});
    // Wait for the screen to actually come back, don't just assume it has. Without this the next
    // affordance clicked into a page still tearing a dialog down and four of them came back
    // `unclickable` — the crawler's own impatience recorded as the application refusing input.
    // Measured, per the rule this project already applies everywhere except here.
    await waitUntil(page, async () => (await readDialogs(page)).length === 0 && !(await menuIsOpen()),
      { budget: 4_000, label: 'restore', kind: 'restore' }).catch(() => {});
    const s = await survey();
    return signatureOf(node, s) === nodeSig ? s : null;
  } catch {
    return null;
  }
}

/**
 * What the current screen offers. Buttons and — deliberately — whether an Alert is showing.
 *
 * An Alert is not noise. 527 `AlertEvent` sites make it the most common screen in the application, and
 * `closeDialogs()` in the shared shoot path is why the suite has never photographed one.
 */
async function survey() {
  // Scope to the topmost popup when one is open. Surveying `body` offers the whole page including
  // everything behind a modal's glass — which is correctly unclickable, and produced 34 `unclickable`
  // affordances that read as the application refusing input when it was the crawler asking the wrong
  // question. What is behind the glass is not an affordance of this node.
  const scope = await page.evaluate(() => {
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')]
      .filter((e) => e.offsetWidth > 0);
    if (!popups.length) return 'body';
    popups[popups.length - 1].setAttribute('data-crawl-scope', '1');
    return '[data-crawl-scope="1"]';
  });
  // An open MENU is a scope too, and this is the second time that lesson has cost a run. Scoping to
  // `.dialog-popup` alone fixed the 34 affordances read from behind a modal's glass; a menu is not a
  // dialog, so `Add Column` and `History` were read from UNDER an open menu, reported as available, and
  // then failed their click — 13 `unclickable` that were the crawler's doing. `elementFromPoint` named
  // the coverer as `div.menuItem-text`, which is what turned a theory into a fact.
  //
  // Clicking a dashboard component tab in design mode opens that component's menu, so this is not an
  // artefact to suppress: those items are affordances, and the 27 `ShowMenuEvent` classes in the
  // reachability graph are otherwise entirely uncrawled.
  // Split by `enabled` exactly as buttons are. Taking every item regardless made the crawler click
  // `Rename` and `Move` on the System root — which cannot be renamed or moved — and report `nothing`,
  // when the honest reading is "disabled at rest", the selection-gated signal the screen profiles want.
  const menuItems = (await readVisibleMenuItems(page).catch(() => []))
    .filter((i) => (i.label ?? i.text));
  const menu = menuItems.filter((i) => i.enabled !== false).map((i) => i.label ?? i.text);
  const menuDisabled = menuItems.filter((i) => i.enabled === false).map((i) => i.label ?? i.text);
  const buttons = menuItems.length ? [] : ((await readButtons(page, scope)) ?? []);
  // A projection of every grid's CONTENT, because sorting, paging and filtering change none of the
  // things the classifier previously watched. A sort changes row ORDER; a filter changes row COUNT; a
  // page changes both. Without this, all three come back `nothing` and no assertion about them can be
  // believed — the crawler cannot tell a working sort from a dead one.
  //
  // Deliberately cheap and order-SENSITIVE: row count plus the first rows' text. Comparing the whole
  // grid would be more precise and would also make every incidental re-render look like a change.
  const grids = await page.evaluate(() => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    return [...document.querySelectorAll('.dataGridWidget')]
      .filter((g) => g.offsetWidth > 0)
      .map((g) => {
        const rows = [...g.querySelectorAll('tbody tr')];
        return { rows: rows.length, head: rows.slice(0, 3).map((r) => norm(r.textContent).slice(0, 80)) };
      });
  }).catch(() => []);
  // A fingerprint of what is INSIDE the scope, because caption + buttons cannot tell two dialogs apart
  // when both are titled `Settings` and both offer OK/Cancel. The dashboard has a distinct settings
  // presenter per component type — Query, Table, Text, ListInput, KeyValueInput, TableFilter,
  // EmbeddedQuery — and without this they all merged into one node, silently losing five dialogs.
  //
  // Deliberately structural, not data: sub-tab labels plus COUNTS of fields. Including values would
  // stop two genuinely identical dialogs from merging the moment their content differed.
  const fingerprint = await page.evaluate((sel) => {
    const root = document.querySelector(sel) ?? document.body;
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const tabs = [...root.querySelectorAll('.linkTab-label, .curveTab-label')]
      .filter((e) => e.offsetWidth > 0).map((e) => norm(e.textContent)).sort();
    const n = (q) => root.querySelectorAll(q).length;
    return `${tabs.join(',')}#${n('input,select,textarea')}/${n('.form-group')}/${n('.ace_editor')}`;
  }, scope).catch(() => '');
  const dialogs = await readDialogs(page).catch(() => []);
  await page.evaluate(() => document.querySelectorAll('[data-crawl-scope]')
    .forEach((e) => e.removeAttribute('data-crawl-scope')));
  return {
    buttons: menuItems.length
      ? [...new Set(menu.map(undouble))]
      : [...new Set(buttons.filter((b) => b.label && b.enabled).map((b) => undouble(b.label)))],
    disabled: menuItems.length
      ? [...new Set(menuDisabled.map(undouble))]
      : [...new Set(buttons.filter((b) => b.label && !b.enabled).map((b) => undouble(b.label)))],
    dialogs: dialogs.map((d) => d.caption).filter(Boolean),
    menu: menuItems.length > 0,
    fingerprint,
    grids,
  };
}

/** Classify what a click did. "Nothing happened" and "it was disabled" are different facts. */
async function classify(before) {
  // NOT shortened. Tried: return as soon as a dialog or menu appears. It saved ~20% of the run and
  // moved three outcomes — `Main Menu` and `Show Menu` from `menu` to `nothing`, and a `Refresh`
  // from `nothing` to `grid-reordered` — because the app was still reacting when the next affordance
  // began, so one click's effect landed inside the next one's measurement. A classifier may not
  // sample before the thing it classifies has finished happening.
  await page.waitForTimeout(1200);
  const after = await survey();
  const newDialogs = after.dialogs.filter((d) => !before.dialogs.includes(d));
  if (newDialogs.some((d) => /alert/i.test(d))) return { outcome: 'alert', detail: newDialogs.join(', ') };
  if (newDialogs.length) return { outcome: 'dialog', detail: newDialogs.join(', ') };
  // A menu is a destination, not a state change. `Main Menu` and every context menu previously
  // classified as `changed` and stopped there, so the whole main-menu tree — User, Help, and the
  // dialogs behind them — was never crawled. A stale menu once exposed those items by accident, as
  // affordances wrongly attributed to the screen underneath; this reaches them on purpose instead.
  const grew = after.buttons.filter((b) => !before.buttons.includes(b));
  // …including a SUBMENU. `after.menu && !before.menu` alone only catches the first menu opening, so
  // every group inside it — Administration, Annotations, User, Help — reported `changed` and listed its
  // children as detail text that nothing ever clicked. The main menu is 45 areas; entering one level
  // and stopping covers none of them.
  if (after.menu && (!before.menu || grew.length)) {
    return { outcome: 'menu', detail: (grew.length ? grew : after.buttons).slice(0, 6).join(', ') };
  }
  if (grew.length) return { outcome: 'changed', detail: `+${grew.slice(0, 4).join(', ')}` };

  // Grid content, before anything else concludes "nothing happened".
  const g0 = before.grids ?? [];
  const g1 = after.grids ?? [];
  for (let i = 0; i < Math.max(g0.length, g1.length); i += 1) {
    const a = g0[i];
    const b = g1[i];
    if (!a || !b) return { outcome: 'grid-appeared', detail: `grid ${i}` };
    if (a.rows !== b.rows) return { outcome: 'grid-rows', detail: `${a.rows} -> ${b.rows} rows` };
    if (JSON.stringify(a.head) !== JSON.stringify(b.head)) {
      return { outcome: 'grid-reordered', detail: `first row: "${(b.head[0] ?? '').slice(0, 40)}"` };
    }
  }
  return { outcome: 'nothing', detail: '' };
}

// ── The crawl ────────────────────────────────────────────────────────────────
// Two ways in, because the application has two.
//
// A menu AREA seeds one node per leaf — the whole Monitoring/Security/Annotations shape. But the
// largest surface in the product is not on a menu at all: a Dashboard is 62 presenters that exist only
// once a Dashboard DOCUMENT is open, reached by double-clicking a row in the explorer. Crawling from
// the menu can never see them, so `DOC=` seeds from an open editor and its sub-tabs instead.
const seeds = [];
if (RECIPES) {
  // Seed from the recipes MINED FROM THE GWT SOURCE, not from what the crawler stumbles on. This is
  // the difference between "what did we find" and "what did we FAIL to reach": every seed here is a
  // route the source says exists, so an unreachable one is a finding rather than an absence.
  const { recipes } = JSON.parse(readFileSync(RECIPES, 'utf8'));
  const wanted = recipes.filter((r) => (RECIPEKIND ? r.via === RECIPEKIND : true));
  for (const r of wanted) {
    const step = r.route[0];
    if (r.via === 'menu') {
      seeds.push({ kind: 'screen', presenter: r.presenter, recipe: r.label,
        path: step.leaf ? [step.group, step.leaf] : [step.group],
        route: [step.leaf ? { via: 'menu', group: step.group, leaf: step.leaf }
          : { via: 'menu', group: 'Main Menu', leaf: step.group }] });
    } else if (r.via === 'doc') {
      seeds.push({ kind: 'editor', presenter: r.presenter, recipe: r.label,
        path: [`${step.type} (document)`], route: [{ via: 'docType', type: step.type }] });
    }
  }
  console.log(`seeded ${seeds.length} recipes from ${RECIPES}`
    + `${RECIPEKIND ? ` (via=${RECIPEKIND})` : ''}\n`);
} else if (DOC) {
  await reset();
  await openDoc(DOC, DOCTYPE);
  const tabs = await readEditorTabs(page, DOC);
  seeds.push({ kind: 'editor', path: [DOC], route: [{ via: 'doc', name: DOC, type: DOCTYPE }] });
  // A tab label is NOT unique. This dashboard has two components both called `Table`, which produced
  // one id, one `seen` entry and one node — the second panel never visited, on a run that reported
  // itself complete. Number the repeats so a collision costs a suffix, not a screen.
  const nthOf = new Map();
  for (const tab of tabs) {
    const nth = nthOf.get(tab) ?? 0;
    nthOf.set(tab, nth + 1);
    seeds.push({ kind: 'editor-tab', path: [DOC, nth ? `${tab} #${nth + 1}` : tab],
      route: [{ via: 'doc', name: DOC, type: DOCTYPE }, { via: 'tab', label: tab, nth }] });
  }
  console.log(`seeded ${seeds.length} nodes from document "${DOC}": ${tabs.join(', ') || '(no sub-tabs)'}`);
} else {
  await reset();
  await gwt.openMainMenu(page);
  await page.waitForTimeout(500);
  if (!(await clickMenuItem(page, AREA))) throw new Error(`no menu group "${AREA}"`);
  await page.waitForTimeout(700);
  const groups = new Set([AREA]);
  const leaves = (await readVisibleMenuItems(page)).map((i) => i.label ?? i.text).filter(Boolean)
    .filter((l) => !groups.has(l));
  await dismissMenus(page).catch(() => {});
  for (const leaf of leaves) {
    seeds.push({ kind: 'screen', path: [AREA, leaf], route: [{ via: 'menu', group: AREA, leaf }] });
  }
  console.log(`seeded ${seeds.length} screens from "${AREA}": ${seeds.map((s) => s.path[1]).join(', ')}`);
}

// Explorer context menus. A DOC crawl right-clicks its own document by default; any crawl can name
// more rows with EXPLORER="System,My Feed". These are seeds rather than affordances because a
// right-click is not something `survey()` can discover — nothing in the markup advertises it.
// PREPENDED, not appended. These are the P3 subject and they are cheap — a right-click and a menu
// read — while a document crawl's own sub-tabs can fill MAXNODES on their own and leave the context
// menus queued behind them, which is exactly what the first P3 run did.
const contextSeeds = [];
const contextTargets = [...new Set([...(DOC ? [DOC] : []), ...EXPLORER])];
for (const target of contextTargets) {
  contextSeeds.push({ kind: 'context', path: [`${target} (right-click)`], route: [{ via: 'context', target }] });
}
if (contextTargets.length) console.log(`  + ${contextTargets.length} explorer context menu(s): ${contextTargets.join(', ')}`);

// The multi-selection menu, and the content tab's own menu. Both are seeds for the same reason a
// right-click is: nothing in the markup advertises them, so no amount of surveying finds them.
for (const group of EXPLORER_MULTI) {
  const [target, ...also] = group;
  contextSeeds.push({
    kind: 'context',
    path: [`${group.join(' + ')} (right-click, ${group.length} selected)`],
    route: [{ via: 'context', target, also }],
  });
}
if (EXPLORER_MULTI.length) console.log(`  + ${EXPLORER_MULTI.length} multi-selection context menu(s)`);

if (DOC) {
  contextSeeds.push({
    kind: 'context',
    path: [`${DOC} (tab right-click)`],
    route: [{ via: 'doc', name: DOC, type: DOCTYPE }, { via: 'tab-context', label: DOC }],
  });
  console.log('  + 1 content-tab context menu');
}
seeds.unshift(...contextSeeds);
console.log();

const queue = [...seeds];
const seen = new Set();
/** Wall-clock accounting — see the `summary` at the end and COVERAGE-PLAN.md. */
const runStartedAt = Date.now();
let navCount = 0;
/**
 * Outcomes after which the screen is the same screen, so closing what opened is enough to carry on.
 * Deliberately excludes `changed` and every `grid-*`: those ARE a state change, and the signature
 * would happily call the result identical.
 */
const RESTORABLE = new Set(['nothing', 'dialog', 'menu', 'vanished', 'unclickable']);

/**
 * Affordances that would end the session the crawl is running in. Clicking `Sign Out` cost 16
 * Navigation nodes before this existed — every one after it reported `not signed in`, which is at
 * least honest, but they were still lost.
 */
const SELF_DESTRUCTIVE = new Set(['Sign Out', 'Sign Out Other Sessions']);

/** Outcomes that are never trusted from a reused page — see the retry below. */
const RETRY_ON_REBUILD = new Set(['unclickable', 'vanished', 'error']);

/** Restores that held, for the summary — the measure of how much the rebuild is being avoided. */
let restored = 0;
/** Fast-path results thrown away and re-asked from a rebuilt page. */
let retriedAfterRestore = 0;
/** Navigations that reused an open document and clicked across its tabs instead. */
let reusedPrefixes = 0;
/** Times the crawl found itself signed out and signed back in. Should normally be zero. */
let signedOutRecoveries = 0;
/**
 * The survey of the node we are currently standing at. crawl.mjs is SUPERSEDED by walk.mjs
 * (gwt-suite-walker); this is kept only so old runs can be re-read, and the assignments below are
 * bookkeeping nothing consumes.
 */
let atNode = null;
void atNode;
const nodes = [];
const affordances = [];
/** signature -> the id of the node that was actually explored for it. */
const explored = new Map();
let capped = false;

while (queue.length) {
  if (nodes.length >= MAXNODES) { capped = true; break; }
  const node = queue.shift();
  const id = idOf(node);
  if (seen.has(id)) continue;
  seen.add(id);

  const before = guard.violations.length;
  // Wall-clock per node, because "it takes hours" was argued about for weeks before anyone timed it.
  // Cheap to carry and it makes the speed work in COVERAGE-PLAN.md measurable rather than asserted.
  const nodeStartedAt = Date.now();
  navCount += 1;
  atNode = null; void atNode;
  try {
    await navigate(node);
  } catch (e) {
    nodes.push({ id, slug: slugOf(node), kind: node.kind, path: node.path, status: 'unreachable',
      ms: Date.now() - nodeStartedAt,
      presenter: node.presenter, recipe: node.recipe, why: String(e?.message ?? e).slice(0, 100) });
    console.log(`  ✗ ${id} — ${String(e?.message ?? e).slice(0, 70)}`);
    continue;
  }

  const state = await survey();
  const rec = { id, slug: slugOf(node), kind: node.kind, path: node.path, route: node.route,
    presenter: node.presenter, recipe: node.recipe, status: 'reached', buttons: state.buttons.length, disabledAtRest: state.disabled, dialogs: state.dialogs,
    // Whether this node's affordances came from a MENU rather than a toolbar — otherwise a node with
    // 7 actions gives no clue that it is a context menu, and the distinction is the whole point.
    menu: state.menu,
    // Arrived to find an Alert sitting over the target. The Pathways editor reports
    // `Remote node '' has no URL set` on an instance with no pathways node, and the crawl recorded
    // that alert AS the editor — one action, `Close` — inside a run that reported 26 of 26 document
    // editors reached. A screen we never actually saw counted as covered, and only the capability
    // diff (expected Save/Save As, observed Close) caught it.
    blockedByAlert: (node.kind === 'editor' || node.kind === 'screen')
      && state.dialogs.some((d) => /alert/i.test(d)),
    // Row counts, kept because they are the difference between "the sort is dead" and "there is one row
    // to sort". Two column sorts reported `nothing` and the honest answer was that we could not tell
    // which — a projection the classifier computes anyway, discarded before anyone could read it.
    gridRows: (state.grids ?? []).map((g) => g.rows) };

  // Reached from a new route, but is it a new PLACE? Record the node either way — that seven component
  // tabs offer an identical toolbar is itself a finding, and losing it would hide the duplication
  // rather than explain it — but explore it only once.
  rec.ms = Date.now() - nodeStartedAt;
  const nodeSig = signatureOf(node, state);
  const twin = explored.get(nodeSig);
  if (twin) {
    rec.status = 'duplicate';
    rec.duplicateOf = twin;
    nodes.push(rec);
    console.log(`  = ${id} — same place as "${twin}", not re-explored`);
    continue;
  }
  explored.set(nodeSig, id);
  // The node was just surveyed, so the first affordance needs no navigation at all.
  atNode = state;

  if (DEPTH !== 'reach') {
    await settleAndShoot(page, join(OUT, 'evidence', `${slugOf(node)}.png`), {
      wait: 1500,
      viewportHeight: VH,
      domFile: join(OUT, 'evidence', `${slugOf(node)}.html.gz`),
    });
    // `pixel` keeps the screenshot; `dom` keeps only the markup.
    if (DEPTH === 'dom') await import('node:fs/promises').then((fs) =>
      fs.rm(join(OUT, 'evidence', `${slugOf(node)}.png`), { force: true }));
  }
  nodes.push(rec);
  console.log(`  ${rec.blockedByAlert ? '!' : '✓'} ${id} — ${state.buttons.length} actions, `
    + `${state.disabled.length} disabled${rec.blockedByAlert ? '  [ALERT over the target — NOT the screen]' : ''}`);

  // An editor's SUB-TABS are nodes. The reachability graph files settings tabs, list panes and
  // permissions panes under "embedded — covered when their parent screen is", and that is simply not
  // true: opening the Feed editor does not exercise its Permissions tab, you have to click it. The
  // wide crawl proved it — 26 editors opened, ZERO sub-tabs visited — so ~100 screens sat behind an
  // assumption. Discovered at runtime rather than seeded, because only the open document knows which
  // tabs it has, and numbered so two panes with one label cannot collide.
  if (node.kind === 'editor' && !rec.blockedByAlert) {
    const tabs = await readEditorTabs(page, node.path[0] ?? '').catch(() => []);
    const nth = new Map();
    for (const tab of tabs) {
      const i = nth.get(tab) ?? 0;
      nth.set(tab, i + 1);
      queue.push({ kind: 'editor-tab', presenter: node.presenter,
        path: [...node.path, i ? `${tab} #${i + 1}` : tab],
        route: [...node.route, { via: 'tab', label: tab, nth: i }] });
    }
    if (tabs.length) console.log(`      + ${tabs.length} sub-tab(s): ${tabs.join(', ')}`);
  }

  // Every enabled affordance, once per (node, label).
  for (const label of state.buttons) {
    // Signing out is not an affordance a crawl can exercise: it destroys the session it is using,
    // and the recovery above then re-authenticates on the next node. Recorded as skipped rather
    // than silently dropped — "not exercised, deliberately" is a coverage fact, and pretending the
    // button does not exist would leave it looking unreached.
    if (SELF_DESTRUCTIVE.has(label)) {
      affordances.push({ node: id, label, kind: 'button', outcome: 'skipped',
        detail: 'ends the crawl session — see SELF_DESTRUCTIVE', rows: [], blocked: [] });
      continue;
    }
    // Chrome is covered once for the whole crawl, not once per node.
    if (CHROME.has(label)) {
      if (chromeDone.has(label)) continue;
      chromeDone.add(label);
    }
    const sig = `${id} :: ${label}`;
    if (seen.has(sig)) continue;
    seen.add(sig);
    const vBefore = guard.violations.length;
    let result;
    let b0;
    /** Did this click run on a REUSED page rather than a rebuilt one? Decides whether to trust it. */
    let onRestored = false;
    try {
      // Already standing at the node (we just surveyed it, or the last click was undone), so skip
      // the rebuild. `atNode` is only ever set by a signature match, never assumed.
      if (atNode) {
        b0 = atNode;
        atNode = null;
        onRestored = true;
      } else {
        navCount += 1;
        await navigate(node);
        b0 = await survey();
      }
      // A short timeout on purpose: a control that will not accept a click within 3s is telling us
      // something (covered by a popup, not actionable, needs a selection first). That is an outcome to
      // record, not an exception to throw — the first run turned 15 of those into `error`, which hides
      // the distinction between "the UI refused" and "the crawler broke".
      try {
        result = (await clickAffordance(label))
          ? await classify(b0)
          : { outcome: 'vanished', detail: 'present when surveyed, not locatable when clicked' };
      } catch {
        result = { outcome: 'unclickable', detail: 'did not accept a click within 3s' };
      }
    } catch (e) {
      result = { outcome: 'error', detail: String(e?.message ?? e).slice(0, 80) };
    }
    // A FAILED click on a reused page is not evidence. Restoring in place is an optimisation, and an
    // optimisation is not allowed to change an answer: on the Administration node it turned three
    // `nothing` and one `dialog` into `unclickable`, and the cause was never established (leftover
    // modal glass was ruled out with probe-glassleak.mjs). So the fast path is optimistic and this is
    // the check that makes it safe — throw the result away, rebuild, and ask again. The cost is one
    // navigation on the rare failure; the alternative is a suite that reports what it could not click
    // rather than what the application does.
    if (onRestored && RETRY_ON_REBUILD.has(result.outcome)) {
      retriedAfterRestore += 1;
      const vRetry = guard.violations.length;
      navCount += 1;
      try {
        await navigate(node);
        b0 = await survey();
        try {
          result = (await clickAffordance(label))
            ? await classify(b0)
            : { outcome: 'vanished', detail: 'present when surveyed, not locatable when clicked' };
        } catch {
          result = { outcome: 'unclickable', detail: 'did not accept a click within 3s' };
        }
      } catch (e) {
        result = { outcome: 'error', detail: String(e?.message ?? e).slice(0, 80) };
      }
      guard.violations.length = Math.max(guard.violations.length, vRetry);
    }

    const blocked = guard.violations.slice(vBefore).map((v) => `${v.method} ${v.path}`);
    // Restore only after a click that did NOT alter the screen. The signature is not a strong enough
    // guarantee on its own: it covers dialogs, buttons and the fingerprint, but NOT a grid's sort or
    // selection, so restoring after a `changed` click let a sorted grid leak into the next
    // affordance. Measured, not theorised — doing it unconditionally moved five of these 58
    // affordances to a different outcome (four `unclickable`, one `grid-reordered`) while reporting
    // the node as identical. A click that changed something gets the full rebuild.
    positionRoute = null;
    atNode = RESTORABLE.has(result.outcome) && !blocked.length
      ? await restoreTo(nodeSig, node)
      : null;
    if (atNode) { restored += 1; positionRoute = node.route; }
    // A sort that changed nothing on a 1-row grid is not a finding; on a 12-row grid it is. Say which,
    // here, instead of leaving `nothing` to be argued about later.
    const rowsAt = (b0?.grids ?? []).map((g) => g.rows);
    if (result.outcome === 'nothing' && isColumnHeader(label)) {
      result.detail = rowsAt.length ? `grid rows: ${rowsAt.join(', ')}` : 'no visible grid';
    }
    affordances.push({ node: id, label, kind: isColumnHeader(label) ? 'column-sort' : 'button',
      ...result, rows: rowsAt, blocked });
    const mark = { dialog: '▸', alert: '!', changed: '~', nothing: '·', vanished: '?', error: 'x',
      unclickable: '-', 'grid-rows': '#', 'grid-reordered': '↕', 'grid-appeared': '+',
      menu: '☰' }[result.outcome] ?? '?';
    console.log(`      ${mark} ${label}${result.detail ? ` → ${result.detail}` : ''}` +
      `${blocked.length ? `  [blocked: ${blocked.join('; ')}]` : ''}`);

    // A dialog is a new node, reached by this button.
    if (result.outcome === 'dialog' || result.outcome === 'alert' || result.outcome === 'menu') {
      queue.push({ kind: result.outcome, path: [...node.path, label],
        route: [...node.route, { via: 'button', label }] });
    }

    // A POST-ACTION STATE is a node too, and until now it was the one outcome the crawl could see
    // and refused to walk into. `changed` means the screen is not the screen we surveyed — a search
    // has run, a grid has filled, a panel has appeared — and everything that state offers has never
    // been surveyed, because the crawler noted the change and moved on.
    //
    // Bounded on purpose. These are the one outcome that cannot be restored (the signature would
    // call the result identical), so each costs a full rebuild, and clicks that change something are
    // common. POSTDEPTH caps how deep a chain of them can go; 1 means "one action past a surveyed
    // screen", which is where the states worth photographing are.
    // Counted in POST-ACTION STEPS, not in route length: a document's sub-tab is already two steps
    // deep and deserves its post-action states as much as a menu screen one step deep does. What
    // must be bounded is a CHAIN of them.
    if (result.outcome === 'changed'
        && (node.route ?? []).filter((step) => step.post).length < POSTDEPTH) {
      queue.push({ kind: 'post-action', path: [...node.path, `after ${label}`],
        route: [...node.route, { via: 'button', label, post: true }] });
    }
  }
  void before;
}

const wallMs = Date.now() - runStartedAt;
const byKind = {};
for (const n of nodes) {
  const k = n.kind ?? 'unknown';
  byKind[k] = byKind[k] ?? { nodes: 0, ms: 0 };
  byKind[k].nodes += 1;
  byKind[k].ms += n.ms ?? 0;
}

const summary = {
  // A recipe run is not an AREA run: the wide pass wrote crawl-annotations.json because AREA still
  // held its default while RECIPES drove everything, which made the output look like an Annotations
  // crawl containing 26 document editors.
  area: RECIPES ? `recipes${RECIPEKIND ? `-${RECIPEKIND}` : ''}` : DOC ? `doc:${DOC}` : AREA, depth: DEPTH, url: URL, capped, maxNodes: MAXNODES,
  // Timing. `navigations` is the number this run paid for — once per node AND once per affordance,
  // which is the shape the whole cost is made of.
  wallMs, navigations: navCount, msPerNavigation: navCount ? Math.round(wallMs / navCount) : 0,
  // How often a click was undone rather than the whole app rebuilt.
  restoredInPlace: restored, retriedAfterRestore, reusedPrefixes, signedOutRecoveries,
  msByKind: byKind,
  nodes: nodes.length, reached: nodes.filter((n) => n.status === 'reached').length,
  duplicates: nodes.filter((n) => n.status === 'duplicate').length,
  blockedByAlert: nodes.filter((n) => n.blockedByAlert).length,
  distinctPlaces: explored.size,
  affordances: affordances.length,
  outcomes: affordances.reduce((m, a) => ({ ...m, [a.outcome]: (m[a.outcome] ?? 0) + 1 }), {}),
  blockedRequests: [...new Set(affordances.flatMap((a) => a.blocked))],
};
await writeFile(join(OUT, `crawl-${summary.area.replace(/[^a-z0-9]+/gi, '-').toLowerCase()}.json`),
  JSON.stringify({ summary, nodes, affordances }, null, 1));

// Evidence ACCUMULATES; the per-area JSON is only the last run. A capped Security pass overwrote a
// complete one and silently deleted the proof that About, Preferences, Import and Export had ever
// been reached — coverage was then computed from the truncated file and reported them as unreached.
// Append-only, so no run can destroy an earlier run's findings.
await appendFile(join(OUT, 'evidence.jsonl'),
  nodes.filter((n) => n.status === 'reached' || n.status === 'duplicate')
    .map((n) => JSON.stringify({ area: summary.area, id: n.id, kind: n.kind, path: n.path,
      dialogs: n.dialogs ?? [] })).join('\n') + '\n');
await browser.close();

console.log(`\n=== ${summary.area} · depth=${DEPTH} ===`);
console.log(`  nodes ${summary.reached}/${summary.nodes} reached, ${summary.affordances} affordances`);
if (summary.blockedByAlert) {
  console.log(`  !! ${summary.blockedByAlert} node(s) were an ALERT over the target, not the target`);
}
console.log(`  ${summary.distinctPlaces} distinct places, `
  + `${summary.duplicates} re-entrances recorded but not re-explored`);
console.log(`  outcomes: ${Object.entries(summary.outcomes).map(([k, v]) => `${k}=${v}`).join(' ')}`);
if (summary.blockedRequests.length) {
  console.log(`  blocked requests (TRIAGE — mutation, or a read the whitelist is missing?):`);
  for (const b of summary.blockedRequests) console.log(`    ${b}`);
}
if (capped) console.log(`  !! CAPPED at MAXNODES=${MAXNODES} — coverage is INCOMPLETE, ${queue.length} queued`);
