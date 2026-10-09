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
// Walk the GWT UI: one session, depth-first, down every branch and back out again.
//
//   URL=… STROOM_USER=admin STROOM_PASS=… AREA=Monitoring node stroom-gwt/stroom-gwt-suite/walk.mjs
//   ... DOC="My Dashboard" DOCTYPE=Dashboard …     # walk from an open document and its sub-tabs
//   ... RECIPES=oracles/route-recipes.json RECIPEKIND=menu|doc …
//   ... EXPLORER="System,My Feed" EXPLORER_MULTI="A|B" …   # context menus, see crawl.mjs
//   ... POSTDEPTH=1 MAXNODES=5000 DEPTH=reach|dom|pixel OUT=stroom-gwt-suite/out/crawl
//   ... DIRECTED=dashboard …                       # directed seeds: design mode, column menus, add components
//   ... DIRECTED=rows,chrome,docs,ai,stream,pipeline,stepping,signin   # the other seed groups (signin last: it ends the session)
//
// This replaces crawl.mjs's model, and the reason is measured (COVERAGE-PLAN.md, 2026-09-04): that
// crawler reached every node from a fresh page load and replayed its route — 4,296 navigations at
// 7.6s for 1,910 nodes, 9.1 hours — and 78% of those nodes were dialogs, alerts and menus whose
// state the parent's own affordance loop had ALREADY surveyed and thrown away, keeping one word.
// 54% turned out to be duplicates, each paying a full navigation to be told so.
//
// The reload was buying isolation it could not deliver. A reload gives a fresh CLIENT, not a fresh
// WORLD: anything the server persisted is still there afterwards. What actually isolates a run is
// the read-only guard aborting mutations, and that is armed here exactly as before.
//
// So this is the test as it should have been shaped: click in, observe, pop back out, carry on.
//
//   * Every click's survey is KEPT. It is the child's survey and its signature, so a duplicate is
//     recognised in place, at no cost, instead of after a rebuild.
//   * A dialog, alert or menu is walked and then POPPED — Escape / Cancel / a click on nothing — and
//     the parent's signature is ASSERTED to have come back. A mismatch is a FINDING (recorded under
//     `findings`), not something to paper over with a reload; the cursor is then re-synced by
//     replaying the node's route IN-APP (menu, tab and document clicks, ~1s), which is the only
//     kind of navigation left.
//   * A post-action state (`changed`) is walked too, POSTDEPTH deep, and re-synced the same way. No
//     inverse is pretended: a search that ran has run.
//   * The classifier waits for the screen to SETTLE — two consecutive surveys that agree — instead
//     of sleeping 1.2s. The old crawler's warning stands: it must not return on the FIRST sign of a
//     change, because the app is still reacting; agreement between two reads is a different test.
//   * A page load happens only when `assertSignedIn` fails.
//
// Routes are still RECORDED on every node. They are how a state is reproduced later by the artefact
// pass and how a failure is reported; they are just no longer the transport for every visit.
//
// Output is the same schema crawl.mjs wrote (`nodes`, `affordances`, `summary`, evidence.jsonl), so
// coverage.mjs, name-captions.mjs and the capability diff read it unchanged.
import { appendFile, mkdir, writeFile } from 'node:fs/promises';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { execSync } from 'node:child_process';
import { chromium } from 'playwright';
import { gwt as gwtAdapter } from './compare/adapters/gwt.mjs';
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
import { domCounters, rendererRssKb } from './lib/mem.mjs';
import { clickEditorTab } from './lib/tabs.mjs';
import { setAce } from './lib/fills.mjs';
import { SEED_DATA } from './lib/seed-names.mjs';
import { classifyAlert, isAlertCaption, readTopDialogBody } from './lib/alerts.mjs';
import { PAGER_BUTTONS, feedbackShown, isFilterAction, pagerVerdict, readColumn, readFeedback, readPager, refreshVerdict, sortVerdict, togglesRoundTrip } from './lib/checks.mjs';
import { clearedVerdict, illegalVerdict, planCleared, planIllegal, readFields } from './lib/illegal.mjs';
import { KEY_CONTRACT, ctrlEnterVerdict, enterVerdict, escapeVerdict } from './lib/keys.mjs';
import { dirtyVerdict, nudge, readSave, readTabFields, valueVerdict } from './lib/dirty.mjs';
import {
  dragDialog, dragVerdict, readDialogBox, readSpinners, resizeDialog, resizeVerdict,
  spinnerGestures, spinnerVerdicts,
} from './lib/mouse.mjs';
import { jumpRange, rangeVerdict, readRange } from './lib/range.mjs';
import { contextVerdict, findTargets, rightClick } from './lib/context.mjs';
import { fromSuite } from './lib/paths.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const URL = env('URL', 'http://localhost:8080');
const gwt = gwtAdapter;
const AREA = env('AREA', 'Annotations');
const DOC = env('DOC', '');
const DOCTYPE = env('DOCTYPE', '');
const EXPLORER = (env('EXPLORER', '') || '').split(',').map((x) => x.trim()).filter(Boolean);
const EXPLORER_MULTI = (env('EXPLORER_MULTI', '') || '').split(';')
  .map((g) => g.split('|').map((x) => x.trim()).filter(Boolean)).filter((g) => g.length > 1);
const RECIPES = fromSuite(env('RECIPES', ''));
const RECIPEKIND = env('RECIPEKIND', '');
/** Restrict `recipes:doc` seeds to these document type stems: DOCTYPES="AnalyticRule,Report". */
const DOCTYPES = (env('DOCTYPES', '') || '').split(',').map((x) => x.trim()).filter(Boolean);
/**
 * DIRECTED seeds: a route that establishes a PRECONDITION the source names (door-preconditions.md)
 * and that no blind walk would hit — design mode on a dashboard, a table maximised so its headers
 * are clickable, a component of each type added. The precondition steps are the seed's route, so
 * they do not spend POSTDEPTH (see `postSteps`): a seed is where exploration STARTS.
 */
const DIRECTED = (env('DIRECTED', '') || '').split(',').map((x) => x.trim()).filter(Boolean);
/**
 * How many labelled fields to drive per editor tab. Every one, bounded: a tab with twenty controls
 * would otherwise cost half a minute, and the point is to fire each field's handler once.
 */
const FIELDS = Number(env('FIELDS', '12'));
/** How many right-clicks to spend on each target KIND in a walk. See `probeMouse`. */
const CONTEXTS = Number(env('CONTEXTS', '6'));
/** How many context menus to WALK per target kind. Each one is a node with its items as affordances. */
const CONTEXT_MENUS = Number(env('CONTEXT_MENUS', '2'));
/** How many of a dialog's boxes to press Enter in. See `probeKeys`. */
const ENTER_FIELDS = Number(env('ENTER_FIELDS', '6'));
const DEPTH = env('DEPTH', 'reach');
const POSTDEPTH = Number(env('POSTDEPTH', '1'));
const MAXNODES = Number(env('MAXNODES', '5000'));
const OUT = fromSuite(env('OUT', `${SUITE}/out/crawl`));
/** How long a screen may keep changing before the classifier reads it anyway. */
const SETTLE_MS = Number(env('SETTLE_MS', '1500'));
const VW = 1600;
const VH = 1000;
/** Which walker this shard ran, so a mixed pass can be told apart shard by shard. */
const WALKER_VERSION = (() => {
  try { return execSync('git rev-parse --short HEAD', { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim(); } catch { return '?'; }
})();

if (!['reach', 'dom', 'pixel'].includes(DEPTH)) {
  console.error(`DEPTH must be reach|dom|pixel, got "${DEPTH}"`);
  process.exit(2);
}

// ── Shared with crawl.mjs: labels, signatures, affordance clicking ───────────
const CHROME = new Set([
  'Main Menu', 'Show Menu', 'Expand All', 'Collapse All', 'Locate Current Item', 'Find In Content',
  'Toggle Alerts', 'Ask Stroom AI', 'Filter Types', 'Clear Filter', 'Quick Filter Syntax Help',
  'New', 'Delete',
  // The port's a11y sweep gives the navigation logo and the tab bar's sidebar toggle an
  // `aria-label`; GWT's are untitled Buttons (the sidebar title sits on the inner SVG), so the
  // reader sees them on one side only. Both are chrome on both sides — CurveTabLayoutViewImpl has
  // the same `Hide Sidebar` toggle — and walking the toggle put a post-action state under every node.
  'Stroom', 'Hide sidebar', 'Show sidebar',
]);
const chromeDone = new Set();
// A directed run walks the precondition it was given, not the app's chrome — the main menu, the
// explorer's New/Delete and the AI dock are covered by the area shards.
if (DIRECTED.length) for (const c of CHROME) chromeDone.add(c);
// `Delete Store` is not a mutation the guard blocks — `result-store/v1/destroy` is a client's own
// search lifecycle and is allowed — but on the Search Result Stores dialog its OK destroys the ONE
// store `seed-data.mjs` left, and with the row gone the node's remaining buttons (Store Settings)
// cannot be re-synced to. Confirm/OK is still walked everywhere the guard blocks the delete.
const SELF_DESTRUCTIVE = new Set(['Sign Out', 'Sign Out Other Sessions', 'Delete Store']);
/**
 * Context-menu items that take the browser somewhere else, or start a download.
 *
 * `MyDataGrid`'s menu offers `Follow URL` and `Open In New Tab` on a cell that holds a link: clicking
 * either navigates away or opens a tab the walker is not tracking, and the walk cannot get back to
 * where it was. `Export Table` streams a file. None of them is destructive — the guard would not even
 * be involved — but all of them END the walk's grip on the page, which is worse than a write it can
 * abort. Recorded as affordances and never clicked, exactly as SELF_DESTRUCTIVE is.
 */
const LEAVES_THE_PAGE = new Set(['Follow URL', 'Open In New Tab', 'Open In This Tab', 'Export Table', 'Download']);
/**
 * Clicks whose state change is UNDONE by a known click, not by closing anything. The Ask Stroom AI
 * dock is the case that forced this: it stays open across the whole walk, and its buttons —
 * `Delete All Messages`, `Download`, `Conversation History`, `Configure` — then appeared on every
 * later screen as if the screen offered them. A reload used to hide that. The value is the label to
 * click afterwards; a label mapping to itself is a toggle.
 */
const INVERSE = new Map([
  ['Ask Stroom AI', 'Ask Stroom AI'],
  ['Toggle Alerts', 'Toggle Alerts'],
  ['Turn Auto Refresh On', 'Turn Auto Refresh Off'],
  ['Turn Auto Refresh Off', 'Turn Auto Refresh On'],
  ['Expand All', 'Collapse All'],
  ['Collapse All', 'Expand All'],
  // The label that appears does not share words with the one clicked, so the twin heuristic in
  // walk() cannot find it.
  ['Toggle wrapping', 'Turn Cell Line Wrapping Off'],
  ['Turn Cell Line Wrapping Off', 'Turn Cell Line Wrapping On'],
  ['Turn Cell Line Wrapping On', 'Turn Cell Line Wrapping Off'],
]);
const looseReported = new Set();
/** Checker verdicts that failed (lib/checks.mjs) — printed in the summary, filed by the ledger. */
let checkFails = 0;
/** Buttons the last `add-component` step made appear — the added component's own toolbar. */
let lastAddDelta = null;

const SORT_INDEX = /\s+\d+$/;
/** `column: <name>` is a dashboard table header in design mode — its click opens the column MENU. */
const COLUMN_MENU = 'column: ';
const isColumnHeader = (label) => SORT_INDEX.test(label) && !label.startsWith(COLUMN_MENU);
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

// A menu node is identified by ITS OWN items (`node.items`, the delta when it opened), not by every
// menu item visible at the time — see the descend step in walk(). Everything else uses the survey.
// A menu node carries NO body fingerprint either: a leaf clicked inside a menu chain navigates the
// screen underneath, and the menu — re-opened by route, items identical — must still be the same
// menu. Its identity is what it offers, full stop.
// Column headers are compared WITHOUT their sort index. Sorting relabels `Database` to `Database 1`,
// and re-syncing by route does not un-sort — it is the same open tab — so with the index in the
// signature every sorted screen read as a different node afterwards. The sort is an expected
// change the classifier records on the click; it is not part of what the screen IS.
// A DIALOG is also identified by the screen it was opened from. Two presenters can wear one face:
// Data Volumes' and Index Volumes' `Edit` both read "Edit Volume Group - Default Volume Group" with
// the same buttons and body, so the second was filed as a duplicate and never descended — and its
// own `New` (IndexVolumeEditPresenter) was never clicked. The root of the route (menu leaf, document
// type or name) is the cheapest fact that separates them; an alert is left as it was, since every
// alert is the one AlertPresenter and re-walking each per screen buys nothing.
const rootOf = (node) => {
  const s = (node.route ?? [])[0];
  return !s ? '' : s.via === 'menu' ? `${s.group}>${s.leaf}` : s.via === 'docType' ? s.type : s.via === 'doc' ? s.name : s.via === 'context' ? s.target : s.via;
};
const signatureOf = (node, state) => [
  node.kind === 'dialog' ? `dialog@${rootOf(node)}` : node.kind,
  // A tab INSIDE a dialog is named by the tab as well as the dialog: the Table Settings dialog's
  // Conditional Formatting and Selection Filter tabs both offer Add / Edit / Remove over the same
  // strip, and with only the caption in the signature the second read as the first, unexplored.
  (state.dialogs.join('+') || node.path[node.path.length - 1])
    + (node.kind === 'editor-tab' && state.dialogs.length ? ` / ${node.path[node.path.length - 1]}` : ''),
  [...new Set((node.items ?? state.buttons).map((b) => (isColumnHeader(b) ? columnName(b) : b)))].sort().join('|'),
  node.kind === 'menu' ? '' : (state.fingerprint ?? ''),
].join(' :: ');

const idOf = (node) => [node.kind, ...node.path].join(' / ');
const slugOf = (node) => idOf(node).replace(/[^a-z0-9]+/gi, '-').toLowerCase().slice(0, 120);

// ── The browser ─────────────────────────────────────────────────────────────
const browser = await chromium.launch({ headless: env('HEADLESS', '1') !== '0' });
const page = await browser.newPage({ viewport: { width: VW, height: VH } });
const guard = attachReadOnlyGuard(page, { enabled: true });
gwt.baseUrl = URL;
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
const creds = credentials();
await gwt.signInFully(page, creds);
await mkdir(OUT, { recursive: true });
if (DEPTH !== 'reach') await mkdir(join(OUT, 'evidence'), { recursive: true });

const menuIsOpen = () => page.evaluate(() => [...document.querySelectorAll('.menuItem-outer')]
  .some((e) => e.offsetWidth > 0 && e.offsetHeight > 0)).catch(() => false);
// ── The page can DIE under us, and a dead page does not fail: it WAITS ───────
// docs7c's renderer reached 5.1 GB in the Pipeline editor and the kernel OOM-killed it at 21:51:58.
// Playwright did not reject the locator that was waiting on it, so the walk slept for 1h37m — 1:56
// of CPU across 4.5 hours — until a human looked. Both of these are terminal: write the checkpoint
// (every root writes one anyway) and leave, so the LANE moves on to its next shard instead of
// holding a browser open all night for nothing.
/** Minutes with no new node before the walk is presumed hung. A node averages ~25s; 15 min is pathological. */
const STALL_MIN = Number(env('STALL_MIN', '15'));
let closing = false;
let lastProgressAt = Date.now();
let died = '';
/** Until the walk proper starts, `nodes` is in its TDZ and there is nothing to checkpoint. */
let walkStarted = false;
async function die(why) {
  if (died || closing) return;
  died = why;
  console.error(`\n!! the walk is STOPPING: ${why}`);
  if (!walkStarted) { console.error('   nothing walked yet — no checkpoint'); process.exit(3); }
  await writeOutput(false)
    .then((s) => console.error(`   checkpoint written to ${OUT} (complete=false) — ${s.nodes} node(s)`))
    .catch((e) => console.error(`   no checkpoint: ${e.message}`));
  process.exit(3);
}
page.on('crash', () => { die('the renderer crashed (OOM-killed?)'); });
browser.on('disconnected', () => { die('the browser disconnected'); });
/**
 * MEMLOG=1 records what the PAGE costs, per node, to `memory.jsonl`. docs7c's renderer reached
 * 5.1 GB and nothing in the suite could see it coming — a walk reports nodes and affordances, never
 * the cost of holding them. The DOM counters say whether growth is retained structure or slack.
 */
const MEMLOG = env('MEMLOG', '0') === '1';
let memCdp = null;
if (MEMLOG) memCdp = await page.context().newCDPSession(page).catch(() => null);
let memPeakKb = 0;
async function memSample(node) {
  if (!MEMLOG || !memCdp) return;
  const rssKb = rendererRssKb();
  const dom = await domCounters(memCdp);
  memPeakKb = Math.max(memPeakKb, rssKb);
  await appendFile(join(OUT, 'memory.jsonl'), `${JSON.stringify({
    n: nodes.length, kind: node?.kind, path: node?.path, rssMb: Math.round(rssKb / 1024),
    nodes: dom.nodes, listeners: dom.listeners, at: Date.now() - runStartedAt })}\n`).catch(() => {});
  // A renderer this big is minutes from the OOM killer on a 32 GB box shared with a GWT build.
  if (rssKb > 3_000_000 && rssKb === memPeakKb) {
    console.log(`      (!! renderer at ${Math.round(rssKb / 1024)} MB — ${dom.nodes} DOM nodes, ${dom.listeners} listeners)`);
  }
}

const stallTimer = setInterval(() => {
  const idleMin = (Date.now() - lastProgressAt) / 60_000;
  if (idleMin >= STALL_MIN) die(`no new node for ${idleMin.toFixed(0)} min (STALL_MIN=${STALL_MIN})`);
}, 30_000);
stallTimer.unref();


/** `link: <text>` is a hyperlink/label the button reader does not see — the sign-in page's `Forgot password?`. */
const LINK = 'link: ';
/** `titled: <t>` is a clickable block the button reader does not see — the annotation editor's setting
 *  blocks (`SettingBlock`, `title="Change Retention Period"`) open their choosers on a click. */
const TITLED = 'titled: ';
/** `entry: comment` — a MOUSEDOWN on an annotation history entry (`[entryType]`, COMMENT = 4) opens
 *  its Edit Entry / Delete Entry menu (`AnnotationEditPresenter.showEntryEditMenu`). */
const ENTRY = 'entry: ';
const ENTRY_TYPES = { comment: '4' };
/** `selector: <css>` — a clickable whose TEXT is data, so no label can name it: the stepping toolbar's
 *  location label (`.stepLocationLink`, `[<metaId>:<part>:<record>]`) opens `Set Location` on a click. */
const SELECTOR = 'selector: ';
/** The clickable-label classes the survey offers as `selector:` affordances (see survey()). */
const CLICKABLE_LABELS = ['.itemNavigator-label', '.characterNavigator-label', '.stepLocationLink'];
async function clickAffordance(label, timeout = 3000) {
  if (label.startsWith(COLUMN_MENU)) return clickDesignHeader(label.slice(COLUMN_MENU.length));
  if (label.startsWith(SELECTOR)) {
    const l = page.locator(label.slice(SELECTOR.length)).filter({ visible: true });
    if (!(await l.count().catch(() => 0))) return false;
    await l.first().click({ timeout });
    return true;
  }
  if (label.startsWith(TITLED)) {
    const box = await page.evaluate((t) => {
      const el = [...document.querySelectorAll(`[title="${t}"]`)].find((e) => e.offsetWidth > 0 && !e.matches('button'));
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: r.x + Math.min(r.width / 2, 40), y: r.y + Math.min(r.height / 2, 12) };
    }, label.slice(TITLED.length));
    if (!box) return false;
    await page.mouse.click(box.x, box.y);
    return true;
  }
  if (label.startsWith(ENTRY)) {
    const box = await page.evaluate((type) => {
      const el = [...document.querySelectorAll(`[entryType="${type}"]`)].find((e) => e.offsetWidth > 0);
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: r.x + Math.min(r.width / 2, 60), y: r.y + Math.min(r.height / 2, 10) };
    }, ENTRY_TYPES[label.slice(ENTRY.length)] ?? label.slice(ENTRY.length));
    if (!box) return false;
    await page.mouse.move(box.x, box.y);
    await page.mouse.down();
    await page.mouse.up();
    return true;
  }
  if (label.startsWith(LINK)) {
    const text = label.slice(LINK.length);
    const l = page.locator('a, .gwt-Hyperlink, .gwt-Anchor, [class*=Link]').filter({ hasText: new RegExp(`^\\s*${text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`) });
    if (!(await l.count().catch(() => 0))) return false;
    await l.first().click({ timeout });
    return true;
  }
  if (await menuIsOpen()) {
    try {
      if (await clickMenuItem(page, label)) return true;
    } catch { /* not a menu item — fall through */ }
  }
  // The label is DATA, not a pattern: `From (Type)` and `To (Name)` are grid headers on the
  // Dependencies screen, and unescaped they broke the button regex and were reported `vanished` —
  // 17 times on one shard. A header without a sort index is still a header, so the header locator
  // is tried for every label, LAST, where a real button of the same name still wins.
  // A chrome label that a SCREEN also uses — `New`, `Delete` — must click the screen's button, not
  // the explorer's, which comes first in the DOM: the Pathways list's `New` (→ PathwayEditPresenter)
  // and every pager's `New` were reaching the explorer's create dialog instead. This is the
  // `create` class in door-preconditions.md.
  if (CHROME.has(label)) {
    const box = await page.evaluate((wanted) => {
      const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
      const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
      const scope = popups.length ? popups[popups.length - 1] : document.body;
      const el = [...scope.querySelectorAll('button, [role=button], .Button')].find((b) => b.offsetWidth > 0 && !b.disabled
        && (norm(b.getAttribute('title')) === wanted || norm(b.getAttribute('aria-label')) === wanted)
        && !b.closest('.navigationTree, .explorerTree, [class*=navigation], [class*=explorer]'));
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
    }, label).catch(() => null);
    if (box) { await page.mouse.click(box.x, box.y); return true; }
  }
  const esc = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  // The TOPMOST dialog first. Dialogs stack — a component's Settings holds `Add New Rule`, and both
  // have an OK — and `.first()` over the whole page is the one BEHIND, whose click the front dialog
  // intercepts: every nested dialog's OK and Cancel read as `did not accept a click within 3s`.
  const popups = page.locator('.dialog-popup, .resizableDialog-popup').filter({ visible: true });
  const top = (await popups.count().catch(() => 0)) ? popups.last() : null;
  const within = (root) => [
    ...(isColumnHeader(label)
      ? [root.locator('.dataGridHeader, [role=columnheader], th').filter({ hasText: columnName(label) })]
      : []),
    root.locator(`button[title="${label}"]:not([disabled])`),
    root.locator(`button[aria-label="${label}"]:not([disabled])`),
    root.locator('.Button, [role=button]').filter({ hasText: new RegExp(`^\\s*(${esc})(\\s*\\1)?\\s*$`) }),
    ...(isColumnHeader(label) ? [] : [root.locator('.dataGridHeader, [role=columnheader], th')
      .filter({ hasText: new RegExp(`^\\s*${esc}(\\s+\\d+)?\\s*$`) })]),
  ];
  const candidates = [...(top ? within(top) : []), ...within(page)];
  for (const c of candidates) {
    if (await c.count().then((n) => n > 0).catch(() => false)) {
      await c.first().click({ timeout });
      return true;
    }
  }
  return false;
}

