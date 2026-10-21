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

// Real input checks for the data grid's keyboard (see index.mjs, and DataGridKeyboard): real Tab into
// and out of the grid (one tab stop), the keys it acts on cancelled as they are pressed (so Space
// and the arrows never scroll), the focus ring shown for the keyboard and not for the mouse, and
// real Tab from one control to the next while editing.

import assert from 'node:assert/strict';

const GRID = '[data-testid="grid"]';

// Waits (up to two seconds) for a condition the page reaches a moment after a key or click
async function waitFor(page, condition, message) {
  const deadline = Date.now() + 2000;
  while (!(await condition()) && Date.now() < deadline) {
    await page.waitForTimeout(25);
  }
  assert.ok(await condition(), message);
}

// Describes the element with the focus: 'row Alpha', 'cell Alpha 4', 'checkbox Bravo',
// 'combobox Alpha', 'input Alpha 4', 'button after', or what else it is
function focused(page) {
  return page.evaluate((grid) => {
    const element = document.activeElement;
    if (!element) {
      return 'nothing';
    }
    const row = element.closest(`${grid} [role="row"][data-row]`);
    if (!row) {
      return `${element.tagName.toLowerCase()} ${element.dataset.testid || element.textContent.trim()}`;
    }
    const name = row.querySelector('[data-col]').textContent.trim();
    if (element === row) {
      return `row ${name}`;
    }
    const cell = element.closest('[data-col]');
    const column = Number(cell.getAttribute('data-col')) + 1;
    if (element === cell) {
      return `cell ${name} ${column}`;
    }
    const role = element.getAttribute('role');
    return role ? `${role} ${name}` : `${element.tagName.toLowerCase()} ${name} ${column}`;
  }, GRID);
}

async function expectFocus(page, expected, message) {
  await waitFor(page, async () => (await focused(page)) === expected,
      `${message}: expected the focus on ${expected}, found ${await focused(page)}`);
}

function focusVisible(page) {
  return page.evaluate(() => Boolean(document.activeElement && document.activeElement.matches(':focus-visible')));
}

// Records whether each key down was cancelled by the time it reached the window
function watchKeys(page) {
  return page.evaluate(() => {
    window.__gridKeysCancelled = [];
    window.addEventListener('keydown', (event) => {
      window.__gridKeysCancelled.push(`${event.key}=${event.defaultPrevented}`);
    });
  });
}

function keysCancelled(page) {
  return page.evaluate(() => window.__gridKeysCancelled.splice(0));
}

// Pauses as a person's hand would: a change of selection draws rows again, and the focus comes back
// to them a moment later
function pace(page) {
  return page.waitForTimeout(100);
}

export const CHECKS = {
  // The play ends on Alpha's row. Tab from the button before the grid reaches the row (the grid's one
  // tab stop), the arrows and Space are cancelled as they are pressed, and Tab leaves the grid
  'widgets-data-grid-datagridkeyboard--rows': async (page) => {
    await watchKeys(page);
    await page.getByRole('button', { name: 'Before' }).click();
    await pace(page);
    await page.keyboard.press('Tab');
    await expectFocus(page, 'row Alpha', 'Tab from the button before the grid reaches its tab stop');
    assert.ok(await focusVisible(page), 'the row reached with Tab shows the focus ring');

    await pace(page);
    await page.keyboard.press('ArrowDown');
    await expectFocus(page, 'row Bravo', 'Down moves to the next row');
    await pace(page);
    await page.keyboard.press(' ');
    await pace(page);
    await page.keyboard.press('ArrowRight');
    await expectFocus(page, 'cell Bravo 1', 'Right moves into the first cell');
    await pace(page);
    await page.keyboard.press('ArrowLeft');
    await expectFocus(page, 'row Bravo', 'Left from the first cell goes back to the row');
    assert.deepEqual(await keysCancelled(page),
        ['Tab=false', 'ArrowDown=true', ' =true', 'ArrowRight=true', 'ArrowLeft=true'],
        'the grid cancels the keys it acts on as they are pressed, so the page never scrolls');

    await pace(page);
    await page.keyboard.press('Tab');
    await expectFocus(page, 'button after', 'Tab leaves the grid');
    await pace(page);
    await page.keyboard.press('Shift+Tab');
    await expectFocus(page, 'row Bravo', 'Shift+Tab comes back to the row with the focus');

    // A click chooses a row and gives it the focus, without the focus ring
    await page.locator(`${GRID} [role="row"][data-row]`, { hasText: 'Delta' }).locator('[data-col]').first().click();
    await expectFocus(page, 'row Delta', 'a click gives the row the focus');
    assert.ok(!(await focusVisible(page)), 'a row clicked shows no focus ring');
  },

  // The play ends on Charlie's Path cell. Enter edits it; real Tab goes on to the next control (the
  // selection box), then the next row's tick box, and Shift+Tab back; Escape ends the editing, after
  // which Tab leaves the grid
  'widgets-data-grid-datagridkeyboard--editing': async (page) => {
    await expectFocus(page, 'cell Charlie 4', 'the play ends on the Path cell');
    await pace(page);
    await page.keyboard.press('Enter');
    await expectFocus(page, 'input Charlie 4', 'Enter edits the text field');
    await pace(page);
    await page.keyboard.press('Tab');
    await expectFocus(page, 'combobox Charlie', 'Tab while editing goes on to the selection box');
    await pace(page);
    await page.keyboard.press('Tab');
    await expectFocus(page, 'checkbox Delta', 'Tab goes on to the next row\'s first control');
    await pace(page);
    await page.keyboard.press('Shift+Tab');
    await expectFocus(page, 'combobox Charlie', 'Shift+Tab goes back');
    await pace(page);
    await page.keyboard.press('Escape');
    await expectFocus(page, 'combobox Charlie', 'Escape ends the editing, staying on the selection box (its cell\'s focus)');
    await pace(page);
    await page.keyboard.press('Tab');
    await expectFocus(page, 'button after', 'Tab, no longer editing, leaves the grid');
    await pace(page);
    await page.keyboard.press('Shift+Tab');
    await expectFocus(page, 'combobox Charlie', 'Shift+Tab comes back to the selection box');
  },

  // A density change made by the page alone, with no key or click after it (as a preference change
  // arriving): the virtual rows' fixed height follows their cells' content, at both densities. The
  // play can't show this, as its own steps run the grid's pending work.
  'widgets-data-grid-behaviour--rows-follow-density-virtual': async (page) => {
    const rowFitsCells = () => page.evaluate(() => {
      // The padded cell is the row's tallest: its content and the padding round it up to the cell.
      // The cells themselves stretch to the row, so can't be measured directly
      const row = document.querySelector('.behaviour-grid-frame .dataGridEvenRow');
      const padded = row.querySelector('.behaviour-padded-cell');
      let natural = padded.getBoundingClientRect().height;
      for (let element = padded.parentElement; element !== row; element = element.parentElement) {
        const style = getComputedStyle(element);
        natural += parseFloat(style.paddingTop) + parseFloat(style.paddingBottom);
      }
      return Math.abs(row.getBoundingClientRect().height - natural) < 1;
    });
    const setCompact = (compact) => page.evaluate(
        (on) => document.documentElement.classList.toggle('stroom-density-compact', on), compact);

    for (const compact of [true, false, true]) {
      await setCompact(compact);
      await waitFor(page, rowFitsCells, `the rows' height fits their cells (compact: ${compact})`);
    }
  },
};
