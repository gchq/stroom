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
// Can a duplicated dashboard component be placed, or cancelled, from the keyboard?
//
// A component tab's Duplicate puts a glass over the window and waits for a click to place the copy
// (gwt-bugs #57). Since the fix Enter places it beside the original and Escape cancels it. Nothing
// is saved: the dashboard is closed afterwards with its changes discarded.
//
//   STROOM_USER=admin STROOM_PASS=a node probe-placement-keys.mjs
//
// Fixed when both lines say "glass gone" and the tab counts are one more, then the same.
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { attachReadOnlyGuard } from './compare/lib/readonly-guard.mjs';
import { clickMenuItem } from './compare/lib/structure.mjs';

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
attachReadOnlyGuard(page, { enabled: true });
gwt.baseUrl = env('URL', 'http://localhost:8080');
await page.goto(gwt.baseUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());
await page.waitForTimeout(2_000);
const dashboard = env('DASHBOARD', 'Test Dashboard With Conditional Formatting');
await gwt.filterExplorer(page, dashboard);
await page.waitForTimeout(1_500);
await gwt.openDocByName(page, dashboard, 'Dashboard');
await page.waitForTimeout(3_000);

const glass = async () => (await page.locator('.flexLayout-glassVisible').count()) > 0;
const tabs = () => page.locator('.tab-bar--link .tab-bar__label').evaluateAll((es) => es.filter((e) => e.offsetWidth > 0).length);

// Duplicate the first component tab from its own Settings menu, then press the key.
const duplicateThenPress = async (key) => {
  const before = await tabs();
  await page.locator('.tab-layout__bar-outer button[title="Tab Actions"]').first().click();
  await page.waitForTimeout(600);
  if (!(await clickMenuItem(page, 'Duplicate'))) throw new Error('no Duplicate in the tab menu');
  await page.waitForTimeout(800);
  const waiting = await glass();
  await page.keyboard.press(key);
  await page.waitForTimeout(800);
  console.log(`${key}: glass ${waiting ? 'up' : 'NOT up'} after Duplicate, ${(await glass()) ? 'STILL UP' : 'glass gone'} after ${key}; `
    + `component tabs ${before} -> ${await tabs()}`);
};

await duplicateThenPress('Enter');
await duplicateThenPress('Escape');
await browser.close();
