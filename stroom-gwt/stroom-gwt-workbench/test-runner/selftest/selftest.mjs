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

// Self-test of the play API's browser code (the JSNI in the workbench framework's play package):
// compares, in the same headless Chromium, the workbench's roles, accessible names, matchers and
// user events with the reference React Storybook plays use (Testing Library, user-event 14 and
// jest-dom from `storybook/test`). See README.md.
//
// Usage: node selftest/selftest.mjs [--url http://localhost:6008] [--react-modules DIR]
//                                   [--update-golden] [--only TEXT] [--verbose]

import { existsSync } from 'node:fs';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

import { chromium } from 'playwright';

import { ARIA_SNIPPETS, MATCHER_SNIPPETS, SCENARIOS, STYLE_CASES } from './corpus.mjs';
import { loadReference, referenceDir } from './reference.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const GOLDEN_FILE = path.join(HERE, 'golden', 'reference.json');
const DEFAULT_URL = process.env.WORKBENCH_URL ?? 'http://localhost:6008';
// The React project is a sibling of this repository
const DEFAULT_REACT_MODULES = process.env.STROOM_UI_REACT_MODULES
  ?? path.resolve(HERE, '../../../../../stroom-ui-react/node_modules');
const STORY_PATH = '/iframe.html?id=widgets-buttons-button--default&viewMode=story&selftest';
const LOAD_TIMEOUT_MILLIS = 60000;

const { values: options } = parseArgs({
  options: {
    url: { type: 'string', default: DEFAULT_URL },
    'react-modules': { type: 'string', default: DEFAULT_REACT_MODULES },
    'update-golden': { type: 'boolean', default: false },
    only: { type: 'string' },
    verbose: { type: 'boolean', default: false },
    help: { type: 'boolean', default: false },
  },
});

if (options.help) {
  console.log(`Usage: node selftest/selftest.mjs [options]

  --url URL             The workbench (default ${DEFAULT_URL}, or $WORKBENCH_URL)
  --react-modules DIR   The React project's node_modules, holding storybook/dist/test
                        (default ${DEFAULT_REACT_MODULES}, or $STROOM_UI_REACT_MODULES).
                        If it isn't there, the results are compared with golden/reference.json.
  --update-golden       Write the reference's results to golden/reference.json
  --only TEXT           Only run the scenarios whose name contains TEXT
  --verbose             Print every comparison`);
  process.exit(0);
}

const scenarios = SCENARIOS.filter((scenario) => !options.only || scenario.name.includes(options.only));

/// Opens the preview page with the self-test hooks and the page probe.
async function openPage(browser, withReference) {
  const context = await browser.newContext();
  const page = await context.newPage();
  const pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  await page.goto(new URL(STORY_PATH, options.url).href);
  await page.waitForFunction(() => window.__workbenchDom !== undefined
    && ['COMPLETED', 'ERRORED'].includes(document.documentElement.getAttribute('data-play-status')),
  null, { timeout: LOAD_TIMEOUT_MILLIS });
  // The story itself isn't part of the test, e.g. its button mustn't be in the tab order
  await page.addStyleTag({ content: '#workbench-root { display: none !important; }' });
  await page.addScriptTag({ path: path.join(HERE, 'page-probe.js') });
  if (withReference) {
    await loadReference(page, withReference);
  }
  return { context, page, pageErrors };
}

/// Roles, names and accessibility of every element of every snippet, as [port, reference].
async function runAria(page, includeReference) {
  return page.evaluate(({ snippets, includeReference }) => {
    const ours = [];
    const reference = [];
    const dom = window.__workbenchDom;
    snippets.forEach((html) => {
      window.__selftest.mount(html);
      const oursItems = [];
      const referenceItems = [];
      window.__selftest.elements().forEach((el) => {
        oursItems.push({
          el: el.outerHTML.slice(0, 70), roles: dom.role(el) == null ? [] : [dom.role(el)],
          name: dom.name(el), inaccessible: dom.isInaccessible(el),
        });
        if (includeReference) {
          const ref = window.__ref;
          referenceItems.push({
            el: el.outerHTML.slice(0, 70),
            roles: el.hasAttribute('role') ? [el.getAttribute('role').split(' ')[0]] : ref.__getImplicitAriaRoles(el),
            name: ref.__computeAccessibleName(el, { computedStyleSupportsPseudoElements: false }),
            inaccessible: ref.__isInaccessible(el),
          });
        }
      });
      ours.push(oursItems);
      reference.push(referenceItems);
    });
    return { ours, reference };
  }, { snippets: ARIA_SNIPPETS, includeReference });
}

