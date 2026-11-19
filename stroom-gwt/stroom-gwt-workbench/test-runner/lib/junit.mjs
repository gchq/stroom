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

// Writes the results as JUnit XML, the format CI servers (GitHub Actions, Jenkins etc.) read.
// There is one <testsuite> per component (story title) and one <testcase> per story, as with
// test-storybook's jest-junit output. Each <testsuite> is valid against Maven Surefire's
// surefire-test-report.xsd (3.0.2), so retried stories are recorded as Surefire records reruns:
//   * a story that failed and then passed when retried (flaky) is a passing <testcase> with a
//     <flakyFailure>/<flakyError> for each failed attempt;
//   * a story that failed on every attempt has a <failure>/<error> for its last attempt and a
//     <rerunFailure>/<rerunError> for each earlier one.
// Each of those has message and type attributes and a <stackTrace> (the details), as the schema
// requires. Screenshots are attached for e.g. the Jenkins JUnit attachments plugin with
// [[ATTACHMENT|<file>]] lines in the <testcase>'s <system-out>.

import { errorRepeatsPageOrConsoleError } from './outcome.mjs';

// The type attribute of a failure, by the story's status.
const FAILURE_TYPES = { FAIL: 'StoryFailure', ERROR: 'StoryError' };

export function escapeXml(text) {
  return String(text ?? '')
    // Characters that aren't allowed in XML 1.0 at all
    // eslint-disable-next-line no-control-regex
    .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f￾￿]/g, '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function seconds(millis) {
  return ((millis ?? 0) / 1000).toFixed(3);
}

// The details of a failed attempt, as text.
export function failureDetail(result) {
  const lines = [];
  if (result.attempts > 1 && !result.flaky) {
    lines.push(`Failed on all ${result.attempts} attempts`);
  }
  if (result.failedStep) {
    lines.push(`Failed step: ${result.failedStep}`);
  }
  if (result.error && !errorRepeatsPageOrConsoleError(result)) {
    lines.push(result.error);
  }
  for (const error of result.pageErrors ?? []) {
    lines.push(`Page error: ${error}`);
  }
  for (const error of result.consoleErrors ?? []) {
    lines.push(`Console error: ${error}`);
  }
  if (result.url) {
    lines.push(`URL: ${result.url}`);
  }
  return lines.join('\n');
}

function message(result) {
  return (result.error ?? result.pageErrors?.[0] ?? 'Failed').split('\n')[0];
}

function attachment(file) {
  return `[[ATTACHMENT|${file}]]`;
}

// A <failure>/<error>, whose details are its text.
function failureElement(name, result) {
  return `      <${name} message="${escapeXml(message(result))}" type="${FAILURE_TYPES[result.status]}">`
    + `${escapeXml(failureDetail(result))}</${name}>`;
}

// A <flakyFailure>, <rerunError> etc. for an earlier attempt, whose details are its <stackTrace>,
// with its screenshot in its <system-out>.
function attemptElement(name, attempt, url) {
  const lines = [`      <${name} message="${escapeXml(message(attempt))}" type="${FAILURE_TYPES[attempt.status]}">`,
    `        <stackTrace>${escapeXml(failureDetail({ ...attempt, url }))}</stackTrace>`];
  if (attempt.screenshot) {
    lines.push(`        <system-out>${escapeXml(attachment(attempt.screenshot))}</system-out>`);
  }
  lines.push(`      </${name}>`);
  return lines;
}

// The elements for the earlier attempts: which is 'failures', 'errors' or (by default) both,
// failures before errors as the schema requires.
function attemptElements(result, prefix, which = 'both') {
  const attempts = result.previousAttempts ?? [];
  return [
    ...(which === 'errors' ? [] : attempts.filter((attempt) => attempt.status !== 'ERROR')
      .flatMap((attempt) => attemptElement(`${prefix}Failure`, attempt, result.url))),
    ...(which === 'failures' ? [] : attempts.filter((attempt) => attempt.status === 'ERROR')
      .flatMap((attempt) => attemptElement(`${prefix}Error`, attempt, result.url))),
  ];
}

// The <system-out> for a test case, or nothing.
function systemOut(lines) {
  return lines.length > 0 ? [`      <system-out>${escapeXml(lines.join('\n'))}</system-out>`] : [];
}

// Every screenshot of the story, earliest attempt first.
function screenshots(result) {
  return [...(result.previousAttempts ?? []).map((attempt) => attempt.screenshot), result.screenshot]
    .filter(Boolean);
}

function testCaseBody(result) {
  if (result.status === 'SKIP') {
    return ['      <skipped/>'];
  }
  if (result.status === 'PASS' && !result.flaky) {
    return [];
  }
  if (result.status === 'PASS') {
    return [...attemptElements(result, 'flaky'),
      ...systemOut([`Flaky: failed ${result.attempts - 1} time(s), then passed on attempt ${result.attempts}`,
        ...screenshots(result).map(attachment)])];
  }
  // <failure>, <rerunFailure>..., <error>, <rerunError>..., in the schema's order
  const failure = failureElement(result.status === 'FAIL' ? 'failure' : 'error', result);
  const rerunFailures = attemptElements(result, 'rerun', 'failures');
  const rerunErrors = attemptElements(result, 'rerun', 'errors');
  const body = result.status === 'FAIL'
    ? [failure, ...rerunFailures, ...rerunErrors]
    : [...rerunFailures, failure, ...rerunErrors];
  return [...body, ...systemOut(screenshots(result).map(attachment))];
}

// results: the runner's results, in order; name: the name of the whole run.
export function toJUnitXml(results, name = 'stroom-gwt-workbench') {
  const suites = new Map();
  for (const result of results) {
    if (!suites.has(result.title)) {
      suites.set(result.title, []);
    }
    suites.get(result.title).push(result);
  }
  const count = (list, status) => list.filter((result) => result.status === status).length;
  const total = (list) => list.reduce((sum, result) => sum + (result.durationMs ?? 0), 0);
  const flakes = (list) => list.filter((result) => result.flaky).length;

  const out = [];
  out.push('<?xml version="1.0" encoding="UTF-8"?>');
  out.push(`<testsuites name="${escapeXml(name)}" tests="${results.length}" `
    + `failures="${count(results, 'FAIL')}" errors="${count(results, 'ERROR')}" `
    + `skipped="${count(results, 'SKIP')}" time="${seconds(total(results))}">`);
  for (const [title, list] of suites) {
    out.push(`  <testsuite name="${escapeXml(title)}" tests="${list.length}" `
      + `failures="${count(list, 'FAIL')}" errors="${count(list, 'ERROR')}" `
      + `skipped="${count(list, 'SKIP')}" flakes="${flakes(list)}" time="${seconds(total(list))}">`);
    for (const result of list) {
      const open = `    <testcase classname="${escapeXml(title)}" name="${escapeXml(result.name)}" `
        + `time="${seconds(result.durationMs)}"`;
      const body = testCaseBody(result);
      if (body.length === 0) {
        out.push(`${open}/>`);
      } else {
        out.push(`${open}>`, ...body, '    </testcase>');
      }
    }
    out.push('  </testsuite>');
  }
  out.push('</testsuites>');
  return out.join('\n') + '\n';
}
