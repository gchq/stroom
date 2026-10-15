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

// Real input checks for the Ace editor (see index.mjs): Ace shows a gutter marker's message when
// the mouse moves over it, working out the row from where the mouse is, which a play's hover
// (dispatched at no particular point) doesn't do.

import assert from 'node:assert/strict';

// The text of Ace's gutter tooltip if it is showing, otherwise null
async function gutterTooltip(page) {
  return page.evaluate(() => {
    const element = document.querySelector('.ace_gutter-tooltip');
    return element && getComputedStyle(element).display !== 'none' ? element.textContent : null;
  });
}

// Whether the page's clock (Date) is fixed, as the screenshot suite fixes it (--fixed-time)
async function isClockFixed(page) {
  return page.evaluate(() => new Promise((resolve) => {
    const before = Date.now();
    setTimeout(() => resolve(Date.now() === before), 50);
  }));
}

// Moves the real mouse over a gutter cell's marker, and waits for Ace's tooltip to show.
// Ace shows it once the mouse has rested for a while, which it measures with Date, so with the
// clock fixed it would never show: the fixed time is moved on past the wait, then put back.
async function hoverMarker(page, cellClass) {
  const marker = page.locator(`.ace_gutter-cell.${cellClass} .ace_gutter_annotation`);
  const box = await marker.boundingBox();
  assert.ok(box, `the ${cellClass} marker is drawn`);
  const fixedTime = await isClockFixed(page) ? await page.evaluate(() => Date.now()) : null;
  await page.mouse.move(box.x + 2, box.y + 2);
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 5 });
  try {
    if (fixedTime !== null) {
      await page.clock.setFixedTime(fixedTime + 2000);
    }
    await page.waitForFunction(() => {
      const element = document.querySelector('.ace_gutter-tooltip');
      return element && getComputedStyle(element).display !== 'none';
    }, null, { timeout: 5000 });
  } finally {
    if (fixedTime !== null) {
      await page.clock.setFixedTime(fixedTime);
    }
  }
}

export const CHECKS = {
  // Hovering each of Stroom's annotation markers shows its message (Stroom's markers are Ace's
  // info, warning and error annotations, with Stroom's classes)
  'widgets-editors-viewers-aceeditorfeatures--all-annotation-types': async (page) => {
    for (const [cellClass, message] of [
      ['stroom_info', 'Consider adding a schema location'],
      ['stroom_warning', 'Deprecated system name'],
      ['stroom_error', 'Unexpected element'],
      ['stroom_fatal_error', 'Document is not well formed'],
    ]) {
      await hoverMarker(page, cellClass);
      const text = await gutterTooltip(page);
      assert.ok(text.includes(message), `the ${cellClass} marker's tooltip shows "${message}" (was "${text}")`);
      // Away from the gutter, so the next marker's tooltip is shown afresh
      await page.mouse.move(600, 500, { steps: 3 });
    }
  },
};
