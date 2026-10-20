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

// Real input checks for menus (see index.mjs, and Widgets/Menu/MenuPanel): an item is chosen when
// the real mouse button is let go over it, so a press moved off it first is taken back (WCAG 2.5.2);
// and the focus ring shows for the keyboard, not the mouse.

import assert from 'node:assert/strict';

const MENU = '.popup--floating:has(.menu)';

// The text of the story's "Last action" result
function lastAction(page) {
  return page.locator('p strong').first().textContent();
}

// The centre of an item, found by its text
async function centreOf(page, text) {
  const box = await page.locator('.menu__item', { hasText: text }).first().boundingBox();
  return { x: box.x + box.width / 2, y: box.y + box.height / 2 };
}

// The focused item's focus ring (its box shadow), or 'none'
function ring(page) {
  return page.evaluate(() => {
    const item = document.activeElement?.querySelector('.menu__item');
    return item ? getComputedStyle(item).boxShadow : 'none';
  });
}

async function openWithMouse(page) {
  const button = page.locator('button').first();
  const box = await button.boundingBox();
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  await page.locator(MENU).first().waitFor();
}

export const CHECKS = {
  'widgets-menu-menupanel--press-moved-off-is-cancelled': async (page) => {
    const before = await lastAction(page);

    // Opened with the mouse: its first item has the focus, without a ring
    await openWithMouse(page);
    assert.equal(await ring(page), 'none', 'a menu opened with the mouse shows no focus ring');

    // Pressed on Open, moved to Reload, let go: nothing is chosen and the menu stays open
    const open = await centreOf(page, 'Open');
    const reload = await centreOf(page, 'Reload');
    await page.mouse.move(open.x, open.y);
    await page.mouse.down();
    await page.mouse.move(reload.x, reload.y, { steps: 4 });
    await page.mouse.up();
    await page.waitForTimeout(200);
    assert.equal(await lastAction(page), before, 'a press moved off its item chooses nothing');
    assert.equal(await page.locator(MENU).count(), 1, 'the menu stays open');

    // Pressed and let go on Reload: chosen, and the menu closes
    await page.mouse.down();
    await page.mouse.up();
    await page.locator(MENU).first().waitFor({ state: 'detached' });
    assert.equal(await lastAction(page), 'Reload', 'a press let go on its item chooses it');

    // Opened with the keyboard: its first item has the focus ring
    await page.locator('button').first().focus();
    await page.keyboard.press('Enter');
    await page.locator(MENU).first().waitFor();
    await page.waitForTimeout(200);
    assert.notEqual(await ring(page), 'none', 'a menu opened with the keyboard shows the focus ring');
    await page.keyboard.press('Escape');
  },
};
