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

/// The range of rows a virtual grid renders for a given scroll position (design section 4.4).
///
/// Every row is the same fixed height, so the range is pure arithmetic. A buffer of rows is
/// rendered above and below the visible rows to reduce pop-in while scrolling.
public final class VirtualWindow {

    /// Rows rendered above and below the visible rows.
    public static final int DEFAULT_BUFFER_ROWS = 5;

    private final int start;
    private final int end;
    private final double rowHeight;
    private final int rowCount;

    private VirtualWindow(final int start, final int end, final double rowHeight, final int rowCount) {
        this.start = start;
        this.end = end;
        this.rowHeight = rowHeight;
        this.rowCount = rowCount;
    }

    /// Work out the rows to render.
    ///
    /// @param scrollTop      The scroller's vertical scroll offset in pixels, measured from the top
    ///                       of the first row.
    /// @param viewportHeight The height in pixels of the area rows are visible in, excluding the
    ///                       header.
    /// @param rowHeight      The fixed row height in pixels, which may be fractional. Must be
    ///                       greater than zero.
    /// @param rowCount       The number of rows on the page.
    /// @param bufferRows     Extra rows to render above and below the visible rows.
    /// @return The window.
    public static VirtualWindow compute(final double scrollTop,
                                        final double viewportHeight,
                                        final double rowHeight,
                                        final int rowCount,
                                        final int bufferRows) {
        if (rowHeight <= 0) {
            throw new IllegalArgumentException("rowHeight must be greater than zero: " + rowHeight);
        }
        if (rowCount <= 0) {
            return new VirtualWindow(0, 0, rowHeight, 0);
        }

        final double maxScrollTop = Math.max(0, rowCount * rowHeight - viewportHeight);
        final double top = Math.max(0, Math.min(scrollTop, maxScrollTop));
        final int first = (int) Math.floor(top / rowHeight);
        final int visible = (int) Math.ceil(Math.max(viewportHeight, 1) / rowHeight) + 1;

        final int start = Math.max(0, first - bufferRows);
        final int end = Math.min(rowCount, first + visible + bufferRows);
        return new VirtualWindow(start, end, rowHeight, rowCount);
    }

    /// @return The index of the first row to render.
    public int getStart() {
        return start;
    }

    /// @return The index after the last row to render.
    public int getEnd() {
        return end;
    }

    /// @return The number of rows to render.
    public int size() {
        return end - start;
    }

    /// @return The vertical offset in pixels of the first rendered row from the top of the content.
    public double getOffset() {
        return start * rowHeight;
    }

    /// @return The total height in pixels of all rows on the page, i.e. the scroll extent.
    public double getContentHeight() {
        return rowCount * rowHeight;
    }

    /// @param row A row index.
    /// @return Whether the row is in the rendered range.
    public boolean contains(final int row) {
        return row >= start && row < end;
    }

    @Override
    public String toString() {
        return "VirtualWindow{start=" + start + ", end=" + end + '}';
    }
}
