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

import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';

import { collectFitInFrame, compareTextFit } from './text-fit.mjs';

const run = (size, stories) => ({ fontSize: size, stories });
const cut = (path, text, by = 4) => ({ kind: 'cut-off', path, text, by });

test('a problem the smaller text size has too is not reported', () => {
  const result = compareTextFit(
    run('medium', { a: [cut('div.cell', 'Name')] }),
    run('large', { a: [cut('div.cell', 'Name', 9)] }));
  assert.equal(result.stories, 1);
  assert.deepEqual(result.added, []);
});

test('a text too long for its box at both sizes is not reported, whichever way it shows', () => {
  const ellipsis = { kind: 'ellipsis', path: 'div.cell', text: 'Name', by: 3 };
  const result = compareTextFit(
    run('medium', { a: [ellipsis] }),
    run('large', { a: [cut('div.header > div', 'Name'), cut('div.cell', 'Type')] }));
  assert.deepEqual(result.added.map((group) => group.examples[0].text), ['Type']);
});

test('new problems are reported, grouped by kind and path, most stories first', () => {
  const overlap = { kind: 'overlap', path: 'span.a', text: 'One', by: 3, other: { path: 'span.b', text: 'Two' } };
  const result = compareTextFit(
    run('medium', { a: [], b: [], c: [] }),
    run('large', {
      a: [cut('div.cell', 'Name'), overlap],
      b: [cut('div.cell', 'Type')],
      // Not in the baseline: not compared
      d: [cut('div.cell', 'Size')],
    }));
  assert.equal(result.stories, 2);
  assert.equal(result.problems, 3);
  assert.deepEqual(result.added.map((group) => [group.kind, group.path, group.stories]),
    [['cut-off', 'div.cell', 2], ['overlap', 'span.a', 1]]);
  assert.deepEqual(result.added[1].examples[0], { story: 'a', text: 'One', by: 3, other: { path: 'span.b', text: 'Two' } });
});

test('an overlap with a different text is a different problem', () => {
  const overlap = (other) => ({ kind: 'overlap', path: 'span.a', text: 'One', by: 3, other: { path: 'span.b', text: other } });
  const result = compareTextFit(run('medium', { a: [overlap('Two')] }), run('large', { a: [overlap('Three')] }));
  assert.equal(result.problems, 1);
});

// The collector itself, in a browser
let browser;
let page;
before(async () => {
  const { chromium } = await import('playwright');
  browser = await chromium.launch();
  page = await browser.newPage();
});
after(async () => {
  await browser?.close();
});

const collect = async (body) => {
  await page.setContent(`<html><body style="margin:0;font:16px sans-serif">${body}</body></html>`);
  return (await page.evaluate(collectFitInFrame, 100)).map(({ kind, text, other }) => [kind, text, other?.text]);
};

test('text past a box that hides its overflow is cut off; text that fits, or can scroll, is not', async () => {
  assert.deepEqual(await collect(`
    <div style="width:40px;overflow:hidden;white-space:nowrap"><span>A long label</span></div>
    <div style="width:200px;overflow:hidden;white-space:nowrap"><span>Fits</span></div>
    <div style="height:10px;overflow:hidden"><span>Too tall</span></div>
    <div style="width:40px;overflow:auto;white-space:nowrap"><span>Scrolls instead</span></div>`),
  [['cut-off', 'A long label', undefined], ['cut-off', 'Too tall', undefined]]);
});

test('text further down a box that scrolls, in a box that hides its overflow, can be scrolled to', async () => {
  assert.deepEqual(await collect(`
    <div style="height:40px;overflow:hidden">
      <div style="height:40px;overflow:auto"><div style="height:100px"></div><span>Scroll down</span></div>
    </div>`),
  []);
});

test('text scrolled out of a box that scrolls is not drawn over what is below it', async () => {
  assert.deepEqual(await collect(`
    <div style="position:absolute;left:0;top:0;width:200px;height:40px;overflow:auto">
      <div style="height:30px"></div><span style="display:block">Scrolled out</span>
    </div>
    <span style="position:absolute;left:0;top:44px">Below the box</span>`),
  []);
});

test('text scrolled out of a box far up its ancestors is not drawn over what is beside it', async () => {
  const nested = (inner) => '<div>'.repeat(8) + inner + '</div>'.repeat(8);
  assert.deepEqual(await collect(`
    <div style="position:absolute;left:0;top:0;width:200px;height:40px;overflow:auto">
      <div style="height:30px"></div>${nested('<span style="display:block">Scrolled out</span>')}
    </div>
    <span style="position:absolute;left:0;top:44px">Below the box</span>`),
  []);
});

test('text past the code editor\'s edge, which its own scroll bars scroll to, is not cut off', async () => {
  assert.deepEqual(await collect(`
    <div class="ace_editor" style="width:100px;height:20px;overflow:hidden">
      <div class="ace_scroller" style="width:100px;height:20px;overflow:hidden;white-space:nowrap">
        <div><span>A long line of code</span></div><div><span>Below</span></div>
      </div>
    </div>`),
  []);
});

test('a dialog whose content is wider than it scrolls sideways', async () => {
  const dialog = (title, width) => `<div class="dialog__container"><div class="dialog__title">${title}</div>
    <div class="dialog__content" style="width:200px;overflow:auto"><div style="width:${width}px">.</div></div></div>`;
  assert.deepEqual(await collect(dialog('Too narrow', 300) + dialog('Fits', 200)),
    [['scrolls-sideways', 'Too narrow', undefined]]);
});

test('a line box a little taller than its box is not cut off while its glyphs fit', async () => {
  assert.deepEqual(await collect(`
    <div style="height:16px;line-height:16px;overflow:hidden;font:16px monospace"><span>Fits</span></div>
    <div style="height:10px;line-height:16px;overflow:hidden;font:16px monospace"><span>Doesn't</span></div>`),
  [['cut-off', "Doesn't", undefined]]);
});

test('visually hidden text (for screen readers) is not cut off', async () => {
  assert.deepEqual(await collect(`
    <span style="position:absolute;width:1px;height:1px;overflow:hidden;white-space:nowrap">Opens in a new tab</span>`),
  []);
});

test('text shortened with an ellipsis is reported once, however often it appears', async () => {
  const shortened = '<div style="width:40px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis">A long label</div>';
  assert.deepEqual(await collect(shortened + shortened),
    [['cut-off', 'A long label', undefined], ['ellipsis', 'A long label', undefined]]);
});

test('texts drawn over each other overlap; a text and the element it is in do not', async () => {
  assert.deepEqual(await collect(`
    <span style="position:absolute;left:0;top:0">First</span>
    <span style="position:absolute;left:10px;top:4px">Second</span>
    <div style="position:absolute;left:0;top:100px">Outer <b>inner</b></div>
    <span style="position:absolute;left:300px;top:0">Apart</span>`),
  [['overlap', 'First', 'Second']]);
});

test('text covered by a box with a background does not overlap; a small box drawn over text does', async () => {
  assert.deepEqual(await collect(`
    <span style="position:absolute;left:0;top:0">Under the dialog</span>
    <div style="position:absolute;left:0;top:0;width:300px;height:100px;background:white">
      <span style="position:absolute;left:10px;top:4px">Dialog title</span>
    </div>
    <span style="position:absolute;left:0;top:200px">Under the badge</span>
    <span style="position:absolute;left:10px;top:204px;background:white">Badge</span>`),
  [['overlap', 'Under the badge', 'Badge']]);
});
