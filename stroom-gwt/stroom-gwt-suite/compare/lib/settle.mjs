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
// Waits that are EVENT-DRIVEN and, more importantly, MEASURED.
//
// Why this file exists. Stage A accumulated a layer of blind `waitForTimeout(N)` calls in front of
// every popup, dialog and editor — each one added when a capture came back empty and someone raised
// the number until it stopped. That is the worst possible instrument for a parity harness:
//
//  - a blind sleep ALWAYS costs N, so the sweep pays for the slowest case on every target;
//  - it cannot distinguish "appeared in 40ms" from "appeared at 2.4s" from "never appeared" — the
//    capture that follows reads the same in all three;
//  - and when the port is genuinely slower than GWT, or opens nothing at all, raising the sleep
//    HIDES exactly the defect the run exists to find. A5b lost 20 dialogs to a swallowed click and
//    the first instinct was to lengthen the wait; the number moved 6 → 5 and the real cause (a
//    full-viewport backdrop eating the next click) survived another two rounds of triage.
//
// So: wait for the CONDITION, with a generous ceiling that costs nothing on the common path, and
// record how long it actually took. The elapsed times are written to `latency.json` next to the
// capture, and the two adapters' files are directly comparable — "the port's dialogs are slow" stops
// being a hunch and becomes a column. A wait that TIMES OUT is recorded as `ok: false` rather than
// being swallowed, because "nothing appeared" is a finding, not a reason to wait longer.
// A deliberate cycle with structure.mjs (it imports the waits from here). Both modules export only
// hoisted function declarations and touch each other's bindings at call time, never at load time,
// so ESM resolves it — keep it that way.
import { countVisibleMenuItems, readDialogs } from './structure.mjs';

const entries = [];

/** Everything measured this run. Consumed by run.mjs. */
export function latencyEntries() {
  return entries;
}

export function resetLatency() {
  entries.length = 0;
}

/**
 * Poll `probe` until it returns something truthy, or `budget` expires.
 * Returns `{ ok, ms, value }` and records the timing. `ok: false` means the condition never held —
 * the caller decides whether that is a finding, but it is never silently indistinguishable from
 * success.
 */
export async function waitUntil(page, probe, { budget = 5_000, poll = 100, label = '?', kind = 'wait' } = {}) {
  const t0 = Date.now();
  for (;;) {
    let value;
    try {
      value = await probe();
    } catch {
      value = null;
    }
    if (value) {
      const ms = Date.now() - t0;
      entries.push({ kind, label, ms, ok: true });
      return { ok: true, ms, value };
    }
    if (Date.now() - t0 >= budget) {
      const ms = Date.now() - t0;
      entries.push({ kind, label, ms, ok: false });
      return { ok: false, ms, value: null };
    }
    await page.waitForTimeout(poll);
  }
}

/** Wait for a selector to be visible. Thin wrapper so the timing lands in the same log. */
export async function waitForVisible(page, selector, { budget = 5_000, label = selector, kind = 'visible' } = {}) {
  return waitUntil(
    page,
    async () => (await page.locator(selector).first().isVisible().catch(() => false)) || null,
    { budget, label, kind },
  );
}

/**
 * Wait for a dialog to APPEAR, then for it to SETTLE. Two distinct conditions, and conflating them
 * cost A5b a whole run: GWT builds its dialogs progressively, so a capture taken the instant the
 * caption exists sees a 65px title bar with no buttons — which diffs as "the port invented every
 * control in this dialog".
 *
 * Returns the dialogs plus both timings. `appeared: false` means the click opened nothing within the
 * budget: report it as `no-dialog` and let the diff argue about it — do NOT raise the budget.
 */
export async function waitForDialogs(page, { budget = 6_000, settleBudget = 4_000, label = '?' } = {}) {
  const appear = await waitUntil(
    page,
    async () => {
      const d = await readDialogs(page);
      return d.length ? d : null;
    },
    { budget, label, kind: 'dialog-appear' },
  );
  if (!appear.ok) return { dialogs: [], appeared: false, settled: false, appearMs: appear.ms, settleMs: 0 };

  const settle = await waitUntil(
    page,
    async () => {
      const d = await readDialogs(page);
      return d.length && d.every((x) => x.heightPx > 100 && x.buttons.length) ? d : null;
    },
    { budget: settleBudget, label, kind: 'dialog-settle' },
  );
  return {
    dialogs: settle.value ?? (await readDialogs(page)),
    appeared: true,
    settled: settle.ok,
    appearMs: appear.ms,
    settleMs: settle.ms,
  };
}

