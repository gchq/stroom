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

// The workbench's equivalent of Storybook's test-runner (`test-storybook`): opens every story's
// preview page in headless Chromium, waits for its play function to finish (or, for a story
// without one, for it to render) and reports which passed and failed.
//
// It reads the state the workbench puts on the page (see RunnerHooks in the workbench framework):
//   window.__workbenchIndex                - every story, like Storybook's index.json
//   <html data-play-status="...">          - RENDERING, PLAYING, PAUSED, COMPLETED or ERRORED
//   window.__workbenchPlay                 - the status, failed step and error
//
// Usage: node run.mjs [options] [patterns...]   (see --help, and README.md)

import { mkdir, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

import { selectStories } from './lib/filter.mjs';
import { toJUnitXml } from './lib/junit.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_URL = process.env.WORKBENCH_URL ?? 'http://localhost:6008';
const DEFAULT_OUTPUT_DIR = path.resolve(HERE, '../build/workbench-test');
const DEFAULT_TIMEOUT_MILLIS = 15000;
const DEFAULT_WORKERS = Math.max(1, Math.min(8, Math.floor(os.availableParallelism() / 2)));
const INDEX_TIMEOUT_MILLIS = 30000;
const DONE_STATUSES = ['COMPLETED', 'ERRORED'];
const MAX_PRINTED_CONSOLE_ERRORS = 5;

const HELP = `Usage: node run.mjs [options] [patterns...]

Runs the Stroom GWT Workbench's stories, and their play functions, in headless Chromium.

Patterns (positional or --filter) select stories: a pattern containing '/' matches titles, e.g.
'Widgets/Buttons' (a prefix) or 'Widgets/*/TickBox' (a glob); any other pattern matches ids, e.g.
'widgets-buttons-' (a prefix) or '*--with-icons' (a glob).

Options:
  --url <url>              The workbench (default: $WORKBENCH_URL or ${DEFAULT_URL})
  -f, --filter <pattern>   Only run matching stories (repeatable)
  --exclude <pattern>      Don't run matching stories (repeatable)
  --include-tags <tags>    Only run stories with one of these comma separated tags
  --exclude-tags <tags>    Don't run stories with any of these tags
  --skip-tags <tags>       Report stories with any of these tags as skipped (default: skip-test)
  -w, --workers <n>        Stories to run at once (default: ${DEFAULT_WORKERS}); --maxWorkers is an alias
  --timeout <ms>           Time allowed for each story (default: ${DEFAULT_TIMEOUT_MILLIS})
  --retries <n>            Retry failed stories up to n times (default: 0)
  --fail-on-console        Fail stories that log console errors (they are always reported)
  --output-dir <dir>       Where to write results.json, junit.xml and screenshots
                           (default: ${path.relative(process.cwd(), DEFAULT_OUTPUT_DIR) || '.'})
  --screenshots            Save a PNG of every story (failed stories are always saved)
  --headed                 Show the browser
  --list                   List the stories that would run, then exit
  -v, --verbose            Show every story, not just failed ones
  -h, --help               Show this help

Exit code: 0 if every story passed, 1 if any failed, 2 if the stories couldn't be run at all.
`;

function parseOptions() {
  const { values, positionals } = parseArgs({
    allowPositionals: true,
    options: {
      url: { type: 'string', default: DEFAULT_URL },
      filter: { type: 'string', short: 'f', multiple: true, default: [] },
      exclude: { type: 'string', multiple: true, default: [] },
      'include-tags': { type: 'string', default: '' },
      'exclude-tags': { type: 'string', default: '' },
      'skip-tags': { type: 'string', default: 'skip-test' },
      workers: { type: 'string', short: 'w' },
      maxWorkers: { type: 'string' },
      timeout: { type: 'string', default: String(DEFAULT_TIMEOUT_MILLIS) },
      retries: { type: 'string', default: '0' },
      'fail-on-console': { type: 'boolean', default: false },
      'output-dir': { type: 'string', default: DEFAULT_OUTPUT_DIR },
      screenshots: { type: 'boolean', default: false },
      headed: { type: 'boolean', default: false },
      list: { type: 'boolean', default: false },
      verbose: { type: 'boolean', short: 'v', default: false },
      help: { type: 'boolean', short: 'h', default: false },
    },
  });
  const tags = (text) => text.split(',').map((tag) => tag.trim()).filter(Boolean);
  const integer = (name, text, min) => {
    const value = Number.parseInt(text, 10);
    if (!Number.isInteger(value) || value < min || String(value) !== text.trim()) {
      throw new Error(`--${name} must be a whole number of at least ${min}, not '${text}'`);
    }
    return value;
  };
  return {
    help: values.help,
    url: values.url.replace(/\/+$/, ''),
    include: [...values.filter, ...positionals],
    exclude: values.exclude,
    includeTags: tags(values['include-tags']),
    excludeTags: tags(values['exclude-tags']),
    skipTags: tags(values['skip-tags']),
    workers: integer('workers', values.workers ?? values.maxWorkers ?? String(DEFAULT_WORKERS), 1),
    timeout: integer('timeout', values.timeout, 100),
    retries: integer('retries', values.retries, 0),
    failOnConsole: values['fail-on-console'],
    outputDir: path.resolve(values['output-dir']),
    screenshots: values.screenshots,
    headed: values.headed,
    list: values.list,
    verbose: values.verbose,
  };
}

function previewUrl(baseUrl, id) {
  return `${baseUrl}/iframe.html?id=${encodeURIComponent(id)}&viewMode=story`;
}

function formatMillis(millis) {
  return millis >= 1000 ? `${(millis / 1000).toFixed(1)} s` : `${Math.round(millis)} ms`;
}

// Reads every story from the workbench's index (window.__workbenchIndex).
async function loadIndex(browser, baseUrl) {
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  try {
    // With no id the preview just shows "Couldn't find story", but still publishes the index
    const response = await page.goto(`${baseUrl}/iframe.html`, { timeout: INDEX_TIMEOUT_MILLIS });
    if (!response || !response.ok()) {
      throw new Error(`${baseUrl}/iframe.html returned ${response ? response.status() : 'nothing'}`);
    }
    try {
      await page.waitForFunction(() => window.__workbenchIndex !== undefined, null,
        { timeout: INDEX_TIMEOUT_MILLIS });
    } catch {
      throw new Error(`The workbench didn't start within ${INDEX_TIMEOUT_MILLIS / 1000} s at ${baseUrl}. `
        + 'Has it been compiled (./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchDraftCompile)?'
        + (errors.length > 0 ? `\nPage errors:\n  ${errors.join('\n  ')}` : ''));
    }
    const index = await page.evaluate(() => window.__workbenchIndex);
    return Object.values(index.entries).filter((entry) => entry.type === 'story');
  } finally {
    await page.close();
  }
}

// Runs one story, returning its result.
async function runStory(context, story, options) {
  const url = previewUrl(options.url, story.id);
  const start = performance.now();
  const pageErrors = [];
  const consoleErrors = [];
  const result = {
    id: story.id,
    title: story.title,
    name: story.name,
    hasPlay: Boolean(story.hasPlay),
    url,
    status: 'PASS',
    durationMs: 0,
    failedStep: null,
    error: null,
    pageErrors,
    consoleErrors,
    steps: [],
    screenshot: null,
  };
  const page = await context.newPage();
  page.on('pageerror', (error) => pageErrors.push(error.stack ?? error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') {
      // e.g. 'Failed to load resource: ... 404' says which resource only in its location
      const location = message.location()?.url;
      const text = location && !message.text().includes(location)
        ? `${message.text()} (${location})`
        : message.text();
      // Only record each distinct error once, as e.g. a missing stylesheet is reported repeatedly
      if (!consoleErrors.includes(text)) {
        consoleErrors.push(text);
      }
    }
  });
  try {
    try {
      await page.goto(url, { timeout: options.timeout, waitUntil: 'domcontentloaded' });
    } catch (error) {
      result.status = 'ERROR';
      result.error = `Unable to open the story: ${error.message.split('\n')[0]}`;
      return result;
    }
    const remaining = Math.max(100, options.timeout - (performance.now() - start));
    let timedOut = false;
    try {
      await page.waitForFunction(
        (statuses) => statuses.includes(document.documentElement.getAttribute('data-play-status')),
        DONE_STATUSES, { timeout: remaining, polling: 50 });
    } catch {
      timedOut = true;
    }
    const state = await page.evaluate(() => ({
      play: window.__workbenchPlay ?? null,
      started: window.__workbenchIndex !== undefined,
    })).catch(() => ({ play: null, started: false }));
    const play = state.play;
    result.steps = (play?.entries ?? []).map((entry) => ({
      depth: entry.depth, text: entry.text, status: entry.status, error: entry.error ?? null,
    }));

    if (timedOut) {
      if (!state.started) {
        result.status = 'ERROR';
        result.error = `The workbench didn't start within ${formatMillis(options.timeout)}`;
      } else {
        result.status = 'FAIL';
        const active = (play?.entries ?? []).filter((entry) => entry.status === 'ACTIVE').pop();
        result.error = `Timed out after ${formatMillis(options.timeout)} waiting for the story`
          + (play ? ` (status ${play.status}` + (play.stepCount > 0
            ? `, at step ${Math.min(play.nextStep + 1, play.stepCount)} of ${play.stepCount}` : '') + ')' : '');
        result.failedStep = active?.text ?? null;
      }
    } else if (play.status === 'ERRORED') {
      result.status = 'FAIL';
      result.failedStep = play.failedStep ?? null;
      result.error = play.error ?? 'The play function failed';
    } else if (play.storyId !== story.id) {
      result.status = 'ERROR';
      result.error = `The preview reported story '${play.storyId}', not '${story.id}'`;
    }
    if (result.status === 'PASS' && pageErrors.length > 0) {
      result.status = 'FAIL';
      result.error = `Uncaught error in the page: ${pageErrors[0].split('\n')[0]}`;
    }
    if (result.status === 'PASS' && options.failOnConsole && consoleErrors.length > 0) {
      result.status = 'FAIL';
      result.error = `Console error: ${consoleErrors[0]}`;
    }

    if (options.screenshots || result.status !== 'PASS') {
      const file = path.join(options.outputDir, 'screenshots', `${story.id}.png`);
      try {
        await page.screenshot({ path: file, fullPage: true, timeout: 5000 });
        result.screenshot = file;
      } catch {
        // The page may have crashed; the result matters more than the screenshot
      }
    }
    return result;
  } finally {
    result.durationMs = Math.round(performance.now() - start);
    await page.close().catch(() => {});
  }
}

