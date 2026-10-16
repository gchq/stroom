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

// Real input checks for the app shell's top bar (see index.mjs, and App/Main/TopBar): the real
// keyboard reaches each of its controls and draws its focus ring, and a mouse click doesn't. Whether
// a ring is drawn in full is checked by the FocusRings story's play.

import assert from 'node:assert/strict';

// The top bar's controls, as named by their title or aria-label
const MAIN_MENU = 'Main Menu';
const HIDE_SIDEBAR = 'Hide Sidebar';
const ASK_AI = 'Ask Stroom AI';
const SHOW_MENU = 'Show Menu';

// The name of the element with the focus (its aria-label, else title), or null if none
function focused(page) {
  return page.evaluate(() => {
    const element = document.activeElement;
    if (!element || element === document.body) {
      return null;
    }
    return element.getAttribute('aria-label') || element.getAttribute('title');
  });
}

// The focused element's focus ring (its box shadow), or 'none'
function ring(page) {
  return page.evaluate(() => getComputedStyle(document.activeElement).boxShadow);
}

// Tabs through the page from the start until each control has had the focus, checking that each
// shows its ring as it gets it. Show Menu is in the same toolbar as Ask Stroom AI, one tab stop, so
// it is reached with Right.
async function expectRingsOnTab(page) {
  await page.evaluate(() => document.activeElement?.blur());
  const reached = new Set();
  for (let i = 0; i < 30 && reached.size < 3; i++) {
    await page.keyboard.press('Tab');
    const name = await focused(page);
    if ([MAIN_MENU, HIDE_SIDEBAR, ASK_AI].includes(name) && !reached.has(name)) {
      reached.add(name);
      assert.notEqual(await ring(page), 'none', `${name} shows its focus ring when tabbed to`);
      if (name === ASK_AI) {
        await page.keyboard.press('ArrowRight');
        assert.equal(await focused(page), SHOW_MENU, 'Right moves from Ask Stroom AI to Show Menu');
        assert.notEqual(await ring(page), 'none', `${SHOW_MENU} shows its focus ring when moved to`);
        await page.keyboard.press('ArrowLeft');
      }
    }
  }
  assert.deepEqual([...reached].sort(), [ASK_AI, HIDE_SIDEBAR, MAIN_MENU].sort(),
    'Tab reaches every control in the top bar');
}

// A real click on Show Menu focuses it without its ring (it opens the menu, which Escape closes)
async function expectNoRingOnClick(page) {
  const button = page.getByTitle(SHOW_MENU);
  const box = await button.boundingBox();
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  assert.equal(await button.evaluate((element) => getComputedStyle(element).boxShadow), 'none',
    'a mouse click shows no focus ring');
  await page.keyboard.press('Escape');
}

export const CHECKS = {
  'app-main-topbar--keyboard': async (page) => {
    await expectRingsOnTab(page);
    await expectNoRingOnClick(page);
  },
};
