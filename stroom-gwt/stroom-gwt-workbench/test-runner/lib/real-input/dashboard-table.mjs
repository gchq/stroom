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

// Real input checks for a dashboard table's filter row (see index.mjs, and FilterCell): a filter
// box commits what is typed when a real Enter is pressed (the browser's change event, which a
// play's keys don't fire), and the header drawn again keeps the focus in the box; real Space and
// Enter work the box's disable button, which keeps the focus.

import assert from 'node:assert/strict';

const BOX = '.dashboard-table-filter-cell .text-field';
const BUTTON = '.dashboard-table-filter-cell__disable-button';

// Waits (up to two seconds) for a condition the page reaches a moment after a key or click
async function waitFor(page, condition, message) {
  const deadline = Date.now() + 2000;
  while (!(await condition()) && Date.now() < deadline) {
    await page.waitForTimeout(25);
  }
  assert.ok(await condition(), message);
}

// Marks the Name column's filter box and button, so that ones drawn again can be told apart
function markHeader(page) {
  return page.evaluate(([box, button]) => {
    document.querySelector(box).__old = true;
    document.querySelector(button).__old = true;
  }, [BOX, BUTTON]);
}

// Describes the focused element: 'box <value> <caret> old|new', 'button <title> old|new' or
// what else it is
function focused(page) {
  return page.evaluate(([box, button]) => {
    const element = document.activeElement;
    const age = element && element.__old ? 'old' : 'new';
    if (element && element.matches(box)) {
      return `box ${element.value} ${element.selectionStart} ${age}`;
    }
    if (element && element.matches(button)) {
      return `button ${element.title} ${age}`;
    }
    return element ? `${element.tagName.toLowerCase()} ${element.className}` : 'nothing';
  }, [BOX, BUTTON]);
}

async function expectFocus(page, expected, message) {
  await waitFor(page, async () => (await focused(page)) === expected,
      `${message}: expected the focus on ${expected}, found ${await focused(page)}`);
}

export const CHECKS = {
  // The play ends with the Name column filtered on 'alp' and the filter on
  'app-editors-dashboardeditor--table-filter-row': async (page) => {
    await page.locator(BOX).click();
    await page.keyboard.press('End');
    await page.keyboard.type('ha');
    await markHeader(page);
    await page.keyboard.press('Enter');
    await expectFocus(page, 'box alpha 5 new',
        'Enter sets the filter, and the header drawn again keeps the focus and caret in the box');

    await page.keyboard.press('Tab');
    await expectFocus(page, 'button Disable Filter new', 'Tab goes on to the box\'s button');
    await markHeader(page);
    await page.keyboard.press(' ');
    await expectFocus(page, 'button Enable Filter new', 'Space turns the filter off and keeps the focus');
    await markHeader(page);
    await page.keyboard.press('Enter');
    await expectFocus(page, 'button Disable Filter new', 'Enter turns the filter on and keeps the focus');

    // Left and Right on the button are its own, not moves along the headers
    await page.keyboard.press('ArrowRight');
    await expectFocus(page, 'button Disable Filter new', 'Right leaves the focus on the button');

    // A real click works it too, and doesn't open the column's menu
    await page.locator(BUTTON).click();
    await waitFor(page, async () => (await page.locator(BUTTON).getAttribute('title')) === 'Enable Filter',
        'a click turns the filter off');
    assert.equal(await page.locator('[role="menuitem"]').count(), 0, 'the click opens no menu');
  },
};
