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

// Real input checks for the selection box (see index.mjs): its combobox keys pressed for real, as the
// list it opens hands its keys on after the browser has handled them.

import assert from 'node:assert/strict';

const POPUP = '.selection-popup';

// Waits (up to two seconds) for a condition the page reaches a moment after a key
async function waitFor(page, condition, message) {
  const deadline = Date.now() + 2000;
  while (!(await condition()) && Date.now() < deadline) {
    await page.waitForTimeout(25);
  }
  assert.ok(await condition(), message);
}

// The text of the element with the focus
function focusedText(page) {
  return page.evaluate(() => document.activeElement?.textContent?.trim() ?? '');
}

function popupOpen(page) {
  return page.locator(POPUP).count().then((count) => count > 0);
}

export const CHECKS = {
  // Typing a character in the closed box opens its list at the first option starting with it;
  // Alt+Up there chooses it; Space opens the list and Escape closes it, giving the focus back
  'widgets-selectors-selectionbox--typing-jumps-to-an-option': async (page) => {
    const box = page.getByRole('combobox');
    await box.focus();
    await page.keyboard.press('e');
    await waitFor(page, async () => (await focusedText(page)) === 'Echo', 'typing "e" goes to Echo');
    await page.keyboard.press('Alt+ArrowUp');
    await waitFor(page, async () => !(await popupOpen(page)), 'Alt+Up closes the list');
    assert.equal(await box.inputValue(), 'Echo', 'Alt+Up chooses the option with the focus');
    assert.ok(await box.evaluate((element) => element === document.activeElement), 'the focus is back in the box');
    await page.keyboard.press('Space');
    await waitFor(page, () => popupOpen(page), 'Space opens the list');
    await page.keyboard.press('Escape');
    await waitFor(page, async () => !(await popupOpen(page)), 'Escape closes the list');
    assert.equal(await box.inputValue(), 'Echo', 'Escape keeps the value');
  },
  // Typing in the closed box of a long list puts what is typed in the list's quick filter
  'widgets-selectors-selectionbox--typing-filters-a-long-list': async (page) => {
    // Close the list the play function left open, so the box has the focus again
    if (await popupOpen(page)) {
      await page.keyboard.press('Escape');
      await waitFor(page, async () => !(await popupOpen(page)), 'Escape closes the list');
    }
    const box = page.getByRole('combobox');
    await box.focus();
    await page.keyboard.type('42');
    const filter = page.locator('.selectionList-quickFilter .quickFilter-textBox');
    await waitFor(page, async () => (await filter.count()) > 0 && (await filter.inputValue()) === '42',
      'what is typed is the quick filter');
  },
};
