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
// Can User Preferences still be closed after saving them fails? (#48)
//
// `UserPreferencesPresenter`'s OK calls `UserPreferencesManager.update`, which has no failure
// handler, so when the save fails nothing resets the dialog: OK keeps its spinner, OK and Cancel stay
// disabled and Escape does nothing. The read-only guard refuses the save, which stands in for any
// failure (a server error, a lost connection). Only a changed preference is saved, so this changes
// the Layout Density first.
//
//   STROOM_USER=admin STROOM_PASS=a node probe-prefs-failsave.mjs
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

await page.locator('[title="Show Menu"]').first().click();
await page.waitForTimeout(600);
await page.getByText('User', { exact: true }).first().hover();
await page.waitForTimeout(600);
await page.getByText('Preferences', { exact: true }).first().click();
await page.waitForTimeout(1_500);
console.log(`opened: ${JSON.stringify(await dialogs())}`);

// The second selection box is Layout Density: choose whichever option isn't chosen
const density = page.locator('.SelectionBox-textBox').nth(1);
const before = await density.inputValue();
await density.click();
await page.waitForTimeout(600);
await page.getByText(before === 'Comfortable' ? 'Compact' : 'Comfortable', { exact: true }).last().click();
await page.waitForTimeout(800);
console.log(`Layout Density: ${before} -> ${await density.inputValue()}`);

await press('OK');
await press('Close');
await press('Cancel');
await page.keyboard.press('Escape');
await page.waitForTimeout(1_000);
const left = await dialogs();
console.log(left.includes('User Preferences')
  ? 'BUG: User Preferences can no longer be closed (OK and Cancel disabled, Escape ignored)'
  : 'the dialog closed');
await browser.close();
