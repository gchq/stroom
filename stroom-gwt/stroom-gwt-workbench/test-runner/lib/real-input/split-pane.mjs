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

// Real input checks for split panes (see index.mjs): splitters dragged with the real mouse
// (including to a minimum, on a right-to-left page and across a frame), contrast in both themes,
// forced colours, the splitter's menu from the keyboard, and mouse versus keyboard focus.

import assert from 'node:assert/strict';

const SPLITTER = '.split-pane__splitter';

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

// The size of the pane holding a widget (by its test id), rather than the widget's, which can't be
// smaller than its padding
async function paneSize(page, testId, vertical) {
  const box = await page.getByTestId(testId).locator('xpath=..').boundingBox();
  return vertical ? box.height : box.width;
}

// Drags a story's first splitter by dx or dy. The pane holding the widget with the test id (by
// default the first, "pane-0") must then be the expected size: by default its size before plus the
// distance dragged, in the direction that grows it (grows, 1 or -1), to the pixel.
async function dragAndExpect(page, { testId = 'pane-0', dx = 0, dy = 0, grows = 1, expected, message = 'a drag' }) {
  const vertical = dy !== 0;
  const before = await paneSize(page, testId, vertical);
  await dragSplitter(page, page.locator(SPLITTER).first(), dx, dy);
  const after = await paneSize(page, testId, vertical);
  const wanted = expected ?? before + grows * (dx || dy);
  assert.ok(Math.abs(after - wanted) <= 1,
    `${message} by ${dx || dy}px: the pane went from ${before}px to ${after}px, not ${wanted}px`);
}

// The contrast of a story's first splitter's keyboard focus colour against what is either side of
// it (WCAG 1.4.11 asks for 3:1), as [before, after]. Its line at rest is drawn in the border colour,
// as other borders are, until the theme colours are revisited, so isn't checked here
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
    const focus = variable('--splitter__focus-color');
    return { focus: [ratio(focus, before), ratio(focus, after)] };
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
  'widgets-layout-splitpanecontent--regular-table-and-editor': async (page) => {
    const splitter = page.locator(SPLITTER).first();
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
  // Beside Stroom's table and code editor, a splitter's keyboard focus can be seen, in both themes (the light theme in a page of its own, leaving this one as it is)
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
    const colours = () => page.locator(SPLITTER).first().evaluate((element) => ({
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
    await dragAndExpect(page, { dx: -100, grows: -1, message: 'a drag left on a right-to-left page' });
    await dragAndExpect(page, { dx: 50, grows: -1, message: 'a drag right on a right-to-left page' });
  },
  // A drag across a frame keeps going: the frame doesn't take the pointer from the splitter
  'widgets-layout-splitpane--over-a-frame': async (page) => {
    await dragAndExpect(page, { dx: 400, message: 'a drag across the frame' });
    await dragAndExpect(page, { dx: -300, message: 'a drag back across the frame' });
  },
  // Dragging grows and shrinks the first pane, down to nothing (neither has a minimum)
  'widgets-layout-splitpane--drag-thin-horizontal': async (page) => {
    await dragAndExpect(page, { dx: 100 });
    await dragAndExpect(page, { dx: -150 });
    // Past the frame's edge (but not the page's): the pane closes
    await dragAndExpect(page, { dx: -270, expected: 0 });
    // and opens again
    await dragAndExpect(page, { dx: 300 });
  },
  'widgets-layout-splitpane--drag-horizontal': async (page) => {
    await dragAndExpect(page, { dx: 100, expected: 400 });
    await dragAndExpect(page, { dx: -150, expected: 250 });
  },
  'widgets-layout-splitpane--drag-thin-vertical': async (page) => {
    await dragAndExpect(page, { dy: 50, expected: 170 });
    await dragAndExpect(page, { dy: -100, expected: 70 });
  },
  'widgets-layout-splitpane--drag-vertical': async (page) => {
    await dragAndExpect(page, { dy: 50, expected: 170 });
  },
  // The second pane is the sized one: dragging left grows it
  'widgets-layout-splitpane--drag-thin-anchor-end': async (page) => {
    await dragAndExpect(page, { testId: 'pane-1', dx: -100, grows: -1 });
    await dragAndExpect(page, { testId: 'pane-1', dx: 50, grows: -1 });
  },
  // A drag stops at the pane's minimum
  'widgets-layout-splitpane--drag-thin-with-minimum': async (page) => {
    await dragAndExpect(page, { dx: -250, expected: 150 });
    await dragAndExpect(page, { dx: 100, expected: 250 });
  },
  // The first pane keeps its share as the page narrows, after a drag as before it
  'widgets-layout-splitpane--drag-thin-proportional': async (page) => {
    await dragAndExpect(page, { dx: 100 });
    const share = async () => (await paneSize(page, 'pane-0', false))
      / ((await page.getByTestId('frame').boundingBox()).width - 2);
    const before = await share();
    const viewport = page.viewportSize();
    await page.setViewportSize({ width: Math.round(viewport.width * 0.75), height: viewport.height });
    try {
      await page.waitForTimeout(200);
      const after = await share();
      assert.ok(Math.abs(after - before) <= 0.005,
        `after the page narrows the first pane keeps its share (${before} before, ${after} after)`);
    } finally {
      await page.setViewportSize(viewport);
    }
  },
};
