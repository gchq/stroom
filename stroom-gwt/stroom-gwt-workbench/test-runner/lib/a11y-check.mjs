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

// The accessibility check: after a story passes, runs axe-core over its page (the story and any
// dialogs or popups it opened) with the WCAG 2.x A and AA rules, and fails the story if a rule is
// broken that isn't in its baseline.
//
// The baseline (a11y-baseline.json, beside run.mjs) lists, for each story, the rules it broke when
// the check was introduced, so that existing problems don't fail every run while new ones do. A
// story missing from it must break no rules. As problems are fixed, the run reports the rules a
// story no longer breaks, and --update-a11y-baseline rewrites its entry.

import { readFile, writeFile } from 'node:fs/promises';

// axe-core's tags for the WCAG 2.0, 2.1 and 2.2 level A and AA rules (its best-practice rules,
// e.g. that all content is in landmarks, aren't checked)
export const A11Y_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

// Where the workbench serves axe-core (fetched by the workbenchFetchAxe task)
export const AXE_PATH = '/__workbench/axe.min.js';

// How often, and how long apart, to try running axe while the preview's own run is in progress
const AXE_ATTEMPTS = 50;
const AXE_PAUSE_MILLIS = 100;

// Runs axe-core over the page and returns {checked: false, reason} if it couldn't, otherwise
// {checked: true, violations: [{id, impact, help, targets}]}, a violation for each rule broken,
// with the elements that break it.
export async function measureA11y(page, baseUrl) {
  const loaded = await page.evaluate(() => Boolean(window.axe));
  if (!loaded) {
    try {
      await page.addScriptTag({ url: `${baseUrl}${AXE_PATH}` });
    } catch (error) {
      return { checked: false, reason: 'axe-core is not available (run the workbenchFetchAxe task)' };
    }
  }
  // Colour contrast is measured from the colours shown now, so a button part way through a hover
  // or press transition gives a different answer each run: finish transitions and animations first
  await page.addStyleTag({ content: '*, *::before, *::after { transition: none !important; '
    + 'animation: none !important; }' });
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  // The preview also runs axe-core after a play, for the workbench's Accessibility addon, and axe
  // can only run once at a time, so this waits for it to finish
  const violations = await page.evaluate(async ({ tags, attempts, pauseMillis }) => {
    const pause = () => new Promise((resolve) => setTimeout(resolve, pauseMillis));
    const busy = () => {
      const state = window.__sbmAxeState;
      return Boolean(state && (state.loading || state.running));
    };
    for (let attempt = 1; ; attempt++) {
      if (busy() && attempt < attempts) {
        await pause();
        continue;
      }
      try {
        const results = await window.axe.run(document, {
          runOnly: { type: 'tag', values: tags },
          resultTypes: ['violations'],
        });
        return results.violations.map((rule) => ({
          id: rule.id,
          impact: rule.impact ?? null,
          help: rule.help,
          targets: rule.nodes.map((node) => (node.target ?? []).map(String).join(' >>> ')),
        }));
      } catch (error) {
        // Started at the same moment as the preview's run
        if (attempt >= attempts || !/already running/i.test(String(error?.message ?? error))) {
          throw error;
        }
        await pause();
      }
    }
  }, { tags: A11Y_TAGS, attempts: AXE_ATTEMPTS, pauseMillis: AXE_PAUSE_MILLIS });
  return { checked: true, violations };
}

// Compares a story's violations with the rules its baseline allows. Returns {error, newRules,
// fixedRules}: the error (or null) fails the story if it broke a rule not in its baseline;
// fixedRules are baseline rules it no longer breaks.
export function judgeA11y(violations, baselineRules = []) {
  const allowed = new Set(baselineRules);
  const broken = new Set(violations.map((violation) => violation.id));
  const added = violations.filter((violation) => !allowed.has(violation.id));
  const fixedRules = [...allowed].filter((rule) => !broken.has(rule)).sort();
  if (added.length === 0) {
    return { error: null, newRules: [], fixedRules };
  }
  const lines = added.map((violation) => `  ${violation.id} (${violation.impact ?? 'unknown impact'}): `
    + `${violation.help}\n    ${violation.targets.slice(0, 3).join('\n    ')}`
    + (violation.targets.length > 3 ? `\n    ... and ${violation.targets.length - 3} more` : ''));
  return {
    error: `Accessibility: ${added.length} rule(s) broken that aren't in its baseline (a11y-baseline.json)\n`
      + lines.join('\n'),
    newRules: added.map((violation) => violation.id).sort(),
    fixedRules,
  };
}

// Reads the baseline, {storyId: [rule, ...]}, or an empty one if there is none.
export async function readBaseline(file) {
  try {
    return JSON.parse(await readFile(file, 'utf8'));
  } catch (error) {
    if (error.code === 'ENOENT') {
      return {};
    }
    throw new Error(`Unable to read the accessibility baseline ${file}: ${error.message}`);
  }
}

// The baseline with each checked story's entry set to the rules it broke now (removed if none);
// stories that weren't checked keep their entries. Sorted, so that changes are easy to review.
export function updateBaseline(baseline, results) {
  const updated = { ...baseline };
  for (const result of results) {
    if (!result.a11y?.checked) {
      continue;
    }
    const rules = [...new Set(result.a11y.violations.map((violation) => violation.id))].sort();
    if (rules.length > 0) {
      updated[result.id] = rules;
    } else {
      delete updated[result.id];
    }
  }
  return Object.fromEntries(Object.keys(updated).sort().map((id) => [id, updated[id]]));
}

export async function writeBaseline(file, baseline) {
  await writeFile(file, JSON.stringify(baseline, null, 2) + '\n');
}