/**
 * Wait for a menu popup to be up and populated. `.menuItem-outer` is the shared class (GWT's
 * cellTable rows and the port's items both carry it); visibility filtering matters because the port
 * pre-mounts menus hidden.
 */
export async function waitForMenu(page, { budget = 4_000, label = 'main-menu', min = 1 } = {}) {
  return waitUntil(page, async () => (await countVisibleMenuItems(page)) >= min || null, {
    budget,
    label,
    kind: 'menu-open',
  });
}

/**
 * Wait for a COUNT to stop changing — for things that arrive in waves rather than at one moment.
 * A doc editor's tab strip is the case that needs it: several tabs are gated on a permission fetch
 * that resolves after the editor first paints, so "some tabs exist" is not "the tab set is built".
 * Settles when the count repeats `stableFor` consecutive polls; still reports the elapsed time, so a
 * tab set that takes 4s to finish is visible rather than absorbed into a 6s sleep.
 */
export async function waitForStableCount(page, countFn, { budget = 8_000, poll = 250, stableFor = 3, label = '?', kind = 'stable-count', allowZero = false } = {}) {
  const t0 = Date.now();
  let last = -1;
  let repeats = 0;
  for (;;) {
    const n = await countFn().catch(() => -1);
    if (n === last && (n > 0 || (allowZero && n === 0))) repeats += 1;
    else repeats = 0;
    last = n;
    if (repeats >= stableFor) {
      const ms = Date.now() - t0;
      entries.push({ kind, label, ms, ok: true, count: n });
      return { ok: true, ms, count: n };
    }
    if (Date.now() - t0 >= budget) {
      const ms = Date.now() - t0;
      entries.push({ kind, label, ms, ok: false, count: last });
      return { ok: false, ms, count: last };
    }
    await page.waitForTimeout(poll);
  }
}

/**
 * Wait for the explorer tree to stop changing — the shell's "the app has finished booting" proxy,
 * since the tree is on screen for the whole session and is the last thing to settle after sign-in
 * or an Expand All. Replaces the 2–4s sleeps every Stage-A journey opened with.
 */
export async function settleExplorer(page, { label = 'explorer', budget = 15_000 } = {}) {
  return waitForStableCount(
    page,
    async () =>
      page.evaluate(
        `(() => [...document.querySelectorAll('.explorerCell')].filter((e) => {
            const r = e.getBoundingClientRect();
            return r.width > 0 && r.height > 0;
          }).length)()`,
      ),
    { budget, poll: 250, stableFor: 3, label, kind: 'explorer-settle' },
  );
}

/** Summary for the console + latency.json: worst offenders first, timeouts called out separately. */
export function latencySummary(all = entries) {
  const byKind = new Map();
  for (const e of all) {
    const k = byKind.get(e.kind) ?? { kind: e.kind, n: 0, total: 0, max: 0, timeouts: 0, samples: [] };
    k.n += 1;
    k.total += e.ms;
    k.max = Math.max(k.max, e.ms);
    if (!e.ok) k.timeouts += 1;
    k.samples.push(e.ms);
    byKind.set(e.kind, k);
  }
  const kinds = [...byKind.values()].map((k) => {
    const sorted = k.samples.slice().sort((a, b) => a - b);
    return {
      kind: k.kind,
      n: k.n,
      timeouts: k.timeouts,
      medianMs: sorted[Math.floor(sorted.length / 2)] ?? 0,
      p95Ms: sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * 0.95))] ?? 0,
      maxMs: k.max,
      meanMs: Math.round(k.total / k.n),
    };
  });
  const slowest = all.slice().sort((a, b) => b.ms - a.ms).slice(0, 15);
  const timedOut = all.filter((e) => !e.ok);
  return { kinds, slowest, timedOut, total: all.length };
}
