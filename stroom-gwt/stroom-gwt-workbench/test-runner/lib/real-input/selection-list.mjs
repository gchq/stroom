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

// Real input checks for the selection list of several choices (see index.mjs), on the explorer's
// Edit Tags dialog: Ctrl and Shift with real clicks, a real double click, and Ctrl+A, Delete and
// Enter pressed for real, as the list hands its keys on after the browser has handled them.

import assert from 'node:assert/strict';

const DOCUMENT_TAGS = '.explorer-node-edit-tags__node-tags';
const KNOWN_TAGS = '.explorer-node-edit-tags__all-tags';

// Waits (up to two seconds) for a condition the page reaches a moment after a key or click
async function waitFor(page, condition, message) {
  const deadline = Date.now() + 2000;
  while (!(await condition()) && Date.now() < deadline) {
    await page.waitForTimeout(25);
  }
  assert.ok(await condition(), message);
}

// The options in a list, as 'tag' or, for a chosen one, 'tag*'
function options(page, list) {
  return page.locator(`${list} [role="option"]`).evaluateAll((elements) => elements
      .map((element) => element.textContent.trim() + (element.getAttribute('aria-selected') === 'true' ? '*' : '')));
}

async function expectOptions(page, list, expected, message) {
  await waitFor(page, async () => JSON.stringify(await options(page, list)) === JSON.stringify(expected),
      `${message}: expected ${JSON.stringify(expected)}, found ${JSON.stringify(await options(page, list))}`);
}

function option(page, list, tag) {
  return page.locator(`${list} [role="option"]`, { hasText: tag });
}

// Pauses as a person's hand would between one action and the next. Each change redraws the list's
// rows, and the focus comes back to them a moment later (about a millisecond, some 50ms after a
// click), as the cell table hands its events on in a deferred command: input sent in that moment,
// as Playwright can, is lost, which no person's can be
function pace(page) {
  return page.waitForTimeout(100);
}

export const CHECKS = {
  // The story ends with every tag on the document. A click chooses one, Shift+click the run to
  // another, Ctrl+click takes one back out, and Delete removes those chosen; in the known tags,
  // Ctrl+A chooses every one and Enter adds them; a double click removes a document tag
  'app-main-edittagsdialog--keys-choose-and-add-tags': async (page) => {
    await expectOptions(page, DOCUMENT_TAGS, ['alpha', 'beta', 'gamma'], 'the story ends with every tag added');

    await pace(page);
    await option(page, DOCUMENT_TAGS, 'alpha').click();
    await expectOptions(page, DOCUMENT_TAGS, ['alpha*', 'beta', 'gamma'], 'a click chooses only the tag clicked');
    await pace(page);
    await option(page, DOCUMENT_TAGS, 'gamma').click({ modifiers: ['Shift'] });
    await expectOptions(page, DOCUMENT_TAGS, ['alpha*', 'beta*', 'gamma*'], 'Shift+click chooses the run of tags');
    await pace(page);
    await option(page, DOCUMENT_TAGS, 'beta').click({ modifiers: ['Control'] });
    await expectOptions(page, DOCUMENT_TAGS, ['alpha*', 'beta', 'gamma*'], 'Ctrl+click takes a tag back out');

    await pace(page);
    await page.keyboard.press('Delete');
    await expectOptions(page, DOCUMENT_TAGS, ['beta'], 'Delete removes the tags chosen');
    await expectOptions(page, KNOWN_TAGS, ['alpha', 'gamma'], 'the removed tags are known tags again');

    await pace(page);
    await option(page, KNOWN_TAGS, 'alpha').click();
    await pace(page);
    await page.keyboard.press('Control+a');
    await expectOptions(page, KNOWN_TAGS, ['alpha*', 'gamma*'], 'Ctrl+A chooses every tag');
    await pace(page);
    await page.keyboard.press('Enter');
    await expectOptions(page, DOCUMENT_TAGS, ['alpha', 'beta', 'gamma'], 'Enter adds the tags chosen');
    await waitFor(page, () => page.evaluate(() => document.activeElement?.tagName === 'INPUT'),
        'the focus goes back to the field after adding');

    await pace(page);
    await option(page, DOCUMENT_TAGS, 'beta').dblclick();
    await expectOptions(page, DOCUMENT_TAGS, ['alpha', 'gamma'], 'a double click removes a document tag');

    // Shift+Down moves to the next tag and chooses it; Shift+Up back takes the first out again, as
    // it goes to it and toggles it
    await pace(page);
    await option(page, DOCUMENT_TAGS, 'alpha').click();
    await expectOptions(page, DOCUMENT_TAGS, ['alpha*', 'gamma'], 'a click chooses alpha');
    await pace(page);
    await page.keyboard.press('Shift+ArrowDown');
    await expectOptions(page, DOCUMENT_TAGS, ['alpha*', 'gamma*'], 'Shift+Down chooses gamma too');
    await pace(page);
    await page.keyboard.press('Shift+ArrowUp');
    await expectOptions(page, DOCUMENT_TAGS, ['alpha', 'gamma*'], 'Shift+Up toggles alpha');
    await pace(page);
    await page.keyboard.press('Shift+Space');
    await expectOptions(page, DOCUMENT_TAGS, ['alpha*', 'gamma*'], 'Shift+Space chooses the run to alpha');
  },
};
