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

// Records, or compares with a recording, the geometry of every data grid in the workbench's
// stories: header height, column widths and row heights, at the default and compact densities
// (see lib/grid-geometry.mjs). The baseline for switching screens from MyDataGrid to
// DataGridWidget (stroom-core-client-widget/DATA_GRID_DESIGN.md, Phases 0 and 3).
//
// Each story is measured once its play has finished. Exits 1 if a comparison finds differences.

import { readFile, writeFile } from 'node:fs/promises';
import { parseArgs } from 'node:util';

import { chromium } from 'playwright';

import { compareGrids, MAX_ROWS, measureGrids } from './lib/grid-geometry.mjs';
import { DONE_STATUSES } from './lib/outcome.mjs';

const USAGE = `Usage: node grid-geometry.mjs --url <workbench url> (--record <file> | --compare <file>)
    [--filter <title prefix>[,<title prefix>...]] [--workers <n>]

  --url <url>         A running workbench, e.g. http://localhost:6008
  --record <file>     Measure the stories and write the measurements to the file
  --compare <file>    Measure the stories in the file and report how they differ from it
  --filter <prefixes> Only stories whose title starts with one of these (default: App/)
  --workers <n>       How many stories to measure at once (default 4)
`;

const PLAY_TIMEOUT_MILLIS = 30000;
const COMPACT_CLASS = 'stroom-density-compact';

let values;
try {
  ({ values } = parseArgs({
    options: {
      url: { type: 'string' },
      record: { type: 'string' },
      compare: { type: 'string' },
      filter: { type: 'string', default: 'App/' },
      workers: { type: 'string', default: '4' },
      help: { type: 'boolean', short: 'h' },
    },
  }));
} catch (error) {
  console.error(`${error.message}\n\n${USAGE}`);
  process.exit(2);
}
if (values.help || !values.url || Boolean(values.record) === Boolean(values.compare)) {
  console.error(USAGE);
  process.exit(values.help ? 0 : 2);
}

const baseUrl = values.url.replace(/\/$/, '');
const baseline = values.compare ? JSON.parse(await readFile(values.compare, 'utf8')) : null;
const browser = await chromium.launch();
try {
  const stories = baseline
    ? Object.keys(baseline.stories)
    : (await loadStoryIds(browser)).filter((story) => values.filter.split(',')
      .some((prefix) => story.title.startsWith(prefix))).map((story) => story.id);
  console.log(`Measuring ${stories.length} stories`);

  const measured = await measureAll(browser, stories, Number(values.workers));
  if (values.record) {
    const withGrids = Object.fromEntries(Object.entries(measured)
      .filter(([, densities]) => densities.default.length > 0 || densities.compact.length > 0));
    await writeFile(values.record, `${JSON.stringify({
      recordedAt: new Date().toISOString(),
      url: baseUrl,
      stories: withGrids,
    }, null, 2)}\n`);
    console.log(`Recorded ${Object.keys(withGrids).length} stories with data grids to ${values.record}`);
  } else {
    let changed = 0;
    for (const [id, expected] of Object.entries(baseline.stories)) {
      const actual = measured[id] || { default: [], compact: [] };
      const differences = ['default', 'compact'].flatMap((density) => compareGrids(expected[density],
        actual[density]).map((difference) => `${density}: ${difference}`));
      if (differences.length > 0) {
        changed++;
        console.log(`\n${id}\n  ${differences.join('\n  ')}`);
      }
    }
    console.log(`\n${changed} of ${Object.keys(baseline.stories).length} stories differ from ${values.compare}`);
    process.exitCode = changed > 0 ? 1 : 0;
  }
} finally {
  await browser.close();
}

async function loadStoryIds(browserToUse) {
  const page = await browserToUse.newPage();
  try {
    await page.goto(`${baseUrl}/iframe.html`);
    await page.waitForFunction(() => window.__workbenchIndex !== undefined, null, { timeout: 60000 });
    const index = await page.evaluate(() => window.__workbenchIndex);
    return Object.values(index.entries).filter((entry) => entry.type === 'story');
  } finally {
    await page.close();
  }
}

async function measureAll(browserToUse, ids, workers) {
  const results = {};
  const queue = [...ids];
  const context = await browserToUse.newContext({ viewport: { width: 1280, height: 800 } });
  await Promise.all(Array.from({ length: Math.max(1, workers) }, async () => {
    const page = await context.newPage();
    while (queue.length > 0) {
      const id = queue.shift();
      try {
        results[id] = await measureStory(page, id);
      } catch (error) {
        console.error(`${id}: ${error.message.split('\n')[0]}`);
        results[id] = { default: [], compact: [], error: error.message.split('\n')[0] };
      }
    }
    await page.close();
  }));
  await context.close();
  return results;
}

async function measureStory(page, id) {
  await page.goto(`${baseUrl}/iframe.html?id=${encodeURIComponent(id)}&viewMode=story`);
  await page.waitForFunction((statuses) => statuses.includes(document.documentElement.getAttribute('data-play-status')),
    DONE_STATUSES, { timeout: PLAY_TIMEOUT_MILLIS, polling: 50 });
  // The workbench's preferences may already be compact, so set each density explicitly
  const measureAt = async (compact) => {
    await page.evaluate(([className, on]) => document.documentElement.classList.toggle(className, on),
      [COMPACT_CLASS, compact]);
    await settle(page);
    return page.evaluate(measureGrids, MAX_ROWS);
  };
  const defaultGrids = await measureAt(false);
  const compactGrids = await measureAt(true);
  return { default: defaultGrids, compact: compactGrids };
}

async function settle(page) {
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  await page.waitForTimeout(300);
}
