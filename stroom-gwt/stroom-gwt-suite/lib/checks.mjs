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
// Checkers — what `changed` has to MEAN (BEHAVIOUR-PLAN.md § B2).
//
// The walk classifies a click by the shape of what happened (a dialog opened, buttons appeared, the
// grid reordered). A checker says whether what happened is what the control is FOR, with an oracle
// that is either the DOM before the click or the source: a sort header sorts, a pager pages, a
// Refresh changes nothing. Each verdict is `pass`, `fail` or `unchecked` with a reason, and lands on
// the affordance record as `check`; a `fail` is a walker bug or a GWT bug, and the triage is the
// work — it is never left as a count.
//
// Two halves per checker, so the walker's loop stays one shape: a READER that captures the oracle
// before the click (and the same after), and a pure VERDICT over the two captures.

const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();

// ── Sort ────────────────────────────────────────────────────────────────────
/**
 * The visible grid whose header text (minus the sort index) is `name`, and that column's cell
 * texts row by row — plus the header's sort direction, read from the icon MyDataGrid puts in it
 * (`svg-image__fields-sort-ascending` / `-descending`) and the order number beside it.
 */
export function readColumn(page, name) {
  return page.evaluate((wanted) => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const strip = (t) => norm(t).replace(/\s+\d+$/, '');
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    for (const g of scope.querySelectorAll('.dataGridWidget, [role=grid]')) {
      if (!(g.offsetWidth > 0)) continue;
      const heads = [...g.querySelectorAll('thead th, [role=columnheader]')];
      const idx = heads.findIndex((h) => strip(h.querySelector('.column-label')?.textContent ?? h.textContent) === wanted);
      if (idx < 0) continue;
      const h = heads[idx];
      const icon = h.querySelector('[class*="fields-sort-"]');
      const direction = icon ? (/descending/.test(icon.className.baseVal ?? icon.className) ? 'desc' : 'asc') : null;
      const order = norm(h.querySelector('.column-sortOrder')?.textContent) || null;
      const rows = [...g.querySelectorAll('tbody tr, [role=row]:not([aria-rowindex="1"])')].filter((r) => r.offsetHeight > 0 && norm(r.textContent));
      const values = rows.map((r) => {
        const cells = r.querySelectorAll('td, [role=gridcell]');
        return norm(cells[idx]?.textContent);
      });
      return { direction, order, values, rows: rows.length };
    }
    return null;
  }, name).catch(() => null);
}

/** Parse a cell for comparison: a number, an ISO-ish date, a size (`32K`), else text. */
function keyOf(v, fold = true) {
  const t = norm(v);
  if (t === '') return { k: 'empty', v: '' };
  if (/^-?\d+(\.\d+)?$/.test(t)) return { k: 'num', v: Number(t) };
  // A grid formats a count with thousands separators — `44,730`. Compared as TEXT that sorts
  // before `6,773`, so a correctly descending column failed. The pager checker learned this on
  // `1,011`; this one had not. Only a fully-grouped number qualifies, so `1,234 items` stays text.
  if (/^-?\d{1,3}(,\d{3})+(\.\d+)?$/.test(t)) return { k: 'num', v: Number(t.replace(/,/g, '')) };
  if (/^\d{4}-\d{2}-\d{2}T/.test(t)) return { k: 'date', v: Date.parse(t) };
  const size = /^(\d+(?:\.\d+)?)\s*([KMGT]?)B?$/i.exec(t);
  if (size) return { k: 'num', v: Number(size[1]) * ({ '': 1, K: 1e3, M: 1e6, G: 1e9, T: 1e12 })[size[2].toUpperCase()] };
  return { k: 'text', v: fold ? t.toLowerCase() : t };
}
/**
 * `fold` decides the COLLATION for text. The server sorts in the database's, and the two that
 * matter disagree: a case-insensitive collation puts `content-length` before `X-Forwarded-For`,
 * a case-sensitive (binary / ASCII) one puts every uppercase letter first, so descending gives
 * `content-length` THEN `X-Forwarded-For`. Both are sorted. The checker's business is that the page
 * is in order, not which collation the instance uses, so a column passes under either.
 */
