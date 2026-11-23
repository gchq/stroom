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

// Tests for the text size checks: node --test lib/

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { compareTextSizes, fontSize, fontSizeScript } from './text-sizes.mjs';

const run = (size, stories) => ({ fontSize: size, stories });
const text = (path, words, px, extra = {}) => ({ path, text: words, px, lineHeight: 'normal', count: 1, ...extra });

test('the font size option takes small, medium or large, or nothing', () => {
  assert.equal(fontSize(undefined), null);
  assert.equal(fontSize('large'), 'large');
  assert.throws(() => fontSize('huge'), /--font-size must be one of small, medium, large, not 'huge'/);
});

// Runs the font size script against a document whose element has these classes, returning them after
const runFontSizeScript = (size, classes) => {
  const classList = new Set(classes);
  const document = {
    documentElement: {
      classList: {
        [Symbol.iterator]: () => classList[Symbol.iterator](),
        contains: (name) => classList.has(name),
        add: (...names) => names.forEach((name) => classList.add(name)),
        remove: (...names) => names.forEach((name) => classList.delete(name)),
      },
    },
  };
  class MutationObserver {
    observe() {}
  }
  new Function('document', 'MutationObserver', fontSizeScript(size))(document, MutationObserver);
  return [...classList];
};

test('the font size script switches the text size class to the chosen one', () => {
  assert.deepEqual(runFontSizeScript('large', ['stroom-theme-light', 'stroom-font-size-medium']),
    ['stroom-theme-light', 'stroom-font-size-large']);
});

test('the font size script leaves a document without a text size class alone', () => {
  assert.deepEqual(runFontSizeScript('large', ['vis-frame']), ['vis-frame']);
});

test('text that grew with the preference is not reported', () => {
  const result = compareTextSizes(
    run('medium', { a: [text('div.label', 'Name', 15.2)] }),
    run('large', { a: [text('div.label', 'Name', 16.8)] }));
  assert.equal(result.compared, 1);
  assert.deepEqual(result.unchanged, []);
});

test('text that stayed the same size is reported, grouped by path, most stories first', () => {
  const result = compareTextSizes(
    run('medium', {
      a: [text('text.caption', 'dave', 11), text('span.tip', 'Hint', 12)],
      b: [text('text.caption', 'desk-1', 11)],
    }),
    run('large', {
      a: [text('text.caption', 'dave', 11), text('span.tip', 'Hint', 12)],
      b: [text('text.caption', 'desk-1', 11)],
    }));
  assert.equal(result.compared, 3);
  assert.deepEqual(result.unchanged.map((group) => [group.path, group.stories]),
    [['text.caption', 2], ['span.tip', 1]]);
  assert.deepEqual(result.unchanged[0].examples[0], { story: 'a', text: 'dave', from: 11, to: 11 });
});

test('a small change below the ratio counts as unchanged', () => {
  const result = compareTextSizes(
    run('medium', { a: [text('p', 'Words', 16)] }),
    run('large', { a: [text('p', 'Words', 16.2)] }));
  assert.equal(result.unchanged.length, 1);
});

test('a text shown several times is matched to the copy that changed most', () => {
  const result = compareTextSizes(
    run('medium', { a: [text('td', 'person', 15.2)] }),
    run('large', { a: [text('td', 'person', 15.2), text('td', 'person', 16.8)] }));
  assert.deepEqual(result.unchanged, []);
});

test('stories and texts only in one run are left out', () => {
  const result = compareTextSizes(
    run('medium', { a: [text('p', 'Only here', 12)], b: [text('p', 'x', 12)] }),
    run('large', { a: [text('p', 'Elsewhere', 12)] }));
  assert.equal(result.compared, 0);
  assert.deepEqual(result.unchanged, []);
});

test('texts in different frames are told apart', () => {
  const result = compareTextSizes(
    run('medium', { a: [text('p', 'Same', 12, { frame: '/ui/vis.html' }), text('p', 'Same', 12)] }),
    run('large', { a: [text('p', 'Same', 12, { frame: '/ui/vis.html' }), text('p', 'Same', 14)] }));
  assert.deepEqual(result.unchanged.map((group) => group.frame), ['/ui/vis.html']);
});
