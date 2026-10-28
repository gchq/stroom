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
// colour, and a mouse click doesn't; and tabbing back into a bar lands on its selected tab.

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

// With focus on a tab the arrow keys moved to (not the selected one), Tab leaves the bar and
// Shift+Tab comes back to the selected tab: the tab stop returns to it when focus leaves the bar, as
// the WAI-ARIA tabs pattern has it
async function expectTabStopReturnsToSelected(page) {
  const selected = () => page.evaluate(() => document.activeElement.getAttribute('aria-selected'));
  assert.equal(await selected(), 'false', 'the arrow keys left focus on a tab that isn\'t selected');
  await page.keyboard.press('Tab');
  assert.ok(await page.evaluate(() => !document.activeElement.closest('.tab-bar')), 'Tab leaves the tab bar');
  await page.keyboard.press('Shift+Tab');
  assert.equal(await selected(), 'true', 'tabbing back into the bar lands on the selected tab');
}

export const CHECKS = {
  // A document tab's ring is a box shadow inside it
  'widgets-tabs-curvetabbar--basic': async (page) => {
    await expectRingOnKeyboardOnly(page, { click: 2, next: 3 }, (element) => getComputedStyle(element).boxShadow);
    await expectTabStopReturnsToSelected(page);
  },
  // A link tab's ring is drawn around its label (by ::after)
  'widgets-tabs-linktabpanel--basic': async (page) => {
    await expectRingOnKeyboardOnly(page, { click: 0, next: 1 }, (element) => {
      const after = getComputedStyle(element, '::after');
      return after.content === 'none' ? 'none' : after.borderTopColor;
    });
    await expectTabStopReturnsToSelected(page);
  },
};
