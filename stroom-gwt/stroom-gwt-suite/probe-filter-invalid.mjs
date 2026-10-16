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
// Can a filter dialog still be closed after its expression fails validation?
//
// Filter Schedules' OK passes the expression to `ExpressionValidator.validateExpression`, which
// alerts when the expression is invalid but never tells the dialog, so nothing resets it: OK and
// Cancel stay disabled and Escape does nothing. An added term with no field is invalid ("Expression
// term has a missing field"). Found by round r10, where the walker logged the dialog as stranded.
//
//   STROOM_USER=admin STROOM_PASS=a node probe-filter-invalid.mjs
//
// Fixed when the last line says the dialog closed.
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { attachReadOnlyGuard } from './compare/lib/readonly-guard.mjs';

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
attachReadOnlyGuard(page, { enabled: true });
gwt.baseUrl = env('URL', 'http://localhost:8080');
await page.goto(gwt.baseUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());
await page.waitForTimeout(2_000);

const dialogs = () => page.evaluate(() => [...document.querySelectorAll('.dialog-titleText')]
  .filter((e) => e.offsetWidth).map((e) => e.textContent.trim()));
const press = async (name) => {
  try {
    await page.getByRole('button', { name, exact: true }).last().click({ timeout: 4_000 });
  } catch {
    console.log(`${name}: could not be pressed (disabled)`);
  }
  await page.waitForTimeout(1_500);
  console.log(`after ${name}: ${JSON.stringify(await dialogs())}`);
};

await page.locator('[title="Show Main Menu"]').first().click();
await page.waitForTimeout(600);
await page.getByText('Monitoring', { exact: true }).first().hover();
await page.waitForTimeout(600);
await page.getByText('Execution Schedule Manager', { exact: true }).first().click();
await page.waitForTimeout(2_000);
await page.locator('[title="Filter Schedules"]').first().click();
await page.waitForTimeout(1_500);
console.log(`opened: ${JSON.stringify(await dialogs())}`);

// A term with no field chosen
await page.locator('.popupContent [title="Add Term"], [role=dialog] [title="Add Term"]').first().click();
await page.waitForTimeout(800);

await press('OK');
await press('Close');
await press('Cancel');
await page.keyboard.press('Escape');
await page.waitForTimeout(1_000);
const left = await dialogs();
console.log(left.includes('Filter Schedules')
  ? 'BUG: Filter Schedules can no longer be closed (OK and Cancel disabled, Escape ignored)'
  : 'the dialog closed');
await browser.close();
