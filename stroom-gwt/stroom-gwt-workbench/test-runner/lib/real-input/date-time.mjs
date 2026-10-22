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

// Real input checks for the calendar grid and the date-time box (see index.mjs): the grid's keys
// pressed for real (so the browser's own Tab, and :focus-visible after a key), and the box's
// Alt+Down and the dialog's Escape.

import assert from 'node:assert/strict';

const DIALOG_TITLE = 'Set Date And Time';

// Waits (up to two seconds) for a condition the page reaches a moment after a key
async function waitFor(page, condition, message) {
  const deadline = Date.now() + 2000;
  while (!(await condition()) && Date.now() < deadline) {
    await page.waitForTimeout(25);
  }
  assert.ok(await condition(), message);
}

// The accessible name (aria-label) of the element with the focus
function focusedLabel(page) {
  return page.evaluate(() => document.activeElement?.getAttribute('aria-label') ?? '');
}

function gridName(page) {
  return page.evaluate(() => document.querySelector('[role="grid"]')?.getAttribute('aria-label') ?? '');
}

function dialogOpen(page) {
  return page.getByText(DIALOG_TITLE, { exact: true }).count().then((count) => count > 0);
}

export const CHECKS = {
  // A click puts the focus on the day clicked (and chooses it); the real keys then move the focus,
  // across months, with the focus ring shown, and the real Tab leaves the grid in one step
  'widgets-date-time-calendargrid--arrow-keys-move-the-focus': async (page) => {
    await page.getByRole('gridcell', { name: 'Wednesday, 17 January 2024' }).click();
    await page.keyboard.press('ArrowRight');
    await waitFor(page, async () => (await focusedLabel(page)) === 'Thursday, 18 January 2024',
      'Right moves to the next day');
    assert.ok(await page.evaluate(() => document.activeElement.matches(':focus-visible')),
      'a day focused by a key shows the focus ring');
    await page.keyboard.press('PageDown');
    await waitFor(page, async () => (await focusedLabel(page)) === 'Sunday, 18 February 2024',
      'Page Down moves a month');
    assert.equal(await gridName(page), 'February 2024', 'the grid shows the month moved to');
    await page.keyboard.press('Shift+PageUp');
    await waitFor(page, async () => (await focusedLabel(page)) === 'Saturday, 18 February 2023',
      'Shift+Page Up moves a year');
    await page.keyboard.press('Enter');
    await waitFor(page, async () => (await page.getByText('Selected: 2023-02-18').count()) > 0,
      'Enter chooses the day with the focus');
    await page.keyboard.press('Tab');
    assert.notEqual(await page.evaluate(() => document.activeElement?.getAttribute('role')), 'gridcell',
      'Tab leaves the grid');
  },
  // Alt+Down in the field opens the dialog; Escape closes it, putting the focus back in the field
  'widgets-date-time-datetimepicker--alt-down-opens-the-dialog': async (page) => {
    const field = page.locator('.date-time-box__input');
    await field.focus();
    await page.keyboard.press('Alt+ArrowDown');
    await waitFor(page, () => dialogOpen(page), 'Alt+Down opens the dialog');
    await page.keyboard.press('Escape');
    await waitFor(page, async () => !(await dialogOpen(page)), 'Escape closes the dialog');
    await waitFor(page, () => field.evaluate((element) => element === document.activeElement),
      'the focus is back in the field');
  },
};
