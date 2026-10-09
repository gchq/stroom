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
// Interactions — screens whose interesting state is reached by DOING something, not by navigating.
//
// Opening a dashboard shows an empty one. The state worth regression-testing is the one after the
// search has run and come back: populated tables, a rendered doughnut, ten series on a line chart,
// three visualisation iframes that had to load their scripts and be handed data. Nearly every defect
// class in that pipeline is invisible on the unsearched screen.
//
// Each action returns only when the screen has genuinely settled, which for a search is not a fixed
// wait: Stroom's search endpoints poll, and how long they take depends on the data.

/** Poll a cheap projection of the screen until it stops changing, or give up. */
async function untilStable(page, project, { samples = 6, intervalMs = 500, maxMs = 60_000 } = {}) {
  const deadline = Date.now() + maxMs;
  let last = null;
  let stable = 0;
  while (Date.now() < deadline) {
    await page.waitForTimeout(intervalMs);
    const now = JSON.stringify(await page.evaluate(project));
    if (now === last) {
      stable += 1;
      if (stable >= samples) return { settled: true, state: last };
    } else {
      stable = 0;
      last = now;
    }
  }
  return { settled: false, state: last };
}

/**
 * Run every query on a dashboard and wait for the results.
 *
 * The dashboard-level "Execute Query" is the FIRST one in the document — the top-right run-all.
 * Each query panel has its own button with the same title, so `.first()` matters: clicking a panel's
 * button runs that panel alone and the rest of the screen stays empty.
 *
 * Settling watches the pagers and the row count together. A pager alone is not enough — it reads
 * "1 to 1 of ?" both before a search and during one — and a row count alone flickers as pages
 * arrive. Together they go quiet only when the search is done.
 */
export async function runDashboardSearch(page) {
  const project = () => ({
    pagers: [...document.querySelectorAll('.pager')].map((e) => e.textContent.replace(/\s+/g, ' ').trim()).slice(0, 6),
    rows: document.querySelectorAll('.dataGridWidget tbody tr, .data-grid__row').length,
  });
  await page.locator('[title="Execute Query"]').first().click({ timeout: 15_000 });
  const tables = await untilStable(page, project);
  const vis = await untilVisualisationsDrawn(page);
  return { ...tables, visualisations: vis };
}

/**
 * Wait for the visualisation iframes to actually DRAW.
 *
 * The tables settle first, and settling on them alone photographs a dashboard whose charts are still
 * blank — which is what the suite's first recording of the searched dashboard captured, while an
 * ad-hoc drive of the same screen got all of them. A visualisation arrives through its own chain:
 * the iframe loads, its scripts load, the frame API handshakes, and only then is it handed data and
 * asked to render.
 *
 * The signal is the drawn geometry inside each frame, not the frame's existence — an iframe with an
 * empty `<svg>` is exactly the state being guarded against. Same-origin, so the frames are readable.
 */
export async function untilVisualisationsDrawn(page, { samples = 4, intervalMs = 500, maxMs = 45_000 } = {}) {
  const deadline = Date.now() + maxMs;
  let last = null;
  let stable = 0;
  const count = async () => {
    let drawn = 0;
    let total = 0;
    for (const f of page.frames()) {
      if (f === page.mainFrame()) continue;
      total += 1;
      const n = await f
        .evaluate(() => document.querySelectorAll('svg circle, svg path, svg rect, svg line, canvas').length)
        .catch(() => 0);
      if (n > 0) drawn += 1;
    }
    return { drawn, total };
  };
  while (Date.now() < deadline) {
    await page.waitForTimeout(intervalMs);
    const now = JSON.stringify(await count());
    if (now === last) {
      stable += 1;
      if (stable >= samples) return { settled: true, ...JSON.parse(now) };
    } else {
      stable = 0;
      last = now;
    }
  }
  return { settled: false, ...(last ? JSON.parse(last) : { drawn: 0, total: 0 }) };
}

export const ACTIONS = { runDashboardSearch, untilVisualisationsDrawn };
