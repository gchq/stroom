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

// Real input checks: after some stories pass, the runner presses real keys and clicks with the
// real mouse (Playwright's page.keyboard and page.mouse) on them. A play can only dispatch events
// itself, which the browser doesn't trust: its Tab is simulated (as user-event's), and the browser
// can't tell its keys from its clicks, which matters to :focus-visible. These checks confirm the
// browser itself behaves as the plays expect.
//
// Each check is keyed by the id of the story it runs on, and throws (e.g. an assertion) if the page
// doesn't behave as it should.

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

export const REAL_INPUT_CHECKS = {
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
  },
};
