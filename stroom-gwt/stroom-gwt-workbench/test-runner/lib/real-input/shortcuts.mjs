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

// Real input checks for the shortcuts of #2278 (see index.mjs): Alt+PageDown and Alt+PageUp move
// between the main tabs, going round, and take the focus into the tab (App/Main/AppShell
// KeyboardShortcuts); '?' shows the list of shortcuts, but not while typing in a field; and Alt+R
// presses the Refresh button of the list that has the focus, not another list's (App/Main/AppShell
// OpenedScreenTakesFocus, the User Groups screen's lists). Both need the app's global key handler,
// which a screen's story on its own doesn't have. A play's keys have no key codes for the key
// bindings to read.

import assert from 'node:assert/strict';

// Waits (up to two seconds) for a condition the page reaches a moment after a key
async function waitFor(page, condition, message) {
  const deadline = Date.now() + 2000;
  while (!(await condition()) && Date.now() < deadline) {
    await page.waitForTimeout(25);
  }
  assert.ok(await condition(), message);
}

// The selected main tab's label, and whether the focus is in the open content
function tabState(page) {
  return page.evaluate(() => {
    const tab = document.querySelector('[role="tab"][aria-selected="true"]');
    const active = document.activeElement;
    return {
      tab: tab ? tab.textContent.trim() : null,
      inContent: Boolean(active && active.closest('.curveTabLayoutViewImpl-layerContainerOuter')),
    };
  });
}

// Whether the list of shortcuts is showing
function shortcutsShowing(page) {
  return page.evaluate(() => [...document.querySelectorAll('.popupPanel, [role="dialog"]')]
    .some((dialog) => dialog.textContent.includes('Keyboard Shortcuts')
      && dialog.querySelector('[aria-label="Filter keyboard shortcuts"]') !== null));
}

export const CHECKS = {
  'app-main-appshell--keyboard-shortcuts': async (page) => {
    // The play leaves 'Colours' (the tab opened last) selected
    await waitFor(page, async () => (await tabState(page)).tab === 'Colours', 'Colours is selected');

    await page.keyboard.press('Alt+PageDown');
    await waitFor(page, async () => (await tabState(page)).tab === 'Countries',
      'Alt+PageDown from the last tab goes round to the first');
    await waitFor(page, async () => (await tabState(page)).inContent, 'the tab chosen has the focus');
    await page.keyboard.press('Alt+PageDown');
    await waitFor(page, async () => (await tabState(page)).tab === 'Colours', 'Alt+PageDown moves to the next tab');
    await page.keyboard.press('Alt+PageUp');
    await waitFor(page, async () => (await tabState(page)).tab === 'Countries',
      'Alt+PageUp moves to the tab before');

    // '?' shows the shortcuts, from anywhere but a field being typed in; Escape closes them
    await page.locator('[role="navigation"] [role="treeitem"]').first().focus();
    await page.keyboard.press('Shift+Slash');
    await waitFor(page, () => shortcutsShowing(page), '? shows the keyboard shortcuts');
    await waitFor(page, () => page.evaluate(() => document.activeElement.getAttribute('aria-label')
      === 'Filter keyboard shortcuts'), 'their filter has the focus');
    await page.keyboard.press('Escape');
    await waitFor(page, async () => !(await shortcutsShowing(page)), 'Escape closes them');

    // In a field, '?' is typed rather than showing them
    const filter = page.getByRole('textbox').first();
    await filter.focus();
    await page.keyboard.press('Shift+Slash');
    await page.waitForTimeout(300);
    assert.equal(await shortcutsShowing(page), false, '? in a field doesn\'t show the shortcuts');
  },
  'app-main-appshell--opened-screen-takes-focus': async (page) => {
    // Choosing a group fills the list of its members
    await page.getByRole('grid').first().getByText('Administrators', { exact: true }).click({ timeout: 5000 });
    await waitFor(page, () => page.evaluate(() => [...document.querySelectorAll('[role="grid"]')]
      .filter((grid) => grid.querySelector('[role="row"][data-row]')).length >= 2), 'a second list has rows');

    // The User Groups screen's lists: which list each Refresh button belongs to (the first list
    // found going out from it), and a log of the lists whose Refresh buttons are pressed
    const lists = await page.evaluate(() => {
      const grids = [...document.querySelectorAll('[role="grid"]')];
      window.__refreshed = [];
      for (const button of document.querySelectorAll('.refresh-button')) {
        let element = button.parentElement;
        while (element && !element.querySelector('[role="grid"]')) {
          element = element.parentElement;
        }
        const list = grids.indexOf(element && element.querySelector('[role="grid"]'));
        button.addEventListener('click', () => window.__refreshed.push(list));
      }
      // The lists with rows, which the focus can be put in
      return grids.map((grid, index) => (grid.querySelector('[role="row"][data-row]') ? index : -1))
        .filter((index) => index >= 0);
    });
    assert.ok(lists.length >= 2, 'the screen has two lists with rows');
    assert.equal(await page.locator('.refresh-button').first().getAttribute('title'), 'Refresh (Alt+R)',
      'a Refresh button shows its shortcut');
    const refreshed = () => page.evaluate(() => window.__refreshed.slice());

    // In each list in turn, Alt+R presses that list's Refresh button
    for (let i = 0; i < 2; i++) {
      // The list's first row takes the focus, as the arrow keys give it
      await page.locator('[role="grid"]').nth(lists[i]).locator('[role="row"][data-row]').first().focus();
      await page.keyboard.press('Alt+r');
      await waitFor(page, async () => (await refreshed()).length === i + 1,
        `Alt+R in list ${lists[i]} presses a Refresh button`);
      assert.equal((await refreshed())[i], lists[i], `Alt+R refreshes list ${lists[i]}, which has the focus`);
    }
  },
};
