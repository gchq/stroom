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
// test-storybook's jest-junit output.

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

function failureDetail(result) {
  const lines = [];
  if (result.failedStep) {
    lines.push(`Failed step: ${result.failedStep}`);
  }
  if (result.error) {
    lines.push(result.error);
  }
  for (const error of result.pageErrors ?? []) {
    lines.push(`Page error: ${error}`);
  }
  for (const error of result.consoleErrors ?? []) {
    lines.push(`Console error: ${error}`);
  }
  lines.push(`URL: ${result.url}`);
  return lines.join('\n');
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

  const out = [];
  out.push('<?xml version="1.0" encoding="UTF-8"?>');
  out.push(`<testsuites name="${escapeXml(name)}" tests="${results.length}" `
    + `failures="${count(results, 'FAIL')}" errors="${count(results, 'ERROR')}" `
    + `skipped="${count(results, 'SKIP')}" time="${seconds(total(results))}">`);
  for (const [title, list] of suites) {
    out.push(`  <testsuite name="${escapeXml(title)}" tests="${list.length}" `
      + `failures="${count(list, 'FAIL')}" errors="${count(list, 'ERROR')}" `
      + `skipped="${count(list, 'SKIP')}" time="${seconds(total(list))}">`);
    for (const result of list) {
      const open = `    <testcase classname="${escapeXml(title)}" name="${escapeXml(result.name)}" `
        + `time="${seconds(result.durationMs)}"`;
      if (result.status === 'PASS') {
        out.push(`${open}/>`);
      } else if (result.status === 'SKIP') {
        out.push(`${open}><skipped/></testcase>`);
      } else {
        const element = result.status === 'ERROR' ? 'error' : 'failure';
        const message = result.error ?? result.pageErrors?.[0] ?? 'Failed';
        out.push(`${open}>`);
        out.push(`      <${element} message="${escapeXml(message.split('\n')[0])}">`
          + `${escapeXml(failureDetail(result))}</${element}>`);
        if (result.screenshot) {
          // Read by e.g. the Jenkins JUnit attachments plugin
          out.push(`      <system-out>[[ATTACHMENT|${escapeXml(result.screenshot)}]]</system-out>`);
        }
        out.push('    </testcase>');
      }
    }
    out.push('  </testsuite>');
  }
  out.push('</testsuites>');
  return out.join('\n') + '\n';
}
