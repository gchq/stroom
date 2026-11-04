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
// colour, and a mouse click doesn't; tabbing back into a bar lands on its selected tab; and the
// overflow selector is filled on hover and ringed on keyboard focus as a tab is.

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

// What draws a tab's ring: its ::after, on its edge (tab-bar.css), a document tab's as a link tab's
function ringColour(element) {
  const after = getComputedStyle(element, '::after');
  return after.content === 'none' ? 'none' : after.borderTopColor;
}

// The whole of the focused element's ring (its ::after): its line, the band outside it and its corners
function focusedRing(page) {
  return page.evaluate(() => {
    const after = getComputedStyle(document.activeElement, '::after');
    return {
      content: after.content,
      line: `${after.borderTopWidth} ${after.borderTopStyle} ${after.borderTopColor}`,
      band: after.boxShadow,
      corners: after.borderTopLeftRadius,
    };
  });
}

// A colour variable, as a computed background colour
function backgroundOf(page, variable) {
  return page.evaluate((name) => {
    const probe = document.createElement('div');
    probe.style.backgroundColor = `var(${name})`;
    document.body.append(probe);
    const colour = getComputedStyle(probe).backgroundColor;
    probe.remove();
    return colour;
  }, variable);
}

// The overflow selector looks like a tab where real input shows it (the rest is the story's play,
// TabWidgets.expectSelectorLikeTabs): a hover fills it as a hovered document tab is filled, and
// keyboard focus draws a tab's ring on it. The focus goes to it as a user's would: from the tab
// stop, Right then Home to the first tab shown (whose ring is the one to match), then Left.
async function expectSelectorLooksLikeTab(page, bar) {
  const selector = page.locator(`${bar} .tab-selector`);
  await page.mouse.move(0, 0);
  const rest = await selector.evaluate((element) => getComputedStyle(element).backgroundColor);
  await selector.hover();
  const hovered = await selector.evaluate((element) => getComputedStyle(element).backgroundColor);
  assert.notEqual(hovered, rest, 'hovering the selector fills it');
  assert.equal(hovered, await backgroundOf(page, '--curve-tab__background-color--hover'),
    'the selector is filled as a hovered document tab is');
  await page.mouse.move(0, 0);

  await page.locator(`${bar} [role="tab"][tabindex="0"]`).focus();
  await page.keyboard.press('ArrowRight');
  await page.keyboard.press('Home');
  const tabRing = await focusedRing(page);
  assert.notEqual(tabRing.content, 'none', 'a tab focused from the keyboard shows its ring');
  await page.keyboard.press('ArrowLeft');
  assert.ok(await selector.evaluate((element) => element === document.activeElement),
    'Left from the first tab shown focuses the selector');
  assert.deepEqual(await focusedRing(page), tabRing, 'the selector\'s focus ring is a tab\'s');
}

export const CHECKS = {
  'widgets-tabs-curvetabbar--basic': async (page) => {
    await expectRingOnKeyboardOnly(page, { click: 2, next: 3 }, ringColour);
    await expectTabStopReturnsToSelected(page);
  },
  'widgets-tabs-linktabpanel--basic': async (page) => {
    await expectRingOnKeyboardOnly(page, { click: 0, next: 1 }, ringColour);
    await expectTabStopReturnsToSelected(page);
  },
  'widgets-tabs-curvetabbar--shrink-then-overflow': async (page) => {
    await expectSelectorLooksLikeTab(page, '.tab-bar--curved');
  },
  'widgets-tabs-linktabpanel--overflow': async (page) => {
    await expectSelectorLooksLikeTab(page, '.tab-bar--link');
  },
};
