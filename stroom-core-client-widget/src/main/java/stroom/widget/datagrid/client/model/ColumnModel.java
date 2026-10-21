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
import java.util.List;
import java.util.Objects;

/// The ordered columns of a grid, with each column's sizing settings and current pixel width.
///
/// This class has no DOM dependencies. The grid widget holds one and renders from it.
///
/// * Widths start unresolved. [#resolve(int)] works out the starting widths once, on the grid's
///   first layout with a non-zero width (design requirement R-C4).
/// * Columns added after that are sized immediately: fixed and fill columns get their declared width
///   and percent columns use the width the grid had when it was first resolved.
/// * The table width is always the sum of the column widths (R-L4).
///
/// @param <C> The type of the column objects, e.g. GWT `Column<R, ?>`.
public final class ColumnModel<C> {

    private final List<Entry<C>> entries = new ArrayList<>();
    private int structureVersion;
    private int widthVersion;
    private int initialOuterWidth;

    /// Add a column at the end.
    ///
    /// @param column The column object.
    /// @param sizing The column's sizing settings.
    public void add(final C column, final ColumnSizing sizing) {
        insert(entries.size(), column, sizing);
    }

    /// Insert a column.
    ///
    /// @param index  The position to insert at, from `0` to [#size()].
    /// @param column The column object.
    /// @param sizing The column's sizing settings.
    public void insert(final int index, final C column, final ColumnSizing sizing) {
        Objects.requireNonNull(column);
        Objects.requireNonNull(sizing);
        final int width = ColumnWidthResolver.startingWidth(sizing, initialOuterWidth);
        entries.add(index, new Entry<>(column, sizing, width));
        structureVersion++;
        widthVersion++;
    }

    /// Remove a column.
    ///
    /// @param column The column object to remove.
    /// @return `true` if the column was found and removed.
    public boolean remove(final C column) {
        final int index = indexOf(column);
        if (index == -1) {
            return false;
        }
        remove(index);
        return true;
    }

    /// Remove the column at an index.
    ///
    /// @param index The index of the column to remove.
    public void remove(final int index) {
        entries.remove(index);
        structureVersion++;
        widthVersion++;
    }

    /// Remove every column. The resolved state is kept, so columns added afterwards are sized as
    /// columns added after the first layout.
    public void clear() {
        if (!entries.isEmpty()) {
            entries.clear();
            structureVersion++;
            widthVersion++;
        }
    }

    /// Move a column, using the same index convention as `MyDataGrid.moveColumn`.
    ///
    /// @param fromIndex The current index of the column.
    /// @param toIndex   The position to insert it before, counted in the order before the move.
    ///                  So `toIndex == size()` moves the column to the end.
    public void move(final int fromIndex, final int toIndex) {
        final Entry<C> entry = entries.remove(fromIndex);
        int newIndex = toIndex;
        if (fromIndex < toIndex) {
            newIndex = toIndex - 1;
        }
        newIndex = Math.max(0, Math.min(newIndex, entries.size()));
        entries.add(newIndex, entry);
        structureVersion++;
        widthVersion++;
    }

    /// Work out where a dragged column would be inserted, replicating `MoveHandle.move`.
    ///
    /// The result is in the same convention as [#move(int, int)]. Returning `fromIndex` means
    /// dropping here would not move the column.
    ///
    /// @param fromIndex The index of the column being dragged.
    /// @param x         The pointer position in table coordinates, i.e. measured from the left edge
    ///                  of the first column, including any horizontal scroll.
    /// @return The insertion position.
    public int insertPosition(final int fromIndex, final double x) {
        final int count = entries.size();
        int pos = count;
        int left = 0;
        for (int i = 0; i < count; i++) {
            final int width = entries.get(i).width;
            if (i == fromIndex + 1) {
                // Don't allow the user to insert a column directly after itself.
                if (left > x) {
                    pos = i - 1;
                    break;
                }
            } else if (i == count - 1 && fromIndex == count - 1) {
                pos = i;
                break;
            } else if (left + (width / 2D) >= x) {
                pos = i;
                break;
            }
            left += width;
        }
        return Math.max(pos, firstMovableIndex());
    }

    /// Work out which column edge a resize handle should be on for a pointer over the header,
    /// replicating `ResizeHandle.update`.
    ///
    /// The handle is on the right edge of the column under the pointer, unless that column cannot be
    /// resized, or the pointer is within `handleWidth` of its left edge and nearer that edge than
    /// the right one. In those cases it is on the previous column's right edge, if that column can
    /// be resized.
    ///
    /// @param x           The pointer position in table coordinates, i.e. measured from the left edge
    ///                    of the first column, including any horizontal scroll.
    /// @param handleWidth The width of the resize handle in pixels.
    /// @return The index of the column whose right edge the handle is on, or `-1` for no handle.
    public int resizeTarget(final double x, final int handleWidth) {
        int left = 0;
        for (int i = 0; i < entries.size(); i++) {
            final int right = left + entries.get(i).width;
            if (x >= left && x < right) {
                final boolean canResizeThis = entries.get(i).sizing.isResizable();
                final boolean canResizePrevious = i > 0 && entries.get(i - 1).sizing.isResizable();
                final double diffLeft = x - left;
                final double diffRight = right - x;
                if (canResizePrevious
                    && (!canResizeThis || (diffLeft <= handleWidth && diffLeft < diffRight))) {
                    return i - 1;
                }
                return canResizeThis
                        ? i
                        : -1;
            }
            left = right;
        }
        return -1;
    }

