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

// Tests of the runner (run.mjs) itself, against a fake workbench whose stories misbehave in the
// ways real ones might: node --test lib/
//
// They need Playwright's Chromium (see README.md), and are skipped without it.

import assert from 'node:assert/strict';
import { execFileSync, spawn } from 'node:child_process';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { mkdtemp, rm } from 'node:fs/promises';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { after, before, test } from 'node:test';
import { fileURLToPath } from 'node:url';

const RUNNER = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../run.mjs');

// Why the tests can't be run, or null if they can
async function unavailableReason() {
  try {
    const { chromium } = await import('playwright');
    return existsSync(chromium.executablePath()) ? null : "Playwright's Chromium isn't installed";
  } catch {
    return "Playwright isn't installed";
  }
}
const skip = (await unavailableReason()) ?? false;

const STORIES = ['pass', 'fail', 'busy-loop-during', 'busy-loop-after', 'reload-fast', 'nav-away-fast',
  'nav-away-later', 'console', 'flaky', '../../../evil', 'kill-browser'];

// The preview page: the story's behaviour is chosen by its id
const PREVIEW_SCRIPT = `
window.__workbenchIndex = ${JSON.stringify({
    v: 5,
    entries: Object.fromEntries(STORIES.map((id) => [id, { type: 'story', id, title: 'Fake/Stories', name: id }])),
  })};
const id = new URLSearchParams(location.search).get('id');
const done = (status, extra = {}) => {
  window.__workbenchPlay = { storyId: id, status, entries: [], stepCount: 0, nextStep: 0, ...extra };
  document.documentElement.setAttribute('data-play-status', status);
};
setTimeout(() => {
  switch (id) {
    case 'fail': done('ERRORED', { error: 'Boom', failedStep: 'step 1' }); break;
    case 'busy-loop-during': while (true) {} break;
    case 'busy-loop-after': done('COMPLETED'); setTimeout(() => { while (true) {} }, 0); break;
    // Reloads before the runner can see that it finished
    case 'reload-fast':
      if (!sessionStorage.reloaded) { sessionStorage.reloaded = 1; done('COMPLETED'); location.reload(); }
      else { done('RENDERING'); }
      break;
    case 'nav-away-fast': location.href = 'about:blank'; break;
    case 'nav-away-later': done('COMPLETED'); setTimeout(() => { location.href = 'about:blank'; }, 10); break;
    case 'console': console.error('Oops'); done('COMPLETED'); break;
    case 'flaky': done(window.FLAKY_ATTEMPT > 1 ? 'COMPLETED' : 'ERRORED', { error: 'Not yet' }); break;
    default: done('COMPLETED');
  }
}, 20);
`;

let server;
let baseUrl;
let outputRoot;
let flakyAttempts = 0;
// Called when the preview of the story with the id is requested
let onPreview = () => {};

