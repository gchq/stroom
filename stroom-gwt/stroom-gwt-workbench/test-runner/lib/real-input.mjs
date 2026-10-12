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

// Split pane comparisons (Widgets/Layout/SplitPane Compare*): an old split panel (test id "old")
// above a split pane (test id "new"), each with its sized pane (test ids "old-sized", "new-sized")
const OLD_SPLITTER = '.thinSplitLayoutPanel-Dragger, .gwt-SplitLayoutPanel-Dragger';
const NEW_SPLITTER = '.split-pane__splitter';
// How far the old panel's pane may move differently from the split pane's in a drag: the old one
// doesn't follow the mouse exactly (it measures a drag from its 7px dragger's edge, not where it was
// grabbed, and takes 2px off a proportional pane each time it lays out); the split pane must follow
// it to the pixel
const OLD_DRAG_TOLERANCE_PX = 6;

// Drags a splitter with the real mouse, from its middle, by dx and dy, in steps as a hand would
async function dragSplitter(page, splitter, dx, dy) {
  const box = await splitter.boundingBox();
  const x = box.x + box.width / 2;
  const y = box.y + box.height / 2;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + dx, y + dy, { steps: 10 });
  await page.mouse.up();
}

// The size of the pane holding a sized widget (its layer in an old panel, its pane in a split pane),
// rather than the widget's, which can't be smaller than its padding
async function sizedPaneSize(page, testId, vertical) {
  const box = await page.getByTestId(testId).locator('xpath=..').boundingBox();
  return vertical ? box.height : box.width;
}

// Drags the old and new splitters alike. The split pane's sized pane must then be the expected
// size (by default, its size before plus the distance dragged, in the direction that grows it),
// and the old panel's pane must have moved as far, give or take OLD_DRAG_TOLERANCE_PX.
async function dragBothAndCompare(page, { dx = 0, dy = 0, grows = 1, expected }) {
  const vertical = dy !== 0;
  const oldBefore = await sizedPaneSize(page, 'old-sized', vertical);
  const newBefore = await sizedPaneSize(page, 'new-sized', vertical);
  await dragSplitter(page, page.getByTestId('old').locator(OLD_SPLITTER).first(), dx, dy);
  await dragSplitter(page, page.getByTestId('new').locator(NEW_SPLITTER).first(), dx, dy);
  // The old panel lays itself out after a drag (deferred)
  await page.waitForTimeout(100);
  const oldSize = await sizedPaneSize(page, 'old-sized', vertical);
  const newSize = await sizedPaneSize(page, 'new-sized', vertical);
  const wanted = expected ?? newBefore + grows * (dx || dy);
  assert.ok(Math.abs(newSize - wanted) <= 1,
    `after a drag by ${dx || dy}px the split pane's pane is ${wanted}px (it is ${newSize}px)`);
  assert.ok(Math.abs((oldSize - oldBefore) - (newSize - newBefore)) <= OLD_DRAG_TOLERANCE_PX,
    `a drag by ${dx || dy}px moves the old panel's pane as far as the split pane's `
    + `(old ${oldBefore}px to ${oldSize}px, new ${newBefore}px to ${newSize}px)`);
}

// Drags a split pane's splitter by dx and checks its first pane (test id "pane-0") grew by grows
async function dragAndExpectGrowth(page, dx, grows, message) {
  const before = await sizedPaneSize(page, 'pane-0', false);
  await dragSplitter(page, page.locator(NEW_SPLITTER).first(), dx, 0);
  const after = await sizedPaneSize(page, 'pane-0', false);
  assert.ok(Math.abs(after - (before + grows)) <= 1,
    `${message}: the first pane went from ${before}px to ${after}px, not ${before + grows}px`);
}