    /// @param x The pointer position in table coordinates.
    /// @return The index of the column under the pointer, or `-1` if it is past the last column.
    public int columnAt(final double x) {
        if (x < 0) {
            return -1;
        }
        int left = 0;
        for (int i = 0; i < entries.size(); i++) {
            left += entries.get(i).width;
            if (x < left) {
                return i;
            }
        }
        return -1;
    }

    /// @return The index of the first movable column, or `0` if no column is movable.
    public int firstMovableIndex() {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).sizing.isMovable()) {
                return i;
            }
        }
        return 0;
    }

    /// Work out the starting widths of all columns. Only does anything the first time it is called
    /// with a width greater than zero.
    ///
    /// @param outerWidth The grid's visible width in pixels.
    /// @return `true` if the widths were resolved by this call.
    public boolean resolve(final int outerWidth) {
        if (isResolved() || outerWidth <= 0) {
            return false;
        }
        initialOuterWidth = outerWidth;
        final int[] widths = ColumnWidthResolver.resolve(getSizings(), outerWidth);
        for (int i = 0; i < widths.length; i++) {
            entries.get(i).width = widths[i];
        }
        widthVersion++;
        return true;
    }

    /// Grow the fill columns to take up any space left in `outerWidth`, as
    /// `MyDataGrid.resizeTableToFitColumns(true)` does. Never shrinks a column.
    ///
    /// @param outerWidth The grid's visible width in pixels.
    /// @return `true` if any width changed.
    public boolean fill(final int outerWidth) {
        if (outerWidth <= 0) {
            return false;
        }
        final int[] widths = getWidths();
        final boolean changed = ColumnWidthResolver.fill(getSizings(), widths, outerWidth);
        if (changed) {
            for (int i = 0; i < widths.length; i++) {
                entries.get(i).width = widths[i];
            }
            widthVersion++;
        }
        return changed;
    }

    /// @return Whether the starting widths have been worked out.
    public boolean isResolved() {
        return initialOuterWidth > 0;
    }

    /// Set a column's width programmatically, e.g. from saved settings. A width below one pixel is
    /// treated as one pixel (R-L5). The column's minimum width is not applied.
    ///
    /// @param index The column index.
    /// @param width The width in pixels.
    public void setWidth(final int index, final int width) {
        final Entry<C> entry = entries.get(index);
        final int newWidth = Math.max(1, width);
        if (entry.width != newWidth) {
            entry.width = newWidth;
            widthVersion++;
        }
    }

    /// Set a column's width as the result of a user resize. The width is never set below the
    /// column's minimum width (R-CR7).
    ///
    /// @param index The column index.
    /// @param width The requested width in pixels.
    /// @return The width actually applied.
    public int resize(final int index, final int width) {
        final int newWidth = clampToMinWidth(index, width);
        setWidth(index, newWidth);
        return newWidth;
    }

    /// @param index The column index.
    /// @param width A requested width in pixels.
    /// @return The width, raised to the column's minimum width if it is below it.
    public int clampToMinWidth(final int index, final int width) {
        return Math.max(entries.get(index).sizing.getMinWidth(), width);
    }

    /// @param column The column object.
    /// @return The index of the column, or `-1` if it is not in the model.
    public int indexOf(final C column) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).column.equals(column)) {
                return i;
            }
        }
        return -1;
    }

    /// @return The number of columns.
    public int size() {
        return entries.size();
    }

    /// @param index The column index.
    /// @return The column object.
    public C getColumn(final int index) {
        return entries.get(index).column;
    }

    /// @param index The column index.
    /// @return The column's sizing settings.
    public ColumnSizing getSizing(final int index) {
        return entries.get(index).sizing;
    }

    /// @param index The column index.
    /// @return The column's current width in pixels.
    public int getWidth(final int index) {
        return entries.get(index).width;
    }

    /// @return The current widths of all columns in pixels.
    public int[] getWidths() {
        final int[] widths = new int[entries.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = entries.get(i).width;
        }
        return widths;
    }

    /// @return The sum of all column widths in pixels. This is the table width.
    public int getTotalWidth() {
        int total = 0;
        for (final Entry<C> entry : entries) {
            total += entry.width;
        }
        return total;
    }

    /// @param index The column index.
    /// @return The distance in pixels from the left edge of the first column to the left edge of
    /// this column.
    public int getLeft(final int index) {
        int left = 0;
        for (int i = 0; i < index; i++) {
            left += entries.get(i).width;
        }
        return left;
    }

    /// @return A CSS `grid-template-columns` value for the current widths, e.g. `"22px 200px"`.
    public String toGridTemplate() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Math.max(1, entries.get(i).width)).append("px");
        }
        return sb.toString();
    }

    /// @return A number that changes whenever a column is added, removed or moved. A renderer
    /// compares it to decide whether to rebuild the header and rows.
    public int getStructureVersion() {
        return structureVersion;
    }

    /// @return A number that changes whenever any column width changes, including as a result of a
    /// structure change.
    public int getWidthVersion() {
        return widthVersion;
    }

    private List<ColumnSizing> getSizings() {
        final List<ColumnSizing> sizings = new ArrayList<>(entries.size());
        for (final Entry<C> entry : entries) {
            sizings.add(entry.sizing);
        }
        return sizings;
    }


    // --------------------------------------------------------------------------------


    private static final class Entry<C> {

        private final C column;
        private final ColumnSizing sizing;
        private int width;

        private Entry(final C column, final ColumnSizing sizing, final int width) {
            this.column = column;
            this.sizing = sizing;
            this.width = width;
        }
    }
}
