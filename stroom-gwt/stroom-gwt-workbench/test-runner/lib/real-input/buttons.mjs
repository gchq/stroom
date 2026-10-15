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

// Real input checks for buttons and toolbars (see index.mjs): floating action buttons, toolbar
// button states (hovered and focused, for their screenshots) and a toolbar crossed with real keys.

import assert from 'node:assert/strict';

// The name of the element with the focus (its title, else aria-label, else text), or null if none
async function focused(page) {
  return page.evaluate(() => {
    const element = document.activeElement;
    if (!element || element === document.body) {
      return null;
    }
    return element.getAttribute('title') || element.getAttribute('aria-label') || element.textContent.trim();
  });
}

// The focused element's drawn focus ring (its box shadow), or 'none'
function ring(page) {
  return page.evaluate(() => getComputedStyle(document.activeElement).boxShadow);
}

// The text of the focus tooltip (FocusTooltip) if it is showing, otherwise null
async function tooltip(page) {
  return page.evaluate(() => {
    const element = document.querySelector('.focus-tooltip');
    return element && getComputedStyle(element).display !== 'none' ? element.textContent : null;
  });
}

async function press(page, key, expected, message) {
  await page.keyboard.press(key);
  assert.equal(await focused(page), expected, `${message} (after ${key})`);
}

// Hovers the mouse over the story's button (data-testid="target"), so its screenshot shows :hover
async function hoverTarget(page) {
  const target = page.getByTestId('target');
  await target.hover();
  assert.ok(await target.evaluate((element) => element.matches(':hover')), 'the button is hovered');
}

// Tabs to the story's button (data-testid="target"), so its screenshot shows :focus-visible
async function focusTarget(page) {
  await page.evaluate(() => document.activeElement?.blur());
  await page.keyboard.press('Tab');
  const target = page.getByTestId('target');
  assert.ok(await target.evaluate((element) => element.matches(':focus-visible')),
    'the button has the keyboard focus');
}

export const CHECKS = {
  // Two floating action buttons, enabled and disabled (Widgets/Buttons/FabButton): one tab stop each
  'widgets-buttons-fabbutton--states': async (page) => {
    await page.evaluate(() => document.activeElement?.blur());
    await press(page, 'Tab', 'Enter Stepping Mode', 'Tab moves to the first button');
    assert.equal(await page.evaluate(() => document.activeElement.localName), 'button',
      'the focus is on the button itself, not the element around it');
    await press(page, 'Tab', 'Select a row to step through its source',
      'Tab moves to the next button, as each is one tab stop');
    await page.keyboard.press('Tab');
    assert.equal(await focused(page), null, 'Tab leaves the second button');
  },
  // Toolbar buttons hovered or focused, for screenshots (Widgets/Buttons/ToolbarButtonStates)
  'widgets-buttons-toolbarbuttonstates--hovered': hoverTarget,
  'widgets-buttons-toolbarbuttonstates--hovered-on': hoverTarget,
  'widgets-buttons-toolbarbuttonstates--hovered-disabled': hoverTarget,
  'widgets-buttons-toolbarbuttonstates--focus-visible': focusTarget,
  'widgets-buttons-toolbarbuttonstates--focus-visible-on': focusTarget,

  // A toolbar of Save, Save As, a separator and a Show Filters toggle (Widgets/Buttons/Toolbar)
  'widgets-buttons-toolbar--real-input': async (page) => {
    await page.evaluate(() => document.activeElement?.blur());
    await press(page, 'Tab', 'Save', 'Tab moves into the toolbar, to its first button');
    assert.equal(await tooltip(page), 'Save', 'the focused button\'s name is shown on keyboard focus');
    await press(page, 'ArrowRight', 'Save As', 'Right moves to the next button');
    assert.equal(await tooltip(page), 'Save As', 'the tooltip follows the focus');
    await press(page, 'ArrowRight', 'Show Filters', 'Right crosses the separator');
    await press(page, 'ArrowRight', 'Save', 'Right wraps from the last button to the first');
    await press(page, 'ArrowLeft', 'Show Filters', 'Left wraps from the first button to the last');
    await press(page, 'Home', 'Save', 'Home moves to the first button');
    await press(page, 'End', 'Show Filters', 'End moves to the last button');
    await press(page, 'ArrowLeft', 'Save As', 'Left moves to the previous button');
    await page.keyboard.press('Escape');
    assert.equal(await tooltip(page), null, 'Escape hides the tooltip');
    assert.equal(await focused(page), 'Save As', 'Escape leaves the focus where it is');
    await page.keyboard.press('Tab');
    assert.ok(!['Save', 'Save As', 'Show Filters'].includes(await focused(page)),
      'Tab leaves the toolbar, as it is one tab stop');
    assert.equal(await tooltip(page), null, 'the tooltip goes when the focus leaves');
    await press(page, 'Shift+Tab', 'Save As', 'Shift+Tab returns to the button last used');
    // A real click focuses the button but shows no tooltip: the mouse user has the title's own
    const box = await page.getByRole('button', { name: 'Save', exact: true }).boundingBox();
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    assert.equal(await focused(page), 'Save', 'a click focuses the button');
    assert.equal(await tooltip(page), null, 'a mouse click shows no focus tooltip');
    assert.equal(await ring(page), 'none', 'a mouse click shows no focus ring');
    await press(page, 'ArrowRight', 'Save As', 'the arrow keys still move along the toolbar after a click');
    assert.notEqual(await ring(page), 'none', 'the keyboard brings the focus ring back');
  },
  // A click on a toolbar button in a page where the keyboard hasn't been used shows no focus ring
  'widgets-buttons-toolbar--default': async (page) => {
    const button = page.locator('.toolbar-button').first();
    const box = await button.boundingBox();
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    assert.ok(await button.evaluate((element) => element === document.activeElement), 'a click focuses the button');
    assert.equal(await ring(page), 'none', 'a mouse click shows no focus ring');
  },
};