const cmpWith = (mode) => (a, b) => {
  const fold = mode === 'locale';
  const ka = keyOf(a, fold);
  const kb = keyOf(b, fold);
  // Empty sorts first whichever way; a mixed column compares as text.
  if (ka.k === 'empty' || kb.k === 'empty') return ka.k === kb.k ? 0 : ka.k === 'empty' ? -1 : 1;
  if (ka.k !== kb.k) return String(ka.v).localeCompare(String(kb.v));
  // `locale` keeps what this checker always did — `localeCompare` over the folded text, which is
  // how a case-insensitive collation orders. `binary` is code-unit order over the text as it is,
  // which is what a case-sensitive collation does and is NOT localeCompare on the raw string:
  // localeCompare puts "a" before "B" whatever the case, and that is the very thing being ruled in.
  if (ka.k === 'text') {
    return fold ? ka.v.localeCompare(kb.v) : (ka.v < kb.v ? -1 : ka.v > kb.v ? 1 : 0);
  }
  return ka.v - kb.v;
};
const COLLATIONS = [
  { name: 'case-insensitive', cmp: cmpWith('locale') },
  { name: 'case-sensitive', cmp: cmpWith('binary') },
];

/**
 * Did the header click SORT? `before` and `after` are `readColumn` captures. The direction is what
 * the header shows AFTER the click; the page's values must be in that order (the server sorted the
 * whole result; the page is a window on it, so the window is sorted too). Equal keys in any order
 * pass — the server's tie-break is not the column's business.
 */
export function sortVerdict(before, after) {
  if (!after || !after.values) return { verdict: 'unchecked', why: 'column not found after the click' };
  if (after.rows < 2) return { verdict: 'unchecked', why: `${after.rows} row(s) — nothing to order` };
  if (!after.direction) return { verdict: 'unchecked', why: 'no sort icon on the header after the click' };
  const vals = after.values;
  const distinct = new Set(vals.map((v) => JSON.stringify(keyOf(v)))).size;
  if (distinct < 2) return { verdict: 'unchecked', why: 'all values equal on this page' };
  // Ordered under EITHER collation is ordered. Only when both disagree is the page unsorted, and
  // the message then quotes the pair that the LAST one tripped on.
  let broke = null;
  const sortedUnder = ({ cmp }) => {
    for (let i = 1; i < vals.length; i += 1) {
      const c = cmp(vals[i - 1], vals[i]);
      if (after.direction === 'asc' ? c > 0 : c < 0) { broke = i; return false; }
    }
    return true;
  };
  if (!COLLATIONS.some(sortedUnder)) {
    const i = broke;
    return { verdict: 'fail', why: `header says ${after.direction}, row ${i} "${vals[i - 1].slice(0, 30)}" then row ${i + 1} "${vals[i].slice(0, 30)}" — under either collation`,
      direction: after.direction, sample: vals.slice(0, 6) };
  }
  const moved = before?.values && JSON.stringify(before.values) !== JSON.stringify(vals);
  return { verdict: 'pass', why: `${vals.length} row(s) in ${after.direction} order${moved ? '' : ' (already were)'}`, direction: after.direction };
}

// ── Pager ───────────────────────────────────────────────────────────────────
/** The pager that holds a visible button titled `title`: its from / to / of and which buttons are enabled. */
export function readPager(page, title) {
  return page.evaluate((wanted) => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    const btn = [...scope.querySelectorAll(`button[title="${wanted}"]`)].find((b) => b.offsetWidth > 0);
    const pager = btn?.closest('.pager-paging');
    if (!pager) return null;
    const labels = [...pager.querySelectorAll('.pager-label')].map((l) => norm(l.textContent));
    // title · from · "to" · to · "of" · of — the separators are labels too, and the title is the
    // grid's name. Read the three numbers by position around the separators.
    const toI = labels.findIndex((l) => l === 'to');
    const ofI = labels.findIndex((l) => l === 'of');
    // `1,011` — the pager formats thousands with a separator.
    const num = (t) => (/^[\d,]+$/.test(t) ? Number(t.replace(/,/g, '')) : t === '?' ? null : t || null);
    const from = toI > 0 ? num(labels[toI - 1]) : null;
    const to = toI >= 0 ? num(labels[toI + 1]) : null;
    const of = ofI >= 0 ? num(labels[ofI + 1]) : null;
    const enabled = Object.fromEntries(['First', 'Backward', 'Forward', 'Last'].map((t) => [t, !![...pager.querySelectorAll(`button[title="${t}"]`)].find((b) => b.offsetWidth > 0 && !b.disabled)]));
    return { from, to, of, enabled, text: labels.join(' ') };
  }, title).catch(() => null);
}

