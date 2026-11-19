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

// Real input checks for the floor map's icon picker (see index.mjs): its radio group's arrow keys
// pressed for real, as the browser moves a radio group's choice itself on a trusted key, which a
// play's keys aren't. Up and Down must choose the icon above or below (the picker's own), and not
// also the one before or after (the browser's).

import assert from 'node:assert/strict';

// The checked radio's cell's position, and its label
function chosen(page) {
  return page.evaluate(() => {
    const radio = document.querySelector('.floormap-icon-picker__input:checked');
    if (!radio) {
      return null;
    }
    const cell = radio.closest('label').getBoundingClientRect();
    return { left: Math.round(cell.left), top: Math.round(cell.top), name: radio.closest('label').title,
      focused: radio === document.activeElement };
  });
}

export const CHECKS = {
  // The story ends with the first icon chosen and focused
  'app-editors-floormapforms--icon-picker-rows': async (page) => {
    const first = await chosen(page);
    assert.ok(first && first.focused, 'the story ends with the first icon chosen, with the focus');

    await page.keyboard.press('ArrowDown');
    await page.waitForTimeout(100);
    const below = await chosen(page);
    assert.ok(below.left === first.left && below.top > first.top,
      `Down chooses the icon below the first (chose ${below.name})`);
    assert.ok(below.focused, 'the icon chosen has the focus');

    await page.keyboard.press('ArrowRight');
    await page.waitForTimeout(100);
    const next = await chosen(page);
    assert.ok(next.top === below.top && next.left > below.left, 'Right chooses the next icon in the row');

    await page.keyboard.press('ArrowUp');
    await page.keyboard.press('ArrowLeft');
    await page.waitForTimeout(100);
    const back = await chosen(page);
    assert.equal(back.name, first.name, 'Up then Left come back to the first icon');
  },
};
