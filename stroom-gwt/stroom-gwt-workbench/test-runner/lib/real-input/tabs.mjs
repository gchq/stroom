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

// Real input checks for tab bars (see index.mjs): keyboard focus draws a tab's ring in the focus
// colour, and a mouse click doesn't.

import assert from 'node:assert/strict';

// The focus colour, as a computed colour
function focusColour(page) {
  return page.evaluate(() => {
    const probe = document.createElement('div');
    probe.style.color = 'var(--tab__focus-color)';
    document.body.append(probe);
    const colour = getComputedStyle(probe).color;
    probe.remove();
    return colour;
  });
}

// Clicks a tab (not the selected one, which a click leaves alone), which mustn't show its ring, then moves to the next with the keyboard, which must.
// ring(element) gives what draws a tab's ring (its colour, or 'none').
async function expectRingOnKeyboardOnly(page, tabs, ring) {
  const colour = await focusColour(page);
  const tab = page.getByRole('tab').nth(tabs.click);
  await tab.click();
  await page.mouse.move(0, 0);
  assert.ok(await tab.evaluate((element) => element === document.activeElement), 'a click focuses the tab');
  assert.ok(!(await tab.evaluate(ring)).includes(colour), 'a clicked tab doesn\'t show the focus ring');
  await page.keyboard.press('ArrowRight');
  const next = page.getByRole('tab').nth(tabs.next);
  assert.ok(await next.evaluate((element) => element === document.activeElement),
    'Right moves the focus to the next tab');
  const drawn = await next.evaluate(ring);
  assert.ok(drawn.includes(colour), `a tab focused from the keyboard shows the focus ring (${drawn}, not ${colour})`);
}

export const CHECKS = {
  // A document tab's ring is a box shadow inside it
  'widgets-tabs-curvetabbar--basic': (page) => expectRingOnKeyboardOnly(page, { click: 2, next: 3 },
    (element) => getComputedStyle(element).boxShadow),
  // A link tab's ring is drawn around its label (by ::after)
  'widgets-tabs-linktabswidget--basic': (page) => expectRingOnKeyboardOnly(page, { click: 0, next: 1 },
    (element) => {
      const after = getComputedStyle(element, '::after');
      return after.content === 'none' ? 'none' : after.borderTopColor;
    }),
};
