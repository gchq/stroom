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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// How a row update writes a changed row, given where the focus is, so that a control with the
/// focus is never replaced under the person using it (the data grid completion design, item 1).
///
/// A row's cells are compared one by one, each by the HTML it was last written with:
///
/// * The focus isn't in the row's cells: the whole row is written at once, as it always was.
/// * The focus is on a cell's own element (moving between cells): the changed cells are written,
///   that one's content only, so its element and the focus stay.
/// * The focus is inside a cell's content (a field, a spinner, an open selection box): the other
///   changed cells are written and that cell is left as it is, out of date, until the focus leaves
///   it.
/// * The row's cells aren't the same columns as before (a column added or removed, or a span
///   changed), or it has never been written: the whole row is written, and a cell that had the
///   focus is asked to take it back.
///
/// It has no dependency on the grid widget, so it can be unit tested.
public final class RowWritePlan {

    private final boolean wholeRow;
    private final boolean resetFocus;
    private final List<Integer> cells;
    private final int contentOnly;
    private final int leftStale;

    private RowWritePlan(final boolean wholeRow,
                         final boolean resetFocus,
                         final List<Integer> cells,
                         final int contentOnly,
                         final int leftStale) {
        this.wholeRow = wholeRow;
        this.resetFocus = resetFocus;
        this.cells = cells;
        this.contentOnly = contentOnly;
        this.leftStale = leftStale;
    }

    /// Plans how to write a row.
    ///
    /// @param before         The HTML each cell was last written with, in order, or null if the row has
    ///                       never been written (or its HTML was forgotten). A cell whose HTML was
    ///                       forgotten is null.
    /// @param beforeColumns  The column index of each of those cells (a spanned column has none).
    /// @param after          The HTML each cell is to have now, in order.
    /// @param afterColumns   The column index of each of those cells.
    /// @param focusedCell    The position (not the column) in `before` of the cell holding the focus,
    ///                       or -1 if the focus isn't in the row's cells.
    /// @param focusOnCell    Whether the focus is on that cell's own element, rather than inside its
    ///                       content.
    /// @return The plan.
    public static RowWritePlan plan(final List<String> before,
                                    final int[] beforeColumns,
                                    final List<String> after,
                                    final int[] afterColumns,
                                    final int focusedCell,
                                    final boolean focusOnCell) {
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(afterColumns, "afterColumns");
        if (before == null
            || beforeColumns == null
            || !Arrays.equals(beforeColumns, afterColumns)
            || before.size() != after.size()) {
            return whole(focusedCell >= 0);
        }

        final List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < after.size(); i++) {
            if (!Objects.equals(before.get(i), after.get(i))) {
                changed.add(i);
            }
        }
        if (changed.isEmpty()) {
            return new RowWritePlan(false, false, Collections.emptyList(), -1, -1);
        }
        if (focusedCell < 0) {
            return whole(false);
        }

        final List<Integer> cells = new ArrayList<>();
        int contentOnly = -1;
        int leftStale = -1;
        for (final int i : changed) {
            if (i != focusedCell) {
                cells.add(i);
            } else if (focusOnCell) {
                contentOnly = i;
            } else {
                leftStale = i;
            }
        }
        return new RowWritePlan(false, false, Collections.unmodifiableList(cells), contentOnly, leftStale);
    }

    private static RowWritePlan whole(final boolean resetFocus) {
        return new RowWritePlan(true, resetFocus, Collections.emptyList(), -1, -1);
    }

    /// @return Whether the whole row is to be written at once.
    public boolean isWholeRow() {
        return wholeRow;
    }

    /// @return Whether, the whole row having been written, the cell that had the focus is to be
    /// asked to take it back.
    public boolean isResetFocus() {
        return resetFocus;
    }

    /// @return The positions of the cells to write whole, each replacing its element, when the whole
    /// row isn't written.
    public List<Integer> getCells() {
        return cells;
    }

    /// @return The position of the cell whose content only is to be written (its element has the
    /// focus), or -1 for none.
    public int getContentOnly() {
        return contentOnly;
    }

    /// @return The position of the cell left out of date because the focus is inside it, or -1 for
    /// none.
    public int getLeftStale() {
        return leftStale;
    }

    /// @return Whether there is nothing to write.
    public boolean isNothing() {
        return !wholeRow && cells.isEmpty() && contentOnly < 0 && leftStale < 0;
    }

    @Override
    public String toString() {
        return "RowWritePlan{wholeRow=" + wholeRow
               + ", resetFocus=" + resetFocus
               + ", cells=" + cells
               + ", contentOnly=" + contentOnly
               + ", leftStale=" + leftStale
               + '}';
    }
}