/**
 * A dashboard table's column headers, in design mode, that a click can actually reach. The column
 * menu (Rename, Expression, Sort, Group, Format, Filter, …) is opened by `MyDataGrid`'s native
 * preview handler on MOUSEUP over a `th`, and that handler is only ATTACHED by a MOUSEMOVE over the
 * heading first; `TablePresenter.setDesignMode` is what turns header selection on. Only headers
 * that `elementFromPoint` returns are offered: in the seed dashboard the Table pane is squashed to
 * ~0px and its `th` sits BEHIND the next pane's tab bar, so a blind click there opened that tab's
 * menu instead — the seed maximises the pane first (`tab-menu` step).
 */
/**
 * Where a seed's column menus live. The dashboard's need design mode and sit in `.dashboard-panel`;
 * a Query document's result table (`QueryResultTablePresenter`) has `allowHeaderSelection` on by
 * default — MyDataGrid's default — so its header menu (Filter, Format, Conditional Formatting, …)
 * opens on any results table with no mode to enter.
 */
let columnScope = { selector: '.dashboard-panel .dataGridWidget th, .dashboard-panel [role=columnheader]', design: true };
async function readDesignHeaders() {
  return page.evaluate(({ selector, design }) => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    if (design && !document.querySelector('button[title="Exit Design Mode"]')) return [];
    return [...document.querySelectorAll(selector)]
      .filter((th) => th.offsetWidth > 30)
      .map((th) => {
        const r = th.getBoundingClientRect();
        const hit = document.elementFromPoint(r.x + 20, r.y + r.height / 2);
        return { name: norm(th.querySelector('.column-label')?.textContent ?? th.textContent), ok: th.contains(hit) };
      })
      .filter((h) => h.name && h.ok).map((h) => h.name);
  }, columnScope).catch(() => []);
}

async function clickDesignHeader(name) {
  const box = await page.evaluate(([wanted, selector]) => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const th = [...document.querySelectorAll(selector)]
      .find((e) => e.offsetWidth > 30 && norm(e.querySelector('.column-label')?.textContent ?? e.textContent) === wanted);
    if (!th) return null;
    const r = th.getBoundingClientRect();
    const hit = document.elementFromPoint(r.x + 20, r.y + r.height / 2);
    return th.contains(hit) ? { x: r.x + 20, y: r.y + r.height / 2 } : null;
  }, [name, columnScope.selector]);
  if (!box) return false;
  // Faithful to the widget: arm the preview handler with a move over the heading, then a primary
  // press and release without moving (a drag threshold exceeded would start a column MOVE).
  await page.mouse.move(box.x, box.y);
  await page.waitForTimeout(120);
  await page.mouse.move(box.x + 4, box.y);
  await page.waitForTimeout(120);
  await page.mouse.down();
  await page.waitForTimeout(60);
  await page.mouse.up();
  return true;
}

/** Centre of the dashboard tab bar that holds the component tab labelled `tab`, or null. */
async function tabBarOf(tab) {
  return page.evaluate((wanted) => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const lab = [...document.querySelectorAll('.linkTab-label')]
      .find((e) => e.offsetWidth > 0 && norm(e.textContent) === wanted);
    const bar = lab?.closest('.tabLayout-barOuter');
    if (!bar) return null;
    const r = bar.getBoundingClientRect();
    const settings = [...bar.querySelectorAll('button[title="Settings"]')].find((b) => b.offsetWidth > 0);
    const s = settings?.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2,
      settings: s ? { x: s.x + s.width / 2, y: s.y + s.height / 2 } : null };
  }, tab).catch(() => null);
}

/** Identical to crawl.mjs's survey, on purpose: the signature must mean the same thing. */
async function survey() {
  const scope = await page.evaluate(() => {
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')]
      .filter((e) => e.offsetWidth > 0);
    if (!popups.length) return 'body';
    popups[popups.length - 1].setAttribute('data-crawl-scope', '1');
    return '[data-crawl-scope="1"]';
  });
  const menuItems = (await readVisibleMenuItems(page).catch(() => []))
    .filter((i) => (i.label ?? i.text));
  const menu = menuItems.filter((i) => i.enabled !== false).map((i) => i.label ?? i.text);
  const menuDisabled = menuItems.filter((i) => i.enabled === false).map((i) => i.label ?? i.text);
  const buttons = menuItems.length ? [] : ((await readButtons(page, scope)) ?? []);
  // Sortable grid headers are affordances on both UIs, but only GWT's happen to be visible to the
  // button reader: CellTable gives a sortable `th` `role="button"`, the port's DataGrid gives its
  // header cell the ARIA role a header should have (`columnheader`, `aria-sort`). Read the port's
  // here under the same label the reader gives GWT's (`Created 1` — text plus sort index), so a
  // header sort is one affordance on both sides rather than a GWT-only one.
  if (!menuItems.length) {
    const headers = await page.evaluate((sel) => {
      const root = document.querySelector(sel) ?? document.body;
      const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
      return [...root.querySelectorAll('[role=columnheader][aria-sort], [role=columnheader].data-grid__header-cell--sortable')]
        .filter((e) => e.offsetWidth > 0 && !e.matches('[role=button]'))
        .map((e) => norm(e.textContent)).filter(Boolean);
    }, scope).catch(() => []);
    for (const label of headers) if (!buttons.some((b) => b.label === label)) buttons.push({ kind: 'header', label, enabled: true, visible: true });
  }
  // Labels that OPEN something, which no markup advertises: a GWT `Label` with a click handler is a
  // div. The data preview's item pager (`.itemNavigator-label`, "Part 1 of 1" → ItemSelection-
  // Presenter), the source view's character pager (`.characterNavigator-label` → CharacterRange-
  // SelectionPresenter) and the stepping toolbar's location (`.stepLocationLink` → StepLocation-
  // Presenter). Offered as `selector:` affordances whenever one is visible in scope.
  if (!menuItems.length) {
    const labels = await page.evaluate(([sel, classes]) => {
      const root = document.querySelector(sel) ?? document.body;
      return classes.filter((cls) => [...root.querySelectorAll(cls)].some((e) => e.offsetWidth > 0 && e.offsetHeight > 0));
    }, [scope, CLICKABLE_LABELS]).catch(() => []);
    for (const cls of labels) buttons.push({ kind: 'label', label: `${SELECTOR}${cls}`, enabled: true, visible: true });
  }
  const grids = await page.evaluate(() => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    // Both grid vocabularies: GWT's `.dataGridWidget` table and the port's ARIA grid (`role=grid`,
    // whose header is `[role=row][aria-rowindex="1"]` and whose data rows follow it).
    return [...document.querySelectorAll('.dataGridWidget, [role=grid]')]
      .filter((g) => g.offsetWidth > 0)
      .map((g) => {
        const rows = [...g.querySelectorAll('tbody tr, [role=row]:not([aria-rowindex="1"])')];
        // `filled`: rows with text. An EMPTY grid still renders one placeholder row, so `rows`
        // alone cannot say whether a client-side Add put anything in it.
        return { rows: rows.length, filled: rows.filter((r) => norm(r.textContent)).length,
          head: rows.slice(0, 3).map((r) => norm(r.textContent).slice(0, 80)) };
      });
  }).catch(() => []);
  const fingerprint = await page.evaluate((sel) => {
    const root = document.querySelector(sel) ?? document.body;
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    // `.linkTab-label` only: those are an editor's own sub-tabs. The CONTENT tab strip
    // (`.curveTab-label`) is the application's history — every screen visited adds one — and a
    // walk that never reloads accumulates it, so including it made every screen-level node's
    // signature drift with whatever had been opened before. crawl.mjs never saw this because each
    // of its nodes began from a reload with an empty strip.
    const tabs = [...root.querySelectorAll('.linkTab-label')]
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

/** Everything the classifier compares, as one string — so "two reads agree" is one comparison. */
const stateKey = (s) => JSON.stringify([s.dialogs, s.buttons, s.menu, s.fingerprint, s.grids]);

/**
 * Survey once the screen has stopped changing: two consecutive reads that agree, at least one poll
 * apart. Replaces crawl.mjs's flat 1.2s — which was 72 minutes of a 9-hour pass — without making the
 * mistake it warned about, which was returning on the FIRST sign of change while the app was still
 * reacting. Agreement between reads is not that: a menu half-populated at read one is populated at
 * read two, and the two differ, so we read again.
 */
async function settledSurvey({ budget = SETTLE_MS, poll = 250, lead = 500 } = {}) {
  const t0 = Date.now();
  // A LEAD before the first read. Two reads that agree because neither has seen the change yet is
  // the same mistake as returning on the first sign of one: 14 column sorts read `nothing` on the
  // Monitoring comparison because the grid re-rendered after both reads. The lead is the minimum
  // the application needs to have started reacting; agreement then says it has finished.
  await page.waitForTimeout(lead);
  let last = await survey();
  let lastKey = stateKey(last);
  for (;;) {
    await page.waitForTimeout(poll);
    const next = await survey();
    const key = stateKey(next);
    if (key === lastKey) return next;
    last = next;
    lastKey = key;
    if (Date.now() - t0 >= budget) return last;
  }
}

/** Classify what a click did, given the survey before it. Returns the outcome AND the after-survey. */
function classifyDelta(before, after) {
  // A dialog that WENT AWAY is `closed`, not `changed`. Its OK/Cancel/Close removed the dialog and
  // the body's chrome came into scope, which read as `+Main Menu, Quick Filter Syntax Help, …` —
  // 67 of 416 "changed" outcomes on the recipes:menu shard, each paying a route re-sync to reopen a
  // dialog whose last affordance had just been clicked.
  if (after.dialogs.length < before.dialogs.length) {
    return { outcome: 'closed', detail: `closed ${before.dialogs.filter((d) => !after.dialogs.includes(d)).join(', ')}` };
  }
  const newDialogs = after.dialogs.filter((d) => !before.dialogs.includes(d));
  if (newDialogs.some((d) => /alert/i.test(d))) return { outcome: 'alert', detail: newDialogs.join(', ') };
  if (newDialogs.length) return { outcome: 'dialog', detail: newDialogs.join(', ') };
  const grew = after.buttons.filter((b) => !before.buttons.includes(b));
  if (after.menu && (!before.menu || grew.length)) {
    return { outcome: 'menu', detail: (grew.length ? grew : after.buttons).slice(0, 6).join(', ') };
  }
  if (grew.length) return { outcome: 'changed', detail: `+${grew.slice(0, 4).join(', ')}` };
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

// ── Navigation: in-app only ─────────────────────────────────────────────────
let reloads = 0;
let signedOutRecoveries = 0;
/** "Discard changes?" prompts answered while closing tabs — a count of documents the walk dirtied. */
let discardedChanges = 0;
const confirmTexts = new Set();
let resyncs = 0;
let navSteps = 0;

/** The one place a page load is allowed: the session is gone. */
async function reloadAndSignIn() {
  reloads += 1;
  await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await gwt.waitForApp(page);
  try {
    await gwt.assertSignedIn(page, creds);
  } catch {
    signedOutRecoveries += 1;
    await gwt.signInFully(page, creds);
    await gwt.waitForApp(page);
  }
  await settleExplorer(page, { label: 'reload' }).catch(() => {});
}

/**
 * An open editor's sub-tabs — `.linkTab-label` only. lib/tabs.mjs's readEditorTabs also reads the
 * CONTENT strip and filters out `Welcome` and the document's own name, which was enough for a
 * recorder that opened one document per fresh page; in a walk the strip holds every screen visited
 * so far, and all of them would be reported as sub-tabs of whichever editor is open.
 */
/**
 * The visible sub-tab labels. `inDialog` reads only the topmost popup's — a dialog's tabs (a dashboard
 * component's Settings: Basic / Conditional Formatting / Selection Filter) sit over a screen that may
 * have its own, and a screen's read must not include a popup's.
 */
async function readSubTabs({ inDialog = false } = {}) {
  return page.evaluate((inDialog) => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const top = popups[popups.length - 1];
    return [...document.querySelectorAll('.linkTab-label')]
      .filter((e) => e.offsetWidth > 0 && e.offsetHeight > 0)
      .filter((e) => (inDialog ? top && top.contains(e) : !popups.some((p) => p.contains(e))))
      .map((e) => norm(e.textContent)).filter(Boolean);
  }, inDialog);
}

/** The content tab strip's labels, in order. */
const contentTabs = () => page.evaluate(() => [...document.querySelectorAll('.curveTab')]
  .filter((t) => t.offsetWidth > 0)
  .map((t) => (t.querySelector('.curveTab-label') || t).textContent.replace(/\s+/g, ' ').trim())
  .filter(Boolean)).catch(() => []);

/**
 * Close EVERY content tab bar Welcome — the client-side "fresh" a reload used to deliver, without
 * the reload. A route that runs through a menu chain opens the LAST leaf's screen, not the first's,
 * so closing the route's first tab left `Properties` open with its paging intact and `Data Receipt
 * Rules` sitting under every later root. Best effort: the tab's own `Close All`, then one by one.
 */
/**
 * Close any DIRTY content tab before opening another document.
 *
 * A tab the field driver has touched is marked `*`, and with one of those open the next document's
 * route would not switch to it — the walker then tried the new document's sub-tabs against whatever
 * was still in front and recorded `no sub-tab "Data Preview" — tabs [Example Solr index*]`. Nine
 * nodes a docs shard, and it doubled (5 -> 9) the moment the driver started filling every field
 * rather than one, so it is a cost of that change and worth paying back.
 *
 * `closeAllContentTabs` already answers the discard prompt once per dirty document, so this is only
 * about calling it at the right moment: before the switch, not after it has failed.
 */
async function discardDirtyTabs() {
  const open = await contentTabs();
  if (!open.some((t) => /\*\s*$/.test(t))) return;
  await closeAllContentTabs();
}

async function closeAllContentTabs() {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const open = (await contentTabs()).filter((t) => t && !/Welcome/.test(t));
    if (!open.length) return true;
    if (attempt === 0 && await rightClickByText(page, '.curveTab', open[0]).catch(() => false)) {
      const before = (await contentTabs()).length;
      if (await clickMenuItem(page, 'Close All').catch(() => false)) {
        // Close All closes tab by tab and may ask once per dirty document.
        let rounds = 0;
        while (rounds < 6 && await settleTabClose(before, 'Close All')) {
          rounds += 1;
          if (!(await contentTabs()).some((t) => t && !/Welcome/.test(t))) break;
        }
      } else {
        await dismissMenus(page).catch(() => {});
      }
      continue;
    }
    for (const t of open) await closeContentTab(t);
  }
  let survived = (await contentTabs()).filter((t) => t && !/Welcome/.test(t));
  // Whatever the timing above, a Confirm that is UP here is the walker's own and gets answered.
  if (survived.length && (await readDialogs(page).catch(() => [])).some((d) => /confirm/i.test(d.caption ?? ''))) {
    if (await clickDialogButton(/confirm/, /^(ok|yes)$/)) {
      discardedChanges += 1;
      await waitUntil(page, async () => !(await readDialogs(page)).some((d) => /confirm/i.test(d.caption ?? '')) || null,
        { budget: 1_500, label: 'discard-late', kind: 'restore' }).catch(() => {});
      await clearOverlays();
      survived = (await contentTabs()).filter((t) => t && !/Welcome/.test(t));
      if (survived.length) { for (const t of survived) { await closeContentTab(t); await discardIfAsked({ wait: 1_000 }); } survived = (await contentTabs()).filter((t) => t && !/Welcome/.test(t)); }
    }
  }
  if (survived.length && !tabsSurvivedReported) {
    tabsSurvivedReported = true;
    console.log(`      (tabs survived Close All: ${await whereAmI()})`);
    // What readDialogs — the reader discardIfAsked polls — sees, against a raw DOM read of the
    // Confirm: caption, root class, buttons, and why the title might fail the visibility test.
    const seen = await readDialogs(page).catch((e) => `threw: ${e.message.slice(0, 80)}`);
    console.log(`      (readDialogs sees: ${typeof seen === 'string' ? seen : JSON.stringify(seen.map((d) => ({ caption: d.caption, h: d.heightPx, buttons: d.buttons.map((b) => b.label) })))})`);
    const raw = await page.evaluate(() => {
      const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
      return [...document.querySelectorAll('.dialog-titleText')].map((t) => {
        const r = t.getBoundingClientRect(); const st = getComputedStyle(t);
        const root = t.closest('[role=dialog]') || t.closest('.dialog-background, .resizableDialog-popup, .dialog-popup, .popupContent') || t.parentElement;
        return { caption: norm(t.textContent), w: Math.round(r.width), h: Math.round(r.height), vis: st.visibility, op: st.opacity, disp: st.display,
          root: root.className.toString().slice(0, 60),
          buttons: [...root.querySelectorAll('button, [role=button], .Button')].map((b) => `${norm(b.textContent || b.getAttribute('title'))}${b.offsetWidth ? '' : '(hidden)'}`) };
      });
    }).catch((e) => `threw: ${e.message.slice(0, 80)}`);
    console.log(`      (raw dialogs: ${JSON.stringify(raw)})`);
  }
  return !survived.length;
}
let tabsSurvivedReported = false;

/**
 * Closing a document the walk left DIRTY asks "discard changes?" — a `Confirm` dialog. closeDialogs
 * never presses OK, which is right for a dialog the application raised and wrong for the one the
 * walker itself provoked by closing the tab: answered with Cancel, the tab stays, the next root
 * opens under the same Confirm, and 22 of 26 document editors on the recipes:doc shard were
 * recorded as duplicates of it. Under the read-only guard nothing was ever going to persist, so
 * discarding IS the reset. Only called from the tab-closing path, on purpose.
 */
let discardTraces = 0;
async function discardIfAsked({ wait = 2_000 } = {}) {
  const trace = discardTraces < 8;
  if (trace) {
    discardTraces += 1;
    const at = (await readDialogs(page).catch(() => [])).map((d) => d.caption).join(',');
    console.log(`      (discardIfAsked #${discardTraces}: entry dialogs [${at}], waiting ${wait}ms)`);
  }
  for (let i = 0; i < 3; i += 1) {
    // The prompt arrives ASYNCHRONOUSLY after the close: a read at t=0 found nothing, the walker
    // moved on, and 22 editors were opened under it. Wait for it, briefly, before deciding.
    const found = await waitUntil(page, async () => (await readDialogs(page)).find((d) => /confirm/i.test(d.caption ?? '')) || null,
      { budget: i === 0 ? wait : 300, label: 'confirm-appear', kind: 'restore' });
    const confirm = found.value;
    if (trace) console.log(`      (discardIfAsked: found=${!!confirm} after ${found.ms}ms)`);
    if (!confirm) return;
    // Say what it asked, once per distinct text, so the cause is named rather than inferred.
    const body = await page.evaluate(`(() => {
      const norm = (t) => String(t ?? '').replace(/\\s+/g, ' ').trim();
      const t = [...document.querySelectorAll('.dialog-titleText')].filter((e) => e.offsetWidth > 0 && /^confirm$/i.test(norm(e.textContent))).pop();
      if (!t) return '';
      const root = t.closest('[role=dialog]') || t.closest('.dialog-background, .resizableDialog-popup, .dialog-popup, .popupContent') || t.parentElement;
      const c = root.cloneNode(true); c.querySelectorAll('svg, style, .dialog-titleText, button, .Button').forEach((x) => x.remove());
      return norm(c.textContent).slice(0, 160);
    })()`).catch(() => '');
    if (!confirmTexts.has(body)) { confirmTexts.add(body); console.log(`      (answered Confirm: "${body}")`); }
    const clicked = await clickDialogButton(/confirm/, /^(ok|yes)$/);
    if (trace) console.log(`      (discardIfAsked: clicked=${clicked})`);
    if (!clicked) { console.log(`      (Confirm up but no OK/Yes button found — ${await whereAmI()})`); return; }
    discardedChanges += 1;
    await waitUntil(page, async () => !(await readDialogs(page)).some((d) => /^confirm$/i.test(d.caption ?? '')) || null,
      { budget: 1_500, label: 'discard', kind: 'restore' }).catch(() => {});
  }
}

/**
 * Close the content tab whose label is `label`, via its own context menu, and WAIT for the outcome:
 * the tab goes, or the "unsaved changes" Confirm appears and is answered. No Escape in between —
 * `dismissMenus` after the Close click was cancelling the prompt when it arrived early and missing
 * it when it arrived late (1–3s under load), which is how Administration's re-syncs came to cost
 * 20s each with zero Confirms answered.
 */
async function closeContentTab(label) {
  if (!label) return false;
  const before = (await contentTabs()).length;
  if (!(await rightClickByText(page, '.curveTab', label).catch(() => false))) return false;
  if (!(await clickMenuItem(page, 'Close').catch(() => false))) { await dismissMenus(page).catch(() => {}); return false; }
  return settleTabClose(before, 'Close');
}

/** After a close was asked for: the strip shrinks, or a Confirm is up and gets answered. */
async function settleTabClose(before, what) {
  for (let round = 0; round < 3; round += 1) {
    const r = await waitUntil(page, async () => {
      if ((await contentTabs()).length < before) return 'closed';
      if ((await readDialogs(page)).some((d) => /confirm/i.test(d.caption ?? ''))) return 'confirm';
      return null;
    }, { budget: 5_000, label: `tab-${what}`, kind: 'restore' });
    if (r.value === 'closed') return true;
    if (r.value !== 'confirm') return false;
    if (!confirmTexts.size) console.log(`      (answered Confirm on ${what})`);
    confirmTexts.add(what);
    if (!(await clickDialogButton(/confirm/, /^(ok|yes)$/))) return false;
    discardedChanges += 1;
    await waitUntil(page, async () => !(await readDialogs(page)).some((d) => /confirm/i.test(d.caption ?? '')) || null,
      { budget: 2_000, label: 'discard', kind: 'restore' }).catch(() => {});
  }
  return (await contentTabs()).length < before;
}


/**
 * A REAL click on a button of the topmost dialog whose caption matches — resolved in-page to a
 * point, then `page.mouse.click`. A synthetic `.click()` from `evaluate` is ignored by GWT's Button
 * (the same trap structure.mjs documents for explorer rows), which is why the walker's own
 * "discard changes?" Confirm stood unanswered through three attempts while `dialogs [Confirm]` sat
 * in the diagnostic. Returns false when no such dialog or button is visible.
 */
async function clickDialogButton(captionRe, labelRe) {
  const pt = await page.evaluate(([capSrc, labSrc]) => {
    const cap = new RegExp(capSrc, 'i');
    const lab = new RegExp(labSrc, 'i');
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const undouble = (s) => { const t = norm(s); const h = t.slice(0, t.length / 2); if (t.length % 2 === 0 && h === t.slice(t.length / 2)) return h.trim(); const m = t.match(/^(.+) \1$/); return m ? m[1] : t; };
    const titles = [...document.querySelectorAll('.dialog-titleText')].filter((e) => e.offsetWidth > 0 && cap.test(norm(e.textContent)));
    const t = titles[titles.length - 1];
    if (!t) return null;
    const root = t.closest('[role=dialog]') || t.closest('.dialog-background, .resizableDialog-popup, .dialog-popup, .popupContent') || t.parentElement;
    const btn = [...root.querySelectorAll('button, [role=button], .Button')]
      .filter((b) => b.offsetWidth > 0 && b.offsetHeight > 0)
      .find((b) => lab.test(undouble(b.textContent || b.getAttribute('title') || '')));
    if (!btn) return null;
    btn.scrollIntoView({ block: 'center' });
    const r = btn.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
  }, [captionRe.source, labelRe.source]).catch(() => null);
  if (!pt) return false;
  await page.mouse.click(pt.x, pt.y);
  return true;
}

/** Where is the cursor, really? For the error message of a step that could not find its target. */
async function whereAmI() {
  return page.evaluate(() => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const tabs = [...document.querySelectorAll('.curveTab')].filter((t) => t.offsetWidth > 0)
      .map((t) => `${norm((t.querySelector('.curveTab-label') || t).textContent)}${/curveTab-selected/.test(t.className) ? '*' : ''}`);
    const sub = [...document.querySelectorAll('.linkTab-label')].filter((e) => e.offsetWidth > 0).map((e) => norm(e.textContent));
    const dialogs = [...document.querySelectorAll('.dialog-titleText')].filter((e) => e.offsetWidth > 0).map((e) => norm(e.textContent));
    return `tabs [${tabs.join(' | ')}] sub-tabs [${sub.join(', ')}] dialogs [${dialogs.join(', ')}]`;
  }).catch(() => '(unknown)');
}

