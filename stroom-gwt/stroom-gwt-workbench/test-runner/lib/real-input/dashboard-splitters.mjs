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

// Real input checks for a dashboard's splitters (see index.mjs, and SplitterOverlay): a splitter
// beside a visualisation is drawn and grabbed by its overlay, on the page's root above the
// visualisation's frame. The real mouse over the visualisation, just right of the line, lights the
// line, shows the resize cursor, and drags the splitter; the overlay follows it.

import assert from 'node:assert/strict';

const SPLITTER = '.flex-layout__splitter[aria-label="Resize T and Chart"]';

// Waits (up to two seconds) for a condition the page reaches a moment after a move or click
async function waitFor(page, condition, message) {
  const deadline = Date.now() + 2000;
  while (!(await condition()) && Date.now() < deadline) {
    await page.waitForTimeout(25);
  }
  assert.ok(await condition(), message);
}

// What is under a point: the overlay's grab area, its line, a frame, or what else
function under(page, x, y) {
  return page.evaluate(([px, py]) => {
    const hit = document.elementFromPoint(px, py);
    if (!hit) {
      return 'nothing';
    }
    if (hit.matches('.flex-layout__overlay-grab')) {
      return `grab ${getComputedStyle(hit).cursor}`;
    }
    return hit.tagName === 'IFRAME' ? 'frame' : hit.className;
  }, [x, y]);
}

// The opacity of the hovered line (the overlay line's ::after) of the overlay under a point
function lineLit(page, x, y) {
  return page.evaluate(([px, py]) => {
    const overlay = document.elementFromPoint(px, py)?.closest('.flex-layout__overlay');
    const line = overlay?.querySelector('.flex-layout__overlay-line');
    return line ? getComputedStyle(line, '::after').opacity : 'no line';
  }, [x, y]);
}

export const CHECKS = {
  // The play ends with the splitter focused, the visualisation (a white box) right of it
  'app-dashboard-vistoolbar--splitter-beside-visualisation': async (page) => {
    const splitter = page.locator(SPLITTER);
    const box = await splitter.boundingBox();
    const x = box.x + box.width / 2 + 3;
    const y = box.y + box.height / 2;

    assert.equal(await under(page, x, y), 'grab col-resize',
      'just right of the line, over the visualisation, the overlay\'s grab area is on top, with the resize cursor');
    assert.equal(await under(page, x + 20, y), 'frame', 'further in, the visualisation is on top');

    await page.mouse.move(x, y);
    await waitFor(page, async () => (await lineLit(page, x, y)) === '1',
      `the line lights as the mouse is over the overlay (opacity ${await lineLit(page, x, y)})`);

    await page.mouse.down();
    await page.mouse.move(x + 40, y, { steps: 10 });
    await page.mouse.up();
    await waitFor(page, async () => Math.abs((await splitter.boundingBox()).x - (box.x + 40)) <= 1,
      `the drag moves the splitter 40px right (from ${box.x} to ${(await splitter.boundingBox()).x})`);
    assert.equal(await under(page, x + 40, y), 'grab col-resize', 'the overlay follows the splitter');

    await page.mouse.move(x + 200, y);
    await waitFor(page, async () => (await lineLit(page, x + 40, y)) === '0',
      'the line goes out as the mouse leaves it');
  },
};