/// jest-dom's visibility, checked, disabled and style matchers, as [port, reference].
async function runMatchers(page, includeReference) {
  return page.evaluate(({ snippets, styles, includeReference }) => {
    const dom = window.__workbenchDom;
    const passes = (assertion) => {
      try {
        assertion();
        return true;
      } catch (e) {
        return false;
      }
    };
    const ours = [];
    const reference = [];
    snippets.forEach((html) => {
      window.__selftest.mount(html);
      window.__selftest.elements().filter((el) => el.id).forEach((el) => {
        const label = html + ' #' + el.id;
        ours.push({ el: label, visible: dom.isVisible(el), checked: dom.checkedState(el) === 1,
          disabled: dom.isDisabled(el) });
        if (includeReference) {
          const expect = window.__ref.expect;
          reference.push({ el: label, visible: passes(() => expect(el).toBeVisible()),
            checked: passes(() => expect(el).toBeChecked()), disabled: passes(() => expect(el).toBeDisabled()) });
        }
      });
    });
    styles.forEach(([html, property, value]) => {
      const el = window.__selftest.mount(html).querySelector('#a');
      const label = html + ' ' + property + ': ' + value;
      ours.push({ el: label, style: dom.hasStyle(el, property, value) });
      if (includeReference) {
        reference.push({ el: label, style: passes(() => window.__ref.expect(el).toHaveStyle({ [property]: value })) });
      }
    });
    return { ours, reference };
  }, { snippets: MATCHER_SNIPPETS, styles: STYLE_CASES, includeReference });
}

/// Runs the scenarios with the port, or with user-event.
async function runScenarios(page, useReference) {
  const results = {};
  for (const scenario of scenarios) {
    await page.evaluate(({ html, setup }) => {
      window.__selftest.mount(html, setup);
    }, scenario);
    const steps = [];
    for (const [action, selector, text] of scenario.actions) {
      const failed = await page.evaluate(async ({ action, selector, text, useReference }) => {
        const el = selector ? window.__selftest.find(selector) : null;
        if (!useReference) {
          const dom = window.__workbenchDom;
          if (action === 'tab' || action === 'shiftTab') {
            return dom.keyboard(action === 'tab' ? '{Tab}' : '{Shift>}{Tab}{/Shift}') != null;
          }
          return (action === 'keyboard' ? dom.keyboard(text) : dom[action](el, text)) != null;
        }
        const userEvent = window.__ref.userEvent;
        try {
          if (action === 'fireEvent') {
            window.__ref.fireEvent[text](el);
          } else if (action === 'rightClick') {
            await userEvent.pointer({ keys: '[MouseRight]', target: el });
          } else if (action === 'tab' || action === 'shiftTab') {
            await userEvent.tab({ shift: action === 'shiftTab' });
          } else if (action === 'keyboard') {
            await userEvent.keyboard(text);
          } else if (action === 'type') {
            await userEvent.type(el, text);
          } else {
            await userEvent[action](el);
          }
          return false;
        } catch (e) {
          return true;
        }
      }, { action, selector, text, useReference });
      const events = await page.evaluate(() => window.__selftest.takeLog());
      const state = await page.evaluate(() => window.__selftest.state());
      steps.push({ action: [action, selector, text].filter((part) => part).join(' '), failed, events, state });
    }
    results[scenario.name] = steps;
  }
  return results;
}

