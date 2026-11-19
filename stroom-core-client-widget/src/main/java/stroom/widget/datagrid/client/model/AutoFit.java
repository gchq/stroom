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

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

/// The rules for sizing a column to fit its content when its resize handle is double-clicked
/// (design R-CR4 and D5).
///
/// The cells measured come from the row data, not the DOM, so a virtual grid, which only has the
/// rows in view in the DOM, is measured the same way as a natural one. A long page is sampled.
public final class AutoFit {

    /// The most rows measured. A page with more rows than this is sampled.
    public static final int MAX_SAMPLE = 1000;

    private AutoFit() {
        // Utility class.
    }

    /// Choose the rows to measure.
    ///
    /// @param rowCount The number of rows on the page.
    /// @param max      The most rows to choose by sampling.
    /// @param inView   Rows that are always measured if they are on the page, i.e. the ones the user
    ///                 can see.
    /// @return The page-relative indices of the rows to measure, in ascending order. Every row if
    /// there are no more than `max`; otherwise `max` rows spread evenly from the first to the last,
    /// plus the rows in view.
    public static int[] sampleRows(final int rowCount, final int max, final Collection<Integer> inView) {
        final SortedSet<Integer> rows = new TreeSet<>();
        if (rowCount <= max) {
            for (int i = 0; i < rowCount; i++) {
                rows.add(i);
            }
        } else if (max > 0) {
            if (max == 1) {
                rows.add(0);
            } else {
                final double step = (rowCount - 1) / (double) (max - 1);
                for (int i = 0; i < max; i++) {
                    rows.add((int) Math.round(i * step));
                }
            }
            for (final Integer row : inView) {
                if (row != null && row >= 0 && row < rowCount) {
                    rows.add(row);
                }
            }
        }
        final int[] result = new int[rows.size()];
        int i = 0;
        for (final Integer row : rows) {
            result[i++] = row;
        }
        return result;
    }

    /// @param measuredWidth The widest content measured, header or cell, including cell padding.
    /// @param minWidth      The column's minimum width.
    /// @return The column width that fits it: one pixel is added to allow for rounding, as
    /// `MyDataGrid` does, and the result is never below the minimum width.
    public static int width(final double measuredWidth, final int minWidth) {
        return Math.max(minWidth, (int) (measuredWidth + 1));
    }
}
