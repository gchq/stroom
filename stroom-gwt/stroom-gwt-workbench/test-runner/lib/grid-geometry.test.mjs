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

// Tests for the data grid geometry comparison: node --test lib/

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { compareGrids } from './grid-geometry.mjs';

const grid = (overrides = {}) => ({
  kind: 'old',
  headers: ['Name', 'Value'],
  headerHeight: 26,
  columnWidths: [200, 300],
  rowHeights: [23.06, 23.06],
  ...overrides,
});

test('identical grids match', () => {
  assert.deepEqual(compareGrids([grid()], [grid({ kind: 'new' })]), []);
});

test('differences within the tolerance are ignored', () => {
  assert.deepEqual(compareGrids([grid()], [grid({ headerHeight: 26.4, columnWidths: [201, 299],
    rowHeights: [23.5, 22.6] })]), []);
});

test('changed sizes are reported', () => {
  assert.deepEqual(compareGrids([grid()], [grid({ headerHeight: 27, columnWidths: [200, 310],
    rowHeights: [24, 23.06] })]), [
    'grid 1 [Name, Value]: header height 26 -> 27',
    'grid 1 [Name, Value]: column widths: [200,300] -> [200,310]',
    'grid 1 [Name, Value]: row heights: [23.06,23.06] -> [24,23.06]',
  ]);
});

test('a different number of rows or columns is reported', () => {
  assert.deepEqual(compareGrids([grid()], [grid({ rowHeights: [23.06] })]), [
    'grid 1 [Name, Value]: row heights: 2 -> 1 ([23.06,23.06] -> [23.06])',
  ]);
});

test('grids are matched by their headers, whatever their order', () => {
  const other = grid({ headers: ['Id'], columnWidths: [60], headerHeight: 30 });
  assert.deepEqual(compareGrids([grid(), other], [other, grid()]), []);
});

test('missing and extra grids are reported', () => {
  assert.deepEqual(compareGrids([grid()], []), ['grid 1 [Name, Value]: missing']);
  assert.deepEqual(compareGrids([], [grid()]), ['extra grid [Name, Value]']);
});

test('a grid whose headers changed is still compared, by position', () => {
  assert.deepEqual(compareGrids([grid()], [grid({ headers: ['Name', 'Amount'] })]), [
    'grid 1 [Name, Value]: headers ["Name","Value"] -> ["Name","Amount"]',
  ]);
});
