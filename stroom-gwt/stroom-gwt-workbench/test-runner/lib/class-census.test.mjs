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

// Tests for the class census: node --test lib/

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { addToCensus, censusJson, collectClasses } from './class-census.mjs';

test('the census counts each class once per story, with the stories sorted', () => {
  const census = new Map();
  addToCensus(census, 'b--two', new Set(['popup', 'text-label']));
  addToCensus(census, 'a--one', new Set(['popup']));
  addToCensus(census, 'a--one', new Set(['popup']));
  assert.deepEqual(JSON.parse(censusJson(census)), {
    classes: {
      popup: { stories: 2, examples: ['a--one', 'b--two'] },
      'text-label': { stories: 1, examples: ['b--two'] },
    },
  });
});

test('the census keeps at most three example stories for a class', () => {
  const census = new Map();
  for (const id of ['d', 'c', 'b', 'a']) {
    addToCensus(census, id, ['max']);
  }
  assert.deepEqual(JSON.parse(censusJson(census)).classes.max, { stories: 4, examples: ['a', 'b', 'c'] });
});

test('an empty census is written as no classes', () => {
  assert.deepEqual(JSON.parse(censusJson(new Map())), { classes: {} });
});

test('the classes are gathered from every frame, and a frame that can not be read adds none', async () => {
  const frame = (names) => ({ evaluate: async () => names });
  const broken = { evaluate: async () => { throw new Error('detached'); } };
  const page = { frames: () => [frame(['a', 'b']), broken, frame(['b', 'c'])] };
  assert.deepEqual([...(await collectClasses(page))].sort(), ['a', 'b', 'c']);
});