/// Compares two results, returning a description of the first difference, or null.
function difference(ours, reference, where = '') {
  if (JSON.stringify(ours) === JSON.stringify(reference)) {
    return null;
  }
  if (Array.isArray(ours) && Array.isArray(reference)) {
    for (let i = 0; i < Math.max(ours.length, reference.length); i++) {
      if (JSON.stringify(ours[i]) !== JSON.stringify(reference[i])) {
        const context = (list) => list.slice(Math.max(0, i - 2), i + 3).map((item) => '      ' + JSON.stringify(item))
          .join('\n');
        return `${where}[${i}] differs\n    workbench:\n${context(ours)}\n    reference:\n${context(reference)}`;
      }
    }
  }
  if (ours && reference && typeof ours === 'object' && typeof reference === 'object') {
    for (const key of new Set([...Object.keys(ours), ...Object.keys(reference)])) {
      const found = difference(ours[key], reference[key], `${where}.${key}`);
      if (found) {
        return found;
      }
    }
  }
  return `${where}\n    workbench: ${JSON.stringify(ours)}\n    reference: ${JSON.stringify(reference)}`;
}

async function main() {
  const dir = referenceDir(options['react-modules']);
  if (!dir && options['update-golden']) {
    console.error(`Can't update the golden file: no storybook/dist/test in ${options['react-modules']}`);
    process.exit(2);
  }
  const browser = await chromium.launch();
  const failures = [];
  let checks = 0;
  try {
    let reference;
    let ours;
    const port = await openPage(browser, null);
    if (dir) {
      console.log(`Comparing with the reference in ${dir}`);
      const ref = await openPage(browser, dir);
      const aria = await runAria(ref.page, true);
      const matchers = await runMatchers(ref.page, true);
      reference = { aria: aria.reference, matchers: matchers.reference,
        scenarios: await runScenarios(ref.page, true) };
      ours = { aria: aria.ours, matchers: matchers.ours };
      await ref.context.close();
      if (options['update-golden']) {
        await writeFile(GOLDEN_FILE, JSON.stringify(reference, null, 1) + '\n');
        console.log(`Wrote ${GOLDEN_FILE}`);
      }
    } else {
      if (!existsSync(GOLDEN_FILE)) {
        throw new Error(`No reference in ${options['react-modules']} and no ${GOLDEN_FILE}`);
      }
      console.log(`No reference in ${options['react-modules']}: comparing with ${GOLDEN_FILE}`);
      reference = JSON.parse(await readFile(GOLDEN_FILE, 'utf8'));
      const aria = await runAria(port.page, false);
      const matchers = await runMatchers(port.page, false);
      ours = { aria: aria.ours, matchers: matchers.ours };
    }
    ours.scenarios = await runScenarios(port.page, false);
    if (port.pageErrors.length) {
      failures.push(`Page errors: ${port.pageErrors.join('; ')}`);
    }
    await port.context.close();

    ARIA_SNIPPETS.forEach((html, i) => {
      checks++;
      const found = difference(ours.aria[i], reference.aria[i]);
      if (found) {
        failures.push(`Roles/names of ${html}: ${found}`);
      }
    });
    checks++;
    const matcherDifference = difference(ours.matchers, reference.matchers, 'matchers');
    if (matcherDifference) {
      failures.push(`jest-dom matchers: ${matcherDifference}`);
    }
    for (const scenario of scenarios) {
      checks++;
      const found = difference(ours.scenarios[scenario.name], reference.scenarios[scenario.name]);
      if (found) {
        failures.push(`Scenario "${scenario.name}": ${found}`);
      } else if (options.verbose) {
        console.log(`ok - ${scenario.name}`);
      }
    }
  } finally {
    await browser.close();
  }
  for (const failure of failures) {
    console.log(`FAIL ${failure}\n`);
  }
  console.log(`${checks - failures.length} of ${checks} checks passed`);
  process.exit(failures.length ? 1 : 0);
}

main().catch((error) => {
  console.error(error);
  process.exit(2);
});
