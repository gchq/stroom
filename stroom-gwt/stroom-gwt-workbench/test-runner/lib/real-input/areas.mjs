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

// Real input checks for moving between the app's main areas (see index.mjs, and App/Main/AppShell
// MovesBetweenAreas, #5403): F6 moves the focus from the open content to the main toolbar, then the
// explorer, then back to the content; Shift+F6 goes the other way. A play's F6 can't do it (its key
// event has no key code for the key bindings to read).

import assert from 'node:assert/strict';

// Which main area has the focus: 'toolbar', 'explorer' (its tree), 'content' (its open
// document), or null
function area(page) {
  return page.evaluate(() => {
    const active = document.activeElement;
    if (!active || active === document.body) {
      return null;
    }
    if (active.closest('.main-toolbar')) {
      return 'toolbar';
    }
    // The explorer at its tree, and the content in its open document (not the tabs above it)
    if (active.closest('[role="navigation"] [role="tree"]')) {
      return 'explorer';
    }
    if (active.closest('[role="main"] .curve-tab-layout__layer-container-outer')) {
      return 'content';
    }
    return null;
  });
}

async function press(page, key) {
  await page.keyboard.press(key);
  // Some areas take the focus a moment later (e.g. the content's open tab)
  await page.waitForTimeout(150);
}

export const CHECKS = {
  'app-main-appshell--moves-between-areas': async (page) => {
    assert.equal(await area(page), 'content', 'the opened document has the focus');
    await press(page, 'F6');
    assert.equal(await area(page), 'toolbar', 'F6 moves from the content round to the main toolbar');
    await press(page, 'F6');
    assert.equal(await area(page), 'explorer', 'F6 moves on to the explorer');
    await press(page, 'F6');
    assert.equal(await area(page), 'content', 'F6 moves on to the content');
    await press(page, 'Shift+F6');
    assert.equal(await area(page), 'explorer', 'Shift+F6 moves back to the explorer');
  },
};
