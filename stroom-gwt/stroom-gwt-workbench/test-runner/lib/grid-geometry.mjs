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

// The data grid geometry baseline (stroom-core-client-widget/DATA_GRID_DESIGN.md, Phase 0): what
// is measured of each data grid in a story, and how a later measurement is compared with it. Used
// by ../grid-geometry.mjs. Covers both MyDataGrid (a pair of tables) and DataGrid (.data-grid), so a
// screen measured with the old grid can be compared after it switches to the new one.

// How far a measurement may move before it counts as changed, in pixels
export const TOLERANCE = {
  headerHeight: 0.5,
  rowHeight: 0.5,
  columnWidth: 1,
};

// The most rows measured per grid
export const MAX_ROWS = 10;

// Measures every visible data grid on the page. Runs in the browser (page.evaluate), so it must not
// refer to anything outside itself.
export function measureGrids(maxRows) {
  const round = (value) => Math.round(value * 100) / 100;
  const visible = (el) => {
    const rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  };
  const text = (el) => (el.innerText || '').replace(/\s+/g, ' ').trim();
  const grids = [];

  // DataGrid: one element with its header and rows
  for (const root of document.querySelectorAll('.data-grid')) {
    if (!visible(root)) {
      continue;
    }
    const head = root.querySelector('.data-grid__head');
    const headerCells = [...root.querySelectorAll('.data-grid__header-row > .data-grid__header-cell')];
    grids.push({
      kind: 'new',
      headers: headerCells.map(text),
      headerHeight: head && visible(head) ? round(head.getBoundingClientRect().height) : 0,
      columnWidths: headerCells.map((cell) => Math.round(cell.getBoundingClientRect().width)),
      rowHeights: [...root.querySelectorAll('.data-grid__body > .data-grid__row')].slice(0, maxRows)
        .map((row) => round(row.getBoundingClientRect().height)),
    });
  }

  // MyDataGrid: a header table and a body table, inside a common root
  for (const thead of document.querySelectorAll('thead')) {
    const headerCells = [...thead.querySelectorAll('th.dataGridHeader')];
    if (headerCells.length === 0 || !visible(thead) || thead.closest('.data-grid')) {
      continue;
    }
    // The nearest ancestor that also holds the body table
    let root = thead.parentElement;
    while (root && !root.querySelector('tbody')) {
      root = root.parentElement;
    }
    const rows = root ? [...root.querySelectorAll('tr.dataGridEvenRow, tr.dataGridOddRow')] : [];
    grids.push({
      kind: 'old',
      headers: headerCells.map(text),
      headerHeight: round(thead.getBoundingClientRect().height),
      columnWidths: headerCells.map((cell) => Math.round(cell.getBoundingClientRect().width)),
      rowHeights: rows.slice(0, maxRows).map((row) => round(row.getBoundingClientRect().height)),
    });
  }
  return grids;
}

// Compares a story's grids with the baseline's. Grids are matched by their header text, then by
// position. Returns a list of human-readable differences, empty if they match.
export function compareGrids(baseline, actual, tolerance = TOLERANCE) {
  const differences = [];
  const unmatched = [...actual];
  baseline.forEach((expected, index) => {
    const label = `grid ${index + 1} [${expected.headers.filter((h) => h).slice(0, 3).join(', ')}]`;
    let matchIndex = unmatched.findIndex((grid) => sameHeaders(grid.headers, expected.headers));
    if (matchIndex < 0 && unmatched.length > 0 && index < actual.length) {
      matchIndex = unmatched.indexOf(actual[index]);
    }
    if (matchIndex < 0) {
      differences.push(`${label}: missing`);
      return;
    }
    const [grid] = unmatched.splice(matchIndex, 1);
    if (!sameHeaders(grid.headers, expected.headers)) {
      differences.push(`${label}: headers ${JSON.stringify(expected.headers)} -> ${JSON.stringify(grid.headers)}`);
    }
    if (Math.abs(grid.headerHeight - expected.headerHeight) > tolerance.headerHeight) {
      differences.push(`${label}: header height ${expected.headerHeight} -> ${grid.headerHeight}`);
    }
    differences.push(...compareLists(`${label}: column widths`, expected.columnWidths, grid.columnWidths,
      tolerance.columnWidth));
    differences.push(...compareLists(`${label}: row heights`, expected.rowHeights, grid.rowHeights,
      tolerance.rowHeight));
  });
  unmatched.forEach((grid) => {
    differences.push(`extra grid [${grid.headers.filter((h) => h).slice(0, 3).join(', ')}]`);
  });
  return differences;
}

function sameHeaders(a, b) {
  return a.length === b.length && a.every((header, i) => header === b[i]);
}

function compareLists(label, expected, actual, tolerance) {
  if (expected.length !== actual.length) {
    return [`${label}: ${expected.length} -> ${actual.length} (${JSON.stringify(expected)} -> ${JSON.stringify(actual)})`];
  }
  const changed = expected.some((value, i) => Math.abs(value - actual[i]) > tolerance);
  return changed
    ? [`${label}: ${JSON.stringify(expected)} -> ${JSON.stringify(actual)}`]
    : [];
}