/**
 * Did the pager button PAGE?
 *
 * Judged by DIRECTION and EDGE, because a Stroom grid does one of two things and the checker cannot
 * tell which from outside. `Pager extends AbstractPager`, and `setPageStart` clamps the start to
 * `rowCount - pageSize` only `if (isRangeLimited && display.isRowCountExact())`. A grid with an
 * exact count therefore shows a FULL last page — User Access goes `1 to 100 of 152` ->
 * `53 to 152 of 152` — while a grid whose count is an estimate shows a SHORT one:
 * `1 to 100 of 341` -> `301 to 341 of 341` under Last. Both are the widget working.
 *
 * So: Forward must move forward and either start where the last page ended or finish at the total;
 * Backward must move back and either end where this page started or begin at 1. That holds for both
 * behaviours and still catches a button that does nothing, goes the wrong way, or lands short.
 */
export function pagerVerdict(kind, before, after) {
  if (!before || !after) return { verdict: 'unchecked', why: 'no pager found around the button' };
  const b = before;
  const a = after;
  if (typeof b.from !== 'number' || typeof b.to !== 'number') return { verdict: 'unchecked', why: `pager read "${b.text}"` };
  if (typeof a.from !== 'number' || typeof a.to !== 'number') return { verdict: 'unchecked', why: `pager read "${a.text}" after the click` };
  const total = typeof b.of === 'number' ? b.of : null;
  // Is this button AT its boundary already? Then it must be disabled and must change nothing.
  const atBoundary = {
    Forward: () => total !== null && b.to >= total,
    Backward: () => b.from <= 1,
    First: () => b.from <= 1,
    Last: () => total !== null && b.to >= total,
  }[kind]?.();
  if (atBoundary === undefined) return { verdict: 'unchecked', why: `not a pager button: ${kind}` };
  if (atBoundary) {
    if (b.enabled[kind]) return { verdict: 'fail', why: `${kind} enabled at the boundary (${b.text})` };
    return a.from === b.from && a.to === b.to ? { verdict: 'pass', why: `at the boundary, unchanged (${a.text})` }
      : { verdict: 'fail', why: `at the boundary yet moved: ${b.text} -> ${a.text}` };
  }
  const ok = {
    // Moved forward, and either abuts the page it came from or runs to the end.
    Forward: () => a.from > b.from && (a.from === b.to + 1 || (total !== null && a.to === total)),
    // Moved back, and either abuts the page it came from or starts at the beginning.
    Backward: () => a.from < b.from && (a.to === b.from - 1 || a.from === 1),
    First: () => a.from === 1,
    Last: () => (total === null ? null : a.to === total),
  }[kind]();
  if (ok === null) return { verdict: 'unchecked', why: `Last needs a known total, and the pager read "${b.text}"` };
  return ok ? { verdict: 'pass', why: `${b.text} -> ${a.text}` }
    : { verdict: 'fail', why: `${kind}: ${b.text} -> ${a.text} is neither adjacent nor at the edge` };
}

// ── Refresh ─────────────────────────────────────────────────────────────────
/**
 * A Refresh is a read, and a read is idempotent: the same rows, in the same order, the same pager.
 * `before` / `after` are the walker's surveys (their `grids` carry `filled` and `head`). A screen
 * whose data is LIVE by design (Nodes' ping times, User Access' sessions, a running task list) will
 * differ, and that is `unstable`, not `fail` — the triage says which screens those are.
 */
export function refreshVerdict(before, after) {
  const gb = before?.grids ?? [];
  const ga = after?.grids ?? [];
  if (!gb.length) return { verdict: 'unchecked', why: 'no grid to compare' };
  if (gb.length !== ga.length) return { verdict: 'unstable', why: `${gb.length} grid(s) before, ${ga.length} after` };
  for (let i = 0; i < gb.length; i += 1) {
    if ((gb[i].filled ?? gb[i].rows) !== (ga[i].filled ?? ga[i].rows)) return { verdict: 'unstable', why: `grid ${i + 1}: ${gb[i].filled ?? gb[i].rows} row(s) then ${ga[i].filled ?? ga[i].rows}` };
    if (JSON.stringify(gb[i].head) !== JSON.stringify(ga[i].head)) {
      // Show the part that DIFFERS, not the first 40 characters — a processor row's name is the
      // same and its task count moved, and the message used to quote two identical strings.
      const a = gb[i].head.join(' ¦ ');
      const b = ga[i].head.join(' ¦ ');
      let at = 0;
      while (at < a.length && at < b.length && a[at] === b[at]) at += 1;
      const from = Math.max(0, at - 12);
      return { verdict: 'unstable', why: `grid ${i + 1}: rows differ at "…${a.slice(from, at + 28)}" vs "…${b.slice(from, at + 28)}"` };
    }
  }
  return { verdict: 'pass', why: `${gb.length} grid(s) unchanged` };
}