function printComponent(title, results, verbose) {
  const failed = results.some((result) => result.status === 'FAIL' || result.status === 'ERROR');
  const allSkipped = results.every((result) => result.status === 'SKIP');
  const label = failed ? 'FAIL' : allSkipped ? 'SKIP' : 'PASS';
  const time = results.reduce((sum, result) => sum + result.durationMs, 0);
  console.log(` ${label}  ${title} (${results.length} ${results.length === 1 ? 'story' : 'stories'}, `
    + `${formatMillis(time)})`);
  for (const result of results) {
    const show = verbose || result.status === 'FAIL' || result.status === 'ERROR';
    if (!show) {
      continue;
    }
    const mark = { PASS: '✓', FAIL: '✕', ERROR: '✕', SKIP: '○' }[result.status];
    console.log(`    ${mark} ${result.name}${result.status === 'SKIP' ? ' (skipped)' : ''} `
      + `(${formatMillis(result.durationMs)})`);
  }
}

function printFailure(result) {
  console.log(`\n  ● ${result.title} › ${result.name}  [${result.id}]\n`);
  if (result.failedStep) {
    console.log(`    Failed step: ${result.failedStep}`);
  }
  if (result.error) {
    console.log(result.error.split('\n').map((line) => `    ${line}`).join('\n'));
  }
  for (const error of result.pageErrors) {
    console.log(`    Page error: ${error.split('\n').slice(0, 3).join('\n      ')}`);
  }
  for (const error of result.consoleErrors.slice(0, MAX_PRINTED_CONSOLE_ERRORS)) {
    console.log(`    Console error: ${error}`);
  }
  if (result.consoleErrors.length > MAX_PRINTED_CONSOLE_ERRORS) {
    console.log(`    ... and ${result.consoleErrors.length - MAX_PRINTED_CONSOLE_ERRORS} more console errors `
      + '(see results.json)');
  }
  console.log(`\n    ${result.url}`);
  if (result.screenshot) {
    console.log(`    Screenshot: ${result.screenshot}`);
  }
}

