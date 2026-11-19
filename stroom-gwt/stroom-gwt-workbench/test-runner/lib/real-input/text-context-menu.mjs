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

// Real input checks for the edit menu Stroom shows on a right-click in a text field or code editor
// (see index.mjs, Widgets/Menu/TextContextMenu and gchq/stroom#5897). Copying, cutting and pasting
// need a click the browser trusts, which a play's can't be, so they are checked here: the menu's
// commands run after the menu closes, and the browser must still count them as the user's.
//
// The test browser is let read and write the clipboard; Playwright only grants that in Chromium.

import assert from 'node:assert/strict';

const BOX = 'Name';

async function allowClipboard(page) {
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
}

function clipboardText(page) {
  return page.evaluate(() => navigator.clipboard.readText());
}

// Selects some of a field's text with real keys: Home, then Shift+Right for each character
async function selectFromStart(page, field, characters) {
  await field.click();
  await page.keyboard.press('Home');
  for (let i = 0; i < characters; i++) {
    await page.keyboard.press('Shift+ArrowRight');
  }
}

// Chooses an item of the open edit menu with the real mouse, waiting for the menu to close
async function choose(page, name) {
  const item = page.getByRole('menuitem', { name: new RegExp(`^${name}`) });
  await item.click();
  await page.getByRole('menu').waitFor({ state: 'detached' });
}

export const CHECKS = {
  'widgets-menu-textcontextmenu--with-selection': async (page) => {
    await allowClipboard(page);
    const box = page.getByRole('textbox', { name: BOX });

    // Shift+F10 opens the menu from the keyboard; Copy copies the selection and keeps it
    await selectFromStart(page, box, 5);
    await page.keyboard.press('Shift+F10');
    await choose(page, 'Copy');
    assert.equal(await clipboardText(page), 'hello', 'Copy puts the selection on the clipboard');
    assert.equal(await box.evaluate((el) => document.activeElement === el), true,
        'the focus is back in the field');

    // A real right-click opens it too; Cut takes the selection out
    await selectFromStart(page, box, 6);
    const area = await box.boundingBox();
    await page.mouse.click(area.x + 8, area.y + (area.height / 2), { button: 'right' });
    await choose(page, 'Cut');
    assert.equal(await box.inputValue(), 'world', 'Cut takes the selection out');
    assert.equal(await clipboardText(page), 'hello ', 'and puts it on the clipboard');

    // Undo puts it back
    await page.mouse.click(area.x + 8, area.y + (area.height / 2), { button: 'right' });
    await choose(page, 'Undo');
    assert.equal(await box.inputValue(), 'hello world', 'Undo puts the cut text back');

    // Paste puts the clipboard's text at the caret
    await page.evaluate(() => navigator.clipboard.writeText('big '));
    await selectFromStart(page, box, 0);
    await page.keyboard.press('Shift+F10');
    await choose(page, 'Paste');
    await page.waitForFunction(() => document.activeElement && document.activeElement.value === 'big hello world');
  },

  'widgets-menu-textcontextmenu--shift-click': async (page) => {
    // A Shift+right-click is left to the browser: no Stroom menu
    const box = page.getByRole('textbox', { name: BOX });
    const area = await box.boundingBox();
    await page.keyboard.down('Shift');
    await page.mouse.click(area.x + 8, area.y + (area.height / 2), { button: 'right' });
    await page.keyboard.up('Shift');
    await page.waitForTimeout(300);
    assert.equal(await page.getByRole('menu').count(), 0, 'Shift+right-click shows no Stroom menu');
    // The browser's own menu takes no key presses in a headless browser, but close it anyway
    await page.keyboard.press('Escape');
  },

  'widgets-editors-viewers-aceeditor--context-menu': async (page) => {
    await allowClipboard(page);
    const content = page.locator('.ace_content');
    await content.waitFor();
    const area = await content.boundingBox();

    // Select All then Copy copies the editor's whole text
    await page.mouse.click(area.x + 20, area.y + 8, { button: 'right' });
    await choose(page, 'Select All');
    await page.mouse.click(area.x + 20, area.y + 8, { button: 'right' });
    await choose(page, 'Copy');
    assert.equal(await clipboardText(page), '<root><a>1</a></root>', 'Copy copies the selection');

    // Cut takes it out, Paste puts it back
    await page.mouse.click(area.x + 20, area.y + 8, { button: 'right' });
    await choose(page, 'Cut');
    await page.waitForFunction(() => document.querySelector('.ace_content').textContent.trim() === '');
    await page.mouse.click(area.x + 20, area.y + 8, { button: 'right' });
    await choose(page, 'Paste');
    await page.waitForFunction(
        () => document.querySelector('.ace_content').textContent.includes('<root><a>1</a></root>'));
  },
};
