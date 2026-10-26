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

// Real input checks for help popups (see index.mjs, and Widgets/Inputs/FormGroup and QuickFilter):
// help opened from the keyboard (F1 in a form group's field, or Enter on a help button) takes the
// focus, so that a screen reader reads it (#5400), and Escape gives it back; help opened with a real
// mouse click leaves the focus where it was. A play's F1 can't open help (its key event has no key
// code for the key bindings to read), and the browser can't tell a play's keys from its clicks.
// A real click in a frame beside the help closes it, as a click anywhere else does (#3550); the
// frame's click is its own document's, which a play can't make.

import assert from 'node:assert/strict';

// What has the focus: 'help' for a help popup, else the focused element's title or tag
function focused(page) {
  return page.evaluate(() => {
    const element = document.activeElement;
    if (!element || element === document.body) {
      return null;
    }
    if (element.classList.contains('help-popup')) {
      return 'help';
    }
    return element.getAttribute('title') || element.tagName.toLowerCase();
  });
}

// Whether a help popup is showing
function helpShowing(page) {
  return page.evaluate(() => document.querySelector('.help-popup') !== null);
}

// Waits for the popup to open (and, from the keyboard, take the focus), or to close
async function waitForHelp(page, showing) {
  await page.waitForFunction((expected) => (document.querySelector('.help-popup') !== null) === expected,
    showing, { timeout: 5000 });
  // The popup takes the focus once it is placed and shown (a deferred step after it opens)
  await page.waitForTimeout(200);
}

export const CHECKS = {
  'widgets-inputs-formgroup--with-help': async (page) => {
    const field = page.getByLabel('Email address');
    // F1 in the field opens the help, which takes the focus; Escape closes it and gives it back
    await field.click();
    await page.keyboard.press('F1');
    await waitForHelp(page, true);
    assert.equal(await focused(page), 'help', 'help opened with F1 takes the focus');
    await page.keyboard.press('Escape');
    await waitForHelp(page, false);
    assert.equal(await focused(page), 'input', 'Escape closes the help and gives the focus back to the field');
    // A real click on the help button opens it but leaves the focus in the field
    await page.getByTitle('Email address - Click for help (F1)').click();
    await waitForHelp(page, true);
    assert.equal(await focused(page), 'input', 'help opened with the mouse leaves the focus in the field');
    await page.keyboard.press('Escape');
    await waitForHelp(page, false);
  },
  'widgets-inputs-quickfilter--with-help': async (page) => {
    const button = page.getByTitle('Quick Filter Syntax Help', { exact: true });
    // Enter on the help button opens the help, which takes the focus; Escape gives it back
    await button.focus();
    await page.keyboard.press('Enter');
    await waitForHelp(page, true);
    assert.equal(await focused(page), 'help', 'help opened with Enter takes the focus');
    await page.keyboard.press('Escape');
    await waitForHelp(page, false);
    assert.equal(await focused(page), 'Quick Filter Syntax Help',
      'Escape closes the help and gives the focus back to its button');
    // A real click leaves the focus off the popup
    await button.click();
    await waitForHelp(page, true);
    assert.notEqual(await focused(page), 'help', 'help opened with the mouse doesn\'t take the focus');
    assert.equal(await helpShowing(page), true);
    await page.keyboard.press('Escape');
    await waitForHelp(page, false);
  },
  'widgets-inputs-quickfilter--with-help-beside-frame': async (page) => {
    await page.getByTitle('Quick Filter Syntax Help', { exact: true }).click();
    await waitForHelp(page, true);
    await page.locator('iframe[title="Preview"]').click({ timeout: 5000 });
    await waitForHelp(page, false);
    assert.equal(await helpShowing(page), false, 'a click in the frame closes the help');
  },
};
