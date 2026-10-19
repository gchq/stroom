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

// Real input checks for the value spinner (see index.mjs): its arrows pressed and held with the
// real mouse and a real finger (touch events from Chromium's DevTools protocol, which the browser
// turns into pointer events as it does a touch screen's), its keys pressed for real, and the
// arrows never taking the focus.

import assert from 'node:assert/strict';

const INPUT = '.value-spinner__input';
const INCREASE = '[title="Increase value"]';

function value(page) {
  return page.locator(INPUT).inputValue();
}

async function centre(page, selector) {
  const box = await page.locator(selector).boundingBox();
  return { x: box.x + box.width / 2, y: box.y + box.height / 2 };
}

// Whether the focus is in the spinner's field
function fieldFocused(page) {
  return page.evaluate((selector) => document.activeElement === document.querySelector(selector), INPUT);
}

// Whether the focus is on anything inside the spinner other than its field (e.g. an arrow)
function arrowFocused(page) {
  return page.evaluate(() => {
    const element = document.activeElement;
    return Boolean(element && element.closest('.value-spinner') && !element.matches('.value-spinner__input'));
  });
}

// Puts a finger on a point, holds it there for a while, then lifts it
async function touchHold(page, point, millis) {
  const session = await page.context().newCDPSession(page);
  try {
    await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
    await page.waitForTimeout(millis);
    await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  } finally {
    await session.detach();
  }
}

// Presses a key, then waits (up to a second) for the field to show the value it should
async function pressFor(page, field, key, expected, message) {
  await page.keyboard.press(key);
  const deadline = Date.now() + 1000;
  let actual = await field.inputValue();
  while (actual !== expected && Date.now() < deadline) {
    await page.waitForTimeout(25);
    actual = await field.inputValue();
  }
  assert.equal(actual, expected, message);
}

export const CHECKS = {
  // The real mouse: a click steps once and leaves the focus in the field; held, an arrow keeps
  // stepping, faster and faster, and stops when let go; Tab passes the arrows by
  'widgets-inputs-valuespinner--arrows-step-the-value': async (page) => {
    const start = Number(await value(page));
    const up = await centre(page, INCREASE);
    await page.mouse.click(up.x, up.y);
    assert.equal(Number(await value(page)), start + 1, 'a real click steps once');
    assert.ok(await fieldFocused(page), 'a click leaves the focus in the field');
    await page.mouse.move(up.x, up.y);
    await page.mouse.down();
    await page.waitForTimeout(1500);
    await page.mouse.up();
    const held = Number(await value(page));
    assert.ok(held > start + 10, `holding the arrow keeps stepping (to ${held})`);
    await page.waitForTimeout(400);
    assert.equal(Number(await value(page)), held, 'letting go stops the stepping');
    await page.locator(INPUT).focus();
    await page.keyboard.press('Tab');
    assert.ok(!(await arrowFocused(page)), 'Tab passes the arrows by');
  },
  // A real finger: a tap steps once without focusing the field (so no on-screen keyboard comes up),
  // and a held finger keeps stepping until it is lifted
  'widgets-inputs-valuespinner--arrows-step-from-typed-text': async (page) => {
    await page.evaluate(() => document.activeElement?.blur());
    const start = Number(await value(page));
    const up = await centre(page, INCREASE);
    await touchHold(page, up, 50);
    assert.equal(Number(await value(page)), start + 1, 'a tap steps once');
    assert.ok(!(await fieldFocused(page)), 'a tap doesn\'t focus the field');
    await touchHold(page, up, 1500);
    const held = Number(await value(page));
    assert.ok(held > start + 10, `a held finger keeps stepping (to ${held})`);
    await page.waitForTimeout(400);
    assert.equal(Number(await value(page)), held, 'lifting the finger stops the stepping');
  },
  // Real keys: Page Up and Page Down take ten steps, Home and End go to the bounds
  'widgets-inputs-valuespinner--page-home-and-end-keys': async (page) => {
    await page.locator(INPUT).focus();
    await page.keyboard.press('Home');
    assert.equal(await value(page), '0', 'Home goes to the minimum');
    await page.keyboard.press('PageUp');
    assert.equal(await value(page), '10', 'Page Up takes ten steps');
    await page.keyboard.press('ArrowUp');
    assert.equal(await value(page), '11', 'Up takes one step');
    await page.keyboard.press('End');
    assert.equal(await value(page), '100', 'End goes to the maximum');
    await page.keyboard.press('PageDown');
    assert.equal(await value(page), '90', 'Page Down takes ten steps');
  },
  // The data grid's spinner (ValueSpinnerCell), with the real mouse: a click steps once and a press
  // keeps stepping while held, committing when let go; a real finger's tap steps once (the grid
  // hands a cell its events after the browser has handled them, so a held finger doesn't repeat)
  'widgets-cell-renderers-valuespinnercell--arrows-step-and-commit': async (page) => {
    const row = page.locator('tr', { hasText: 'Alpha' });
    const field = row.getByRole('spinbutton');
    const box = await row.locator(INCREASE).boundingBox();
    const up = { x: box.x + box.width / 2, y: box.y + box.height / 2 };
    const start = Number(await field.inputValue());
    await page.mouse.click(up.x, up.y);
    assert.equal(Number(await field.inputValue()), start + 1, 'a real click steps once');
    await page.mouse.move(up.x, up.y);
    await page.mouse.down();
    await page.waitForTimeout(1500);
    await page.mouse.up();
    const held = Number(await field.inputValue());
    assert.ok(held > start + 10, `holding the arrow keeps stepping (to ${held})`);
    assert.equal(await field.getAttribute('aria-valuenow'), String(held), 'the value is told as it steps');
    await page.waitForTimeout(400);
    assert.equal(Number(await field.inputValue()), held, 'letting go stops the stepping');
    await page.mouse.move(0, 0);
    const down = await row.locator('[title="Decrease value"]').boundingBox();
    await touchHold(page, { x: down.x + down.width / 2, y: down.y + down.height / 2 }, 50);
    await page.waitForTimeout(200);
    assert.equal(Number(await field.inputValue()), held - 1, 'a tap steps once');
  },
  // The grid spinner's keys, pressed for real while its field has the focus (clicked in its text,
  // clear of the arrows)
  'widgets-cell-renderers-valuespinnercell--keys-while-focused': async (page) => {
    const field = page.locator('tr', { hasText: 'Bravo' }).getByRole('spinbutton');
    await field.click({ position: { x: 10, y: 12 } });
    // The grid hands the cell its key events a moment after they happen, so wait for each value
    await pressFor(page, field, 'End', '100', 'End goes to the maximum');
    await pressFor(page, field, 'PageDown', '90', 'Page Down takes ten steps');
    await pressFor(page, field, 'ArrowDown', '89', 'Down takes one step');
    assert.ok(await field.evaluate((element) => element === document.activeElement),
      'the focus stays in the field');
  },
  // A disabled spinner's arrows do nothing when clicked or tapped
  'widgets-inputs-valuespinner--disabled': async (page) => {
    const start = await value(page);
    const up = await centre(page, INCREASE);
    await page.mouse.click(up.x, up.y);
    await touchHold(page, up, 50);
    assert.equal(await value(page), start, 'a disabled spinner\'s arrows do nothing');
    assert.ok(!(await arrowFocused(page)), 'a click doesn\'t focus an arrow');
  },
};
