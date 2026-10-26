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
    const filter = page.locator('.selection-list__filter .quick-filter__input');
    await waitFor(page, async () => (await filter.count()) > 0 && (await filter.inputValue()) === '42',
      'what is typed is the quick filter');
  },
  // Home and End, pressed for real in the closed box, open the list at its first and last option
  'widgets-selectors-selectionbox--home-and-end-open-at-the-ends': async (page) => {
    const box = page.getByRole('combobox');
    await box.focus();
    await page.keyboard.press('End');
    await waitFor(page, async () => (await focusedText(page)) === 'Echo', 'End opens the list at its last option');
    await page.keyboard.press('Escape');
    await waitFor(page, async () => !(await popupOpen(page)), 'Escape closes the list');
    await page.keyboard.press('Home');
    await waitFor(page, async () => (await focusedText(page)) === 'Alpha', 'Home opens the list at its first option');
    await page.keyboard.press('Escape');
    await waitFor(page, async () => !(await popupOpen(page)), 'Escape closes the list');
  },
  // The selection box and text field as grid cells (SelectionBoxCell, TextFieldCell), in the floor
  // map settings' Value Schema grid: Down opens a row's list (the grid doesn't take it as a row
  // move), Escape closes it with the focus back on the box, a real click chooses and the focus comes
  // back to the box the grid draws again; the arrows in a text field stay in it, and Tab goes on
  // from one row's controls to the next row's
  'app-editors-floormapsettings--schema': async (page) => {
    const box = () => page.getByRole('combobox', { name: 'Role for $.temp' });
    const hasFocus = (locator) => locator.evaluate((element) => element === document.activeElement);
    await box().focus();
    await page.keyboard.press('Alt+ArrowDown');
    await waitFor(page, () => popupOpen(page), 'Alt+Down opens the row\'s list');
    await page.keyboard.press('Escape');
    await waitFor(page, async () => !(await popupOpen(page)), 'Escape closes the list');
    await waitFor(page, () => hasFocus(box()), 'the focus is back on the box');

    await page.keyboard.press('Enter');
    await waitFor(page, () => popupOpen(page), 'Enter opens the row\'s list');
    await page.locator(`${POPUP} [role="option"]`, { hasText: 'STATUS' }).click();
    await waitFor(page, async () => (await box().inputValue()) === 'STATUS', 'a click chooses the role');
    await waitFor(page, () => hasFocus(box()), 'the focus comes back to the row\'s box');

    const path = page.getByRole('textbox', { name: 'Path for $.type' });
    await path.focus();
    await page.keyboard.press('ArrowLeft');
    await page.keyboard.press('ArrowDown');
    await page.waitForTimeout(200);
    assert.ok(await hasFocus(path), 'the arrows in a text field stay in it');

    const defaultField = page.getByRole('textbox', { name: 'Default for $.type' });
    await defaultField.focus();
    // As a person would before pressing Tab: the grid handles the focus a moment later, in a deferred
    // command, and a Tab sent in that moment can find the focus on the page (see selection-list.mjs)
    await page.waitForTimeout(150);
    const nextRole = page.getByRole('combobox', { name: 'Role for $.label' });
    let reached = false;
    const visited = [];
    // The grid's own keyboard cell may take a Tab on the way (a stray tab stop of every grid)
    for (let i = 0; i < 3 && !reached; i++) {
      await page.keyboard.press('Tab');
      await page.waitForTimeout(100);
      reached = await hasFocus(nextRole);
      visited.push(await page.evaluate(() => document.activeElement.getAttribute('aria-label')
          || `${document.activeElement.tagName} "${document.activeElement.textContent.trim().slice(0, 12)}"`));
    }
    assert.ok(reached, `Tab goes on from one row's controls to the next row's (went to ${visited.join(', ')})`);
  },
};