export const REAL_INPUT_CHECKS = {
  // In forced colours (e.g. Windows high contrast) a thin splitter is still drawn, in the text
  // colour, where its background would otherwise be replaced
  'widgets-layout-splitpane--keyboard': async (page) => {
    // The splitter's colour and the page's, which forced colours replaces a background with
    const colours = () => page.locator(NEW_SPLITTER).first().evaluate((element) => ({
      splitter: getComputedStyle(element, '::before').backgroundColor,
      page: getComputedStyle(document.body).backgroundColor,
    }));
    const normal = await colours();
    assert.notEqual(normal.splitter, normal.page, 'the thin splitter is drawn');
    await page.emulateMedia({ forcedColors: 'active' });
    try {
      const forced = await colours();
      assert.notEqual(forced.splitter, forced.page,
        `the thin splitter is drawn in forced colours (it is ${forced.splitter}, the page ${forced.page})`);
    } finally {
      await page.emulateMedia({ forcedColors: 'none' });
    }
  },
  // The first pane is on the right: dragging the splitter left grows it
  'widgets-layout-splitpane--right-to-left': async (page) => {
    await dragAndExpectGrowth(page, -100, 100, 'a drag left on a right-to-left page');
    await dragAndExpectGrowth(page, 50, -50, 'a drag right on a right-to-left page');
  },
  // A drag across a frame keeps going: the frame doesn't take the pointer from the splitter
  'widgets-layout-splitpane--over-a-frame': async (page) => {
    await dragAndExpectGrowth(page, 400, 400, 'a drag across the frame');
    await dragAndExpectGrowth(page, -300, -300, 'a drag back across the frame');
  },
  // Dragging grows and shrinks the first pane alike, down to nothing (neither has a minimum)
  'widgets-layout-splitpane--compare-thin-horizontal': async (page) => {
    await dragBothAndCompare(page, { dx: 100 });
    await dragBothAndCompare(page, { dx: -150 });
    // Past the frame's edge (but not the page's): the pane closes
    await dragBothAndCompare(page, { dx: -270, expected: 0 });
    // and opens again
    await dragBothAndCompare(page, { dx: 300 });
  },
  'widgets-layout-splitpane--compare-horizontal': async (page) => {
    await dragBothAndCompare(page, { dx: 100, expected: 400 });
    await dragBothAndCompare(page, { dx: -150, expected: 250 });
  },
  'widgets-layout-splitpane--compare-thin-vertical': async (page) => {
    await dragBothAndCompare(page, { dy: 50, expected: 170 });
    await dragBothAndCompare(page, { dy: -100, expected: 70 });
  },
  'widgets-layout-splitpane--compare-vertical': async (page) => {
    await dragBothAndCompare(page, { dy: 50, expected: 170 });
  },
  // The second pane is the sized one: dragging left grows it
  'widgets-layout-splitpane--compare-thin-anchor-end': async (page) => {
    await dragBothAndCompare(page, { dx: -100, grows: -1 });
    await dragBothAndCompare(page, { dx: 50, grows: -1 });
  },
  // A drag stops at the pane's minimum
  'widgets-layout-splitpane--compare-thin-with-minimum': async (page) => {
    await dragBothAndCompare(page, { dx: -250, expected: 150 });
    await dragBothAndCompare(page, { dx: 100, expected: 250 });
  },
  // The first pane keeps its share as the page narrows, after a drag as before it
  'widgets-layout-splitpane--compare-thin-proportional': async (page) => {
    await dragBothAndCompare(page, { dx: 100 });
    const viewport = page.viewportSize();
    await page.setViewportSize({ width: Math.round(viewport.width * 0.75), height: viewport.height });
    await page.waitForTimeout(200);
    const oldSize = await sizedPaneSize(page, 'old-sized', false);
    const newSize = await sizedPaneSize(page, 'new-sized', false);
    await page.setViewportSize(viewport);
    assert.ok(Math.abs(oldSize - newSize) <= OLD_DRAG_TOLERANCE_PX,
      `after the page narrows the panes match (old ${oldSize}px, new ${newSize}px)`);
  },

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