async function main() {
  let options;
  try {
    options = parseOptions();
  } catch (error) {
    console.error(`${error.message}\n\n${HELP}`);
    return 2;
  }
  if (options.help) {
    process.stdout.write(HELP);
    return 0;
  }

  let chromium;
  try {
    ({ chromium } = await import('playwright'));
  } catch {
    console.error(`Playwright isn't installed. Run 'npm ci' (and 'npx playwright install chromium') in ${HERE}, `
      + 'or use ./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchTest');
    return 2;
  }

  const startTime = performance.now();
  let browser;
  try {
    browser = await chromium.launch({ headless: !options.headed });
  } catch (error) {
    console.error(`Unable to start Chromium: ${error.message}\nRun 'npx playwright install chromium' in ${HERE}`);
    return 2;
  }

  try {
    let allStories;
    try {
      allStories = await loadIndex(browser, options.url);
    } catch (error) {
      // Playwright's errors include a call log, which isn't needed
      console.error(`Unable to read the stories from the workbench: ${error.message.split('\n')[0]}`);
      return 2;
    }
    const stories = selectStories(allStories, options);
    if (options.list) {
      stories.forEach((story) => console.log(`${story.id}${story.skip ? ' (skip)' : ''}`));
      console.log(`\n${stories.length} of ${allStories.length} stories`);
      return 0;
    }
    if (stories.length === 0) {
      console.error(`No stories match (${allStories.length} stories in the workbench at ${options.url})`);
      return 1;
    }
    console.log(`Running ${stories.length} of ${allStories.length} stories against ${options.url} `
      + `with ${Math.min(options.workers, stories.length)} worker(s)\n`);

    await mkdir(options.outputDir, { recursive: true });
    const results = new Array(stories.length);
    const remainingByTitle = new Map();
    stories.forEach((story) => remainingByTitle.set(story.title, (remainingByTitle.get(story.title) ?? 0) + 1));
    const printed = new Set();
    let next = 0;

    const onDone = (index) => {
      const title = stories[index].title;
      const remaining = remainingByTitle.get(title) - 1;
      remainingByTitle.set(title, remaining);
      if (remaining === 0 && !printed.has(title)) {
        printed.add(title);
        printComponent(title, results.filter((result) => result && result.title === title), options.verbose);
      }
    };

    const worker = async () => {
      const context = await browser.newContext({ viewport: { width: 1280, height: 720 } });
      try {
        while (next < stories.length) {
          const index = next++;
          const story = stories[index];
          if (story.skip) {
            results[index] = {
              id: story.id, title: story.title, name: story.name, hasPlay: Boolean(story.hasPlay),
              url: previewUrl(options.url, story.id), status: 'SKIP', durationMs: 0, failedStep: null,
              error: null, pageErrors: [], consoleErrors: [], steps: [], screenshot: null,
            };
          } else {
            let result = await runStory(context, story, options);
            let attempts = 1;
            while (result.status !== 'PASS' && attempts <= options.retries) {
              attempts++;
              result = await runStory(context, story, options);
            }
            result.attempts = attempts;
            results[index] = result;
          }
          onDone(index);
        }
      } finally {
        await context.close();
      }
    };
    await Promise.all(Array.from({ length: Math.min(options.workers, stories.length) }, worker));

    const failures = results.filter((result) => result.status === 'FAIL' || result.status === 'ERROR');
    if (failures.length > 0) {
      console.log('\nFailures:');
      failures.forEach(printFailure);
    }

    const count = (status) => results.filter((result) => result.status === status).length;
    const titles = [...new Set(results.map((result) => result.title))];
    const failedTitles = new Set(failures.map((result) => result.title));
    const durationMs = Math.round(performance.now() - startTime);
    const parts = (entries) => entries.filter(([n]) => n > 0).map(([n, label]) => `${n} ${label}`).join(', ');
    console.log('');
    console.log(`Test Suites: ${parts([[failedTitles.size, 'failed'],
      [titles.length - failedTitles.size, 'passed']])}, ${titles.length} total`);
    console.log(`Tests:       ${parts([[count('FAIL'), 'failed'], [count('ERROR'), 'errors'],
      [count('SKIP'), 'skipped'], [count('PASS'), 'passed']])}, ${results.length} total`);
    console.log(`Time:        ${formatMillis(durationMs)}`);

    const summary = {
      total: results.length,
      passed: count('PASS'),
      failed: count('FAIL'),
      errors: count('ERROR'),
      skipped: count('SKIP'),
    };
    const jsonFile = path.join(options.outputDir, 'results.json');
    const junitFile = path.join(options.outputDir, 'junit.xml');
    await writeFile(jsonFile, JSON.stringify({
      url: options.url,
      startedAt: new Date(Date.now() - durationMs).toISOString(),
      durationMs,
      summary,
      stories: results,
    }, null, 2) + '\n');
    await writeFile(junitFile, toJUnitXml(results));
    console.log(`Reports:     ${jsonFile}\n             ${junitFile}`);
    return failures.length > 0 ? 1 : 0;
  } finally {
    await browser.close();
  }
}

process.exitCode = await main();