/**
 * A document is open when ITS EDITOR'S sub-tab bar exists — `.linkTab-label`, not any tab label.
 * The content strip's `Welcome` tab satisfied the old test instantly, so after a fast re-sync the
 * walker clicked sub-tabs of an editor that had not rendered yet: nine of Analytic Rule's ten tabs
 * came back "no sub-tab". crawl.mjs got away with the same test because its reload made every open
 * slow enough to lose the race.
 */
async function waitForDocOpen() {
  const r = await waitUntil(page, () => page.evaluate(() => [...document.querySelectorAll('.linkTab-label')]
    .some((e) => e.offsetWidth > 0)), { budget: 6_000, label: 'doc-open', kind: 'editor' }).catch(() => ({ ok: false }));
  if (!r.ok) console.log(`      (doc-open: no editor sub-tab bar after 6s — ${await whereAmI()})`);
}

async function expandExplorer() {
  await page.locator('button[title="Expand All"]').first().click({ timeout: 10_000 }).catch(() => {});
  await settleExplorer(page, { label: 'expand', budget: 5_000 }).catch(() => {});
}

async function openDoc(name, type) {
  await expandExplorer();
  await gwt.openDocByName(page, name, type);
  await activateContentTab(name);
  await waitForDocOpen();
}

/**
 * Make the document's content tab the ACTIVE one. Opening a document that is already open does not
 * bring its tab to the front — after a re-sync the diagnostic read
 * `tabs [Seed Analytic Rule | * Data Retention]` with Data Retention selected, so the editor's
 * sub-tab bar was there and hidden, and every sub-tab step failed with "no sub-tab".
 */
async function activateContentTab(label) {
  if (!label) return;
  const clicked = await page.evaluate((wanted) => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim().replace(/^\* /, '');
    const tabs = [...document.querySelectorAll('.curveTab')].filter((t) => t.offsetWidth > 0);
    const mine = tabs.find((t) => norm((t.querySelector('.curveTab-label') || t).textContent) === wanted);
    if (!mine || /curveTab-selected/.test(mine.className)) return !!mine;
    (mine.querySelector('.curveTab-label') || mine).click();
    return true;
  }, label).catch(() => false);
  if (clicked) await page.waitForTimeout(300);
}

/** Clear whatever is up — dialogs, menus — without touching the screen underneath. */
async function clearOverlays() {
  await closeDialogs(page).catch(() => {});
  await dismissMenus(page).catch(() => {});
  await waitUntil(page, async () => (await readDialogs(page)).length === 0 && !(await menuIsOpen()),
    { budget: 3_000, label: 'clear', kind: 'restore' }).catch(() => {});
}

/**
 * Close ONLY what the last click opened, so the thing underneath survives to be checked.
 *
 * `closeDialogs` closes every dialog, which is right for a reset and wrong for a pop: a dialog opened
 * from a dialog must be popped without taking its parent with it. So a dialog/alert child gets one
 * Escape, then — if the count did not drop — the topmost dialog's own Cancel/Close (never OK). A
 * menu child gets the menu dismissed. Returns true when the count of the popped kind went down.
 */
async function closeTop(childKind) {
  if (childKind === 'menu') {
    await dismissMenus(page).catch(() => {});
    const gone = await waitUntil(page, async () => !(await menuIsOpen()), { budget: 1_500, label: 'pop-menu', kind: 'restore' });
    return gone.ok;
  }
  const n0 = (await readDialogs(page).catch(() => [])).length;
  if (!n0) return true;
  // The topmost dialog's OWN Cancel/Close first, Escape second. Escape is not guaranteed to take
  // only the top popup, and a pop that takes the parent too fails its signature check and falls
  // through to a full re-sync — which is where the 17s-a-pop on the Navigation shard went.
  const clickedOwn = await clickDialogButton(/./, /^(cancel|close)$/);
  let dropped = clickedOwn
    ? await waitUntil(page, async () => (await readDialogs(page)).length < n0 || null, { budget: 1_500, label: 'pop-cancel', kind: 'restore' })
    : { ok: false };
  if (!dropped.ok) {
    await page.keyboard.press('Escape').catch(() => {});
    dropped = await waitUntil(page, async () => (await readDialogs(page)).length < n0 || null,
      { budget: 1_000, label: 'pop-escape', kind: 'restore' });
  }
  if (!dropped.ok) {
    await page.evaluate(`(() => {
      const norm = (t) => String(t ?? '').replace(/\\s+/g, ' ').trim();
      const undouble = (s) => { const t = norm(s); const h = t.slice(0, t.length / 2); return t.length % 2 === 0 && h === t.slice(t.length / 2) ? h.trim() : t; };
      const titles = [...document.querySelectorAll('.dialog-titleText')].filter((e) => e.offsetWidth > 0);
      const t = titles[titles.length - 1];
      if (!t) return;
      const root = t.closest('[role=dialog]') || t.closest('.dialog-background, .resizableDialog-popup, .dialog-popup, .popupContent') || t.parentElement;
      const btn = [...root.querySelectorAll('button, [role=button], .Button')]
        .find((b) => /^(cancel|close)$/i.test(undouble(b.textContent || b.getAttribute('title') || '')));
      if (btn) btn.click();
    })()`).catch(() => {});
    dropped = await waitUntil(page, async () => (await readDialogs(page)).length < n0 || null,
      { budget: 1_500, label: 'pop-cancel', kind: 'restore' });
  }
  return dropped.ok;
}

