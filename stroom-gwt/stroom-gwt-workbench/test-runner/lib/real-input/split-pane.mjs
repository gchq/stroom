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

// Real input checks for split panes (see index.mjs): old and new splitters dragged alike with the
// real mouse, dragging on a right-to-left page and across a frame, contrast in both themes,
// forced colours, the splitter's menu from the keyboard, and mouse versus keyboard focus.

import assert from 'node:assert/strict';

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

// The contrast of a story's first splitter against what is either side of it (WCAG 1.4.11 asks
// for 3:1): its line at rest and its keyboard focus colour, each as [before, after]
async function splitterContrast(page) {
  await page.evaluate(() => document.activeElement?.blur());
  await page.mouse.move(0, 0);
  return page.evaluate(() => {
    const rgb = (colour) => colour.match(/[\d.]+/g).slice(0, 4).map(Number);
    const luminance = ([r, g, b]) => {
      const linear = (v) => (v / 255 <= 0.03928 ? v / 255 / 12.92 : ((v / 255 + 0.055) / 1.055) ** 2.4);
      return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
    };
    const ratio = (a, b) => {
      const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
      return Math.round(((light + 0.05) / (dark + 0.05)) * 100) / 100;
    };
    // The colour shown at a point: the first background that isn't transparent
    const backgroundAt = (x, y) => {
      for (let element = document.elementFromPoint(x, y); element; element = element.parentElement) {
        const colour = rgb(getComputedStyle(element).backgroundColor);
        if (colour.length < 4 || colour[3] > 0) {
          return colour;
        }
      }
      return [255, 255, 255];
    };
    const variable = (name) => {
      const probe = document.createElement('div');
      probe.style.backgroundColor = `var(${name})`;
      document.body.append(probe);
      const colour = rgb(getComputedStyle(probe).backgroundColor);
      probe.remove();
      return colour;
    };
    const splitter = document.querySelector('.split-pane__splitter');
    const box = splitter.getBoundingClientRect();
    // Part way down it, either side of it
    const y = box.top + box.height * 0.7;
    const before = backgroundAt(box.left - 4, y);
    const after = backgroundAt(box.right + 4, y);
    const line = rgb(getComputedStyle(splitter, '::before').backgroundColor);
    const focus = variable('--splitter__focus-color');
    return { line: [ratio(line, before), ratio(line, after)], focus: [ratio(focus, before), ratio(focus, after)] };
  });
}

function expectContrast(measured, theme) {
  for (const [what, ratios] of Object.entries(measured)) {
    assert.ok(Math.min(...ratios) >= 3,
      `in the ${theme} theme the splitter's ${what} is at least 3:1 against the panes either side `
      + `(it is ${ratios.join(':1 and ')}:1)`);
  }
}

export const CHECKS = {
  // A mouse press gives the splitter the focus as the pointer's, not the keyboard's: it shows the
  // drag highlight while held, not the keyboard focus bar, before and after (once a regression:
  // focusing it from script made the press show as keyboard focus)
  'widgets-layout-splitpanecontent--compare-thin-with-content': async (page) => {
    const splitter = page.getByTestId('new').locator(NEW_SPLITTER).first();
    const box = await splitter.boundingBox();
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await page.mouse.down();
    const held = await splitter.evaluate((element) => ({
      focused: element === document.activeElement,
      focusVisible: element.matches(':focus-visible'),
      colour: getComputedStyle(element, '::before').backgroundColor,
      highlight: (() => {
        const probe = document.createElement('div');
        probe.style.backgroundColor = 'var(--splitter__background-color--highlight)';
        document.body.append(probe);
        const colour = getComputedStyle(probe).backgroundColor;
        probe.remove();
        return colour;
      })(),
    }));
    await page.mouse.up();
    assert.ok(held.focused, 'a press gives the splitter the focus');
    assert.ok(!held.focusVisible, 'a press doesn\'t show as keyboard focus');
    assert.equal(held.colour, held.highlight, 'the held splitter shows the drag highlight');
    assert.ok(!(await splitter.evaluate((element) => element.matches(':focus-visible'))),
      'after the press it still doesn\'t show as keyboard focus');
  },
  // From the keyboard: the context menu key and Shift+F10 open a splitter's menu, Escape closes it
  // and gives the focus back to the splitter, and Alt+M restores the maximised pane (the play leaves
  // the first pane maximised)
  'widgets-layout-splitpanecontent--splitter-menu': async (page) => {
    const splitter = page.getByRole('separator', { name: 'Properties' });
    const menu = page.locator('.menuItem-text', { hasText: 'Restore' });
    await splitter.focus();
    for (const key of ['ContextMenu', 'Shift+F10']) {
      await page.keyboard.press(key);
      await menu.waitFor({ state: 'visible', timeout: 2000 });
      await page.keyboard.press('Escape');
      await menu.waitFor({ state: 'hidden', timeout: 2000 });
      assert.ok(await splitter.evaluate((element) => element === document.activeElement),
        `after ${key} and Escape the focus is back on the splitter`);
    }
    const content = page.getByTestId('content').locator('xpath=..');
    assert.equal((await content.boundingBox()).width, 0, 'the first pane is maximised');
    await page.keyboard.press('Alt+m');
    assert.ok((await content.boundingBox()).width > 0, 'Alt+M restores the panes');
  },
  // Beside Stroom's table and code editor, a splitter's line and its keyboard focus can be seen,
  // in both themes (the light theme in a page of its own, leaving this one as it is)
  'widgets-layout-splitpanecontent--table-and-editor': async (page) => {
    expectContrast(await splitterContrast(page), 'dark');
    const light = await page.context().newPage();
    try {
      await light.setViewportSize(page.viewportSize());
      await light.goto(`${page.url()}&globals=theme:light`);
      await light.locator('.ace_content').waitFor();
      await light.getByText('Person 1', { exact: true }).waitFor();
      expectContrast(await splitterContrast(light), 'light');
    } finally {
      await light.close();
    }
  },
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
};
