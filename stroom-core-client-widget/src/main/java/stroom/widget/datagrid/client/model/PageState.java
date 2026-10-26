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

package stroom.widget.datagrid.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// The rows on the current page, the visible range and the row count (design requirement R-D1).
///
/// This replicates the data handling in GWT's `HasDataPresenter` without any rendering, so
/// `AsyncDataProvider`, `RestDataProvider`, `ListDataProvider` and `Pager` behave exactly as they do
/// with `MyDataGrid`. It also records which rows have changed since the last render.
///
/// The methods report what changed so the caller can fire `RangeChangeEvent` and
/// `RowCountChangeEvent`.
///
/// @param <T> The row type.
public final class PageState<T> {

    private final List<T> rows = new ArrayList<>();
    private int pageStart;
    private int pageSize;
    private int rowCount;
    private boolean rowCountExact;

    // The page-relative range of rows changed since the last call to takeDirtyRows().
    private int dirtyFrom = Integer.MAX_VALUE;
    private int dirtyTo = Integer.MIN_VALUE;
    private boolean allDirty = true;

    /// @param pageSize The initial page size.
    public PageState(final int pageSize) {
        this.pageSize = Math.max(0, pageSize);
    }

    /// Set the total row count.
    ///
    /// @param count   The row count.
    /// @param isExact Whether the count is exact.
    /// @return `true` if the count or its exactness changed, so `RowCountChangeEvent` should fire.
    public boolean setRowCount(final int count, final boolean isExact) {
        if (count == rowCount && isExact == rowCountExact) {
            return false;
        }
        rowCount = count;
        rowCountExact = isExact;
        trimToRowCount();
        return true;
    }

    /// Replace rows on the current page.
    ///
    /// @param start  The absolute index of the first value.
    /// @param values The new row values.
    /// @return `true` if the row count grew to fit the values, so `RowCountChangeEvent` should fire.
    public boolean setRowData(final int start, final List<? extends T> values) {
        final int valuesEnd = start + values.size();
        final int pageEnd = pageStart + pageSize;
        final int boundedStart = Math.max(start, pageStart);
        final int boundedEnd = Math.min(valuesEnd, pageEnd);
        if (start != pageStart && boundedStart >= boundedEnd) {
            // The data is out of range for the current page. Empty lists that start on the page
            // start are allowed.
            return false;
        }

        // Create placeholders up to the specified index.
        final int cacheOffset = Math.max(0, boundedStart - pageStart - rows.size());
        for (int i = 0; i < cacheOffset; i++) {
            rows.add(null);
        }

        for (int i = boundedStart; i < boundedEnd; i++) {
            final T value = values.get(i - start);
            final int dataIndex = i - pageStart;
            if (dataIndex < rows.size()) {
                rows.set(dataIndex, value);
            } else {
                rows.add(value);
            }
        }
        markDirty(boundedStart - cacheOffset - pageStart, boundedEnd - pageStart);

        if (valuesEnd > rowCount) {
            return setRowCount(valuesEnd, rowCountExact);
        }
        return false;
    }

    /// Change the visible range.
    ///
    /// @param start     The absolute index of the first row on the page.
    /// @param length    The page size.
    /// @param clearData Whether to discard all current rows.
    /// @return `true` if the page start or size changed, so `RangeChangeEvent` should fire.
    public boolean setVisibleRange(final int start, final int length, final boolean clearData) {
        if (start < 0) {
            throw new IllegalArgumentException("Range start cannot be less than 0");
        }
        if (length < 0) {
            throw new IllegalArgumentException("Range length cannot be less than 0");
        }

        final boolean pageStartChanged = pageStart != start;
        if (pageStartChanged) {
            if (!clearData) {
                if (start > pageStart) {
                    final int increase = start - pageStart;
                    if (rows.size() > increase) {
                        rows.subList(0, increase).clear();
                    } else {
                        rows.clear();
                    }
                } else {
                    final int decrease = pageStart - start;
                    if (!rows.isEmpty() && decrease < pageSize) {
                        for (int i = 0; i < decrease; i++) {
                            rows.add(0, null);
                        }
                    } else {
                        rows.clear();
                    }
                }
            }
            pageStart = start;
            allDirty = true;
        }

        final boolean pageSizeChanged = pageSize != length;
        if (pageSizeChanged) {
            pageSize = length;
        }

        if (clearData) {
            rows.clear();
            allDirty = true;
        }

        trimToRowCount();
        return pageStartChanged || pageSizeChanged;
    }

    /// Mark every row as needing to be rendered again, e.g. for `redraw()`.
    public void markAllDirty() {
        allDirty = true;
    }

    /// Mark one row as needing to be rendered again, e.g. for `redrawRow()`.
    ///
    /// @param index The page-relative row index.
    public void markDirty(final int index) {
        markDirty(index, index + 1);
    }

    /// Return the page-relative range of rows that have changed since the last call, and reset it.
    ///
    /// @return `null` if every row has changed, otherwise a two-element array `[from, to)`, which is
    /// empty (`from >= to`) if nothing has changed.
    public int[] takeDirtyRows() {
        final int[] result;
        if (allDirty) {
            result = null;
        } else if (dirtyFrom >= dirtyTo) {
            result = new int[]{0, 0};
        } else {
            result = new int[]{dirtyFrom, dirtyTo};
        }
        allDirty = false;
        dirtyFrom = Integer.MAX_VALUE;
        dirtyTo = Integer.MIN_VALUE;
        return result;
    }

    /// @return The rows on the current page. May contain `null` placeholders.
    public List<T> getRows() {
        return Collections.unmodifiableList(rows);
    }

    /// @param index The page-relative row index.
    /// @return The row, or `null` if it is a placeholder or out of range.
    public T getRow(final int index) {
        if (index < 0 || index >= rows.size()) {
            return null;
        }
        return rows.get(index);
    }

    /// @return The number of rows held for the current page.
    public int getRowDataSize() {
        return rows.size();
    }

    /// @return The absolute index of the first row on the page.
    public int getPageStart() {
        return pageStart;
    }

    /// @return The page size.
    public int getPageSize() {
        return pageSize;
    }

    /// @return The total row count.
    public int getRowCount() {
        return rowCount;
    }

    /// @return Whether the row count is exact.
    public boolean isRowCountExact() {
        return rowCountExact;
    }

    private void markDirty(final int from, final int to) {
        dirtyFrom = Math.min(dirtyFrom, Math.max(0, from));
        dirtyTo = Math.max(dirtyTo, to);
    }

    private void trimToRowCount() {
        final int expectedSize = Math.max(0, Math.min(pageSize, rowCount - pageStart));
        if (rows.size() > expectedSize) {
            rows.subList(expectedSize, rows.size()).clear();
            allDirty = true;
        }
    }
}