/** Drive one route step from wherever the cursor is. */
async function driveStep(step) {
  navSteps += 1;
  if (step.via === 'menu') {
    await gwt.openMainMenu(page);
    await waitForMenu(page, { budget: 400, label: 'main-menu' }).catch(() => {});
    if (!(await clickMenuItem(page, step.group))) throw new Error(`no menu group "${step.group}"`);
    await waitUntil(page, () => menuIsOpen(), { budget: 500, label: `menu:${step.group}`, kind: 'menu' })
      .catch(() => {});
    if (!(await clickMenuItem(page, step.leaf))) throw new Error(`no menu leaf "${step.leaf}"`);
    await waitUntil(page, async () => !(await menuIsOpen()), { budget: 1_500, label: `leaf:${step.leaf}`, kind: 'screen' })
      .catch(() => {});
    await waitForStableCount(page, () => page.evaluate(() =>
      document.querySelectorAll('.dataGridRow, .dataGridCell, tr, [role=row]').length),
    { budget: 1_500, poll: 150, stableFor: 2, label: `content:${step.leaf}`, kind: 'content', allowZero: true })
      .catch(() => {});
    // Only if a menu is still open: `dismissMenus` presses Escape first, and a leaf that opens a
    // DIALOG (Search Results, Find, Recent Items, About…) answers that Escape by closing — so every
    // such leaf surveyed as the bare screen, and its dialog was reached only round the long way,
    // through the chrome walk's `Show Menu`.
    if (await menuIsOpen()) await dismissMenus(page).catch(() => {});
  } else if (step.via === 'button') {
    const dialogsBefore = (await readDialogs(page).catch(() => [])).length;
    // `match` is a pattern over what is offered NOW — a row's action menu says `Open user 'admin'`,
    // and the seed cannot know the name. The first offered label that matches is the label.
    let label = step.label;
    if (step.match) {
      const re = new RegExp(step.match);
      label = (await survey()).buttons.find((b) => re.test(b));
      if (!label) throw new Error(`nothing offered matches /${step.match}/`);
    }
    // A TOGGLE in a route is a state to be IN, not a click to be made. The Ask Stroom AI dock is not
    // closed by the reset (it is neither a tab nor a dialog), so a replay that re-clicked it closed
    // it — `re-sync after "New Conversation" reached a different state`, and `Conversation History`
    // was `present when surveyed, not locatable when clicked`. InlineSvgToggleButton says `.on`.
    if (step.post && INVERSE.get(label) === label) {
      const on = await page.locator(`button[title="${label}"].on`).count().catch(() => 0);
      if (on) return;
    }
    if (!(await clickAffordance(label, 8000))) throw new Error(`no affordance "${label}"`);
    if (step.opens === 'menu') {
      await waitForMenu(page, { budget: 1_500, label: `button:${label}` }).catch(() => {});
    } else if (step.opens === 'dialog' || step.opens === 'alert') {
      const appeared = await waitUntil(page, async () => (await readDialogs(page)).length > dialogsBefore || null,
        { budget: 1_500, label: `button:${step.label}`, kind: 'dialog-appear' });
      if (appeared.ok) {
        await waitUntil(page, async () => (await readDialogs(page)).every((d) => d.heightPx > 100 && d.buttons.length) || null,
          { budget: 600, label: `button:${step.label}`, kind: 'dialog-settle' }).catch(() => {});
      }
    } else if (step.opens === 'changed') {
      await waitForStableCount(page, () => page.evaluate(() =>
        document.querySelectorAll('.dataGridRow, .dataGridCell, tr, [role=row]').length),
      { budget: 1_000, poll: 150, stableFor: 2, label: `button:${step.label}`, kind: 'content', allowZero: true })
        .catch(() => {});
    } else {
      await waitForDialogs(page, { budget: 1_200, settleBudget: 600, label: `button:${step.label}` })
        .catch(() => {});
    }
  } else if (step.via === 'docType') {
    await discardDirtyTabs();
    await expandExplorer();
    const found = await page.evaluate((type) => {
      const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
      const squash = (s) => s.toLowerCase().replace(/[^a-z0-9]/g, '');
      const rows = [...document.querySelectorAll('.explorerCell')].filter((e) => e.offsetWidth > 0)
        .map((e) => ({ title: norm(e.querySelector('.explorerCell-icon')?.getAttribute('title')),
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
    if (found.how !== 'exact' && !looseReported.has(step.type)) {
      looseReported.add(step.type);
      console.log(`  (type "${step.type}" matched explorer title "${found.title}" by ${found.how})`);
    }
    await gwt.openDocByName(page, found.label, found.title);
    await activateContentTab(found.label);
    await waitForDocOpen();
  } else if (step.via === 'context') {
    await expandExplorer();
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
  } else if (step.via === 'right-click') {
    // Re-find the target the same way `findTargets` did and right-click it. The step carries the KIND
    // ("an explorer row", "a grid row", "the editor") rather than coordinates, because coordinates do
    // not survive a replay — and the kind is what identifies the handler under test.
    const [t] = (await findTargets(page)).filter((x) => x.what === step.what);
    if (!t) throw new Error(`no ${step.what} to right-click`);
    await page.mouse.click(t.x, t.y, { button: 'right' });
    await waitForMenu(page, { budget: 1_500, label: `right-click:${step.what}` }).catch(() => {});
  } else if (step.via === 'tab-context') {
    if (!(await rightClickByText(page, '.curveTab', step.label))) {
      throw new Error(`no content tab "${step.label}" to right-click`);
    }
  } else if (step.via === 'doc') {
    await discardDirtyTabs();
    await openDoc(step.name, step.type);
  } else if (step.via === 'select-row') {
    // Select the first row of the first grid that has one, inside the topmost dialog if one is up.
    // A real mouse click: GWT's selection model does not answer a synthetic `.click()`.
    // The row may still be on its way: the Search Result Stores dialog asks every node in turn and
    // paints its grid when the last answers, so a read taken as the dialog opens sees the empty
    // placeholder row. Poll briefly for a row that has text before giving up.
    const findCell = () => page.evaluate(([match, text]) => {
      const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
      const scope = popups.length ? popups[popups.length - 1] : document.body;
      const wanted = text ? new RegExp(text) : null;
      for (const g of scope.querySelectorAll('.dataGridWidget, [role=grid]')) {
        if (!(g.offsetWidth > 0)) continue;
        // `match` is a selector the wanted row must contain — `.svg-image__users` is a GROUP row on
        // the Users screen, where the first row is the user `admin` and Edit stays disabled.
        // `text` is a regex the row's TEXT must match — the stepping meta list's first row is an
        // Error stream the pipeline cannot parse (`step` answers foundRecord:false and the step
        // controls stay disabled); an `Events` row steps.
        const row = [...g.querySelectorAll('tbody tr, [role=row]:not([aria-rowindex="1"])')].find((r) => r.offsetHeight > 0 && r.textContent.trim()
          && (!match || r.querySelector(match)) && (!wanted || wanted.test(r.textContent.replace(/\s+/g, ' '))));
        if (!row) continue;
        // A cell WITH TEXT: a stream grid's first wide-enough cell is its 24px TickBoxCell, and a
        // click there CHECKS the row (the batch selection — Process/Delete light up) without
        // SELECTING it, so the data preview never loaded and `View Source` had nothing to show.
        // And a cell that is PLAIN: the Search Result Stores' owner column is a UserRef link cell
        // (`.userRefLinkContainer`, a copy icon), which CONSUMES the click, so the row was never
        // selected. Prefer a text cell with nothing interactive in it; fall back to any text cell.
        const tds = [...row.querySelectorAll('td, [role=gridcell]')];
        const interactive = 'a, u[link], button, input, .svg-image, .hoverIconContainer, .userRefLinkContainer';
        const td = tds.find((c) => c.offsetWidth > 20 && c.textContent.trim() && !c.querySelector(interactive))
          ?? tds.find((c) => c.offsetWidth > 20 && c.textContent.trim()) ?? tds.find((c) => c.offsetWidth > 20) ?? row;
        const r = td.getBoundingClientRect();
        return { x: r.x + Math.min(r.width / 2, 60), y: r.y + r.height / 2 };
      }
      return null;
    }, [step.match ?? null, step.text ?? null]);
    let cell = await findCell();
    for (let i = 0; !cell && i < 10; i += 1) { await page.waitForTimeout(500); cell = await findCell(); }
    if (!cell) throw new Error(`no grid row to select${step.match ? ` matching ${step.match}` : ''}${step.text ? ` with text /${step.text}/` : ''}`);
    // And let the grid finish arriving: that same dialog repaints when its LAST node answers, and a
    // selection made on the first paint was gone by the survey (Store Settings still disabled).
    await waitForStableCount(page, () => page.evaluate(() =>
      document.querySelectorAll('.dataGridWidget tbody tr, [role=grid] [role=row]').length),
    { budget: 2_000, poll: 300, stableFor: 3, label: 'select-row:settle', kind: 'content', allowZero: true }).catch(() => {});
    cell = (await findCell()) ?? cell;
    await page.mouse.click(cell.x, cell.y);
    await waitForStableCount(page, () => page.evaluate(() =>
      [...document.querySelectorAll('button, [role=button]')].filter((b) => b.offsetWidth > 0 && !b.disabled).length),
    { budget: 1_500, poll: 150, stableFor: 2, label: 'select-row', kind: 'content', allowZero: true }).catch(() => {});
  } else if (step.via === 'tick-row') {
    // The first row's TICK BOX (`TickBoxCell` renders `div.tickBox`), a real click — the batch
    // selection, which enables the batch buttons without selecting the row for the preview.
    const box = await page.evaluate(() => {
      const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
      const scope = popups.length ? popups[popups.length - 1] : document.body;
      const tick = [...scope.querySelectorAll('.dataGridWidget tbody tr .tickBox, [role=grid] [role=row] .tickBox')].find((e) => e.offsetWidth > 0);
      if (!tick) return null;
      const r = tick.getBoundingClientRect();
      return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
    });
    if (!box) throw new Error('no tick box in a row');
    await page.mouse.click(box.x, box.y);
    await waitForStableCount(page, () => page.evaluate(() =>
      [...document.querySelectorAll('button, [role=button]')].filter((b) => b.offsetWidth > 0 && !b.disabled).length),
    { budget: 1_500, poll: 150, stableFor: 2, label: 'tick-row', kind: 'content', allowZero: true }).catch(() => {});
  } else if (step.via === 'row-action') {
    // A grid row's ACTION CELL — the `⋮` `ActionCell` renders as `.svgCell-button` in the row — opens
    // a per-row menu (Dependencies: Open / Properties / Delete / Locate; Users: Open user…). Not a
    // button the survey offers, so it is a seed.
    const box = await page.evaluate((nth) => {
      const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
      const scope = popups.length ? popups[popups.length - 1] : document.body;
      const rows = [...scope.querySelectorAll('.dataGridWidget tbody tr, [role=grid] [role=row]:not([aria-rowindex="1"])')].filter((r) => r.offsetHeight > 0 && r.textContent.trim());
      const row = rows[nth ?? 0];
      const cell = row && [...row.querySelectorAll('.svgCell-button')].find((c) => c.offsetWidth > 0);
      if (!cell) return null;
      const r = cell.getBoundingClientRect();
      return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
    }, step.nth);
    if (!box) throw new Error('no action cell on the row');
    await page.mouse.click(box.x, box.y);
    await waitForMenu(page, { budget: 1_500, label: 'row-action' }).catch(() => {});
  } else if (step.via === 'select-tree') {
    // The first item of a tree in scope — a real click. A GWT Tree's items are `.gwt-TreeItem` (a
    // visualisation's asset tree); the explorer tree a document CHOOSER shows is `.explorerCell`
    // rows (`selector`), and `match` names what the wanted row must contain — the Pipeline icon,
    // because a chooser lists the folders above its documents too.
    const box = await page.evaluate(([selector, match]) => {
      const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
      const scope = popups.length ? popups[popups.length - 1] : document.body;
      const item = [...scope.querySelectorAll(selector)].find((e) => e.offsetWidth > 0 && e.textContent.trim()
        && (!match || e.querySelector(match)));
      if (!item) return null;
      const r = item.getBoundingClientRect();
      return { x: r.x + Math.min(r.width / 2, 40), y: r.y + Math.min(r.height / 2, 10) };
    }, [step.selector ?? '.gwt-TreeItem', step.match ?? null]);
    if (!box) throw new Error(`no tree item to select${step.match ? ` matching ${step.match}` : ''}`);
    await page.mouse.click(box.x, box.y);
    await page.waitForTimeout(400);
  } else if (step.via === 'fill') {
    // Type into a text box in scope — the quick filter of a document chooser, whose tree shows only
    // folders until a filter expands it to the matching documents. `empty: true` picks the first
    // EMPTY visible text box in the topmost dialog instead of a selector — a create dialog's name.
    const box = await page.evaluate(([selector, empty]) => {
      const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
      const scope = popups.length ? popups[popups.length - 1] : document.body;
      const sel = empty ? 'input[type="text"], input:not([type]), .gwt-TextBox, textarea' : selector;
      const el = [...scope.querySelectorAll(sel)].find((e) => e.offsetWidth > 0 && !e.disabled && !e.readOnly
        && (!empty || !String(e.value ?? '').trim()) && !e.classList.contains('quickFilter-textBox'));
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
    }, [step.selector ?? null, !!step.empty]);
    if (!box) throw new Error(step.empty ? 'no empty text box to fill' : `nothing to fill matching ${step.selector}`);
    const countRows = () => page.evaluate(() =>
      [...document.querySelectorAll('.explorerCell, tr, [role=row]')].filter((e) => e.offsetWidth > 0).length);
    const before = await countRows();
    await page.mouse.click(box.x, box.y);
    await page.keyboard.type(step.text, { delay: 30 });
    // The filter is debounced and the answer is a round trip: wait for the list to CHANGE (a
    // stable count read before the response arrives is stable at the wrong number), then settle.
    await waitUntil(page, async () => ((await countRows()) !== before ? true : null),
      { budget: 4_000, label: `fill:${step.text}`, kind: 'content' }).catch(() => {});
    await waitForStableCount(page, countRows,
      { budget: 3_000, poll: 200, stableFor: 3, label: `fill:${step.text}`, kind: 'content', allowZero: true }).catch(() => {});
  } else if (step.via === 'set-select') {
    // A `SelectionBox` (BaseSelectionBox): its icon opens a SelectionPopup, and ONE mousedown on an
    // item selects it, sets the value and hides the popup. The rule editor's `Formatting Type` is
    // one; `Custom` is what reveals `Edit Custom Style` (CustomRowStylePresenter).
    const icon = await page.evaluate((selector) => {
      const box = [...document.querySelectorAll(selector)].find((e) => e.offsetWidth > 0);
      // The ICON, not the text box: a click on the text box calls showPopup() and then bubbles to
      // the box's own click handler, which calls it again — and the second call HIDES the popup.
      const el = box?.querySelector('.svgIconBox-icon') ?? box;
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
    }, step.selector);
    if (!icon) throw new Error(`no selection box ${step.selector}`);
    await page.mouse.click(icon.x, icon.y);
    const item = await waitUntil(page, async () => page.evaluate((wanted) => {
      const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
      // ON SCREEN: a popup is laid out off-viewport (−956,−938) before it is positioned, and another
      // SelectionList (the query help pane) may hold the same text; `offsetWidth > 0` passes both.
      const row = [...document.querySelectorAll('.selectionList-elementChooser tr, .selectionList-elementChooser .dataGridRow')]
        .find((e) => e.offsetWidth > 0 && norm(e.textContent) === wanted && e.getBoundingClientRect().x >= 0 && e.getBoundingClientRect().y >= 0);
      if (!row) return null;
      const r = row.getBoundingClientRect();
      return { x: r.x + Math.min(r.width / 2, 40), y: r.y + r.height / 2 };
    }, step.value), { budget: 2_000, label: `set-select:${step.value}`, kind: 'menu' });
    if (!item.ok || !item.value) throw new Error(`no item "${step.value}" in the selection popup`);
    await page.mouse.move(item.value.x, item.value.y);
    await page.mouse.down();
    await page.mouse.up();
    await page.waitForTimeout(400);
    const shows = await page.evaluate((selector) => [...document.querySelectorAll(selector)].find((e) => e.offsetWidth > 0)?.querySelector('.SelectionBox-renderBox')?.textContent.trim(), step.selector);
    console.log(`      (set-select ${step.selector} = "${step.value}" → box reads "${shows}")`);
  } else if (step.via === 'click-in-header') {
    // A child of a column HEADER — the `.column-valueFilterIcon` that opens ColumnValuesFilterPresenter.
    // The same MyDataGrid mouseup path as the column menu, so the same move → press → release, but
    // aimed at the icon: `QueryTableColumnsManager.onShowMenu` looks at the event's target.
    const box = await page.evaluate(([wanted, child]) => {
      const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
      const th = [...document.querySelectorAll('.dataGridWidget th, [role=columnheader]')]
        .find((e) => e.offsetWidth > 30 && norm(e.querySelector('.column-label')?.textContent ?? e.textContent) === wanted);
      const el = th?.querySelector(child);
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: r.x + r.width / 2, y: r.y + r.height / 2, w: r.width };
    }, [step.column, step.child]);
    if (!box || !box.w) throw new Error(`no ${step.child} in header "${step.column}"`);
    await page.mouse.move(box.x, box.y);
    await page.waitForTimeout(120);
    await page.mouse.move(box.x + 1, box.y);
    await page.waitForTimeout(120);
    await page.mouse.down();
    await page.waitForTimeout(60);
    await page.mouse.up();
    // What opens is a SIMPLE popup (`simplePopup-popup`), not a captioned dialog, so the dialog
    // reader cannot name it; the seed names it, and this is the evidence the seed's claim rests on.
    const popup = await waitUntil(page, () => page.evaluate(() =>
      [...document.querySelectorAll('.simplePopup-popup')].some((e) => e.offsetWidth > 0) || null),
    { budget: 2_000, label: 'click-in-header', kind: 'dialog-appear' });
    if (!popup.ok) throw new Error(`no popup after clicking ${step.child} in header "${step.column}"`);
  } else if (step.via === 'set-ace') {
    // Replace an Ace editor's text — client-side, nothing saved. What a query RETURNS is a
    // precondition: the result table's column menu and Download need columns and rows, and the
    // seeded documents' queries all error or return nothing on this instance.
    if (!(await setAce(page, step.text, step.index ?? 0))) throw new Error('no Ace editor to set');
    await page.waitForTimeout(300);
  } else if (step.via === 'select-element') {
    // A pipeline element on the Structure canvas. Not a GWT Tree: `DraggableTreePanel` selects on
    // MOUSEUP when the target is the element the MOUSEDOWN landed on, and a drag between the two
    // moves the element — so press and release on one spot, no movement. The LAST box is the
    // deepest element (Source is first), which is the one whose Add/Edit/Remove all apply.
    const box = await page.evaluate((match) => {
      // The box itself carries no class: its BACKGROUND (`-background`) and LABEL (`-label`) do.
      // `match` picks an element by name — the pipeline REFERENCE list is enabled only for an element
      // with a pipelineReference property, which is the XSLT filter, not the Reference Data Filter
      // (that one LOADS reference data; the XSLT filter CONSUMES it).
      const boxes = [...document.querySelectorAll('.pipelineElementBox-label')].filter((e) => e.offsetWidth > 0);
      const el = match ? boxes.find((e) => new RegExp(match, 'i').test(e.textContent)) : boxes[boxes.length - 1];
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: r.x + r.width / 2, y: r.y + r.height / 2, n: boxes.length };
    }, step.match ?? null);
    if (!box) throw new Error(`no pipeline element on the canvas${step.match ? ` matching /${step.match}/` : ''}`);
    await page.mouse.move(box.x, box.y);
    await page.mouse.down();
    await page.mouse.up();
    await waitForStableCount(page, () => page.evaluate(() =>
      [...document.querySelectorAll('button, [role=button]')].filter((b) => b.offsetWidth > 0 && !b.disabled).length),
    { budget: 1_500, poll: 150, stableFor: 2, label: 'select-element', kind: 'content', allowZero: true }).catch(() => {});
  } else if (step.via === 'dblclick') {
    // MainPresenter opens the user task manager on a DOUBLE click of the toolbar spinner — a
    // ClickHandler with a timer, so two real clicks.
    const el = page.locator(step.selector).first();
    if (!(await el.count().catch(() => 0))) throw new Error(`nothing matches ${step.selector}`);
    await el.dblclick({ timeout: 3_000, force: true });
    await waitForDialogs(page, { budget: 2_000, settleBudget: 600, label: 'dblclick' }).catch(() => {});
  } else if (step.via === 'content-tab') {
    await activateContentTab(step.label);
  } else if (step.via === 'signed-out') {
    // The sign-in page is a surface too, and the only way to stand on it is to leave the session.
    // The next root's re-sync finds `assertSignedIn` failing and signs back in.
    await page.context().clearCookies();
    await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await gwt.waitForSignIn(page).catch(() => {});
    await page.waitForTimeout(800);
  } else if (step.via === 'tab-menu') {
    // A dashboard component tab's own menu (the `Settings` button on its tab bar): Rename, Settings,
    // Hide, Duplicate, Remove, Maximise. Used to MAXIMISE a pane so its headers can be clicked.
    const bar = await tabBarOf(step.tab);
    if (!bar?.settings) throw new Error(`no tab bar with a Settings button for "${step.tab}"`);
    await page.mouse.click(bar.settings.x, bar.settings.y);
    await waitForMenu(page, { budget: 1_500, label: `tab-menu:${step.tab}` }).catch(() => {});
    if (!(await clickMenuItem(page, step.item))) throw new Error(`no "${step.item}" in the "${step.tab}" tab menu`);
    await waitUntil(page, async () => !(await menuIsOpen()), { budget: 1_500, label: `tab-menu:${step.item}`, kind: 'screen' })
      .catch(() => {});
    await page.waitForTimeout(400);
  } else if (step.via === 'add-component') {
    // Add Component › <menu path> puts the dashboard into DESTINATION mode — the new tab is "being
    // dragged" with no button held — and a primary MOUSEDOWN on a pane places it (FlexLayout
    // .onMouseDown → finishSelection when newComponents != null). Dropping on the tab bar that
    // holds `onto` adds the component as a tab there and selects it; the component's Settings
    // dialog then opens by itself (`addedComponent.showSettings()`) and is closed here so the node
    // surveyed is the COMPONENT, not its settings — its own tab bar re-offers Settings.
    const tabsBefore = await readSubTabs().catch(() => []);
    const buttonsBefore = (await survey()).buttons;
    if (!(await clickAffordance('Add Component', 8000))) throw new Error('no "Add Component" button — not in design mode?');
    await waitForMenu(page, { budget: 1_500, label: 'add-component' }).catch(() => {});
    for (const item of step.menu) {
      if (!(await clickMenuItem(page, item))) throw new Error(`no "${item}" under Add Component`);
      await page.waitForTimeout(400);
    }
    const bar = await tabBarOf(step.onto);
    if (!bar) throw new Error(`no tab bar holding "${step.onto}" to drop the component on`);
    await page.mouse.move(bar.x, bar.y);
    await page.waitForTimeout(150);
    await page.mouse.click(bar.x, bar.y);
    const appeared = await waitUntil(page, async () => (await readSubTabs()).length > tabsBefore.length || null,
      { budget: 3_000, label: 'component-placed', kind: 'tab' }).catch(() => ({ ok: false }));
    if (!appeared.ok) throw new Error(`component "${step.menu.at(-1)}" was not placed`);
    await waitUntil(page, async () => (await readDialogs(page)).length > 0 || null,
      { budget: 2_000, label: 'component-settings', kind: 'dialog-appear' }).catch(() => {});
    if ((await readDialogs(page).catch(() => [])).length) await closeTop('dialog');
    // What the component brought with it — its seed walks that, not the dashboard's toolbar again.
    const now = await settledSurvey({ budget: 800 });
    lastAddDelta = { items: now.buttons.filter((b) => !buttonsBefore.includes(b)), disabled: now.disabled };
  } else if (step.via === 'tab') {
    // Wait for the tab to EXIST before asking clickEditorTab, whose count check is immediate.
    await waitUntil(page, () => page.evaluate((label) => [...document.querySelectorAll('.linkTab-label')]
      .some((e) => e.offsetWidth > 0 && e.textContent.replace(/\s+/g, ' ').trim() === label), step.label),
    { budget: 5_000, label: `tab-exists:${step.label}`, kind: 'tab' }).catch(() => {});
    if (!(await clickEditorTab(page, step.label, { nth: step.nth ?? 0 }))) {
      // A NESTED tab — Report's `Table` under Query, Pipeline's `Meta` under Data — is listed as a
      // sibling by readSubTabs but only exists while its parent tab is selected. Try each visible
      // top-level tab in turn and look again; bounded by the strip's length, and only on a miss.
      let found = false;
      for (const parent of await readSubTabs().catch(() => [])) {
        if (parent === step.label) continue;
        if (!(await clickEditorTab(page, parent, { nth: 0, timeout: 3_000 }).catch(() => false))) continue;
        await waitUntil(page, () => page.evaluate((label) => [...document.querySelectorAll('.linkTab-label')]
          .some((e) => e.offsetWidth > 0 && e.textContent.replace(/\s+/g, ' ').trim() === label), step.label),
        { budget: 1_500, label: `nested:${step.label}`, kind: 'tab' }).catch(() => {});
        if (await clickEditorTab(page, step.label, { nth: step.nth ?? 0, timeout: 3_000 }).catch(() => false)) { found = true; break; }
      }
      if (!found) throw new Error(`no sub-tab "${step.label}"#${step.nth ?? 0} — ${await whereAmI()}`);
    }
    await waitUntil(page, () => page.evaluate(() => !document.querySelector('.gwt-Label-loading, .loading')),
      { budget: 1_000, label: `tab:${step.label}`, kind: 'tab' }).catch(() => {});
  } else {
    throw new Error(`unknown route step via="${step.via}"`);
  }
}

/**
 * Put the cursor back on `node` by replaying its route IN-APP. Called after a pop that did not
 * restore the parent, and after a post-action state. Reloads only if the session is gone.
 */
async function resync(node) {
  resyncs += 1;
  const t0 = Date.now();
  await clearOverlays();
  const tClear = Date.now();
  try {
    await gwt.assertSignedIn(page, creds);
  } catch {
    await reloadAndSignIn();
  }
  // A screen keeps its own state while its tab is open: re-clicking the menu leaf only focuses
  // the tab, so a filter applied by `Filter Schedules → OK` stayed applied and every header click
  // after it sorted an empty grid. Closing the tab and reopening it is what actually resets a
  // screen — the client-side part of "fresh", which is the only part a reload ever delivered.
  await closeAllContentTabs();
  // A TOGGLE left on is state too, and neither a closed tab nor a closed dialog turns it off: the AI
  // dock opened by one seed's route was still open for the next seed, whose every node then offered
  // `Conversation History` and `Configure` as its own. Whatever the route about to be replayed
  // does not itself turn on, turn off — the route step skips a toggle that is already on.
  const wantsOn = new Set(node.route.filter((st) => st.via === 'button' && st.post && INVERSE.get(st.label) === st.label).map((st) => st.label));
  for (const [label, inverse] of INVERSE) {
    if (inverse !== label || wantsOn.has(label)) continue;
    if (await page.locator(`button[title="${label}"].on`).count().catch(() => 0)) await clickAffordance(label).catch(() => {});
  }
  const tTabs = Date.now();
  for (const step of node.route) await driveStep(step);
  await discardIfAsked({ wait: 600 });
  const ms = Date.now() - t0;
  // Say where a slow re-sync went. 38s a re-sync on the Navigation shard was invisible until timed.
  if (ms > 8_000) {
    console.log(`      (slow re-sync ${Math.round(ms / 1000)}s: clear ${tClear - t0}ms, tabs ${tTabs - tClear}ms, `
      + `route ${Date.now() - tTabs}ms over ${node.route.length} step(s))`);
  }
}

// ── Seeds ────────────────────────────────────────────────────────────────────
const seeds = [];
if (RECIPES) {
  const { recipes } = JSON.parse(readFileSync(RECIPES, 'utf8'));
  const wanted = recipes.filter((r) => (RECIPEKIND ? r.via === RECIPEKIND : true))
    .filter((r) => !DOCTYPES.length || r.via !== 'doc' || DOCTYPES.includes(r.route[0]?.type));
  // DOCTYPES matches a recipe's type EXACTLY, so a near-miss silently walks nothing: the docs3
  // shard asked for "XSLT" and "XMLSchema" where the recipes say `Xslt` and `XmlSchema`, and both
  // documents went unwalked for as long as that shard has existed — with no hint but a seed count
  // nobody counted. A requested type that matches no recipe is a mistake, so say so and stop.
  if (DOCTYPES.length) {
    const known = new Set(recipes.filter((r) => r.via === 'doc').map((r) => r.route[0]?.type));
    const unknown = DOCTYPES.filter((t) => !known.has(t));
    if (unknown.length) {
      console.error(`DOCTYPES names ${unknown.length} type(s) no recipe declares: ${unknown.join(', ')}`);
      console.error(`  recipes declare: ${[...known].sort().join(', ')}`);
      process.exit(2);
    }
  }
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
  console.log(`seeded ${seeds.length} recipes from ${RECIPES}${RECIPEKIND ? ` (via=${RECIPEKIND})` : ''}`);
} else if (DOC) {
  seeds.push({ kind: 'editor', path: [DOC], route: [{ via: 'doc', name: DOC, type: DOCTYPE }] });
  console.log(`seeded document "${DOC}" — its sub-tabs are discovered when it opens`);
} else if (DIRECTED.length) {
  console.log(`directed seeds only (${DIRECTED.join(', ')})`);
} else {
  await gwt.openMainMenu(page);
  await waitForMenu(page, { budget: 1_000, label: 'main-menu' }).catch(() => {});
  // The area's LEAVES are the items that appear when its group is clicked — not everything visible
  // afterwards, which includes the other top-level groups. crawl.mjs seeded those too, so it had
  // "screens" called `Monitoring / Administration` that were really the Administration submenu
  // over whatever happened to be open, with a body that depended on history.
  const groupsBefore = new Set((await readVisibleMenuItems(page)).map((i) => i.label ?? i.text).filter(Boolean));
  if (!(await clickMenuItem(page, AREA))) throw new Error(`no menu group "${AREA}"`);
  await waitUntil(page, () => menuIsOpen(), { budget: 700, label: `menu:${AREA}`, kind: 'menu' }).catch(() => {});
  const leaves = (await readVisibleMenuItems(page)).map((i) => i.label ?? i.text).filter(Boolean)
    .filter((l) => !groupsBefore.has(l));
  await dismissMenus(page).catch(() => {});
  for (const leaf of leaves) {
    seeds.push({ kind: 'screen', path: [AREA, leaf], route: [{ via: 'menu', group: AREA, leaf }] });
  }
  console.log(`seeded ${seeds.length} screens from "${AREA}": ${seeds.map((s) => s.path[1]).join(', ')}`);
}

const contextSeeds = [];
for (const target of [...new Set([...(DOC ? [DOC] : []), ...EXPLORER])]) {
  contextSeeds.push({ kind: 'context', path: [`${target} (right-click)`], route: [{ via: 'context', target }] });
}
for (const group of EXPLORER_MULTI) {
  const [target, ...also] = group;
  contextSeeds.push({ kind: 'context',
    path: [`${group.join(' + ')} (right-click, ${group.length} selected)`],
    route: [{ via: 'context', target, also }] });
}
if (DOC) {
  contextSeeds.push({ kind: 'context', path: [`${DOC} (tab right-click)`],
    route: [{ via: 'doc', name: DOC, type: DOCTYPE }, { via: 'tab-context', label: DOC }] });
}
if (contextSeeds.length) console.log(`  + ${contextSeeds.length} context menu seed(s)`);
seeds.unshift(...contextSeeds);

if (DIRECTED.includes('dashboard')) {
  // The dashboard's design mode is where the source puts three classes of door (door-preconditions.md):
  // the table's column menu (Rename/Expression/Format/Filter… — a header click, only in design mode),
  // and the component TYPES that exist only where a dashboard holds one — added client-side, no
  // mutation until Save, which the guard blocks anyway. The re-sync closes and reopens the
  // document (discarding), so every seed starts from the stored dashboard.
  const design = [
    { via: 'docType', type: 'Dashboard' },
    { via: 'button', label: 'Enter Design Mode', post: true, opens: 'changed' },
  ];
  const path = ['Dashboard (document)', 'after Enter Design Mode'];
  // No `seedDepth` here on purpose: the route's own post steps spend POSTDEPTH, so a column menu's
  // `changed` items (sorts, moves, Hide, Remove) are recorded but NOT descended — each of those
  // states is the dashboard's whole toolbar again, which the document shard walks.
  seeds.push({ kind: 'editor-tab', presenter: 'DashboardPresenter', columnMenus: true,
    path: [...path, 'Table'],
    route: [...design, { via: 'tab-menu', tab: 'Table', item: 'Maximise', post: true, opens: 'changed' }] });
  for (const [label, menu] of [
    ['Key/Value Input', ['Input', 'Key/Value Input']],
    ['List Input', ['Input', 'List Input']],
    ['Text Input', ['Input', 'Text Input']],
    ['Table Filter', ['Input', 'Table Filter']],
    ['Embedded Query', ['Embedded Query']],
  ]) {
    seeds.push({ kind: 'editor-tab', presenter: 'DashboardPresenter',
      path: [...path, label],
      route: [...design, { via: 'add-component', menu, onto: 'Query', post: true, opens: 'changed' }] });
  }
  console.log(`  + 6 directed dashboard seed(s): column menu on a maximised Table, 5 component types`);
}
if (DIRECTED.includes('rows')) {
  // door-preconditions.md, 2026-09-17 research pass: doors behind a row's action-cell menu, a row
  // that must be a GROUP, and a tree item — each named by the source, none reachable blind.
  seeds.push(
    { kind: 'menu', path: ['Tools', 'Dependencies', 'row ⋮'],
      route: [{ via: 'menu', group: 'Tools', leaf: 'Dependencies' }, { via: 'row-action', nth: 0, opens: 'menu' }] },
    { kind: 'menu', path: ['Security', 'Users', 'row ⋮'],
      route: [{ via: 'menu', group: 'Security', leaf: 'Users' }, { via: 'row-action', nth: 0, opens: 'menu' }] },
    // `Open user '…'` opens the user's own tab (UserTabPresenter, a content tab with sub-tabs).
    { kind: 'editor', presenter: 'UserTabPresenter', path: ['Security', 'Users', 'row ⋮', 'Open user'],
      route: [{ via: 'menu', group: 'Security', leaf: 'Users' }, { via: 'row-action', nth: 0, opens: 'menu' },
        { via: 'button', match: '^Open (user|group) ', post: true, opens: 'changed' }] },
    { kind: 'post-action', path: ['Security', 'User Groups', 'after select row (a group)'],
      route: [{ via: 'menu', group: 'Security', leaf: 'User Groups' }, { via: 'select-row', match: '.svg-image__users', post: true, opens: 'changed' }] },
    // The visualisation `seed-data.mjs` gave an asset — by NAME, because no other visualisation here
    // has one and the Assets tree of the first in the explorer is empty (`Rename` needs an item).
    { kind: 'post-action', presenter: 'VisualisationAssetsPresenter', path: ['Visualisation (document)', 'Assets', 'after select tree item'],
      route: [{ via: 'doc', name: SEED_DATA.visualisation, type: 'Visualisation' }, { via: 'tab', label: 'Assets', nth: 0 }, { via: 'select-tree', post: true, opens: 'changed' }] },
  );
  console.log('  + 5 directed row seed(s): action-cell menus, a group row, an asset tree item');
}
if (DIRECTED.includes('chrome')) {
  seeds.push(
    { kind: 'screen', presenter: 'WelcomePresenter', path: ['Welcome'], route: [{ via: 'content-tab', label: 'Welcome' }] },
    { kind: 'dialog', presenter: 'UserTaskManagerPresenter', path: ['spinner (double-click)'],
      route: [{ via: 'dblclick', selector: '.spinner, [class*=spinner]', opens: 'dialog' }] },
  );
  console.log('  + 2 directed chrome seed(s): the Welcome tab, the spinner double-click');
}
if (DIRECTED.includes('docs')) {
  seeds.push(
    { kind: 'editor', presenter: 'FolderPresenter', path: ['Folder (document)'], route: [{ via: 'docType', type: 'Folder' }] },
    { kind: 'editor', presenter: 'FolderRootPresenter', path: ['System (root)'], route: [{ via: 'doc', name: 'System', type: 'System' }] },
  );
  console.log('  + 2 directed document seed(s): a Folder, the System root');
}
if (DIRECTED.includes('ai')) {
  // The AI dock's own dialogs (door-preconditions.md: `Conversation History` → AiChatHistoryPresenter).
  // `Download` and the attachment viewer stay disabled until a chat has messages — data, not a seed.
  seeds.push({ kind: 'post-action', presenter: 'AskStroomAiPresenter', path: ['after Ask Stroom AI'],
    route: [{ via: 'button', label: 'Ask Stroom AI', post: true, opens: 'changed' }] });
  console.log('  + 1 directed AI dock seed');
}
if (DIRECTED.includes('stream')) {
  // A stream's SOURCE tab: the data preview under a selected stream row carries a `View Source`
  // label (`DataViewImpl.sourceLinkLabel`, a clickable Label not a button) that fires ShowDataEvent
  // as a STROOM_TAB — SourceTabPresenter, labelled `Stream <id> : <part> : <record>`.
  seeds.push({ kind: 'editor', presenter: 'SourceTabPresenter', path: ['System (root)', 'Data', 'after select row in Data', 'View Source'],
    route: [{ via: 'doc', name: 'System', type: 'System' }, { via: 'tab', label: 'Data', nth: 0 },
      { via: 'select-row', post: true, opens: 'changed' }, { via: 'button', label: `${LINK}View Source`, post: true, opens: 'changed' }] });
  console.log('  + 1 directed stream seed: View Source on a selected stream');
}
if (DIRECTED.includes('pipeline')) {
  // The Structure tab's whole toolbar is disabled at rest (Add / Edit / Remove element, Edit
  // Property, New / Edit / Remove Reference): every one of NewElementPresenter,
  // NewPropertyPresenter and NewPipelineReferencePresenter sits behind a SELECTED ELEMENT.
  // `seedDepth` = the route: the selection is the seed's precondition, not a post step it spent, so
  // the properties grid's own `select-row` (→ Edit Property, NewPropertyPresenter) still runs.
  seeds.push({ kind: 'post-action', presenter: 'PipelineStructurePresenter', path: ['Pipeline (document)', 'Structure', 'after select element'], seedDepth: 3,
    route: [{ via: 'docType', type: 'Pipeline' }, { via: 'tab', label: 'Structure', nth: 0 }, { via: 'select-element', post: true, opens: 'changed' }] });
  console.log('  + 1 directed pipeline seed: a selected element on the Structure canvas');
}
if (DIRECTED.includes('results')) {
  // A query that RETURNS ROWS, under the guard: `Execute Query` is a search, which the read-only
  // guard allows (plan §1.2), and `probe-results.mjs` found `Example Index` answers 100 rows and
  // `Annotations` 10 — the seeded documents' own queries all error. `limit` goes BEFORE `select`.
  // Two seeds over the same state: the result table's column menu (ColumnFilter, Rules/Conditional
  // Formatting, ColumnValuesFilter, Format), and the toolbar + a selected row (Download, and the
  // Annotate menu — Change Status / Assigned To need rows that carry annotation ids).
  const run = (text) => [
    { via: 'docType', type: 'Query' },
    { via: 'set-ace', text, post: true, opens: 'changed' },
    { via: 'button', label: 'Execute Query', post: true, opens: 'changed' },
  ];
  // The node's presenter is the RESULT TABLE, not the document: what these seeds walk — the column
  // menu, Download, Annotate — is QueryResultTablePresenter's surface (three embeds below the doc,
  // which is further than the ledger's one-level narrowing reaches), and `Filter '…'`, `Settings`
  // and `Download Options` each belong to two presenters application-wide, so only the parent can
  // say which. The annotate seed selects a row first: the menu's Change Status / Assigned To items
  // exist only for a selection whose rows carry annotation ids, and a selection that enables no new
  // BUTTON is not one the generic row rule descends.
  const withResults = run('from "Example Index" limit 20 select StreamId, EventTime, UserId');
  const annotations = run('from "Annotations" select Id, Title, Status');
  seeds.push(
    { kind: 'post-action', presenter: 'QueryResultTablePresenter', columnMenus: { selector: '.dataGridWidget th, [role=columnheader]', design: false },
      path: ['Query (document)', 'after query with results'], route: withResults },
    { kind: 'post-action', presenter: 'QueryResultTablePresenter', seedDepth: 3,
      path: ['Query (document)', 'after query with results (toolbar)'], route: withResults },
    { kind: 'menu', presenter: 'QueryResultTablePresenter', seedDepth: 4,
      path: ['Query (document)', 'after annotations query', 'after select row', 'Annotate'],
      route: [...annotations, { via: 'select-row', post: true, opens: 'changed' }, { via: 'button', label: 'Annotate', opens: 'menu' }] },
  );
  console.log('  + 3 directed results seed(s): a query with rows — its column menu, its toolbar, and the Annotate menu on a selected annotation row');
}
if (DIRECTED.includes('annotation')) {
  // The annotation EDITOR, opened from a selected annotation row's Annotate › Edit Annotation. Its
  // sub-tabs walk as an editor's do (Events › Add → AddEventLinkPresenter); its own surface adds two
  // things the button reader cannot see: the `Change Retention Period` setting block
  // (DurationPresenter) and a comment entry's mousedown menu (Edit Entry → CommentEditPresenter).
  const annotations = [
    { via: 'docType', type: 'Query' },
    { via: 'set-ace', text: 'from "Annotations" select Id, Title, Status', post: true, opens: 'changed' },
    { via: 'button', label: 'Execute Query', post: true, opens: 'changed' },
    // The annotation `seed-data.mjs` gave a comment entry, by title: `entry: comment` needs one.
    { via: 'select-row', text: SEED_DATA.annotation.title, post: true, opens: 'changed' },
    { via: 'button', label: 'Annotate', opens: 'menu' },
    { via: 'button', match: '^Edit Annotation', post: true, opens: 'changed' },
  ];
  seeds.push({ kind: 'editor', presenter: 'AnnotationPresenter', seedDepth: 6,
    path: ['Annotation (editor)'], route: annotations,
    extra: [`${TITLED}Change Retention Period`, `${ENTRY}comment`] });
  console.log('  + 1 directed annotation editor seed');
}
if (DIRECTED.includes('cf')) {
  // Conditional formatting, one value deeper: the rule editor's `Formatting Type` set to Custom
  // reveals `Edit Custom Style` (CustomRowStylePresenter); the header's value-filter ICON, not the
  // header, opens ColumnValuesFilterPresenter. Both on the results table.
  const results = [
    { via: 'docType', type: 'Query' },
    { via: 'set-ace', text: 'from "Example Index" limit 20 select StreamId, EventTime, UserId', post: true, opens: 'changed' },
    { via: 'button', label: 'Execute Query', post: true, opens: 'changed' },
  ];
  const scope = { selector: '.dataGridWidget th, [role=columnheader]', design: false };
  seeds.push(
    { kind: 'post-action', presenter: 'RulePresenter', seedDepth: 7, columnScope: scope,
      path: ['Query (document)', 'after query with results', 'column: StreamId', 'Conditional Formatting', 'Add', 'after Formatting Type = Custom'],
      route: [...results, { via: 'button', label: `${COLUMN_MENU}StreamId`, opens: 'menu' }, { via: 'button', label: 'Conditional Formatting', opens: 'dialog' },
        { via: 'button', label: 'Add', opens: 'dialog' }, { via: 'set-select', selector: '.conditionalFormatTypeSelection', value: 'Custom', post: true, opens: 'changed' }] },
    { kind: 'post-action', presenter: 'ColumnValuesFilterPresenter', seedDepth: 4, columnScope: scope,
      path: ['Query (document)', 'after query with results', 'column: StreamId (value filter icon)'],
      route: [...results, { via: 'click-in-header', column: 'StreamId', child: '.column-valueFilterIcon', opens: 'dialog' }] },
  );
  console.log('  + 2 directed conditional-formatting seed(s): Formatting Type = Custom, the value-filter icon');
}
if (DIRECTED.includes('pipeline-ref')) {
  // A pipeline REFERENCE needs an element with a pipelineReference PROPERTY selected — the XSLT
  // filter (probe-pass4.mjs: a client-side-added Reference Data Filter enables Edit Element but not
  // New Reference; the XSLT filter enables New Reference). NewPipelineReferencePresenter.
  seeds.push({ kind: 'post-action', presenter: 'PipelineStructurePresenter', seedDepth: 3,
    path: ['Pipeline (document)', 'Structure', 'after select element (xsltFilter)'],
    route: [{ via: 'docType', type: 'Pipeline' }, { via: 'tab', label: 'Structure', nth: 0 }, { via: 'select-element', match: 'xslt', post: true, opens: 'changed' }] });
  console.log('  + 1 directed pipeline-reference seed');
}
if (DIRECTED.includes('pathways')) {
  // The Pathways editor opened under an Alert on the document shard and was never walked; its list's
  // `New` (a pager button, not the explorer's) opens PathwayEditPresenter, and a pathway's
  // constraints list has its own `New` (ConstraintEditPresenter).
  // On this instance the editor opens under `Remote node '' has no URL set` (findPathways, 500 — a
  // server/config defect, not the screen); closing the alert leaves the editor whole.
  seeds.push({ kind: 'editor', presenter: 'PathwaysPresenter', path: ['Pathways (document)', 'after Close'],
    route: [{ via: 'docType', type: 'Pathways' }, { via: 'button', label: 'Close', opens: 'closed' }] });
  console.log('  + 1 directed pathways seed');
}
if (DIRECTED.includes('stepping')) {
  // STEPPING MODE on a pipeline: `Enter Stepping Mode` swaps the Structure tab for SteppingPresenter,
  // whose meta list finds ONE stream for the seed pipeline and so auto-selects it and steps
  // (`addChangeDataHandler` → `beginStepping` when the page has a single row). `POST /stepping/v1/step`
  // was BLOCKED by the read-only guard until triaged (2026-09-21): it captures element IO into a
  // scratch store under Stroom's TEMP dir, creates no meta and writes no stream, so it is allowed now
  // and the step controls (First/Backward/Forward/Last/Refresh) run for real.
  //
  // Two doors, neither a button: `Set Location` (StepLocationPresenter) opens from the toolbar's
  // location LABEL, whose text is `[<metaId>:<part>:<record>]` — data, hence `selector:`; and
  // ElementPresenter is created client-side by `SteppingPresenter.getContent` when a NON-SOURCE
  // element is selected in the stepping tree — the same canvas as the Structure tab, so the same
  // `select-element` step. Its panes (code, input, output, log) are the second seed's surface.
  const stepping = [
    { via: 'docType', type: 'Pipeline' },
    { via: 'tab', label: 'Structure', nth: 0 },
    { via: 'button', label: 'Enter Stepping Mode', post: true, opens: 'changed' },
  ];
  // The meta list finds 100 streams here and its first row is an ERROR stream: selecting it runs
  // `step` (200, `Content is not allowed in prolog`, foundRecord:false) and the step controls stay
  // disabled. An `Events` row parses: `[<meta>:1:1]`, Step Forward → `[<meta>:1:2]`, and the
  // response's elementMap carries the XSLT filter's input and output (probe-stepping.mjs).
  const onStream = [...stepping, { via: 'select-row', text: '^\\S+ Events ', post: true, opens: 'changed' }];
  // The location label (`selector: .stepLocationLink` → Set Location) is offered by the survey
  // itself (CLICKABLE_LABELS); it used to be an `extra` on these two seeds.
  seeds.push({ kind: 'post-action', presenter: 'SteppingPresenter', seedDepth: 3,
    path: ['Pipeline (document)', 'Structure', 'after Enter Stepping Mode'], route: stepping });
  seeds.push({ kind: 'post-action', presenter: 'SteppingPresenter', seedDepth: 4,
    path: ['Pipeline (document)', 'Structure', 'after Enter Stepping Mode', 'after select stream (Events)'], route: onStream });
  seeds.push({ kind: 'post-action', presenter: 'ElementPresenter', seedDepth: 5,
    path: ['Pipeline (document)', 'Structure', 'after Enter Stepping Mode', 'after select stream (Events)', 'after select element (xsltFilter)'],
    route: [...onStream, { via: 'select-element', match: 'xslt', post: true, opens: 'changed' }] });
  console.log('  + 3 directed stepping seed(s): stepping mode at rest, stepping on an Events stream, its XSLT element');
}
if (DIRECTED.includes('datalink')) {
  // A result cell that is a `data(…)` HYPERLINK with displayType `tab` — rendered `<u link="…">`, and
  // DataGridSelectionEventManager fires HyperlinkEvent for it, whose handler fires ShowDataEvent as
  // a STROOM_TAB — DataPreviewTab-
  // Presenter, the one door opened only by an event (door-preconditions.md, class `event`). Nothing
  // to seed: the query builds the link over the same rows the `results` seeds use.
  const linked = [
    { via: 'docType', type: 'Query' },
    { via: 'set-ace', text: "from \"Example Index\" limit 5 select StreamId, data('Open', StreamId, 1, 1, 1, 1, 10, 1, 'preview', 'tab') as Link", post: true, opens: 'changed' },
    { via: 'button', label: 'Execute Query', post: true, opens: 'changed' },
  ];
  const link = `${SELECTOR}.dataGridWidget tbody u[link]`;
  seeds.push({ kind: 'post-action', presenter: 'QueryResultTablePresenter', seedDepth: 3,
    path: ['Query (document)', 'after query with data links'], route: linked, extra: [link] });
  // The tab the link opens, walked as an EDITOR and named — the same shape as the `stream` seed's
  // View Source: nothing on the route says which presenter a content tab is, so the seed does.
  seeds.push({ kind: 'editor', presenter: 'DataPreviewTabPresenter', seedDepth: 4,
    path: ['Query (document)', 'after query with data links', 'Stream (data preview tab)'],
    route: [...linked, { via: 'button', label: link, post: true, opens: 'changed' }] });
  console.log('  + 2 directed data-link seeds: a query whose rows carry data() links, and the tab a link opens');
}
if (DIRECTED.includes('settings')) {
  // A component's Settings DIALOG, whose own tabs the walk now descends: the Table's is Basic /
  // Conditional Formatting / Selection Filter and the Query's is Basic / Selection Query, and each
  // Selection tab's Add opens `Add New Selection Handler` (SelectionHandlerPresenter) — a `create`
  // path on a list that is empty here, one tab across from where the document shard stood.
  const design = [
    { via: 'docType', type: 'Dashboard' },
    { via: 'button', label: 'Enter Design Mode', post: true, opens: 'changed' },
  ];
  // The dialog IS the component's settings presenter; the selection-handler and rules lists are
  // what it embeds, and the handler inventory joins through that embedding.
  for (const [tab, presenter] of [['Table', 'TableSettingsPresenter'], ['Query', 'QuerySettingsPresenter']]) {
    seeds.push({ kind: 'dialog', presenter, seedDepth: 3,
      path: ['Dashboard (document)', 'after Enter Design Mode', tab, 'Settings'],
      route: [...design, { via: 'tab-menu', tab, item: 'Settings', opens: 'dialog' }] });
  }
  console.log('  + 2 directed settings seeds: the Table and Query components\' Settings dialogs');
}
if (DIRECTED.includes('gitrepo')) {
  // The Git repository `seed-data.mjs` gave a URL: its Settings tab shows `Push to Git` (hidden on
  // every content-pack repo here), whose click opens the commit dialog (GitRepoCommitDialog-
  // Presenter) before any push — the push is the dialog's OK, which the guard blocks.
  seeds.push({ kind: 'editor-tab', presenter: 'GitRepoPresenter', seedDepth: 2,
    path: ['GitRepo (document, with a URL)', 'Settings'],
    route: [{ via: 'doc', name: SEED_DATA.gitRepo, type: 'GitRepo' }, { via: 'tab', label: 'Settings', nth: 0 }] });
  console.log('  + 1 directed gitrepo seed: the Settings tab of a repository that can push');
}
if (DIRECTED.includes('fields')) {
  // The field lists that fill CLIENT-SIDE from their own New (the handler inventory's unreached
  // Edit / Remove): a Lucene index's Fields tab and a Solr index's. `New Field` › OK is a
  // validation alert on a blank form; the walker fills the name and presses OK again, the row
  // appears, and the selection rule reaches Edit Field / Remove Field on it.
  for (const [type, presenter] of [['LuceneIndex', 'IndexFieldListPresenter'], ['SolrIndex', 'SolrIndexFieldListPresenter']]) {
    seeds.push({ kind: 'editor-tab', presenter, seedDepth: 2, path: [`${type} (document)`, 'Fields'],
      route: [{ via: 'docType', type }, { via: 'tab', label: 'Fields', nth: 0 }] });
  }
  console.log('  + 2 directed fields seeds: the Lucene and Solr index Fields tabs');
}
if (DIRECTED.includes('stores')) {
  // The Search Result Stores dialog (Monitoring › Search Results) over the store `seed-data.mjs`
  // left: its row enables Store Settings (ResultStoreSettingsPresenter). The generic selection rule
  // missed it because the dialog paints its grid only after every node has answered.
  seeds.push({ kind: 'post-action', presenter: 'ResultStoreListPresenter', seedDepth: 2,
    path: ['Monitoring', 'Search Results', 'after select row (a store)'],
    route: [{ via: 'menu', group: 'Monitoring', leaf: 'Search Results' }, { via: 'select-row', text: 'node', post: true, opens: 'changed' }] });
  console.log('  + 1 directed stores seed: a result store row selected');
}
if (DIRECTED.includes('process')) {
  // A dashboard's `Process` (MANAGE_PROCESSORS_PERMISSION) opens `Choose Pipeline To Process
  // Results With`; a pipeline chosen and OK'd opens `Process Search Results` (ProcessorLimits-
  // Presenter). The chooser's tree shows folders until its quick filter is typed into, and the
  // walk's own OK on the chooser at rest chose nothing, so this is a seed: filter, pick, OK.
  seeds.push({ kind: 'dialog', presenter: 'ProcessorLimitsPresenter', seedDepth: 5,
    path: ['Dashboard (document)', 'Process', 'after filter', 'after select tree item (a pipeline)', 'OK'],
    route: [{ via: 'docType', type: 'Dashboard' }, { via: 'button', label: 'Process', opens: 'dialog' },
      { via: 'fill', selector: 'input.quickFilter-textBox', text: 'Example', post: true, opens: 'changed' },
      { via: 'select-tree', selector: '.explorerCell', match: '.explorerCell-icon[title="Pipeline"]', post: true, opens: 'changed' },
      { via: 'button', label: 'OK', opens: 'dialog' }] });
  console.log('  + 1 directed process seed: a pipeline chosen for a dashboard\'s Process');
}
if (DIRECTED.includes('signin')) {
  // Last, because reaching it ends the session.
  seeds.push({ kind: 'screen', presenter: 'LoginPresenter', signIn: true, path: ['Sign in'], route: [{ via: 'signed-out' }] });
  console.log('  + 1 directed sign-in page seed');
}
console.log();

// ── The walk ─────────────────────────────────────────────────────────────────
const runStartedAt = Date.now();
const nodes = [];
walkStarted = true;
const affordances = [];
/** Pops that did not give the parent back, and what was there instead. The walk's own findings. */
const findings = [];
/** Reversible clicks popped, and how many of those pops could not close what had opened. */
let pops = 0;
let popsStuck = 0;
const seen = new Set();
/** signature -> id of the node explored for it. */
const explored = new Map();
let capped = false;
const mark = { dialog: '▸', alert: '!', changed: '~', nothing: '·', vanished: '?', error: 'x',
  unclickable: '-', 'grid-rows': '#', 'grid-reordered': '↕', 'grid-appeared': '+', menu: '☰', closed: '◂' };

/** Post-action steps EXPLORED from this node — a directed seed's own precondition steps do not count. */
const postSteps = (node) => (node.route ?? []).slice(node.seedDepth ?? 0).filter((s) => s.post).length;

/**
 * ILLEGAL VALUES on a form's OK (BEHAVIOUR-PLAN.md § B3, lib/illegal.mjs).
 *
 * Run BEFORE the real OK, while the form is open and untouched: type a value each box cannot hold,
 * press OK, and read what the presenter says. Then put the boxes back as they were — the node is
 * about to be walked properly, and a form left holding `a/b*?"<>` would answer every later OK with
 * the same complaint. Once per dialog CAPTION per run: the matrix is the form's, not the route's.
 */
/** What a new dialog counts as, in classifyDelta's vocabulary: it is an `alert` when its CAPTION
 * says so. `CommonAlertViewImpl` renders a Confirm too, so the body's shape cannot tell them
 * apart — and a Confirm is the presenter proceeding, not refusing. */
const newDialogOutcome = (said) => (isAlertCaption(said?.caption) ? 'alert' : 'dialog');

/**
 * Is focus in an ACE editor RIGHT NOW? `AbstractPopupPanel.onPreviewNativeEvent` skips key handling
 * when the event target carries `ace_text-input`, so this has to be read at the moment of the press
 * — reading it once at the start of a probe missed the click that moved focus INTO the editor.
 */
const aceHasFocus = () => page.evaluate(() =>
  !!document.activeElement?.classList?.contains('ace_text-input')).catch(() => false);

/**
 * A dialog's caption carries its subject — `Filter 'New Field 0'`, `Filter 'New Field 1'` — so a
 * probe keyed on the raw caption pays for every column. Same rule as coverage.mjs:94.
 */
const captionKey = (caption) => String(caption ?? '').replace(/'[^']*'/g, "'…'").replace(/\s+/g, ' ').trim();

/**
 * What a key or a click did to the dialog STACK. Counting dialogs is not enough: an OK that opens
 * the next dialog in a flow REPLACES the one it closed — `Create Processors` › OK becomes
 * `Choose Pipeline To Process Data With` — so the count is 1 before and 1 after and the probe read
 * it as `nothing`. That is how five `Ctrl+Enter did nothing` fails were recorded against a key
 * doing exactly what the OK button does. Compare the captions.
 */
const stackOutcome = (before, after, said) => {
  if (after.length < before.length) return 'closed';
  if (after.length > before.length) return newDialogOutcome(said);
  if (after.length && after.at(-1) !== before.at(-1)) return newDialogOutcome(said);
  return isAlertCaption(said?.caption) && !before.some(isAlertCaption) ? 'alert' : 'nothing';
};

const illegalDone = new Set();
/** Put each box back to the value it held before the probe typed over it. */
async function restoreFields(plan) {
  const now = await readFields(page);
  for (const f of plan) {
    const box = now?.fields?.[f.i];
    if (!box) continue;
    await page.mouse.click(box.x, box.y);
    await page.keyboard.press('Control+A');
    if (f.value) await page.keyboard.type(f.value, { delay: 10 });
    else await page.keyboard.press('Delete');
  }
}
async function probeIllegalValues(node, id) {
  const form = await readFields(page);
  const cap = captionKey(form?.caption ?? '');
  if (!form?.fields?.length || illegalDone.has(cap)) return;
  illegalDone.add(cap);
  // One VARIANT per rule (lib/illegal.mjs): a form reports the first rule that fails, so a single
  // pass with every box illegal only ever exercises one row of its matrix.
  // Plus ONE cleared variant: a form that opens pre-filled, emptied, then OK. The OK checker only
  // presses OK in the state a dialog arrived in, so for an Edit dialog the empty row is untested —
  // and that row is where #43 lived (a cleared API-key expiry left OK dead on a null `Long`).
  const variants = [...planIllegal(form.fields), ...planCleared(form.fields)];
  for (const plan of variants) {
    const fills = plan.slice(0, 6);
    const aimed = fills.find((f) => f.target) ?? fills[0];
    const capsBefore = await readDialogs(page).catch(() => []);
    const nBefore = capsBefore.length;
    if (!nBefore) return;
    const vBefore = guard.violations.length;
    const tStart = Date.now();
    try {
      // Coordinates are re-read for each variant: the boxes were restored between them and a form
      // that grew a message or resized has moved them.
      const now = await readFields(page);
      for (const f of fills) {
        const box = now?.fields?.[f.i] ?? f;
        await page.mouse.click(box.x, box.y);
        await page.keyboard.press('Control+A');
        if (f.text) await page.keyboard.type(f.text, { delay: 10 });
        else await page.keyboard.press('Delete');
      }
      // Did the box TAKE it? A `DateTimeBox` or a numeric spinner may refuse or normalise what was
      // typed, and a variant whose value never landed proves nothing about the form's rules.
      const after = await readFields(page);
      const target = fills.find((f) => f.target);
      const landed = !target || String(after?.fields?.[target.i]?.value ?? '') === String(target.text);
      if (!(await clickAffordance('OK'))) {
        // The values are in the boxes and OK would not take a click: put them back and say nothing
        // more — the node's own OK affordance is about to report the same thing.
        await restoreFields(fills);
        return;
      }
      await settledSurvey();
      const blocked = guard.violations.slice(vBefore).map((v) => `${v.method} ${v.path}`);
      const capsAfter = await readDialogs(page).catch(() => []);
      const nAfter = capsAfter.length;
      const said = nAfter ? await readTopDialogBody(page) : null;
      if (said) said.class = classifyAlert({ ...said, blocked });
      // What OK did, read from the dialog STACK (a flow's next dialog replaces this one, so the
      // count alone says `nothing`), and then inline feedback — PRESENCE, not change, since a
      // message raised a moment earlier by another probe is still the form answering.
      const inline = feedbackShown(await readFeedback(page));
      const stacked = stackOutcome(capsBefore, capsAfter, said);
      const what = stacked === 'nothing' && inline ? 'inline' : stacked;
      const cleared = aimed?.kind === 'cleared';
      const label = cleared
        ? `OK (cleared${aimed?.label ? ` "${aimed.label}"` : ''})`
        : `OK (illegal ${aimed?.kind ?? 'value'}${aimed?.label ? ` in "${aimed.label}"` : ''})`;
      // Did each box actually MOVE? The untargeted boxes are refilled with the value they already
      // hold (`f.value || LEGAL[kind]`), and retyping a box's own value cannot fire a
      // ValueChangeEvent — so crediting that field's value handler would be a lie. This is read
      // from the form, not predicted: a box that normalised what was typed counts as changed, and
      // one the form refused does not.
      const moved = (f) => String(now?.fields?.[f.i]?.value ?? '') !== String(after?.fields?.[f.i]?.value ?? '');
      const check = { kind: 'illegal', landed, inline, cleared,
        plan: fills.map((f) => ({ label: f.label, kind: f.kind, text: f.text, target: !!f.target, asserted: !!f.asserted, expects: f.expects, changed: moved(f) })),
        // The cleared variant has its OWN verdict, and it differs in one place that matters: a dead
        // OK is a FAIL there, where an illegal value with no rule is only unchecked.
        ...(cleared
          ? clearedVerdict({ outcome: what, said, blocked, landed, inline, label: aimed?.label })
          : illegalVerdict({ outcome: what, said, blocked, plan: fills, landed, inline })) };
      affordances.push({ node: id, label, kind: 'illegal', outcome: what,
        detail: fills.map((f) => `${f.label || 'box'}[${f.kind}]${f.target ? '*' : ''}="${f.text}"`).join(', '),
        rows: [], blocked, clickMs: Date.now() - tStart, backMs: 0, ...(said ? { said } : {}), check });
      if (check.verdict === 'fail') checkFails += 1;
      console.log(`      ! ${label} → ${what}  {${cleared ? 'cleared' : 'illegal'} ${check.verdict}: ${check.why}}`);
      // Put it back. An alert first, then the boxes; a form that ACCEPTED the values is gone and
      // the route has to be replayed — and the remaining variants have no form to run against.
      if (nAfter > nBefore) await closeTop(what === 'alert' ? 'alert' : 'dialog');
      if ((await readDialogs(page).catch(() => [])).length >= nBefore) await restoreFields(fills);
      else { await resync(node); return; }
    } catch (e) {
      console.log(`      ! OK (illegal values) — could not drive: ${String(e?.message ?? e).slice(0, 80)}`);
      await resync(node).catch(() => {});
      return;
    }
  }
}

/**
 * The dialog KEYBOARD contract (lib/keys.mjs): Ctrl+Enter is OK, Escape closes. Both are owed by
 * every `AbstractPopupPanel`, so both are checkable here without knowing the presenter. Run once per
 * dialog caption, before the real OK — the Ctrl+Enter outcome is judged against what OK then does,
 * and Escape is judged on its own.
 */
const keysDone = new Set();
async function probeKeys(node, id) {
  const form = await readFields(page);
  const caption = captionKey(form?.caption ?? '');
  if (!caption || keysDone.has(caption)) return null;
  keysDone.add(caption);
  const capsBefore = await readDialogs(page).catch(() => []);
  const nBefore = capsBefore.length;
  if (!nBefore) return null;
  let seen = null;
  // ENTER, in EVERY named box the form offers, up to `ENTER_FIELDS`. There is no contract to check
  // — `KeyBinding:75` maps Enter to EXECUTE and the panel ignores it, so a view may bind it or not —
  // but it is what fires a view's own `addKeyDownHandler`, the 72 `needs-key` handlers still owed.
  // The field travels with each record, because a key handler is fired by a key ON ITS RECEIVER and
  // the inventory joins it by the box's name: pressing Enter in one box of five proves one handler.
  //
  // Enter may also submit the dialog, and then the node has to be put back before the next box. That
  // costs a route replay, so re-syncs are budgeted: two per dialog, after which this gives up and
  // leaves the rest of the boxes for another run rather than spending the walk here.
  let enterResyncs = 0;
  for (const f of (form?.fields ?? []).slice(0, ENTER_FIELDS)) {
    const t = Date.now();
    const vField = guard.violations.length;
    try {
      // Is the FORM still what is in front? A previous field's Enter may have opened a confirm, and
      // Enter on a confirm means YES — the walk accepted "You are about to process all feeds. Are you
      // sure you wish to do this?" that way, and only the read-only guard stopped the write. So the
      // stack is checked before every press, not just repaired after one.
      const front = await readDialogs(page).catch(() => []);
      if (!front.length) {
        console.log('      · Enter — the dialog is gone; the remaining boxes are left for another run');
        break;
      }
      if (front.at(-1) !== capsBefore.at(-1)) {
        console.log(`      · Enter — "${front.at(-1)}" is in front of the form, not pressing into it`);
        await closeTop(/alert/i.test(String(front.at(-1))) ? 'alert' : 'dialog').catch(() => {});
        const back = await readDialogs(page).catch(() => []);
        if (back.at(-1) !== capsBefore.at(-1)) { await resync(node).catch(() => {}); break; }
        continue;
      }
      await page.mouse.click(f.x, f.y);
      // Sampled AFTER the click that focuses the box — that click is what can land in an editor.
      // A box that turns out to be an editor is SKIPPED, not a reason to abandon the probe: this
      // used to `return`, which cost the dialog its Ctrl+Enter and Escape checks as well, so a
      // dialog whose first field was an editor had no keyboard contract checked at all.
      if (await aceHasFocus()) {
        console.log(`      · Enter in "${f.label || 'a box'}" — focus is in an ACE editor, which the panel excludes`);
        continue;
      }
      await page.keyboard.press(KEY_CONTRACT.enter.key);
      await settledSurvey();
      const capsNow = await readDialogs(page).catch(() => []);
      const eSaid = capsNow.length ? await readTopDialogBody(page) : null;
      // The violations are sliced AFTER the alert has been read, not before. An aborted write can
      // surface a little later than the survey, and slicing first left `blocked: []` on an alert that
      // was nothing but the guard's own abort — which `classifyAlert` then had to call an `exception`,
      // and it went to the ledger as a new GWT defect.
      const eBlocked = guard.violations.slice(vField).map((v) => `${v.method} ${v.path}`);
      if (eSaid) eSaid.class = classifyAlert({ ...eSaid, blocked: eBlocked });
      // A dialog can answer INLINE and stay open — `Enter Your Current Password` writes
      // "Password is required" into a `.feedback` label and marks the box invalid. Reading the
      // dialog stack alone calls that `nothing`, which is how a key doing exactly what the OK
      // button does was recorded as a no-op five times over.
      const eInline = feedbackShown(await readFeedback(page));
      const stacked0 = stackOutcome(capsBefore, capsNow, eSaid);
      const outcome = stacked0 === 'nothing' && eInline ? 'inline' : stacked0;
      const ec = { kind: 'key', ...enterVerdict({ outcome, said: eSaid, field: f.label }) };
      affordances.push({ node: id, label: 'Enter', kind: 'key', outcome, field: f.label,
        detail: KEY_CONTRACT.enter.cite, rows: [], blocked: eBlocked, clickMs: Date.now() - t, backMs: 0,
        ...(eSaid ? { said: eSaid } : {}), check: ec });
      console.log(`      · Enter in "${f.label || 'a box'}" → ${outcome}  {key ${ec.verdict}}`);
      // Put the stack back: pop anything Enter added, re-sync anything it closed or replaced.
      if (capsNow.length > nBefore) await closeTop(outcome === 'alert' ? 'alert' : 'dialog');
      const capsBack = await readDialogs(page).catch(() => []);
      if (capsBack.length < nBefore || (capsBack.length && capsBack.at(-1) !== capsBefore.at(-1))) {
        if (enterResyncs >= 2) {
          console.log(`      · Enter closed the dialog twice — the remaining boxes are left for another run`);
          await resync(node).catch(() => {});
          break;
        }
        enterResyncs += 1;
        await resync(node);
        // The re-synced dialog is a NEW set of elements, so the remaining points are stale.
        const again = await readFields(page);
        if (!again?.fields?.length) break;
        for (const [i, nf] of again.fields.entries()) {
          const old = (form.fields ?? [])[i];
          if (old && nf.label === old.label) { old.x = nf.x; old.y = nf.y; }
        }
      }
    } catch (e) {
      console.log(`      ! Enter in "${f.label || 'a box'}" — could not drive: ${String(e?.message ?? e).slice(0, 60)}`);
      await resync(node).catch(() => {});
      break;
    }
  }
  // Ctrl+Enter's own violations only — Enter above may have fired a request of its own.
  const vCtrl = guard.violations.length;
  const aceFocused = await aceHasFocus();
  try {
    await page.keyboard.press(KEY_CONTRACT.ok.key);
    await settledSurvey();
    const blocked = guard.violations.slice(vCtrl).map((v) => `${v.method} ${v.path}`);
    const capsAfter = await readDialogs(page).catch(() => []);
    const nAfter = capsAfter.length;
    const said = nAfter ? await readTopDialogBody(page) : null;
    if (said) said.class = classifyAlert({ ...said, blocked });
    const inline = feedbackShown(await readFeedback(page));
    const stacked = stackOutcome(capsBefore, capsAfter, said);
    const outcome = stacked === 'nothing' && inline ? 'inline' : stacked;
    seen = { outcome, inline, said: outcome === 'nothing' ? null : said, blocked, aceFocused, caption };
    if (nAfter > nBefore) await closeTop('alert');
    if ((await readDialogs(page).catch(() => [])).length < nBefore) await resync(node);
  } catch (e) {
    console.log(`      ! Ctrl+Enter — could not drive: ${String(e?.message ?? e).slice(0, 60)}`);
    await resync(node).catch(() => {});
  }
  return seen;
}

/**
 * Escape closes the dialog. Pressed LAST at a dialog node, because it ends the node — and once per
 * CAPTION, because the contract belongs to the panel class and the re-sync that puts the dialog
 * back costs a route replay a time.
 */
const escapeDone = new Set();
async function probeEscape(node, id) {
  const nBefore = (await readDialogs(page).catch(() => [])).length;
  if (!nBefore) return;
  const cap = captionKey((await readDialogs(page).catch(() => [])).at(-1) ?? '');
  if (escapeDone.has(cap)) return;
  escapeDone.add(cap);
  const t = Date.now();
  const aceFocused = await aceHasFocus();
  await page.keyboard.press(KEY_CONTRACT.escape.key).catch(() => {});
  await settledSurvey();
  const nAfter = (await readDialogs(page).catch(() => [])).length;
  let said = null;
  if (nAfter > nBefore) {
    said = await readTopDialogBody(page);
    if (said) said.class = classifyAlert({ ...said, blocked: [] });
  }
  const check = { kind: 'key', before: nBefore, after: nAfter, aceFocused, ...escapeVerdict({ before: nBefore, after: nAfter, said, aceFocused }) };
  affordances.push({ node: id, label: 'Escape', kind: 'key', outcome: nAfter < nBefore ? 'closed' : nAfter > nBefore ? 'dialog' : 'nothing',
    aceFocused, detail: KEY_CONTRACT.escape.cite, rows: [], blocked: [], clickMs: Date.now() - t, backMs: 0, ...(said ? { said } : {}), check });
  if (check.verdict === 'fail') checkFails += 1;
  console.log(`      · Escape → ${nAfter < nBefore ? 'closed' : nAfter > nBefore ? 'a dialog' : 'nothing'}  {key ${check.verdict}: ${check.why}}`);
  if (nAfter > nBefore) await closeTop('alert');
  if ((await readDialogs(page).catch(() => [])).length < nBefore) await resync(node).catch(() => {});
}

/**
 * The MOUSE gestures (lib/mouse.mjs) — § B3's fourth driver.
 *
 * Three contracts, each owned by ONE class that every screen shares, which is why a handful of
 * samples settles them rather than a gesture per node:
 *
 *   Dialog / ResizableDialog   press-move-release on the caption moves it; a handle resizes it
 *   Spinner                    an arrow's press, release, hover and leave
 *
 * Both are bounded: a dialog's drag is tried once per CAPTION and at most DRAGS times in a walk,
 * and the spinners once per label per screen. The gestures are put back — a drag by its reverse, a
 * press by the opposite arrow — but a spinner press marks the document dirty, exactly as the dirty
 * driver does, and the walker's tab-close machinery already answers the discard prompt.
 */
const dragsDone = new Set();
const spunDone = new Set();
const rangeDone = new Set();
const ctxDone = new Set();
const ctxSpent = new Map();
/** How many context MENUS to walk per target kind — the items, not just the handler. */
const ctxMenuSpent = new Map();
let dragBudget = Number(env('DRAGS', '12'));
async function probeMouse(node, id) {
  // ── the dialog's drag and resize ──
  if (node.kind === 'dialog' && dragBudget > 0) {
    const box = await readDialogBox(page);
    if (box?.at && !dragsDone.has(box.caption)) {
      dragsDone.add(box.caption);
      dragBudget -= 1;
      const t = Date.now();
      const ev = await dragDialog(page, box).catch(() => null);
      const check = { kind: 'mousedown', gesture: 'drag', ...dragVerdict(ev), evidence: ev };
      affordances.push({ node: id, label: 'drag by the caption', kind: 'mousedown',
        outcome: ev?.got && (ev.got.dx || ev.got.dy) ? 'moved' : 'nothing',
        detail: 'Dialog:185-236 beginDragging/continueDragging/endDragging + popupPanel-dragGlass',
        rows: [], blocked: [], clickMs: Date.now() - t, backMs: 0, check });
      if (check.verdict === 'fail') checkFails += 1;
      console.log(`      ⇄ drag "${box.caption}" → ${check.why}  {mousedown ${check.verdict}}`);
      // The drag may have left the dialog a few pixels off; re-read before resizing it.
      const again = await readDialogBox(page);
      if (again?.se) {
        const t2 = Date.now();
        const rev = await resizeDialog(page, again).catch(() => null);
        const rcheck = { kind: 'mousemove', gesture: 'resize', ...resizeVerdict(rev), evidence: rev };
        affordances.push({ node: id, label: 'resize by the SE handle', kind: 'mousemove',
          outcome: rev?.got && (rev.got.dw || rev.got.dh) ? 'resized' : 'nothing',
          detail: 'ResizableDialog:171-190 beginDragging picks the handle; continueDragging needs isResizable()',
          rows: [], blocked: [], clickMs: Date.now() - t2, backMs: 0, check: rcheck });
        if (rcheck.verdict === 'fail') checkFails += 1;
        console.log(`      ⇲ resize "${again.caption}" → ${rcheck.why}  {mousemove ${rcheck.verdict}}`);
      }
    }
  }
  // ── the value spinners on whatever is in front ──
  const spinners = (await readSpinners(page)).slice(0, 2);
  for (const sp of spinners) {
    const key = `${node.path[node.path.length - 1]} :: ${sp.label}`;
    if (spunDone.has(key)) continue;
    spunDone.add(key);
    const t = Date.now();
    const ev = await spinnerGestures(page, sp).catch(() => null);
    for (const v of spinnerVerdicts(ev, sp.label)) {
      const check = { kind: v.kind, verdict: v.verdict, why: v.why, field: sp.label, evidence: ev };
      affordances.push({ node: id, label: `${v.kind} on "${sp.label}" spinner`, kind: v.kind, field: sp.label,
        outcome: v.verdict === 'pass' ? 'changed' : 'nothing',
        detail: 'Spinner:104-137 arrow classes valueSpinner-arrowUp{Hover,Pressed}',
        rows: [], blocked: [], clickMs: Date.now() - t, backMs: 0, check });
      if (v.verdict === 'fail') checkFails += 1;
    }
    const vs = spinnerVerdicts(ev, sp.label);
    // A spinner press that MOVED the value is also a value handler firing: `Spinner.increase():320`
    // ends in `ValueChangeEvent.fire(this, value)`. Recording only the mouse verdicts left the
    // spinner's own `value` handler uncredited and, worse, left the whole PANEL untyped — the AI
    // Configure tabs are driven by nothing else, so `typedOn` never learned their field labels and
    // their views stayed `needs-value` however often the arrows were pressed.
    if (ev && !ev.skipped && ev.pressed && ev.rest && ev.pressed.value !== ev.rest.value) {
      affordances.push({ node: id, label: `stepped the spinner "${sp.label}"`, kind: 'value', field: sp.label,
        outcome: 'changed', detail: 'Spinner:320 increase() ends in ValueChangeEvent.fire(this, value)',
        rows: [], blocked: [], clickMs: Date.now() - t, backMs: 0,
        check: { kind: 'value', before: ev.rest.value, after: ev.pressed.value, verdict: 'pass',
          why: `the spinner "${sp.label}" moved ${ev.rest.value} -> ${ev.pressed.value}, so its ValueChangeEvent fired`,
          plan: [{ label: sp.label, kind: 'spinner', text: 'stepped the spinner', target: true, changed: true }] } });
    }
    console.log(`      ↕ spinner "${sp.label}" → ${vs.map((v) => `${v.kind}=${v.verdict}`).join(' ')}`);
  }
  // ── right-click (lib/context.mjs) ──
  // Once per screen per TARGET KIND. Verified by probe on three kinds: an explorer row opens the
  // document menu, an ACE editor opens its own (Styles / Line Numbers / Wrap Lines), and a grid row
  // opens Copy / Export Table. Silence is `unchecked`, never a fail — a registration says a handler
  // exists on SOME element of a view, not that every element answers.
  for (const t of await findTargets(page)) {
    // A BUDGET per target kind, not per screen. The explorer row and the grid row are present on
    // almost every screen, so the per-screen key spent 52 right-clicks proving `NavigationPresenter`'s
    // one handler and 48 more on the grid's — a hundred gestures and two minutes a shard for evidence
    // already in hand. The handler is what is being shown; a handful of screens shows it.
    const spent = ctxSpent.get(t.what) ?? 0;
    if (spent >= CONTEXTS) continue;
    const ckey = `${node.path[node.path.length - 1]} :: ${t.what}`;
    if (ctxDone.has(ckey)) continue;
    ctxDone.add(ckey);
    ctxSpent.set(t.what, spent + 1);
    const tc = Date.now();
    const ev = await rightClick(page, t).catch(() => null);
    const v = contextVerdict(ev);
    affordances.push({ node: id, label: `right-click ${t.what}`, kind: 'context',
      outcome: v.verdict === 'pass' ? 'menu' : 'nothing',
      detail: 'a context handler that runs puts a .menuItem-outer menu on screen',
      rows: [], blocked: [], clickMs: Date.now() - tc, backMs: 0, check: { kind: 'context', ...v, evidence: ev } });
    if (v.verdict === 'fail') checkFails += 1;
    if (v.verdict !== 'unchecked') console.log(`      ⊿ right-click ${t.what} → ${v.why.slice(0, 100)}  {context ${v.verdict}}`);
  }
  // ── the pager's row range (lib/range.mjs) ──
  // Once per screen that HAS a multi-page grid: the range editor is `Pager`'s, one class shared by
  // every grid in the application, so a handful of screens settles it. Eight handlers ride on this
  // one gesture — lblFrom and lblTo's clicks, txtFrom/txtTo's Enter, and four focus/blur.
  const rkey = node.path.join(' / ');
  if (!rangeDone.has(rkey)) {
    const r = await readRange(page);
    if (r && r.of && r.of > (r.to ?? 1) - (r.from ?? 1) + 1) {
      rangeDone.add(rkey);
      const t = Date.now();
      const rev = await jumpRange(page, r).catch(() => null);
      const check = { kind: 'key', gesture: 'range', ...rangeVerdict(rev), evidence: rev };
      affordances.push({ node: id, label: 'type a row number in the pager', kind: 'key', field: 'txtFrom',
        outcome: check.verdict === 'pass' ? 'changed' : 'nothing',
        detail: 'Pager:132 txtFrom ENTER -> setEditing(false) -> fireMoveEvent',
        rows: [], blocked: [], clickMs: Date.now() - t, backMs: 0, check });
      if (check.verdict === 'fail') checkFails += 1;
      console.log(`      ⇥ pager range → ${check.why}  {key ${check.verdict}}`);
    }
  }
}

/**
 * Does typing on this editor TAB mark the document dirty (lib/dirty.mjs)? The oracle is
 * `DocTabPresenter:274` — `saveButton.setEnabled(isDirty())` — so Save's state IS the document's.
 *
 * Once per tab signature per run, and EVERY labelled field up to `FIELDS`. It drives two questions
 * at once because they need different numbers of fields:
 *
 *   dirty  is answered once per tab, and only while the document is still clean — Save is one-way,
 *          so no later field can be told from the first.
 *   value  is answered per field, by whether the control itself moved, which stays readable however
 *          dirty the document is. That is what `addValueChangeHandler` fires on.
 */
const dirtyDone = new Set();
async function probeDirty(node, id) {
  if (!['editor', 'editor-tab'].includes(node.kind)) return;
  const key = node.path.join(' / ');
  if (dirtyDone.has(key)) return;
  const before = await readSave(page);
  // No Save button and no dialog in front: not a document editor, nothing to say.
  const dialogsUp = (await readDialogs(page).catch(() => [])).length > 0;
  if (!before && !dialogsUp) return;
  // VALUE-ONLY mode for a tab inside a dialog. There is no Save button to read, so the `dirty`
  // question cannot be asked at all — but every field is still a `value` handler, and in this scope
  // nothing else drives them: Ask Stroom AI's Configure tabs were touched by the spinner gesture and
  // by nothing else, so their views stayed `needs-value` however often the walk visited them.
  const valueOnly = !before;
  dirtyDone.add(key);
  const fields = (await readTabFields(page, { inDialogScope: valueOnly })).slice(0, FIELDS);
  if (!fields.length) return;
  /** The dialog stack as the loop found it, so an alert raised by a field can be told apart. */
  const fieldCaps = await readDialogs(page).catch(() => []);
  for (const f of fields) {
    const t = Date.now();
    const saveBefore = await readSave(page);
    try {
      const how = await nudge(page, f);
      if (!how) continue;
      await page.waitForTimeout(500);
      const saveAfter = await readSave(page);
      // Did the CONTROL move? That is what a `value` handler fires on, and it is readable per field
      // however dirty the document already is — which is the whole reason this loop no longer stops
      // at the first success. `FeedSettingsPresenter` owns ten value handlers; typing in one box and
      // breaking left the other nine looking unreachable for as long as this driver has existed.
      // An ALERT may have arrived: some fields save immediately — "Dock Type" fires
      // `POST /api/preferences/v1` the moment it changes — and the guard aborts that, which puts an
      // error popup over the form. Left there, every later field reads inside the ALERT and comes back
      // `unchecked`, and the walk carries on with the wrong thing in front. Close it first.
      const capsNow = await readDialogs(page).catch(() => []);
      if (capsNow.length > fieldCaps.length) {
        await closeTop('alert').catch(() => {});
        await page.waitForTimeout(300);
      }
      // Re-read by LABEL, with the index only as a fallback: a form that grew a message has moved its
      // fields, so `[f.i]` can be a different box than the one just driven.
      const allNow = await readTabFields(page, { inDialogScope: valueOnly });
      const now = allNow.find((x) => x.label === f.label) ?? allNow[f.i];
      const vcheck = { kind: 'value', before: f.value, after: now?.value ?? null,
        plan: [{ label: f.label, kind: f.kind, text: how, target: true,
          changed: String(f.value ?? '') !== String(now?.value ?? '') }],
        ...valueVerdict({ before: f.value, after: now?.value ?? null, label: f.label, kind: f.kind, how }) };
      affordances.push({ node: id, label: `${how} "${f.label}"`, kind: 'value', field: f.label,
        outcome: vcheck.verdict === 'pass' ? 'changed' : 'nothing',
        detail: 'a ValueChangeEvent fires when the widget\'s value changes',
        rows: [], blocked: [], clickMs: Date.now() - t, backMs: 0, check: vcheck });
      // The DIRTY question is answered once per tab, and only while the document is still clean:
      // Save is one-way, so a second field cannot be told from the first. Recording it for an
      // already-dirty tab would be a verdict about nothing.
      if (!valueOnly && !saveBefore?.enabled) {
        const check = { kind: 'dirty', before: saveBefore, after: saveAfter,
          plan: [{ label: f.label, kind: f.kind, text: how, target: true }],
          ...dirtyVerdict({ before: saveBefore, after: saveAfter, field: f.label, how }) };
        affordances.push({ node: id, label: `${how} "${f.label}"`, kind: 'dirty', field: f.label,
          outcome: saveAfter?.enabled ? 'changed' : 'nothing', detail: 'DocTabPresenter:274 saveButton.setEnabled(isDirty())',
          rows: [], blocked: [], clickMs: Date.now() - t, backMs: 0, check });
        if (check.verdict === 'fail') checkFails += 1;
        console.log(`      ✎ ${how} "${f.label}" [${f.kind}] → Save ${saveAfter?.enabled ? 'enabled' : 'still disabled'}`
          + `  {dirty ${check.verdict} · value ${vcheck.verdict}}`);
      } else {
        console.log(`      ✎ ${how} "${f.label}" [${f.kind}]  {value ${vcheck.verdict}}`);
      }
      // ENTER in this box, while it still holds the typed value. `probeKeys` presses Enter only in a
      // DIALOG — `readFields` scopes to the topmost popup — so a settings TAB's boxes have never had a
      // key event at all, and `SolrIndexSettingsViewImpl` owes five key handlers on boxes this driver
      // types in on every walk. A view may bind Enter or not (`KeyBinding:75` has the panel ignore
      // it), so there is nothing to fail: the field travels with the record and the inventory joins it
      // to the handler's receiver by name.
      if (f.kind === 'text' && now && now.kind === 'text') {
        const tk = Date.now();
        const capsK = await readDialogs(page).catch(() => []);
        await page.mouse.click(now.x, now.y);
        await page.keyboard.press(KEY_CONTRACT.enter.key);
        await page.waitForTimeout(400);
        const capsAfterK = await readDialogs(page).catch(() => []);
        const grewK = capsAfterK.length > capsK.length;
        const saidK = grewK ? await readTopDialogBody(page) : null;
        if (saidK) saidK.class = classifyAlert({ ...saidK, blocked: [] });
        const outcomeK = grewK ? (isAlertCaption(capsAfterK.at(-1)) ? 'alert' : 'dialog') : 'nothing';
        affordances.push({ node: id, label: 'Enter', kind: 'key', outcome: outcomeK, field: f.label,
          detail: KEY_CONTRACT.enter.cite, rows: [], blocked: [], clickMs: Date.now() - tk, backMs: 0,
          ...(saidK ? { said: saidK } : {}),
          check: { kind: 'key', ...enterVerdict({ outcome: outcomeK, said: saidK, field: f.label }) } });
        if (grewK) await closeTop(outcomeK === 'alert' ? 'alert' : 'dialog').catch(() => {});
      }
      // A text box is put back so the tab reads as it did. A tick, a selection or a spinner step is
      // left: the document is dirty regardless, and the walker's tab-close machinery answers the
      // discard prompt.
      if (f.kind === 'text' && now && now.kind === 'text') {
        await page.mouse.click(now.x, now.y);
        await page.keyboard.press('Control+A');
        if (f.value) await page.keyboard.type(f.value, { delay: 10 });
        else await page.keyboard.press('Delete');
        await page.keyboard.press('Tab');
      }
    } catch (e) {
      console.log(`      ✎ "${f.label}" [${f.kind}] — could not drive: ${String(e?.message ?? e).slice(0, 70)}`);
      break;
    }
  }
}

/**
 * Explore `node`, standing at it, with `state` the survey taken on arrival. Returns nothing; every
 * child is walked in place and popped. On return the page is NOT guaranteed to be at `node` — the
 * caller pops or re-syncs, because only the caller knows what it wants next.
 */
async function walk(node, state, arrivedAt) {
  const id = idOf(node);
  /** What this node OFFERS: its own menu items for a submenu, the survey's buttons otherwise. */
  const items = node.items ?? [...state.buttons, ...(node.extra ?? [])];
  const disabledItems = node.disabledItems ?? state.disabled;
  const rec = { id, slug: slugOf(node), kind: node.kind, path: node.path, route: node.route,
    presenter: node.presenter, recipe: node.recipe, status: 'reached',
    buttons: items.length, disabledAtRest: disabledItems, dialogs: state.dialogs,
    menu: state.menu,
    blockedByAlert: (node.kind === 'editor' || node.kind === 'screen')
      && state.dialogs.some((d) => /alert/i.test(d)),
    // Any dialog over a ROOT that the route did not open — a leftover Confirm, a load error — means
    // this record is of the dialog, not the editor. Said on the record so a "duplicate of Confirm"
    // cannot pass for a covered editor again.
    blockedByDialog: (node.kind === 'editor' || node.kind === 'screen') && state.dialogs.length > 0
      ? state.dialogs.join('+') : undefined,
    gridRows: (state.grids ?? []).map((g) => g.rows),
    ms: Date.now() - arrivedAt };
  const sig = signatureOf(node, state);
  const twin = explored.get(sig);
  if (twin) {
    rec.status = 'duplicate';
    rec.duplicateOf = twin;
    lastProgressAt = Date.now();
    nodes.push(rec);
    await memSample(rec);
    console.log(`  = ${id} — same place as "${twin}", not re-explored`);
    return;
  }
  explored.set(sig, id);
  lastProgressAt = Date.now();
  nodes.push(rec);
  await memSample(rec);
  console.log(`  ${rec.blockedByAlert ? '!' : '✓'} ${id} — ${items.length} actions, `
    + `${disabledItems.length} disabled${rec.blockedByAlert ? '  [ALERT over the target — NOT the screen]' : ''}`);

  if (DEPTH !== 'reach') {
    await settleAndShoot(page, join(OUT, 'evidence', `${slugOf(node)}.png`), {
      wait: 1500, viewportHeight: VH, domFile: join(OUT, 'evidence', `${slugOf(node)}.html.gz`),
    });
    if (DEPTH === 'dom') await import('node:fs/promises').then((fs) =>
      fs.rm(join(OUT, 'evidence', `${slugOf(node)}.png`), { force: true }));
  }

  // Sub-tabs are siblings: walk each by clicking across, no pop needed between them. An EDITOR's,
  // and — since pass 6 — a SCREEN's and a DIALOG's too: Data Receipt Rules is Rules / Fields /
  // Documentation, Data Retention is Rules / Impact Summary, a dashboard component's Settings is
  // Basic / Conditional Formatting / Selection Filter. The survey reads the selected pane only, so a
  // tab never clicked is a surface never seen — `New Field` (FieldEditPresenter), the impact
  // summary's `Filter` (EditExpressionPresenter) and Selection Filter's Add (SelectionHandler-
  // Presenter) all sat one tab across from where every walk had stood.
  if (['editor', 'screen', 'dialog'].includes(node.kind) && !rec.blockedByAlert) {
    const tabs = await readSubTabs({ inDialog: node.kind === 'dialog' }).catch(() => []);
    if (tabs.length) console.log(`      + ${tabs.length} sub-tab(s): ${tabs.join(', ')}`);
    const nth = new Map();
    for (const tab of tabs) {
      if (nodes.length >= MAXNODES) { capped = true; return; }
      const i = nth.get(tab) ?? 0;
      nth.set(tab, i + 1);
      const child = { kind: 'editor-tab', presenter: node.presenter, seedDepth: node.seedDepth,
        path: [...node.path, i ? `${tab} #${i + 1}` : tab],
        route: [...node.route, { via: 'tab', label: tab, nth: i }] };
      const t0 = Date.now();
      try {
        // A DIALOG's tabs live inside the dialog: clearing overlays would close the very thing
        // whose tabs these are — and so would `dismissMenus`, whose Escape a dialog answers too —
        // so nothing is cleared there. And the previous tab's own OK/Cancel CLOSED the dialog, so
        // if it is not up any more, replay the route that opened it.
        if (node.kind !== 'dialog') await clearOverlays();
        else if (!(await readDialogs(page).catch(() => [])).length) await resync(node);
        await driveStep(child.route.at(-1));
      } catch (e) {
        nodes.push({ id: idOf(child), slug: slugOf(child), kind: child.kind, path: child.path,
          status: 'unreachable', ms: Date.now() - t0, presenter: child.presenter,
          why: String(e?.message ?? e).slice(0, 100) });
        console.log(`  ✗ ${idOf(child)} — ${String(e?.message ?? e).slice(0, 70)}`);
        continue;
      }
      await walk(child, await settledSurvey(), t0);
    }
    // Back on the editor itself for its own toolbar. A sub-tab walk leaves the cursor wherever the
    // last tab's last pop left it; the editor's affordances are the same from any tab, so re-arm
    // by returning to the first tab rather than replaying the whole route.
    if (tabs.length) {
      if (node.kind !== 'dialog') await clearOverlays();
      else if (!(await readDialogs(page).catch(() => [])).length) await resync(node).catch(() => {});
      await clickEditorTab(page, tabs[0], { nth: 0 }).catch(() => {});
    }
  }

  let current = state;
  // Does typing here mark the document dirty (§ B3's second driver)? Editor tabs only, and before
  // the affordances are walked, so the tab is in the state the ledger recorded.
  if (['editor', 'editor-tab'].includes(node.kind)) {
    await probeDirty(node, id).catch(() => {});
    current = await settledSurvey({ lead: 0 });
  }
  // The mouse gestures (§ B3's fourth driver). After the dirty probe, because a spinner press marks
  // the document dirty too and the dirty probe wants a clean tab; before the affordance loop,
  // because a drag moves every click point the survey recorded — hence the re-survey.
  if (['editor', 'editor-tab', 'dialog', 'screen'].includes(node.kind)) {
    await probeMouse(node, id).catch(() => {});
    current = await settledSurvey({ lead: 0 });
  }
  // Escape closes a dialog — the other half of the framework's keyboard contract (lib/keys.mjs).
  // FIRST, not last: a dialog's last affordance is almost always the Cancel that closes it, and the
  // walker returns straight from that ("already closed"), so a probe after the loop never ran. The
  // probe re-syncs the dialog it closed, so the node is intact for the loop below.
  if (node.kind === 'dialog') {
    await probeEscape(node, id).catch(() => {});
    current = await settledSurvey({ lead: 0 });
    // If the re-sync inside the probe did not get the dialog back, STOP: every affordance below
    // would be clicked on whatever is there instead, and the record would say it was this dialog.
    if (!current.dialogs.length) {
      findings.push({ node: id, label: 'Escape', outcome: 'closed', kind: 'resync-failed', expected: sig, observed: signatureOf(node, current),
        detail: 'Escape closed the dialog and the route replay did not re-open it — the node was not walked' });
      console.log('      ‼ Escape closed the dialog and it did not come back — node skipped');
      return;
    }
  }
  // What Ctrl+Enter did at this dialog, waiting for OK to say what it should have done.
  let keySaw = null;
  for (const label of items) {
    if (nodes.length >= MAXNODES) { capped = true; return; }
    if (LEAVES_THE_PAGE.has(label)) {
      affordances.push({ node: id, label, kind: 'skipped', outcome: 'not clicked',
        detail: 'navigates away or downloads — see LEAVES_THE_PAGE', rows: [], blocked: [] });
      continue;
    }
    if (SELF_DESTRUCTIVE.has(label)) {
      affordances.push({ node: id, label, kind: 'button', outcome: 'skipped',
        detail: 'ends the walk session or destroys the seeded row — see SELF_DESTRUCTIVE', rows: [], blocked: [] });
      continue;
    }
    // Chrome is app-level — the explorer's New/Delete, the main menu, the AI dock — and is covered
    // once per run. But a screen's OWN `New` or `Delete` button is that screen's affordance: the
    // volume, node-group, profile and user editors all open from one, and treating every `New`
    // as the explorer's hid eight create dialogs. So a chrome label counts as chrome only when the
    // element sits in the explorer / navigation pane.
    if (CHROME.has(label)) {
      const inExplorer = await page.evaluate((wanted) => {
        const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
        const els = [...document.querySelectorAll('button, [role=button], .Button')].filter((b) => b.offsetWidth > 0
          && (norm(b.getAttribute('title')) === wanted || norm(b.getAttribute('aria-label')) === wanted || norm(b.textContent) === wanted));
        if (!els.length) return true;
        return els.every((b) => !!b.closest('.navigationTree, .explorerTree, [class*=navigation], [class*=explorer]'));
      }, label).catch(() => true);
      if (inExplorer) {
        if (chromeDone.has(label)) continue;
        chromeDone.add(label);
      }
    }
    const key = `${id} :: ${label}`;
    if (seen.has(key)) continue;
    seen.add(key);

    // Before the real OK, drive the form's own rules with values it cannot hold (§ B3). Not the
    // explorer's create dialog — its OK is a server create the guard aborts, and the seed script is
    // how documents are made — and not a state this walk reached BY answering an OK.
    if (node.kind === 'dialog' && label === 'OK'
      && !/^New /.test(String(state.dialogs.at(-1) ?? '')) && !/after OK \(/.test(node.path.at(-1) ?? '')) {
      keySaw = await probeKeys(node, id);
      await probeIllegalValues(node, id);
      current = await settledSurvey({ lead: 0 });
    }

    // Are we still standing at the node? The previous affordance was popped and asserted, or this is
    // the first one. If a pop failed the loop re-synced already, so `current` is trustworthy here.
    const vBefore = guard.violations.length;
    const before = current;
    const tabsBefore = await contentTabs();
    // Timed in two halves, because "12s per affordance" says nothing about whether the click and
    // its settle or the way back is what costs. Written onto the affordance record.
    const tClick = Date.now();
    let tBack;
    let result;
    let after = null;
    // The oracle for a CHECKER (lib/checks.mjs, BEHAVIOUR-PLAN.md § B2), captured before the click:
    // a sort header's column and direction, a pager button's from/to/of; Refresh compares surveys.
    const probe = isColumnHeader(label) ? { kind: 'sort', before: await readColumn(page, columnName(label)) }
      : PAGER_BUTTONS.has(label) ? { kind: 'pager', before: await readPager(page, label) }
        : label === 'Refresh' ? { kind: 'refresh' } : null;
    // Not every validation message is a dialog: the identity views write into a `.feedback` label
    // and mark the box `invalid` (lib/checks.mjs). Captured before the click, read after.
    const fbBefore = node.kind === 'dialog' && label === 'OK' ? await readFeedback(page) : null;
    try {
      if (await clickAffordance(label)) {
        after = await settledSurvey();
        result = classifyDelta(before, after);
        // A click that changed the screen may have started a load that has not landed: `after Data
        // Retention` was surveyed with 10 buttons and had 14 once its rules grid arrived, so every
        // pop on it then "failed". Wait for READY, not PRESENT — the rule driveStep already applies.
        if (result.outcome === 'changed') {
          await waitForStableCount(page, () => page.evaluate(() =>
            document.querySelectorAll('.dataGridRow, .dataGridCell, tr, [role=row]').length),
          { budget: 1_500, poll: 150, stableFor: 2, label: `content:${label}`, kind: 'content', allowZero: true })
            .catch(() => {});
          after = await settledSurvey({ lead: 0 });
          result = classifyDelta(before, after);
        }
      } else {
        result = { outcome: 'vanished', detail: 'present when surveyed, not locatable when clicked' };
      }
    } catch (e) {
      result = /Timeout|timeout/.test(String(e?.message))
        ? { outcome: 'unclickable', detail: 'did not accept a click within 3s' }
        : { outcome: 'error', detail: String(e?.message ?? e).slice(0, 80) };
    }
    const blocked = guard.violations.slice(vBefore).map((v) => `${v.method} ${v.path}`);
    const rowsAt = (before?.grids ?? []).map((g) => g.rows);
    if (result.outcome === 'nothing' && isColumnHeader(label)) {
      result.detail = rowsAt.length ? `grid rows: ${rowsAt.join(', ')}` : 'no visible grid';
    }
    // What the dialog SAID (BEHAVIOUR-PLAN.md § B0). An alert's message, hidden detail and icon,
    // and its class; a plain dialog's body. `alert` outcomes used to carry the caption only, which
    // is how a server NPE (gwt-bugs #36) sat in a crawl file as `Alert` until a probe read it.
    let said = null;
    if (result.outcome === 'alert' || result.outcome === 'dialog') {
      said = await readTopDialogBody(page);
      if (said && result.outcome === 'alert') said.class = classifyAlert({ ...said, blocked });
    }
    // The checker's verdict, from the oracle and what is there now.
    let check = null;
    // OK on a DIALOG: it must do one of three things — close (accepted, or a client-side add),
    // raise a VALIDATION message, or be blocked by the guard. Silence with the dialog still open
    // is a dead OK (#40: both buttons left disabled); an exception is #37 / #38.
    if (node.kind === 'dialog' && label === 'OK' && after) {
      const cls = said?.class;
      check = blocked.length ? { kind: 'ok', verdict: 'unchecked', why: 'the guard blocked the write' }
        : result.outcome === 'closed' ? { kind: 'ok', verdict: 'pass', why: 'accepted' }
          : result.outcome === 'alert' && cls === 'validation' ? { kind: 'ok', verdict: 'pass', why: `validation: ${said.text.slice(0, 60)}` }
            : result.outcome === 'alert' && cls === 'exception' ? { kind: 'ok', verdict: 'fail', why: `exception: ${said.text.slice(0, 80)}` }
              : result.outcome === 'alert' ? { kind: 'ok', verdict: 'pass', why: `${cls}: ${said?.text?.slice(0, 60) ?? ''}` }
                : result.outcome === 'nothing' ? await (async () => {
                  // PRESENCE, not change: an earlier Ctrl+Enter may have raised the same message.
                  const fbAfter = await readFeedback(page);
                  const inline = feedbackShown(fbAfter);
                  return inline ? { kind: 'ok', verdict: 'pass', why: `validation, inline: ${inline.slice(0, 80)}`, before: fbBefore, after: fbAfter }
                    : { kind: 'ok', verdict: 'fail', why: 'OK did nothing: the dialog is still open with no message', before: fbBefore, after: fbAfter };
                })()
                  : result.outcome === 'dialog' ? { kind: 'ok', verdict: 'pass', why: `opened ${result.detail}` }
                    : { kind: 'ok', verdict: 'unchecked', why: result.outcome };
      // Filter › OK: what it filters must not GROW. The count is a weak subset (the surveys keep
      // counts and first rows, not every row); a stronger one needs the rows captured before the
      // Filter button was pressed, which is the parent's business.
      if (check.verdict === 'pass' && result.outcome === 'closed' && isFilterAction(node.route.at(-1)?.label)) {
        const rows = (st) => (st?.grids ?? []).reduce((n, g) => n + (g.filled ?? 0), 0);
        check = rows(after) <= rows(before) ? { kind: 'filter', verdict: 'pass', why: `${rows(before)} row(s) → ${rows(after)}` }
          : { kind: 'filter', verdict: 'fail', why: `rows grew on Filter › OK: ${rows(before)} → ${rows(after)}` };
      }
      if (check.verdict === 'fail') checkFails += 1;
      // Ctrl+Enter was pressed at this dialog before OK was clicked; now OK has said what it does,
      // the framework's contract can be judged (lib/keys.mjs).
      if (keySaw) {
        const kc = { kind: 'key', ...ctrlEnterVerdict({ keyOutcome: keySaw.outcome, okOutcome: result.outcome,
          keySaid: keySaw.said, aceFocused: keySaw.aceFocused, okBlocked: blocked.length > 0, keyBlocked: keySaw.blocked.length > 0 }) };
        affordances.push({ node: id, label: 'Ctrl+Enter', kind: 'key', outcome: keySaw.outcome,
          okOutcome: result.outcome, aceFocused: keySaw.aceFocused, inline: keySaw.inline ?? null,
          detail: KEY_CONTRACT.ok.cite, rows: [], blocked: keySaw.blocked, clickMs: 0, backMs: 0,
          ...(keySaw.said ? { said: keySaw.said } : {}), check: kc });
        if (kc.verdict === 'fail') checkFails += 1;
        console.log(`      · Ctrl+Enter → ${keySaw.outcome}  {key ${kc.verdict}: ${kc.why}}`);
        keySaw = null;
      }
    }
    if (probe && after && !blocked.length) {
      // The captures travel with the verdict, so the ledger can re-judge a recorded click under a
      // corrected checker without re-running the walk (as it re-classes alerts).
      if (probe.kind === 'sort') { const now = await readColumn(page, columnName(label)); check = { kind: 'sort', before: probe.before, after: now, ...sortVerdict(probe.before, now) }; }
      else if (probe.kind === 'pager') { const now = await readPager(page, label); check = { kind: 'pager', before: probe.before, after: now, ...pagerVerdict(label, probe.before, now) }; }
      else if (probe.kind === 'refresh') check = { kind: 'refresh', before: before.grids, after: after.grids, ...refreshVerdict(before, after) };
      if (check?.verdict === 'fail') checkFails += 1;
    }
    const affRec = { node: id, label, kind: isColumnHeader(label) ? 'column-sort' : 'button',
      ...result, rows: rowsAt, blocked, clickMs: Date.now() - tClick, backMs: 0,
      ...(said ? { said } : {}), ...(check ? { check } : {}) };
    affordances.push(affRec);
    console.log(`      ${mark[result.outcome] ?? '?'} ${label}${result.detail ? ` → ${result.detail}` : ''}`
      + `${blocked.length ? `  [blocked: ${blocked.join('; ')}]` : ''}`
      + `${said?.class ? `  [${said.class}${said.icon ? `/${said.icon}` : ''}: ${said.text.slice(0, 90)}]` : ''}`
      + `${check ? `  {${check.kind} ${check.verdict}${check.verdict !== 'pass' ? `: ${check.why}` : ''}}` : ''}`);

    // ── A CLIENT-SIDE ADD (BEHAVIOUR-PLAN.md § B2, first job) ──
    // A list that fills from its own Add — conditional-formatting rules, selection handlers, index
    // fields, notifications, execution schedules, pathways, constraints, links — writes nothing
    // until the document is saved, so its `Add › OK` closes the dialog and a row appears, and the
    // guard has nothing to say. The handler inventory found nearly every unreached click handler in
    // a presenter is Edit / Remove / Up / Down on exactly such a list, empty here. So when an OK
    // in a create dialog closed it and the grid GREW, the state we are now standing in is a node:
    // walk it here (no re-sync — this IS its state), depth unspent so the selection rule descends.
    const sumRows = (st) => (st?.grids ?? []).reduce((n, g) => n + (g.filled ?? 0), 0);
    if (node.kind === 'dialog' && label === 'OK' && result.outcome === 'closed' && !blocked.length && after
      && /^(Add|New|Create)\b|\bAdd\b/.test(String(node.route.at(-1)?.label ?? '')) && sumRows(after) > sumRows(before)) {
      const child = { kind: 'post-action', path: [...node.path, 'after OK (a row added client-side)'],
        route: [...node.route, { via: 'button', label: 'OK', post: true, opens: 'changed' }] };
      child.seedDepth = child.route.length;
      const childId = idOf(child);
      if (!seen.has(childId) && nodes.length < MAXNODES) {
        seen.add(childId);
        console.log(`      + client-side add: ${sumRows(before)} -> ${sumRows(after)} row(s); walking the list with its new row`);
        await walk(child, after, Date.now());
      }
    }

    // ── A VALIDATION alert on OK, in a dialog with an empty text box (BEHAVIOUR-PLAN.md § B3, the
    // first row of every form's matrix): "You must provide a name" is the form working; what the
    // form does with a name is the next behaviour. Close the alert, fill the first empty text box
    // with a seed value, press OK again, and walk what that reaches — a client-side list with its
    // new row (Index Fields' `New Field`), or the guard's block on the create.
    // Not for the explorer's own create dialog (`New › <category> › <type>`): its OK is a server
    // create the guard will block, twenty-six times over, and seed.mjs is how documents are made.
    const explorerCreate = node.path.some((p, i) => p === 'New' && i < node.path.length - 1) && /^New /.test(String(state.dialogs.at(-1) ?? ''));
    if (node.kind === 'dialog' && label === 'OK' && result.outcome === 'alert' && said?.class === 'validation'
      && !blocked.length && !explorerCreate && !/after OK \(/.test(node.path.at(-1) ?? '')) {
      const hasEmpty = await page.evaluate(() => {
        const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
        // The alert is the topmost popup; the form is the one beneath it.
        const form = popups[popups.length - 2];
        return !!form && [...form.querySelectorAll('input[type="text"], input:not([type]), .gwt-TextBox, textarea')]
          .some((e) => e.offsetWidth > 0 && !e.disabled && !e.readOnly && !String(e.value ?? '').trim() && !e.classList.contains('quickFilter-textBox'));
      }).catch(() => false);
      if (hasEmpty) {
        const caption = String(node.path.at(-1) ?? 'value').replace(/[^A-Za-z0-9 ]+/g, ' ').trim().slice(0, 30);
        const child = { kind: 'post-action', path: [...node.path, 'after OK (with a name filled)'],
          route: [...node.route, { via: 'fill', empty: true, text: `Seed ${caption}`, post: true, opens: 'changed' },
            { via: 'button', label: 'OK', post: true, opens: 'changed' }] };
        child.seedDepth = child.route.length;
        const childId = idOf(child);
        if (!seen.has(childId) && nodes.length < MAXNODES) {
          seen.add(childId);
          try {
            await closeTop('alert');
            await driveStep(child.route.at(-2));
            await driveStep(child.route.at(-1));
            const arrived = await settledSurvey();
            console.log(`      + validation answered: filled "Seed ${caption}" and pressed OK again → ${arrived.dialogs.join(', ') || 'no dialog'}`);
            await walk(child, arrived, Date.now());
          } catch (e) {
            console.log(`      + validation answered: could not fill and retry — ${String(e?.message ?? e).slice(0, 80)}`);
          }
        }
      }
    }

    // ── Descend ──
    const opened = result.outcome === 'dialog' || result.outcome === 'alert' || result.outcome === 'menu';
    let selfClosed = false;
    // A chrome TOGGLE's state is walked whatever POSTDEPTH says: it is covered once per run, so it
    // is bounded, and the once-per-run click always landed on a post-action node with the depth
    // spent — the Ask Stroom AI dock's Configure / History / Download dialogs were never seen.
    const post = result.outcome === 'changed' && (postSteps(node) < POSTDEPTH || CHROME.has(label));
    if (opened || post) {
      // The step records what the click OPENED, so a replay can wait for that thing rather than
      // for a dialog that a menu step will never produce: every re-sync on the Navigation probe was
      // ~1.4s a step, budget-bound, and the route replay was 95% of the re-sync.
      const child = opened
        ? { kind: result.outcome, path: [...node.path, label], seedDepth: node.seedDepth,
          route: [...node.route, { via: 'button', label, opens: result.outcome }] }
        : { kind: 'post-action', path: [...node.path, `after ${label}`], seedDepth: node.seedDepth,
          route: [...node.route, { via: 'button', label, post: true, opens: 'changed' }] };
      const childId = idOf(child);
      if (!seen.has(childId)) {
        seen.add(childId);
        // A MENU child's state is the DELTA — the items that appeared — not everything visible.
        // Surveying the whole popup handed every submenu its parent's sibling groups as affordances,
        // so each submenu re-offered the entire main menu: 118 menu nodes under ONE screen in the
        // first attempt, 85 of them duplicates, and signatures that changed with whichever
        // submenus happened to be open, so no re-sync could ever match one. A submenu is its own
        // items; that is also what the reachability graph counts.
        // The delta lives on the NODE (`items`), not in the survey: the classifier's `before` must
        // stay the full picture, or every sibling group reads as "grew" on the next click.
        if (result.outcome === 'menu') {
          child.items = after.buttons.filter((b) => !before.buttons.includes(b));
          child.disabledItems = after.disabled.filter((b) => !before.disabled.includes(b));
        }
        selfClosed = (await walk(child, after, Date.now())) === true;
      }
    }

    // `backMs` starts HERE, after any descent: measured from the click it read as "650s to get
    // back" from a submenu whose subtree was the whole main-menu tree.
    tBack = Date.now();

    // ── Pop, and ASSERT the parent came back — where a pop is the right move at all ──
    //
    // Every click declares what it should have done to the state, and that decides the way back:
    //   * opened a dialog/alert/menu from a SCREEN or a DIALOG — reversible: close only the topmost
    //     thing, then the parent's signature MUST be back. If it is not, that is a finding about the
    //     application (Cancel did not cancel; Escape left something behind), recorded as such.
    //   * opened anything from a MENU — a menu cannot outlive its child's dismissal, so the parent
    //     is re-synced by route. Expected; not a finding.
    //   * nothing / vanished / unclickable — the state should be UNCHANGED; if it is not, the
    //     classifier was wrong and that is worth knowing. Recorded, then re-synced.
    //   * changed / grid-* / post-action / a blocked mutation — the change was the point (or an
    //     alert may be up). No inverse is pretended; re-sync by route.
    // A CONTEXT menu is a menu: dismissing its submenu dismisses it, so it cannot be verified
    // either — 48 false pop findings on the P3 pass, every one on a context node.
    const isMenuNode = node.kind === 'menu' || node.kind === 'context';
    const reversible = opened && !isMenuNode;
    const unchanged = ['nothing', 'vanished', 'unclickable'].includes(result.outcome);
    // A `changed` with a KNOWN INVERSE is reversible too: click the inverse, then assert the node
    // is back, exactly as for a dialog. And whatever the outcome, a content tab that appeared is
    // this click's doing — a leaf chosen from a menu, a screen a button opened — and is closed
    // before the check, or the next node is surveyed over the wrong screen.
    // A toggle without an entry: the click made exactly one new button appear whose label shares
    // its opening words with the click (`Click to show all jobs` -> `Click to show enabled jobs`).
    // Bounded — one candidate, same prefix — and the signature check decides whether it worked.
    const grew = after ? after.buttons.filter((b) => !before.buttons.includes(b)) : [];
    const twin = grew.length === 1 && grew[0].split(' ').slice(0, 2).join(' ') === label.split(' ').slice(0, 2).join(' ')
      && grew[0] !== label ? grew[0] : null;
    const inverse = result.outcome === 'changed' && !isMenuNode ? (INVERSE.get(label) ?? twin) : null;
    let restored = null;
    if (!blocked.length && (reversible || unchanged || inverse)) {
      if (reversible && !selfClosed) {
        pops += 1;
        if (!(await closeTop(result.outcome))) popsStuck += 1;
      }
      if (inverse) {
        pops += 1;
        if (!(await clickAffordance(inverse).catch(() => false))) popsStuck += 1;
      }
      for (const t of (await contentTabs()).filter((t) => !tabsBefore.includes(t))) await closeContentTab(t);
      const back = await settledSurvey({ budget: 800 });
      // A toggle's round trip is a CHECK (BEHAVIOUR-PLAN.md § B2): click, click the inverse, and
      // the node must be exactly as it was — the pop already measures it; this names it.
      // Only a pair whose second application UNDOES the first is a round-trip (lib/checks.mjs).
      // A COLUMN HEADER is not a toggle. `Data Size` and `Data Size 1` are the same column with and
      // without its sort indicator, so the pairing logic read them as opposites — but clicking a
      // sorted header cycles the direction, it does not un-sort, so the node cannot restore and the
      // check reported `did not restore the node (lost Data Size)` against ordinary sorting. Sorting
      // has its own checker (`sortVerdict`); this one should keep its hands off it.
      if (inverse && togglesRoundTrip(label) && !isColumnHeader(label) && !isColumnHeader(inverse)) {
        const ok = signatureOf(node, back) === sig;
        // A node that GAINED buttons was still settling when it was surveyed — the Params tab's
        // `Duplicate` dirties the dashboard and its Save appears a beat later, which made the next
        // click's round-trip compare against a stale baseline. `probe-ai-dirty.mjs` proved the dock
        // itself dirties nothing. That is `unstable` (the state moved under us), as a Refresh over
        // live data is — not a failed toggle.
        const gained = back.buttons.filter((b) => !items.includes(b));
        const lost = items.filter((b) => !back.buttons.includes(b));
        affRec.check = { kind: 'toggle', gained, lost,
          ...(ok ? { verdict: 'pass', why: `${label} → ${inverse} restored the node` }
            : gained.length && !lost.length
              ? { verdict: 'unstable', why: `the node gained ${gained.join(', ')} while the round-trip ran — it was still settling when surveyed` }
              : { verdict: 'fail', why: `${label} → ${inverse} did not restore the node${lost.length ? ` (lost ${lost.join(', ')})` : ''}` }) };
        if (affRec.check.verdict === 'fail') checkFails += 1;
      }
      if (signatureOf(node, back) === sig) restored = back;
      else {
        findings.push({ node: id, label, outcome: result.outcome, kind: reversible ? 'pop' : 'drift',
          expected: sig, observed: signatureOf(node, back),
          detail: reversible
            ? `after closing "${label}": buttons ${back.buttons.length} (was ${items.length}), `
              + `dialogs [${back.dialogs.join(', ')}], menu=${back.menu}`
            : `classified "${result.outcome}" but the state moved: buttons ${back.buttons.length} `
              + `(was ${items.length}), dialogs [${back.dialogs.join(', ')}]` });
        console.log(`      ‼ ${reversible ? 'closing' : 'after'} "${label}" did not leave the node as it was — recorded`);
      }
    }
    affRec.backMs = Date.now() - tBack;
    if (restored) {
      current = restored;
    } else if (result.outcome === 'closed' && label === items[items.length - 1]) {
      // This dialog's own last affordance closed it. Reopening it by route just to leave again is
      // the one re-sync that buys nothing: return, telling the parent it is ALREADY closed — the
      // parent must not pop again, or its Escape lands on the next dialog down (every
      // `New › <type> › OK` alert did exactly that to the create dialog under it).
      return true;
    } else {
      try {
        for (const t of (await contentTabs()).filter((t) => !tabsBefore.includes(t))) await closeContentTab(t);
        await resync(node);
        current = await settledSurvey();
        affRec.backMs = Date.now() - tBack;
        if (signatureOf(node, current) !== sig) {
          // Re-synced by route and STILL not the node we surveyed: the world moved under us
          // (a live grid, a session change). Record and carry on from what is actually there —
          // and say WHAT differs, because "different" on its own cost three attempts to diagnose.
          const was = items;
          const now = node.items ?? current.buttons;
          const diff = [
            ...now.filter((b) => !was.includes(b)).map((b) => `+${b}`),
            ...was.filter((b) => !now.includes(b)).map((b) => `-${b}`),
          ];
          const why = diff.length ? `buttons ${diff.slice(0, 6).join(' ')}`
            : current.dialogs.join('+') !== state.dialogs.join('+') ? `dialogs [${current.dialogs.join(', ')}] (was [${state.dialogs.join(', ')}])`
              : `fingerprint ${current.fingerprint} (was ${state.fingerprint})`;
          findings.push({ node: id, label, outcome: result.outcome, kind: 'resync', expected: sig,
            observed: signatureOf(node, current), detail: `route replayed after "${label}": ${why}` });
          console.log(`      ‼ re-sync after "${label}" reached a different state: ${why.slice(0, 110)}`);
        }
      } catch (e) {
        findings.push({ node: id, label, outcome: result.outcome, kind: 'resync-failed', expected: sig, observed: null,
          detail: `re-sync failed: ${String(e?.message ?? e).slice(0, 220)}` });
        console.log(`      ‼ could not re-sync to the node: ${String(e?.message ?? e).slice(0, 220)}`);
        return;
      }
    }
  }

  // ── A CONTEXT MENU is a node (lib/context.mjs) ──
  // The right-click driver proves the handler fires by seeing a menu appear; the menu's ITEMS are a
  // different question and a bigger one. `DocumentPluginEventManager`'s nine commands and
  // `MyDataGrid`'s eleven are exactly those items — New, Rename, Delete, Info, Dependencies, Copy,
  // Cell, Row, Column, Export Table — and nothing had ever clicked one.
  //
  // So the menu becomes a `menu` child node like any other, which hands it to the walker's own
  // machinery: the affordance loop clicks each item, `menuClicked` credits the command, dialogs are
  // popped, the guard aborts writes, and `LEAVES_THE_PAGE` keeps the walk off the items that would
  // navigate. Budgeted per target kind, like the right-click check itself.
  if (['screen', 'editor-tab', 'dialog', 'post-action'].includes(node.kind) && !rec.blockedByAlert
    && postSteps(node) < POSTDEPTH && nodes.length < MAXNODES) {
    for (const t of await findTargets(page)) {
      if ((ctxMenuSpent.get(t.what) ?? 0) >= CONTEXT_MENUS) continue;
      const child = { kind: 'menu', path: [...node.path, `right-click ${t.what}`], seedDepth: node.seedDepth,
        route: [...node.route, { via: 'right-click', what: t.what, opens: 'menu' }] };
      const childId = idOf(child);
      if (seen.has(childId)) continue;
      const beforeMenu = await page.evaluate(() => document.querySelectorAll('.menuItem-outer').length).catch(() => 0);
      const t0 = Date.now();
      try {
        await driveStep(child.route.at(-1));
        const after = await settledSurvey();
        const items = (await readVisibleMenuItems(page).catch(() => [])).map((m) => (typeof m === 'string' ? m : m?.label)).filter(Boolean);
        const grew = (await page.evaluate(() => document.querySelectorAll('.menuItem-outer').length).catch(() => 0)) > beforeMenu;
        if (!grew || !items.length) {
          // Say so rather than continuing in silence: a right-click that opens nothing here, when the
          // checker a moment earlier saw a menu, means the page moved under us — and a silent skip
          // looks identical to the block never running at all.
          console.log(`      (right-click ${t.what}: no menu to walk — items=${items.length} grew=${grew})`);
          await dismissMenus(page).catch(() => {});
          continue;
        }
        seen.add(childId);
        ctxMenuSpent.set(t.what, (ctxMenuSpent.get(t.what) ?? 0) + 1);
        child.items = items;
        child.disabledItems = after.disabled.filter((b) => items.includes(b));
        console.log(`      ⊿ right-click ${t.what} → menu of ${items.length}: ${items.slice(0, 8).join(', ')}`);
        await walk(child, after, t0);
      } catch (e) {
        console.log(`      (right-click ${t.what}: ${String(e?.message ?? e).slice(0, 70)})`);
      }
      await dismissMenus(page).catch(() => {});
      if ((await readDialogs(page).catch(() => [])).length) await closeDialogs(page).catch(() => {});
    }
  }

  // ── A selection is a precondition (door-preconditions.md, class `selection`) ──
  // `Edit` on the volume-group, node-group, result-store and user screens is DISABLED until a row
  // is selected, so no click on the screen at rest can open those dialogs. Select the first row
  // and walk ONLY what that newly enabled — the rest of the screen was walked above. Bounded by
  // POSTDEPTH like any other post-action state, and skipped when the selection enabled nothing.
  // NOT gated on there being a disabled button any more. That gate asked "will selecting a row
  // enable something?", which is the right question for exploring but the wrong one for the
  // SELECTION HANDLER — `addSelectionHandler` fires when the row is chosen whether or not a button
  // lights up, and 88 selection handlers were unreached because their screens had nothing disabled
  // to begin with. The grid-with-rows test stays: without a row there is nothing to select.
  if (['screen', 'editor-tab', 'dialog', 'post-action'].includes(node.kind) && !rec.blockedByAlert
    && postSteps(node) < POSTDEPTH
    // A grid with a row that has TEXT: an empty Node Groups / Processor Profiles grid still renders
    // one 20px placeholder row, and clicking it selects nothing.
    && (current.grids ?? []).some((g) => g.rows > 0 && (g.head ?? []).some(Boolean)) && nodes.length < MAXNODES) {
    // The owning screen is in the label: a signature is the kind, the LAST path element, the items
    // and a body fingerprint, and `after select row` + [Edit, Delete] over a one-grid screen is the
    // same string on Data Volumes and Index Volumes — the second was filed as a duplicate of the
    // first and IndexVolumeGroupEditPresenter never opened.
    const child = { kind: 'post-action', path: [...node.path, `after select row in ${node.path.at(-1)}`],
      seedDepth: node.seedDepth,
      route: [...node.route, { via: 'select-row', post: true, opens: 'changed' }] };
    const childId = idOf(child);
    if (!seen.has(childId)) {
      seen.add(childId);
      const t0 = Date.now();
      try {
        await driveStep(child.route.at(-1));
        const after = await settledSurvey();
        child.items = after.buttons.filter((b) => !items.includes(b));
        child.disabledItems = after.disabled.filter((b) => !disabledItems.includes(b));
        // Walked either way, because the NODE is the evidence that a row was selected here — that is
        // what credits the screen's selection handler. When the selection enabled nothing there are
        // no affordances to click, so the walk records the state and returns straight away; it costs
        // one node, not a subtree.
        if (child.items.length) console.log(`      ↳ selecting a row enabled: ${child.items.join(', ')}`);
        else console.log('      ↳ selecting a row enabled nothing new (recorded for the selection handler)');
        await walk(child, after, t0);
      } catch (e) {
        console.log(`      (select row: ${String(e?.message ?? e).slice(0, 80)})`);
      }
    }
    // A TICK is a different selection: a stream grid's first column is a `TickBoxCell`, and
    // ticking a row is what enables the BATCH buttons — Download, Restore, Delete, Process,
    // `Selection summary` (MetaPresenter's, all unreached in the handler inventory) — while a
    // click on the row selects it for the preview. Same shape as above, on the tick.
    const hasTick = await page.evaluate(() => [...document.querySelectorAll('.dataGridWidget tbody tr .tickBox, [role=grid] [role=row] .tickBox')]
      .some((e) => e.offsetWidth > 0)).catch(() => false);
    if (hasTick) {
      const tick = { kind: 'post-action', path: [...node.path, `after tick row in ${node.path.at(-1)}`],
        seedDepth: node.seedDepth,
        route: [...node.route, { via: 'tick-row', post: true, opens: 'changed' }] };
      const tickId = idOf(tick);
      if (!seen.has(tickId)) {
        seen.add(tickId);
        const t0 = Date.now();
        try {
          await resync(node);
          await driveStep(tick.route.at(-1));
          const after = await settledSurvey();
          tick.items = after.buttons.filter((b) => !items.includes(b));
          tick.disabledItems = after.disabled.filter((b) => !disabledItems.includes(b));
          if (tick.items.length) {
            console.log(`      ↳ ticking a row enabled: ${tick.items.join(', ')}`);
            await walk(tick, after, t0);
          }
        } catch (e) {
          console.log(`      (tick row: ${String(e?.message ?? e).slice(0, 80)})`);
        }
      }
    }
  }
}

// ── Roots ────────────────────────────────────────────────────────────────────
for (const seed of seeds) {
  if (nodes.length >= MAXNODES) { capped = true; break; }
  const id = idOf(seed);
  if (seen.has(id)) continue;
  seen.add(id);
  const t0 = Date.now();
  // Where this seed's column headers are, BEFORE the route is driven: a route may itself click one.
  columnScope = typeof seed.columnMenus === 'object' ? seed.columnMenus
    : seed.columnScope ?? { selector: '.dashboard-panel .dataGridWidget th, .dashboard-panel [role=columnheader]', design: true };
  try {
    await resync(seed);
  } catch (e) {
    nodes.push({ id, slug: slugOf(seed), kind: seed.kind, path: seed.path, status: 'unreachable',
      ms: Date.now() - t0, presenter: seed.presenter, recipe: seed.recipe,
      why: String(e?.message ?? e).slice(0, 100) });
    console.log(`  ✗ ${id} — ${String(e?.message ?? e).slice(0, 70)}`);
    continue;
  }
  const arrived = await settledSurvey();
  if (seed.columnMenus) {
    const headers = await readDesignHeaders();
    // The headers ONLY: the dashboard's toolbar around them is the document shard's business.
    seed.items = headers.map((h) => `${COLUMN_MENU}${h}`);
    seed.disabledItems = [];
    console.log(`      + ${headers.length} design-mode column header(s): ${headers.join(', ')}`);
  } else if (seed.kind === 'menu') {
    seed.items = arrived.buttons;
    seed.disabledItems = arrived.disabled;
  } else if (seed.signIn) {
    seed.items = [...arrived.buttons.filter((b) => !/sign in/i.test(b)), `${LINK}Forgot password?`];
    seed.disabledItems = arrived.disabled;
  } else if (seed.route.at(-1)?.via === 'add-component' && lastAddDelta) {
    seed.items = lastAddDelta.items;
    seed.disabledItems = lastAddDelta.disabled;
    console.log(`      + component toolbar: ${seed.items.join(', ') || '(nothing new)'}`);
  }
  await walk(seed, arrived, t0);
  await writeOutput(false).catch(() => {});
  // Leave nothing up for the next root, and close the document we opened so the tab bar does not
  // fill with 26 editors by the end of a recipes:doc run.
  await clearOverlays();
  await closeAllContentTabs();
}

// ── Output: crawl.mjs's schema ───────────────────────────────────────────────
// Written after EVERY root as well as at the end: a document shard is hours long, and a run that is
// stopped — or that dies — used to lose everything it had walked.
const summary = await writeOutput(true);

async function writeOutput(final) {
const wallMs = Date.now() - runStartedAt;
const byKind = {};
for (const n of nodes) {
  const k = n.kind ?? 'unknown';
  byKind[k] = byKind[k] ?? { nodes: 0, ms: 0 };
  byKind[k].nodes += 1;
  byKind[k].ms += n.ms ?? 0;
}
const summary = {
  area: DIRECTED.length ? `directed-${DIRECTED.join('-')}` : RECIPES ? `recipes${RECIPEKIND ? `-${RECIPEKIND}` : ''}${DOCTYPES.length ? `-${DOCTYPES.join('-')}` : ''}` : DOC ? `doc:${DOC}` : AREA,
  depth: DEPTH, url: URL, capped, maxNodes: MAXNODES, walker: 'walk.mjs', walkerVersion: WALKER_VERSION,
  complete: final,
  wallMs,
  // The old cost was counted in NAVIGATIONS (a reload plus a route). Here a reload is the exception,
  // so both are reported: route steps driven in-app, and page loads.
  navigations: reloads, routeSteps: navSteps, resyncs, reloads, signedOutRecoveries, pops, popsStuck, discardedChanges,
  msPerNavigation: navSteps ? Math.round(wallMs / navSteps) : 0,
  msByKind: byKind,
  nodes: nodes.length, reached: nodes.filter((n) => n.status === 'reached').length,
  duplicates: nodes.filter((n) => n.status === 'duplicate').length,
  blockedByAlert: nodes.filter((n) => n.blockedByAlert).length,
  distinctPlaces: explored.size,
  affordances: affordances.length,
  outcomes: affordances.reduce((m, a) => ({ ...m, [a.outcome]: (m[a.outcome] ?? 0) + 1 }), {}),
  blockedRequests: [...new Set(affordances.flatMap((a) => a.blocked))],
  findings: findings.length,
};
await writeFile(join(OUT, `crawl-${summary.area.replace(/[^a-z0-9]+/gi, '-').toLowerCase()}.json`),
  JSON.stringify({ summary, nodes, affordances, findings }, null, 1));
return summary;
}

await appendFile(join(OUT, 'evidence.jsonl'),
  nodes.filter((n) => n.status === 'reached' || n.status === 'duplicate')
    .map((n) => JSON.stringify({ area: summary.area, id: n.id, kind: n.kind, path: n.path,
      dialogs: n.dialogs ?? [] })).join('\n') + '\n');
closing = true;
clearInterval(stallTimer);
await browser.close();

console.log(`\n=== ${summary.area} · depth=${DEPTH} · walk ===`);
console.log(`  nodes ${summary.reached}/${summary.nodes} reached, ${summary.affordances} affordances`);
if (summary.blockedByAlert) console.log(`  !! ${summary.blockedByAlert} node(s) were an ALERT over the target, not the target`);
console.log(`  ${summary.distinctPlaces} distinct places, ${summary.duplicates} re-entrances recorded but not re-explored`);
console.log(`  outcomes: ${Object.entries(summary.outcomes).map(([k, v]) => `${k}=${v}`).join(' ')}`);
{
  const checked = affordances.filter((a) => a.check);
  const by = {};
  for (const a of checked) by[a.check.verdict] = (by[a.check.verdict] ?? 0) + 1;
  if (checked.length) console.log(`  checks: ${Object.entries(by).map(([k, v]) => `${k}=${v}`).join(' ')}${checkFails ? '  — FAILS need triage (a walker bug or a GWT bug)' : ''}`);
}
console.log(`  ${Math.round(summary.wallMs / 1000)}s wall · ${navSteps} route steps · ${resyncs} re-syncs · ${reloads} reload(s)`
  + ` · ${findings.length} pop finding(s)`);
if (summary.blockedRequests.length) {
  console.log('  blocked requests (TRIAGE — mutation, or a read the whitelist is missing?):');
  for (const b of summary.blockedRequests) console.log(`    ${b}`);
}
if (capped) console.log(`  !! CAPPED at MAXNODES=${MAXNODES} — coverage is INCOMPLETE`);
