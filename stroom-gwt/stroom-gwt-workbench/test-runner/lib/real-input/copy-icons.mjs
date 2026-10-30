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

// Real input checks for the copy icons in grid cells (see index.mjs, and Widgets/Cell Renderers/
// FeedRefCell): an icon copies on the press of the mouse button, so the press mustn't start a text
// selection, which the slightest drag stretched across the page (#5111), and the copy leaves the
// focus where it was rather than on the page. Only the browser's own press starts a selection.
// Chromium didn't show the selection even before, so the check also asks that the press is
// cancelled, which is what stops any browser starting one.

import assert from 'node:assert/strict';

// The selected text, what has the focus ('row' for a grid row, else the focused element's tag) and
// whether the last mouse press was cancelled
function state(page) {
  return page.evaluate(() => {
    const element = document.activeElement;
    let focus = 'nothing';
    if (element && element !== document.body) {
      focus = element.getAttribute('role') === 'row' ? 'row' : element.tagName.toLowerCase();
    }
    const pressCancelled = window.__lastMouseDown ? window.__lastMouseDown.defaultPrevented : null;
    return { selection: window.getSelection().toString(), focus, pressCancelled };
  });
}

export const CHECKS = {
  'widgets-cell-renderers-feedrefcell--context-menu': async (page) => {
    // The row has the focus
    await page.getByText('TEST_FEED', { exact: true }).click();
    assert.equal((await state(page)).focus, 'row', 'a click on the row focuses it');

    // Keep the last mouse press, to see once it has gone everywhere whether it was cancelled
    await page.evaluate(() => window.addEventListener('mousedown', (event) => {
      window.__lastMouseDown = event;
    }));

    // Press on the copy icon (shown on hover), drag across the page and let go
    const copy = page.locator('.doc-ref-link__copy').first();
    await copy.hover();
    const box = await copy.boundingBox();
    await page.mouse.move(box.x + (box.width / 2), box.y + (box.height / 2));
    await page.mouse.down();
    await page.mouse.move(5, 5, { steps: 5 });
    await page.mouse.move(box.x + 200, box.y + 150, { steps: 5 });
    await page.mouse.up();

    const after = await state(page);
    assert.equal(after.pressCancelled, true, 'the press on the copy icon is cancelled');
    assert.equal(after.selection, '', 'a press on the copy icon starts no text selection');
    assert.equal(after.focus, 'row', 'the copy leaves the focus on the row');
  },
};
