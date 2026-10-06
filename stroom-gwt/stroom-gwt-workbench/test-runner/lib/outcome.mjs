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

// Deciding a story's outcome from what the runner saw in its page, and retrying failed stories.
// Kept apart from run.mjs, which drives the browser, so that they can be tested without one.

// The statuses (data-play-status) of a story that has finished.
export const DONE_STATUSES = ['COMPLETED', 'ERRORED'];

export function formatMillis(millis) {
  return millis >= 1000 ? `${(millis / 1000).toFixed(1)} s` : `${Math.round(millis)} ms`;
}

export function isFailure(result) {
  return result.status === 'FAIL' || result.status === 'ERROR';
}

// Decides a story's outcome, returning {status, error, failedStep}, where status is PASS, FAIL or
// ERROR. seen is what the runner saw:
//   storyId       - the story it opened
//   crashed       - whether the page crashed
//   unresponsive  - whether the page stopped responding to the runner (e.g. a busy loop), so that
//                   the story was abandoned after its time ran out
//   navigated     - null, or {url, reloaded} if the page loaded another document (reloaded, or
//                   navigated away) after the story was opened
//   timedOut      - whether it timed out waiting for a done status
//   timeoutMillis - the time it waited
//   state         - {play: window.__workbenchPlay or null, status: data-play-status or null,
//                    started: whether window.__workbenchIndex was set}, read after the story
//                   finished and settled (or timed out)
//   pageErrors, consoleErrors - the page's uncaught errors and console errors
//   failOnConsole - whether console errors fail the story
export function decideOutcome(seen) {
  const { play, status, started } = seen.state;
  const outcome = { status: 'PASS', error: null, failedStep: null };
  const set = (newStatus, error, failedStep = null) => Object.assign(outcome,
    { status: newStatus, error, failedStep });

  if (seen.crashed) {
    set('ERROR', 'The page crashed');
  } else if (seen.unresponsive) {
    set('FAIL', `Timed out after ${formatMillis(seen.timeoutMillis)}: the page stopped responding `
      + '(e.g. a story, play function or timer that never returns, such as an endless loop)');
  } else if (seen.navigated) {
    set('ERROR', seen.navigated.reloaded
      ? 'The page reloaded while the story was being tested (does the story or its play function '
        + 'reload the page?)'
      : `The page navigated away, to ${seen.navigated.url}, while the story was being tested (does `
        + 'the story or its play function change the location?)');
  } else if (seen.timedOut) {
    if (!started) {
      set('ERROR', `The workbench didn't start within ${formatMillis(seen.timeoutMillis)}`);
    } else {
      const active = (play?.entries ?? []).filter((entry) => entry.status === 'ACTIVE').pop();
      set('FAIL', `Timed out after ${formatMillis(seen.timeoutMillis)} waiting for the story`
        + (play ? ` (status ${play.status}` + (play.stepCount > 0
          ? `, at step ${Math.min(play.nextStep + 1, play.stepCount)} of ${play.stepCount}` : '') + ')' : ''),
        active?.text ?? null);
    }
  } else if (!DONE_STATUSES.includes(status)) {
    // The status went back, e.g. the story re-rendered itself after finishing
    set('ERROR', `The story finished, then its status became '${status ?? 'none'}'`);
  } else if (!play) {
    set('ERROR', `The page's data-play-status is ${status} but window.__workbenchPlay isn't set`);
  } else if (play.status === 'ERRORED') {
    set('FAIL', play.error ?? 'The play function failed', play.failedStep ?? null);
  } else if (play.storyId !== seen.storyId) {
    set('ERROR', `The preview reported story '${play.storyId}', not '${seen.storyId}'`);
  }
  if (outcome.status === 'PASS' && seen.pageErrors.length > 0) {
    set('FAIL', `Uncaught error in the page: ${seen.pageErrors[0].split('\n')[0]}`);
  }
  if (outcome.status === 'PASS' && seen.failOnConsole && seen.consoleErrors.length > 0) {
    set('FAIL', `Console error: ${seen.consoleErrors[0]}`);
  }
  return outcome;
}

// Whether a result's error was taken from its first page or console error (see decideOutcome),
// so needn't be repeated when they are listed.
export function errorRepeatsPageOrConsoleError(result) {
  const error = result.error ?? '';
  return (result.pageErrors?.length > 0
      && error === `Uncaught error in the page: ${result.pageErrors[0].split('\n')[0]}`)
    || (result.consoleErrors?.length > 0 && error === `Console error: ${result.consoleErrors[0]}`);
}

// Runs a story with runOnce(attempt) (which returns its result for that attempt, numbered from
// 1), retrying it up to retries times while it fails. Adds to the final result: attempts,
// previousAttempts (the earlier, failed, attempts' details), attemptDurationsMs (each attempt's
// time) and flaky (passed after failing). Its durationMs becomes the total time of every attempt.
export async function runWithRetries(runOnce, retries) {
  const previousAttempts = [];
  let result = await runOnce(1);
  while (isFailure(result) && previousAttempts.length < retries) {
    previousAttempts.push({
      status: result.status,
      durationMs: result.durationMs,
      failedStep: result.failedStep,
      error: result.error,
      pageErrors: result.pageErrors,
      consoleErrors: result.consoleErrors,
      screenshot: result.screenshot,
    });
    result = await runOnce(previousAttempts.length + 1);
  }
  result.attemptDurationsMs = [...previousAttempts.map((attempt) => attempt.durationMs), result.durationMs];
  result.durationMs = result.attemptDurationsMs.reduce((sum, millis) => sum + (millis ?? 0), 0);
  result.attempts = previousAttempts.length + 1;
  result.previousAttempts = previousAttempts;
  result.flaky = result.status === 'PASS' && previousAttempts.length > 0;
  return result;
}
