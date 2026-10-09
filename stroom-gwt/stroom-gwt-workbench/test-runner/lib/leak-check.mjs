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

// The leak check (--leak-check): after a story passes, closes its screen as closing a Stroom tab
// does (disposing its presenters) and opens it again, many times, measuring after each close what
// the page still holds. A screen that leaks keeps more DOM nodes and event listeners after each
// close, e.g. presenters left on the event bus, or Ace editors left on the window's resize event.
//
// Only stories that open their screen with ScreenHarness.afterStartUp can be checked: with
// probe=1 in the address, the harness exposes window.__leakProbe {close(dispose), open(), shown(),
// pending()} (see the workbench's MemoryProbe).

// The most each close may keep, on average, before the story fails. A little growth isn't a leak,
// e.g. the browser's own bookkeeping, or a cache that fills once; a leaking screen keeps hundreds
// or thousands of nodes per close.
export const LEAK_THRESHOLDS = { nodes: 20, listeners: 5 };

// Closes and opens the screen before measuring, so that what is made once (e.g. caches, shared
// popups) isn't counted
const WARM_UP_CYCLES = 2;
// How long a screen may take to open again
const OPEN_TIMEOUT_MILLIS = 15000;
// How long to wait for the screen's REST requests to be answered once it has opened. A screen
// that polls (e.g. a running query) always has one waiting, so this isn't required.
const REQUESTS_TIMEOUT_MILLIS = 3000;
// How long to let the screen's last asynchronous work finish after it has opened
const OPEN_SETTLE_MILLIS = 300;
// How long to let a closed screen's last asynchronous work finish before measuring a story that
// seemed to leak, to confirm it: e.g. a deferred command or a short timer keeps the closed screen
// until it has run. Measuring every story this way would make the check three times as slow.
const CONFIRM_CLOSE_SETTLE_MILLIS = 1500;

// Adds the leak probe's parameter to a story's address.
export function withLeakProbe(url) {
  return `${url}&probe=1`;
}

// Closes and opens the story's screen `cycles` times in the page, after a warm up, and returns
// {checked: false, reason} if the story can't be checked, otherwise {checked: true, cycles, nodes,
// listeners, heapKB, confirmed} with the growth per close (see judgeLeak), measured again more
// slowly (confirmed) if it seemed to leak. Throws if the screen doesn't open again.
export async function measureLeak(page, cycles) {
  const shown = await page.evaluate(() => window.__leakProbe?.shown() ?? null);
  if (shown === null) {
    return { checked: false, reason: "the story doesn't open its screen with afterStartUp" };
  }
  if (shown === 0) {
    return { checked: false, reason: 'the story shows nothing from afterStartUp' };
  }
  const cdp = await page.context().newCDPSession(page);
  try {
    await cdp.send('Performance.enable');
    const leak = await measureCycles(page, cdp, cycles, 0);
    if (!judgeLeak(leak)) {
      return leak;
    }
    return { ...await measureCycles(page, cdp, cycles, CONFIRM_CLOSE_SETTLE_MILLIS), confirmed: true };
  } finally {
    await cdp.detach().catch(() => {});
  }
}

// The growth per close over `cycles` closes, after a warm up, waiting closeSettleMillis after each
// close before measuring.
async function measureCycles(page, cdp, cycles, closeSettleMillis) {
  for (let i = 0; i < WARM_UP_CYCLES; i++) {
    await page.evaluate(() => window.__leakProbe.close(true));
    await reopen(page);
  }
  const samples = [];
  for (let i = 0; i < cycles; i++) {
    await page.evaluate(() => window.__leakProbe.close(true));
    if (closeSettleMillis > 0) {
      await page.waitForTimeout(closeSettleMillis);
    }
    samples.push(await measure(cdp));
    await reopen(page);
  }
  return { checked: true, cycles, ...growthPerCycle(samples) };
}

// Opens the screen again and waits until it is shown and, for a while, until its REST requests
// have been answered.
async function reopen(page) {
  await page.evaluate(() => window.__leakProbe.open());
  try {
    await page.waitForFunction(() => window.__leakProbe.shown() > 0, null, { timeout: OPEN_TIMEOUT_MILLIS });
  } catch {
    throw new Error(`Leak check: the screen didn't open again within ${OPEN_TIMEOUT_MILLIS} ms`);
  }
  await page.waitForFunction(() => window.__leakProbe.pending() === 0, null, { timeout: REQUESTS_TIMEOUT_MILLIS })
    .catch(() => {});
  await page.waitForTimeout(OPEN_SETTLE_MILLIS);
}

// What the page holds once garbage has been collected.
async function measure(cdp) {
  // More than once, as freeing some objects lets others be freed
  for (let i = 0; i < 3; i++) {
    await cdp.send('HeapProfiler.collectGarbage');
  }
  const { metrics } = await cdp.send('Performance.getMetrics');
  const value = (name) => metrics.find((metric) => metric.name === name)?.value ?? 0;
  return { nodes: value('Nodes'), listeners: value('JSEventListeners'), heap: value('JSHeapUsedSize') };
}

// The growth per close of each measure, as the slope of the least squares line through the
// samples (so that one noisy sample doesn't decide it).
export function growthPerCycle(samples) {
  return {
    nodes: Math.round(slope(samples.map((sample) => sample.nodes))),
    listeners: Math.round(slope(samples.map((sample) => sample.listeners))),
    heapKB: Math.round(slope(samples.map((sample) => sample.heap)) / 1024),
  };
}

function slope(values) {
  const count = values.length;
  if (count < 2) {
    return 0;
  }
  const meanX = (count - 1) / 2;
  const meanY = values.reduce((sum, value) => sum + value, 0) / count;
  let covariance = 0;
  let variance = 0;
  values.forEach((value, x) => {
    covariance += (x - meanX) * (value - meanY);
    variance += (x - meanX) ** 2;
  });
  return covariance / variance;
}

// The story's error if the growth per close is over the thresholds, otherwise null.
export function judgeLeak(leak, thresholds = LEAK_THRESHOLDS) {
  if (!leak?.checked
      || (leak.nodes <= thresholds.nodes && leak.listeners <= thresholds.listeners)) {
    return null;
  }
  return `Leak: each close of the screen keeps ${leak.nodes} DOM nodes and ${leak.listeners} `
    + `event listeners (${leak.heapKB} KB of heap), over ${leak.cycles} closes; at most `
    + `${thresholds.nodes} nodes and ${thresholds.listeners} listeners are allowed`;
}
