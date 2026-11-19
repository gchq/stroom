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

import java.util.List;

/// Works out the starting pixel width of every column (design requirement R-C4).
///
/// The order is:
///
/// 1. Fixed columns use their declared width.
/// 2. Percent columns become `(int) (outerWidth × percent / 100)`.
/// 3. Fill columns start at their declared floor and share whatever space is left, by weight.
///
/// Fill only ever grows a column. If the fixed and percent columns already overflow the grid, fill
/// columns keep their floor and the grid scrolls horizontally. Shares are truncated rather than
/// rounded, as GWT's `MyDataGrid.resizeColumnsAndFill` does.
public final class ColumnWidthResolver {

    private ColumnWidthResolver() {
        // Utility class.
    }

    /// Resolve the starting width of every column.
    ///
    /// @param sizings    The column sizings in display order.
    /// @param outerWidth The grid's visible width in pixels. Must be greater than zero.
    /// @return The resolved widths in pixels, one per column, in the same order.
    public static int[] resolve(final List<ColumnSizing> sizings, final int outerWidth) {
        if (outerWidth <= 0) {
            throw new IllegalArgumentException("outerWidth must be greater than zero: " + outerWidth);
        }

        final int[] widths = new int[sizings.size()];
        for (int i = 0; i < sizings.size(); i++) {
            widths[i] = startingWidth(sizings.get(i), outerWidth);
        }
        fill(sizings, widths, outerWidth);
        return widths;
    }

    /// The starting width of one column, ignoring any fill share.
    ///
    /// @param sizing     The column's sizing.
    /// @param outerWidth The grid's visible width in pixels.
    /// @return The width in pixels.
    public static int startingWidth(final ColumnSizing sizing, final int outerWidth) {
        if (sizing.getType() == ColumnWidthType.PERCENT) {
            return (int) (Math.max(0, outerWidth) * sizing.getPercent() / 100D);
        }
        return sizing.getWidth();
    }

    /// Grow the fill columns in `widths` so that the columns take up any space left in
    /// `outerWidth`. Does nothing if there is no space left or no fill column.
    ///
    /// @param sizings    The column sizings in display order.
    /// @param widths     The current widths, one per column. Updated in place.
    /// @param outerWidth The grid's visible width in pixels.
    /// @return `true` if any width changed.
    public static boolean fill(final List<ColumnSizing> sizings, final int[] widths, final int outerWidth) {
        int totalWidth = 0;
        double totalWeight = 0;
        for (int i = 0; i < widths.length; i++) {
            totalWidth += widths[i];
            final ColumnSizing sizing = sizings.get(i);
            if (sizing.isFill()) {
                totalWeight += sizing.getFillWeight();
            }
        }

        final int remaining = outerWidth - totalWidth;
        if (remaining <= 0 || totalWeight <= 0) {
            return false;
        }

        final double delta = remaining / totalWeight;
        boolean changed = false;
        for (int i = 0; i < widths.length; i++) {
            final ColumnSizing sizing = sizings.get(i);
            if (sizing.isFill()) {
                final int share = (int) (delta * sizing.getFillWeight());
                if (share > 0) {
                    widths[i] += share;
                    changed = true;
                }
            }
        }
        return changed;
    }

    /// Take up to `amount` pixels back from the fill columns in `widths`, from the width they were
    /// given above their floor, sharing it by weight (any left over comes from the last first).
    /// The opposite of [#fill], for a column that has grown into the space they filled.
    ///
    /// @param sizings The column sizings in display order.
    /// @param widths  The current widths, one per column. Updated in place.
    /// @param amount  The most to take back, in pixels.
    /// @return The number of pixels taken back.
    public static int unfill(final List<ColumnSizing> sizings, final int[] widths, final int amount) {
        double totalWeight = 0;
        for (final ColumnSizing sizing : sizings) {
            if (sizing.isFill()) {
                totalWeight += sizing.getFillWeight();
            }
        }
        if (amount <= 0 || totalWeight <= 0) {
            return 0;
        }

        int left = amount;
        for (int i = 0; i < widths.length && left > 0; i++) {
            final ColumnSizing sizing = sizings.get(i);
            if (sizing.isFill()) {
                final int share = (int) Math.ceil(amount * sizing.getFillWeight() / totalWeight);
                final int taken = Math.min(Math.min(share, left), spareAboveFloor(sizing, widths[i]));
                widths[i] -= taken;
                left -= taken;
            }
        }
        for (int i = widths.length - 1; i >= 0 && left > 0; i--) {
            final ColumnSizing sizing = sizings.get(i);
            if (sizing.isFill()) {
                final int taken = Math.min(left, spareAboveFloor(sizing, widths[i]));
                widths[i] -= taken;
                left -= taken;
            }
        }
        return amount - left;
    }

    private static int spareAboveFloor(final ColumnSizing sizing, final int width) {
        return Math.max(0, width - sizing.getWidth());
    }
}