export const PAGER_BUTTONS = new Set(['First', 'Backward', 'Forward', 'Last']);

// ── Inline feedback ─────────────────────────────────────────────────────────
/**
 * Not every validation message is a dialog. The four identity views —
 * `ChangePasswordViewImpl`, `CurrentPasswordViewImpl`, `EmailResetPasswordViewImpl`,
 * `LoginViewImpl` — report INLINE: `validate()` writes into a `<g:Label styleName="feedback">`
 * ("Password is required", "Passwords must match") and puts `invalid` on the box. Nothing opens,
 * nothing is added to the button row, so the `ok` checker read a working form as a dead OK and
 * filed three fails against it.
 *
 * Read the topmost dialog's feedback labels and how many boxes are marked invalid, before the
 * click and after; a change in either IS the form answering.
 */
export function readFeedback(page) {
  return page.evaluate(() => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    const texts = [...scope.querySelectorAll('.feedback, .invalid-feedback')]
      .filter((e) => e.offsetWidth > 0).map((e) => norm(e.textContent)).filter(Boolean);
    const invalid = scope.querySelectorAll('.invalid').length;
    return { texts, invalid };
  }).catch(() => null);
}

/** Did the form answer INLINE between the two captures? Returns only what is NEW. */
export function feedbackSaid(before, after) {
  if (!after) return null;
  const was = new Set(before?.texts ?? []);
  const now = (after.texts ?? []).filter((t) => !was.has(t));
  if (now.length) return now.join('; ');
  if ((after.invalid ?? 0) > (before?.invalid ?? 0)) return `${after.invalid} box(es) marked invalid`;
  return null;
}

/**
 * Is a message SHOWING? Not whether it changed — `Enter Your Current Password` reported a dead OK
 * because Ctrl+Enter had fired the same validation a moment earlier, so `.feedback` read
 * "Password is required" both before and after and the delta was empty. The user is looking at a
 * message either way. The class belongs to the four identity views and is set back to "" when the
 * form is valid, so presence IS the answer.
 */
export function feedbackShown(after) {
  const texts = (after?.texts ?? []).filter(Boolean);
  if (texts.length) return texts.join('; ');
  return (after?.invalid ?? 0) > 0 ? `${after.invalid} box(es) marked invalid` : null;
}

// ── Which dialogs the Filter check applies to ───────────────────────────────
/**
 * `Filter › OK must not grow the rows` is a real rule, but only for a dialog that FILTERS. The
 * walker matched any opener containing the word — and `Add XPath Filter`, inside `Manage Step
 * Filters`, adds a row by design, so filtering was accused of growing the grid 100 → 101.
 *
 * An opener that begins with Add / New / Create is an ADD whatever else its name says; `Manage …`
 * opens a manager, not a filter. What is left — `Filter`, `Filter Documents …`, `Quick Filter` —
 * is the rule's subject.
 */
export function isFilterAction(label) {
  const t = String(label ?? '').trim();
  if (/^(Add|New|Create|Edit|Manage|Remove|Delete)\b/i.test(t)) return false;
  return /\bfilters?\b/i.test(t);
}

// ── Which pairs the toggle round-trip applies to ────────────────────────────
/**
 * `Expand All` / `Collapse All` are each other's counterpart for POPPING — clicking one after the
 * other puts the tree back into a sane state — but they are not INVERSES. Both are absolute:
 * `Collapse All` collapses everything, including the folders that were open before `Expand All`
 * ran, so the pair restores the previous state only if the tree began fully collapsed. The walker
 * calls `expandExplorer()` for its document steps, so it never does, and the toggle checker
 * reported five failures against a control doing exactly what its name says.
 *
 * A genuine toggle is one whose second application undoes the first: a self-inverse
 * (`Ask Stroom AI`), or an explicit On/Off pair.
 */
export const ABSOLUTE_PAIRS = new Set(['Expand All', 'Collapse All']);
export const togglesRoundTrip = (label) => !ABSOLUTE_PAIRS.has(String(label ?? '').trim());
