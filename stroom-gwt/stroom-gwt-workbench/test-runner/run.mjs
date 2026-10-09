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

import { mkdir, rename, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

import { SharedBrowser } from './lib/browsers.mjs';
import { TIMED_OUT, withDeadline } from './lib/deadline.mjs';
import { selectStories } from './lib/filter.mjs';
import { toJUnitXml } from './lib/junit.mjs';
import {
  decideOutcome, DONE_STATUSES, errorRepeatsPageOrConsoleError, formatMillis, isFailure, runWithRetries,
} from './lib/outcome.mjs';
import { judgeLeak, measureLeak, withLeakProbe } from './lib/leak-check.mjs';
import { screenshotFile } from './lib/screenshots.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_URL = process.env.WORKBENCH_URL ?? 'http://localhost:6008';
const DEFAULT_OUTPUT_DIR = path.resolve(HERE, '../build/workbench-test');
const DEFAULT_TIMEOUT_MILLIS = 15000;
// How long to keep watching a story after it has finished, so that e.g. an error thrown by a timer
// it started fails it rather than going unnoticed
const DEFAULT_SETTLE_MILLIS = 100;
const DEFAULT_LEAK_CYCLES = 8;
const DEFAULT_WORKERS = Math.max(1, Math.min(8, Math.floor(os.availableParallelism() / 2)));
const INDEX_TIMEOUT_MILLIS = 30000;
const MAX_PRINTED_CONSOLE_ERRORS = 5;
// How much longer than --timeout plus --settle to wait for a story's page before deciding that it
// has stopped responding (e.g. an endless loop), as some Playwright calls never time out
const UNRESPONSIVE_GRACE_MILLIS = 5000;
const SCREENSHOT_TIMEOUT_MILLIS = 5000;
// How long to wait for a browser context to close
const CLOSE_TIMEOUT_MILLIS = 10000;

// The exit codes
const EXIT_PASSED = 0;
const EXIT_FAILED = 1;
const EXIT_RUN_FAILED = 2;
const EXIT_NO_STORIES = 3;
// A cancelled run exits with 128 + the signal's number, as a shell does, e.g. 130 for SIGINT

// Numbers each new top level document in a story's page (in session storage, which survives
// reloads), so that the runner can tell if the page reloads or navigates away
const DOCUMENT_COUNTER_SCRIPT = `(() => {
  if (window !== window.top) {
    return;
  }
  let number = -1;
  try {
    number = Number(sessionStorage.getItem('__workbenchRunnerDocuments') ?? 0) + 1;
    sessionStorage.setItem('__workbenchRunnerDocuments', String(number));
  } catch {
    // e.g. about:blank, which has no storage of its own
  }
  Object.defineProperty(window, '__workbenchRunnerDocument', { value: number });
})();`;

// The signal that cancelled the run (e.g. Ctrl-C, or Gradle stopping it), or null
let cancelledBy = null;

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
  --retries <n>            Retry failed stories up to n times (default: 0). A story that passes on
                           a retry is reported as flaky
  --settle <ms>            How long to keep watching a story for errors after it has finished
                           (default: ${DEFAULT_SETTLE_MILLIS})
  --fail-on-console        Fail stories that log console errors (they are always reported)
  --output-dir <dir>       Where to write results.json, junit.xml and screenshots
                           (default: ${path.relative(process.cwd(), DEFAULT_OUTPUT_DIR) || '.'})
  --screenshots            Save a PNG of every story (failed stories are always saved)
  --fixed-time <iso>       Fix the stories' clock (Date) at this time, e.g. 2026-01-01T12:00:00Z,
                           so that times they show are the same every run (for comparing
                           screenshots). Timers still run
  --leak-check             After each story passes, close and open its screen again many times
                           and fail it if each close keeps more DOM nodes or event listeners
                           (only stories that open their screen with ScreenHarness.afterStartUp
                           can be checked; the others are run as usual)
  --leak-cycles <n>        How many closes the leak check measures (default: ${DEFAULT_LEAK_CYCLES})
  --headed                 Show the browser
  --list                   List the stories that would run, then exit
  -v, --verbose            Show every story, not just failed ones
  -h, --help               Show this help

Exit code: 0 if every story passed, 1 if any failed, 2 if the stories couldn't be run at all or
the run failed part way (the reports are still written, with the stories not run as errors), 3 if
no stories match the patterns and tags, and 128 + the signal's number (e.g. 130 for Ctrl-C) if the
run was cancelled.
`;

// The time given with --fixed-time, or null
function fixedTime(text) {
  if (text === undefined) {
    return null;
  }
  const time = new Date(text);
  if (Number.isNaN(time.getTime())) {
    throw new Error(`--fixed-time must be a date and time, e.g. 2026-01-01T12:00:00Z, not '${text}'`);
  }
  return time;
}

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
      settle: { type: 'string', default: String(DEFAULT_SETTLE_MILLIS) },
      'fail-on-console': { type: 'boolean', default: false },
      'output-dir': { type: 'string', default: DEFAULT_OUTPUT_DIR },
      screenshots: { type: 'boolean', default: false },
      'fixed-time': { type: 'string' },
      'leak-check': { type: 'boolean', default: false },
      'leak-cycles': { type: 'string', default: String(DEFAULT_LEAK_CYCLES) },
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
    settle: integer('settle', values.settle, 0),
    failOnConsole: values['fail-on-console'],
    outputDir: path.resolve(values['output-dir']),
    screenshots: values.screenshots,
    fixedTime: fixedTime(values['fixed-time']),
    leakCheck: values['leak-check'],
    leakCycles: integer('leak-cycles', values['leak-cycles'], 2),
    headed: values.headed,
    list: values.list,
    verbose: values.verbose,
  };
}

function previewUrl(baseUrl, id) {
  return `${baseUrl}/iframe.html?id=${encodeURIComponent(id)}&viewMode=story`;
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
    const index = await withDeadline(page.evaluate(() => window.__workbenchIndex), INDEX_TIMEOUT_MILLIS);
    if (index === TIMED_OUT) {
      throw new Error(`The workbench page at ${baseUrl} stopped responding`);
    }
    return Object.values(index.entries).filter((entry) => entry.type === 'story');
  } finally {
    await withDeadline(page.close().catch(() => {}), CLOSE_TIMEOUT_MILLIS);
  }
}

// A result for a story that hasn't been run (yet).
function emptyResult(story, options, status) {
  return {
    id: story.id,
    title: story.title,
    name: story.name,
    hasPlay: Boolean(story.hasPlay),
    url: options.leakCheck
      ? withLeakProbe(previewUrl(options.url, story.id))
      : previewUrl(options.url, story.id),
    leak: null,
    status,
    durationMs: 0,
    failedStep: null,
    error: null,
    pageErrors: [],
    consoleErrors: [],
    steps: [],
    screenshot: null,
  };
}

// The first line of an error's message; Playwright's errors include a call log, which isn't needed.
function firstLine(error) {
  return String(error?.message ?? error).split('\n')[0];
}

// Runs one attempt at a story (numbered from 1) in the shared browser. If the browser stops
// meanwhile it is relaunched and the story run again, noting that in its result
// (browserRestarts). Returns its result; never throws.
async function runStory(browsers, story, options, attempt) {
  let restarts = 0;
  let durationMs = 0;
  while (true) {
    const browser = browsers.browser;
    const result = await runStoryOnce(browser, story, options, attempt);
    durationMs += result.durationMs;
    result.durationMs = durationMs;
    if (restarts > 0) {
      result.browserRestarts = restarts;
    }
    if (browser.isConnected() || cancelledBy) {
      return result;
    }
    // Whatever the story seemed to do, the real cause is that the browser has gone
    console.log(`  Chromium stopped unexpectedly while running ${story.id}; relaunching it to run the `
      + 'story again');
    try {
      await browsers.replace(browser);
    } catch (error) {
      return Object.assign(result, {
        status: 'ERROR',
        error: `Chromium stopped unexpectedly while running the story: ${firstLine(error)}`,
        failedStep: null,
        screenshot: null,
      });
    }
    restarts++;
  }
}

// Runs one attempt at a story in a new browser context, so that nothing (e.g. local storage, or a
// crashed page) carries over from the previous story. Returns its result; never throws.
async function runStoryOnce(browser, story, options, attempt) {
  const start = performance.now();
  const result = emptyResult(story, options, 'PASS');
  // What the page reports while the story runs
  const seen = { crashed: false, pageErrors: [], consoleErrors: [] };
  let context = null;
  try {
    context = await browser.newContext({ viewport: { width: 1280, height: 720 } });
    await context.addInitScript(DOCUMENT_COUNTER_SCRIPT);
    if (options.fixedTime) {
      await context.clock.setFixedTime(options.fixedTime);
    }
    const page = await context.newPage();
    watchPage(page, seen);
    // Some Playwright calls (e.g. evaluate) never time out if the page is stuck, e.g. in an
    // endless loop, so give up on the page if it hasn't answered well after the story's time
    const observed = await withDeadline(observeStory(page, result.url, options, start, seen),
      options.timeout + options.settle + UNRESPONSIVE_GRACE_MILLIS);
    const unresponsive = observed === TIMED_OUT;
    if (!unresponsive && observed.openError) {
      Object.assign(result, { status: 'ERROR', error: observed.openError });
      result.pageErrors = [...seen.pageErrors];
      result.consoleErrors = [...seen.consoleErrors];
      return result;
    }
    const state = unresponsive ? { play: null, status: null, started: true } : observed.state;
    result.steps = (state.play?.entries ?? []).map((entry) => ({
      depth: entry.depth, text: entry.text, status: entry.status, error: entry.error ?? null,
    }));
    // The errors so far decide the outcome. Any while the screenshot is taken are kept apart.
    result.pageErrors = [...seen.pageErrors];
    result.consoleErrors = [...seen.consoleErrors];
    Object.assign(result, decideOutcome({
      storyId: story.id,
      crashed: seen.crashed,
      unresponsive,
      navigated: unresponsive ? null : observed.navigated,
      timedOut: unresponsive ? false : observed.timedOut,
      timeoutMillis: options.timeout,
      state,
      pageErrors: result.pageErrors,
      consoleErrors: result.consoleErrors,
      failOnConsole: options.failOnConsole,
    }));
    if (options.leakCheck && result.status === 'PASS') {
      await checkForLeak(page, story, options, result);
    }
    if (!unresponsive && !seen.crashed && (options.screenshots || result.status !== 'PASS')) {
      await takeScreenshot(page, screenshotFile(screenshotsDir(options), story.id, attempt), result, seen);
    }
  } catch (error) {
    // e.g. the page crashed, or the browser has gone
    result.status = 'ERROR';
    result.error = `Unable to run the story: ${firstLine(error)}`;
  } finally {
    result.durationMs = Math.round(performance.now() - start);
    if (context) {
      await withDeadline(context.close().catch(() => {}), CLOSE_TIMEOUT_MILLIS);
    }
  }
  return result;
}

// The leak check (--leak-check) of a story that passed: fails it if each close of its screen keeps
// more of the page (see lib/leak-check.mjs). The measures are kept as the result's leak.
async function checkForLeak(page, story, options, result) {
  try {
    const leak = await withDeadline(measureLeak(page, options.leakCycles),
      2 * (options.leakCycles + 4) * (options.timeout + UNRESPONSIVE_GRACE_MILLIS));
    if (leak === TIMED_OUT) {
      throw new Error('Leak check: the page stopped responding');
    }
    result.leak = leak;
    const error = judgeLeak(leak);
    if (error) {
      Object.assign(result, { status: 'FAIL', error });
    }
  } catch (error) {
    Object.assign(result, { status: 'FAIL', error: firstLine(error) });
  }
}

// Records what the page reports in seen: whether it crashed, its uncaught errors and its console
// errors.
function watchPage(page, seen) {
  page.on('crash', () => {
    seen.crashed = true;
  });
  page.on('pageerror', (error) => seen.pageErrors.push(error.stack ?? error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') {
      // e.g. 'Failed to load resource: ... 404' says which resource only in its location
      const location = message.location()?.url;
      const text = location && !message.text().includes(location)
        ? `${message.text()} (${location})`
        : message.text();
      // Only record each distinct error once, as e.g. a missing stylesheet is reported repeatedly
      if (!seen.consoleErrors.includes(text)) {
        seen.consoleErrors.push(text);
      }
    }
  });
}

// Opens the story in the page and waits for it to finish (and settle), returning what was seen:
// {openError} if it couldn't be opened, otherwise {state, timedOut, navigated} (see decideOutcome).
async function observeStory(page, url, options, start, seen) {
  try {
    await page.goto(url, { timeout: options.timeout, waitUntil: 'domcontentloaded' });
  } catch (error) {
    return { openError: seen.crashed ? 'The page crashed' : `Unable to open the story: ${firstLine(error)}` };
  }
  const origin = new URL(url).origin;
  const remaining = Math.max(100, options.timeout - (performance.now() - start));
  let timedOut = false;
  try {
    // Until the story has finished, or the page has loaded another document (which may never
    // finish), e.g. a reload or about:blank
    await page.waitForFunction(
      ([statuses, storyOrigin]) => window.__workbenchRunnerDocument !== 1
        || window.location.origin !== storyOrigin
        || statuses.includes(document.documentElement.getAttribute('data-play-status')),
      [DONE_STATUSES, origin], { timeout: remaining, polling: 50 });
  } catch {
    timedOut = true;
  }
  if (!timedOut && !seen.crashed) {
    // Keep watching for a moment: the next frame, then the settle time
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => resolve())))
      .catch(() => {});
    if (options.settle > 0) {
      await page.waitForTimeout(options.settle).catch(() => {});
    }
  }
  const state = await page.evaluate(() => ({
    play: window.__workbenchPlay ?? null,
    status: document.documentElement.getAttribute('data-play-status'),
    started: window.__workbenchIndex !== undefined,
    document: window.__workbenchRunnerDocument ?? null,
    href: window.location.href,
  })).catch(() => null);
  // A state that can't be read (unless the page crashed) means the page is between documents
  const href = state?.href ?? page.url();
  const navigated = !seen.crashed && (state === null || state.document !== 1 || new URL(href).origin !== origin)
    ? { url: href, reloaded: href.split('#')[0] === url }
    : null;
  return {
    state: state ?? { play: null, status: null, started: false },
    timedOut,
    navigated,
  };
}

// Saves a screenshot of the page to file, recording it in the result, and any errors the page
// reports meanwhile as the result's screenshotErrors (they don't change its outcome).
async function takeScreenshot(page, file, result, seen) {
  const pageErrorCount = seen.pageErrors.length;
  const consoleErrorCount = seen.consoleErrors.length;
  const saved = await withDeadline(
    // Animations are stopped (at their end) and text carets hidden (Ace's is its own blinking
    // element), as they would make each screenshot of the story differ
    page.screenshot({
      path: file,
      fullPage: true,
      timeout: SCREENSHOT_TIMEOUT_MILLIS,
      animations: 'disabled',
      caret: 'hide',
      style: '.ace_cursor { visibility: hidden !important; }',
    }).then(() => true, () => false),
    SCREENSHOT_TIMEOUT_MILLIS + 2000);
  if (saved === true) {
    // The result matters more than the screenshot, so a failure to take one is ignored
    result.screenshot = file;
  }
  const lateErrors = [
    ...seen.pageErrors.slice(pageErrorCount).map((error) => `Page error: ${error}`),
    ...seen.consoleErrors.slice(consoleErrorCount).map((error) => `Console error: ${error}`),
  ];
  if (lateErrors.length > 0) {
    result.screenshotErrors = lateErrors;
  }
}

function screenshotsDir(options) {
  return path.join(options.outputDir, 'screenshots');
}

// Runs a story, retrying it if it fails (--retries). Each attempt's screenshot is saved as
// <id>.attempt-<n>.png; the last attempt's is then renamed to <id>.png.
async function runStoryWithRetries(browsers, story, options) {
  const result = await runWithRetries((attempt) => runStory(browsers, story, options, attempt), options.retries);
  if (result.screenshot) {
    const file = screenshotFile(screenshotsDir(options), story.id);
    try {
      await rename(result.screenshot, file);
      result.screenshot = file;
    } catch {
      // Keep the attempt's name
    }
  }
  return result;
}

function printComponent(title, results, verbose) {
  const failed = results.some(isFailure);
  const allSkipped = results.every((result) => result.status === 'SKIP');
  const label = failed ? 'FAIL' : allSkipped ? 'SKIP' : 'PASS';
  const time = results.reduce((sum, result) => sum + result.durationMs, 0);
  console.log(` ${label}  ${title} (${results.length} ${results.length === 1 ? 'story' : 'stories'}, `
    + `${formatMillis(time)})`);
  for (const result of results) {
    if (!verbose && !isFailure(result) && !result.flaky) {
      continue;
    }
    const mark = { PASS: '✓', FAIL: '✕', ERROR: '✕', SKIP: '○' }[result.status];
    const note = result.status === 'SKIP' ? ' (skipped)'
      : result.flaky ? ` (flaky: passed on attempt ${result.attempts})`
        : result.attempts > 1 ? ` (failed on all ${result.attempts} attempts)` : '';
    const leak = !result.leak ? ''
      : result.leak.checked
        ? ` [per close: ${result.leak.nodes} nodes, ${result.leak.listeners} listeners, ${result.leak.heapKB} KB]`
        : ` [leak not checked: ${result.leak.reason}]`;
    console.log(`    ${mark} ${result.name}${note}${leak} (${formatMillis(result.durationMs)})`);
  }
}

function printFailure(result) {
  console.log(`\n  ● ${result.title} › ${result.name}  [${result.id}]\n`);
  if (result.attempts > 1) {
    console.log(`    Failed on all ${result.attempts} attempts`);
  }
  if (result.failedStep) {
    console.log(`    Failed step: ${result.failedStep}`);
  }
  // An error taken from the first page or console error is shown with them below
  if (result.error && !errorRepeatsPageOrConsoleError(result)) {
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
  printNotes(result);
  console.log(`\n    ${result.url}`);
  if (result.screenshot) {
    console.log(`    Screenshot: ${result.screenshot}`);
  }
}

// Prints what happened outside the story's outcome: errors while its screenshot was taken, and
// Chromium restarts.
function printNotes(result) {
  for (const error of result.screenshotErrors ?? []) {
    console.log(`    While taking the screenshot (ignored): ${error.split('\n')[0]}`);
  }
  if (result.browserRestarts > 0) {
    console.log(`    Chromium stopped unexpectedly while it ran, so it was run again in a new Chromium `
      + `(${result.browserRestarts} time(s))`);
  }
}

function printFlaky(result) {
  console.log(`\n  ● ${result.title} › ${result.name}  [${result.id}] passed on attempt ${result.attempts}`);
  result.previousAttempts.forEach((attempt, i) => {
    console.log(`    Attempt ${i + 1}: ${attempt.status}${attempt.failedStep ? ` at ${attempt.failedStep}` : ''}`
      + ` - ${(attempt.error ?? '').split('\n')[0]}`
      + (attempt.screenshot ? `\n      Screenshot: ${attempt.screenshot}` : ''));
  });
  printNotes(result);
}

// Removes the reports of a previous run, so that they can't be mistaken for this run's.
async function removeReports(outputDir) {
  for (const name of ['results.json', 'junit.xml', 'screenshots']) {
    await rm(path.join(outputDir, name), { recursive: true, force: true });
  }
}

async function writeReports(options, results, durationMs, runError, browserRelaunches) {
  const count = (status) => results.filter((result) => result.status === status).length;
  const summary = {
    total: results.length,
    passed: count('PASS'),
    failed: count('FAIL'),
    errors: count('ERROR'),
    skipped: count('SKIP'),
    flaky: results.filter((result) => result.flaky).length,
  };
  const jsonFile = path.join(options.outputDir, 'results.json');
  const junitFile = path.join(options.outputDir, 'junit.xml');
  await mkdir(options.outputDir, { recursive: true });
  await writeFile(jsonFile, JSON.stringify({
    url: options.url,
    startedAt: new Date(Date.now() - durationMs).toISOString(),
    durationMs,
    ...(runError ? { runError } : {}),
    ...(browserRelaunches > 0 ? { browserRelaunches } : {}),
    summary,
    stories: results,
  }, null, 2) + '\n');
  await writeFile(junitFile, toJUnitXml(results));
  console.log(`Reports:     ${jsonFile}\n             ${junitFile}`);
}

// The exit code for a run cancelled by the signal, as a shell gives it.
function cancelledExitCode(signal) {
  return 128 + (os.constants.signals[signal] ?? 15);
}

async function main() {
  let options;
  try {
    options = parseOptions();
  } catch (error) {
    console.error(`${error.message}\n\n${HELP}`);
    return EXIT_RUN_FAILED;
  }
  if (options.help) {
    process.stdout.write(HELP);
    return EXIT_PASSED;
  }

  let chromium;
  try {
    ({ chromium } = await import('playwright'));
  } catch {
    console.error(`Playwright isn't installed. Run 'npm ci' (and 'npx playwright install chromium') in ${HERE}, `
      + 'or use ./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchTest');
    return EXIT_RUN_FAILED;
  }

  if (!options.list) {
    await removeReports(options.outputDir);
  }

  const startTime = performance.now();
  const browsers = new SharedBrowser(() => chromium.launch({ headless: !options.headed }),
    { isCancelled: () => cancelledBy !== null });
  // On Ctrl-C, or when Gradle stops the run, stop the stories (by closing the browser) and still
  // write the reports. A second signal stops at once.
  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.on(signal, () => {
      if (cancelledBy) {
        process.exit(cancelledExitCode(signal));
      }
      cancelledBy = signal;
      console.error(`\nCancelled (${signal}); stopping the stories`);
      browsers.close();
    });
  }
  try {
    await browsers.start();
  } catch (error) {
    console.error(`Unable to start Chromium: ${error.message}\nRun 'npx playwright install chromium' in ${HERE}`);
    return cancelledBy ? cancelledExitCode(cancelledBy) : EXIT_RUN_FAILED;
  }

  try {
    let allStories;
    try {
      allStories = await loadIndex(browsers.browser, options.url);
    } catch (error) {
      if (cancelledBy) {
        console.error('The test run was cancelled');
        return cancelledExitCode(cancelledBy);
      }
      console.error(`Unable to read the stories from the workbench: ${firstLine(error)}`);
      return EXIT_RUN_FAILED;
    }
    const stories = selectStories(allStories, options);
    if (options.list) {
      stories.forEach((story) => console.log(`${story.id}${story.skip ? ' (skip)' : ''}`));
      console.log(`\n${stories.length} of ${allStories.length} stories`);
      return EXIT_PASSED;
    }
    if (stories.length === 0) {
      console.error(`No stories match the patterns and tags given (${allStories.length} stories in the `
        + `workbench at ${options.url}). Use --list to see which stories a pattern selects.`);
      return EXIT_NO_STORIES;
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

    // Stops taking stories once the run is cancelled, or the browser can't be relaunched
    const worker = async () => {
      while (next < stories.length && !cancelledBy && !browsers.failure) {
        const index = next++;
        const story = stories[index];
        let result;
        if (story.skip) {
          result = emptyResult(story, options, 'SKIP');
        } else {
          try {
            result = await runStoryWithRetries(browsers, story, options);
          } catch (error) {
            // runStory doesn't throw, so this is a bug in the runner; report it against the story
            result = { ...emptyResult(story, options, 'ERROR'), error: `The runner failed: ${firstLine(error)}` };
          }
        }
        if (cancelledBy) {
          // Its result is probably just the effect of the cancellation, so it counts as not run
          break;
        }
        results[index] = result;
        onDone(index);
      }
    };

    let runError = null;
    try {
      await Promise.all(Array.from({ length: Math.min(options.workers, stories.length) }, worker));
    } catch (error) {
      runError = error.stack ?? String(error);
    }
    if (cancelledBy) {
      runError = `The test run was cancelled (${cancelledBy})`;
    } else if (!runError && browsers.failure) {
      runError = `Chromium stopped unexpectedly, and the run couldn't continue: ${firstLine(browsers.failure)}`;
    }
    // Any story not run because the run failed or was cancelled
    stories.forEach((story, i) => {
      if (!results[i]) {
        results[i] = { ...emptyResult(story, options, 'ERROR'),
          error: cancelledBy ? 'Not run, as the test run was cancelled' : 'Not run, as the test run failed' };
      }
    });

    const failures = results.filter(isFailure);
    if (failures.length > 0 && !cancelledBy) {
      console.log('\nFailures:');
      failures.forEach(printFailure);
    }
    const flaky = results.filter((result) => result.flaky);
    if (flaky.length > 0 && !cancelledBy) {
      console.log('\nFlaky (failed, then passed when retried):');
      flaky.forEach(printFlaky);
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
      [count('SKIP'), 'skipped'], [flaky.length, 'flaky'], [count('PASS'), 'passed']])}, ${results.length} total`);
    console.log(`Time:        ${formatMillis(durationMs)}`);
    if (browsers.relaunches > 0) {
      console.log(`Chromium:    stopped unexpectedly and was relaunched ${browsers.relaunches} time(s)`);
    }
    await writeReports(options, results, durationMs, runError, browsers.relaunches);
    if (runError) {
      console.error(cancelledBy ? `\n${runError}` : `\nThe test run failed: ${runError}`);
      return cancelledBy ? cancelledExitCode(cancelledBy) : EXIT_RUN_FAILED;
    }
    return failures.length > 0 ? EXIT_FAILED : EXIT_PASSED;
  } catch (error) {
    console.error(`The test run failed: ${error.stack ?? error}`);
    return cancelledBy ? cancelledExitCode(cancelledBy) : EXIT_RUN_FAILED;
  } finally {
    await withDeadline(browsers.close(), CLOSE_TIMEOUT_MILLIS);
  }
}

process.exitCode = await main();
// Something may still keep the process alive, e.g. a Playwright call into a page that stopped
// responding, so exit anyway if it hasn't exited by itself shortly
setTimeout(() => process.exit(), 2000).unref();