before(async () => {
  if (skip) {
    return;
  }
  outputRoot = await mkdtemp(path.join(os.tmpdir(), 'workbench-runner-test-'));
  server = http.createServer((request, response) => {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname !== '/iframe.html') {
      response.writeHead(404).end();
      return;
    }
    const id = url.searchParams.get('id');
    onPreview(id);
    const attempt = id === 'flaky' ? ++flakyAttempts : 0;
    response.writeHead(200, { 'content-type': 'text/html' });
    response.end(`<!doctype html><html><body>Story<script>window.FLAKY_ATTEMPT = ${attempt};`
      + `${PREVIEW_SCRIPT}</script></body></html>`);
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  server?.close();
  if (outputRoot) {
    await rm(outputRoot, { recursive: true, force: true });
  }
});

// Starts the runner with the arguments, returning {child, done}, where done resolves to
// {code, output, results} once it exits.
function startRunner(name, args) {
  const outputDir = path.join(outputRoot, name);
  const child = spawn(process.execPath, [RUNNER, '--url', baseUrl, '--output-dir', outputDir, ...args],
    { stdio: ['ignore', 'pipe', 'pipe'] });
  let output = '';
  child.stdout.on('data', (data) => {
    output += data;
  });
  child.stderr.on('data', (data) => {
    output += data;
  });
  const done = new Promise((resolve) => child.on('exit', (code) => {
    const resultsFile = path.join(outputDir, 'results.json');
    const results = existsSync(resultsFile) ? JSON.parse(readFileSync(resultsFile, 'utf8')) : null;
    resolve({ code, output, results, outputDir });
  }));
  return { child, done };
}

function run(name, args) {
  return startRunner(name, args).done;
}

function story(results, id) {
  return results.stories.find((result) => result.id === id);
}

test('runner: a page stuck in an endless loop fails, rather than hanging the run', { skip }, async () => {
  const started = performance.now();
  const { code, results } = await run('busy', ['--timeout', '1000', 'busy-loop-*', 'pass']);
  assert.equal(code, 1);
  for (const id of ['busy-loop-during', 'busy-loop-after']) {
    assert.equal(story(results, id).status, 'FAIL');
    assert.match(story(results, id).error, /^Timed out after 1\.0 s: the page stopped responding/);
  }
  assert.equal(story(results, 'pass').status, 'PASS');
  // The time allowed, the settle time and the grace period
  assert.ok(performance.now() - started < 20000);
});

test('runner: reloading or navigating away is an error, however soon it happens', { skip }, async () => {
  const { code, results } = await run('navigation', ['reload-fast', 'nav-away-*']);
  assert.equal(code, 1);
  assert.deepEqual([story(results, 'reload-fast').status, story(results, 'reload-fast').error], ['ERROR',
    'The page reloaded while the story was being tested (does the story or its play function reload the page?)']);
  for (const id of ['nav-away-fast', 'nav-away-later']) {
    assert.equal(story(results, id).status, 'ERROR');
    assert.match(story(results, id).error, /^The page navigated away, to about:blank, while the story/);
  }
});

test('runner: screenshots stay in the screenshots directory, whatever the story id', { skip }, async () => {
  const { code, results, outputDir } = await run('screenshots', ['--screenshots', '*evil']);
  assert.equal(code, 0);
  const screenshots = path.join(outputDir, 'screenshots');
  assert.equal(path.dirname(story(results, '../../../evil').screenshot), screenshots);
  assert.deepEqual(readdirSync(screenshots), [path.basename(story(results, '../../../evil').screenshot)]);
  assert.match(readdirSync(screenshots)[0], /^_\._\.\._\.\._evil-[0-9a-f]{10}\.png$/);
});

test('runner: retries keep each attempt, its time and its screenshot', { skip }, async () => {
  flakyAttempts = 0;
  const { code, results, outputDir, output } = await run('retries', ['--retries', '2', 'flaky', 'fail']);
  assert.equal(code, 1);
  const flaky = story(results, 'flaky');
  assert.deepEqual([flaky.status, flaky.flaky, flaky.attempts], ['PASS', true, 2]);
  assert.equal(flaky.attemptDurationsMs.length, 2);
  assert.equal(flaky.durationMs, flaky.attemptDurationsMs[0] + flaky.attemptDurationsMs[1]);
  const screenshots = path.join(outputDir, 'screenshots');
  assert.equal(flaky.previousAttempts[0].screenshot, path.join(screenshots, 'flaky.attempt-1.png'));
  const failed = story(results, 'fail');
  assert.deepEqual(failed.previousAttempts.map((attempt) => path.basename(attempt.screenshot)),
    ['fail.attempt-1.png', 'fail.attempt-2.png']);
  assert.equal(failed.screenshot, path.join(screenshots, 'fail.png'));
  assert.deepEqual(readdirSync(screenshots).sort(),
    ['fail.attempt-1.png', 'fail.attempt-2.png', 'fail.png', 'flaky.attempt-1.png']);
  assert.match(output, /Failed on all 3 attempts/);
});

test('runner: a console error that fails a story is printed once', { skip }, async () => {
  const { code, output } = await run('console', ['--fail-on-console', 'console']);
  assert.equal(code, 1);
  assert.equal(output.match(/Console error: Oops/g).length, 1);
});

test('runner: no matching stories has its own exit code', { skip }, async () => {
  const { code, output } = await run('none', ['no-such-story']);
  assert.equal(code, 3);
  assert.match(output, /No stories match/);
});

test('runner: a cancelled run says so, and reports the stories not run', { skip }, async () => {
  const { child, done } = startRunner('cancel', ['--timeout', '20000', '-w', '1', 'busy-loop-during', 'pass']);
  onPreview = (id) => {
    if (id === 'busy-loop-during') {
      onPreview = () => {};
      setTimeout(() => child.kill('SIGTERM'), 500);
    }
  };
  const { code, results } = await done;
  assert.equal(code, 128 + os.constants.signals.SIGTERM);
  assert.equal(results.runError, 'The test run was cancelled (SIGTERM)');
  // In index order: 'pass' ran before the run was cancelled
  assert.deepEqual(results.stories.map((result) => [result.id, result.status, result.error]), [
    ['pass', 'PASS', null],
    ['busy-loop-during', 'ERROR', 'Not run, as the test run was cancelled'],
  ]);
});

test('runner: a crashed browser is relaunched and the story run again', {
  skip: skip || (process.platform !== 'linux' ? 'Finds the browser with ps --ppid' : false),
}, async () => {
  const { child, done } = startRunner('crash', ['-w', '1', 'kill-browser', 'pass', 'fail']);
  onPreview = (id) => {
    if (id === 'kill-browser') {
      onPreview = () => {};
      // Kill the runner's Chromium (its child processes)
      const children = execFileSync('ps', ['-o', 'pid=', '--ppid', String(child.pid)]).toString()
        .trim().split(/\s+/).filter(Boolean);
      children.forEach((pid) => process.kill(Number(pid), 'SIGKILL'));
    }
  };
  const { code, results, output } = await done;
  assert.equal(code, 1);
  assert.equal(results.browserRelaunches, 1);
  assert.deepEqual(results.stories.map((result) => [result.id, result.status, result.browserRestarts]), [
    ['pass', 'PASS', undefined],
    ['fail', 'FAIL', undefined],
    ['kill-browser', 'PASS', 1],
  ]);
  assert.match(output, /Chromium stopped unexpectedly while running kill-browser; relaunching it/);
});
