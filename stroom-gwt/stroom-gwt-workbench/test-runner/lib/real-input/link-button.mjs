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

// Real input checks for link buttons (see index.mjs, and Widgets/Inputs/ViewStates): a link button
// underlines under the real mouse, unless it is disabled. A play's hover can't show :hover, which
// only the real mouse gives. The time range selector is a link button; disabled and read only, it
// is aria-disabled (it once still underlined, as link-button.css's hover won over its own rule).

import assert from 'node:assert/strict';

// The selector's text decoration once the real mouse is over it
async function hoveredDecoration(page, selector) {
  const target = page.locator(selector).first();
  await target.hover();
  assert.ok(await target.evaluate((element) => element.matches(':hover')), `${selector} is hovered`);
  return target.evaluate((element) => getComputedStyle(element).textDecorationLine);
}

// Notes the scroll position of every scrolled element, so a hover (which scrolls its target into
// view) can be undone before the story's screenshot
async function scrollPositions(page) {
  return page.evaluate(() => Array.from(document.querySelectorAll('*'))
    .map((element, index) => [index, element.scrollTop, element.scrollLeft]));
}

async function restoreScrollPositions(page, positions) {
  await page.evaluate((saved) => {
    const elements = document.querySelectorAll('*');
    for (const [index, top, left] of saved) {
      if (elements[index]) {
        elements[index].scrollTop = top;
        elements[index].scrollLeft = left;
      }
    }
  }, positions);
}

export const CHECKS = {
  'widgets-inputs-viewstates--all-controls': async (page) => {
    const positions = await scrollPositions(page);
    assert.equal(await hoveredDecoration(page, '.time-range-selector:not([aria-disabled="true"])'),
      'underline', 'an enabled link button underlines on hover');
    assert.equal(await hoveredDecoration(page,
      '.time-range-selector[aria-disabled="true"]:not(.time-range-selector--read-only)'),
    'none', 'a disabled link button doesn\'t underline on hover');
    assert.equal(await hoveredDecoration(page, '.time-range-selector--read-only'),
      'none', 'a read-only link button doesn\'t underline on hover');
    await page.mouse.move(0, 0);
    await restoreScrollPositions(page, positions);
  },
};
