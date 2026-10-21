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

import stroom.widget.util.client.Selection;
import stroom.widget.util.client.SelectionType;

import java.util.List;
import java.util.function.Predicate;

/// The row selection and keyboard movement rules (design requirements R-S3 and R-S8).
///
/// These replicate `DataGridSelectionEventManager.doSelect` and
/// `AbstractSelectionEventManager.move` without any dependency on a grid widget, so they can be
/// unit tested. Whether multi-select is allowed comes from the selection type, which the selection
/// model's owner sets, as it does today.
public final class SelectionRules {

    /// The number of rows Page Down moves by.
    public static final int PAGE_SIZE = 10;

    private SelectionRules() {
        // Utility class.
    }

    /// Apply a click (or Space) on a row to a selection.
    ///
    /// @param selection The selection to update in place.
    /// @param rows      The rows on the current page, in display order.
    /// @param anchor    The current range anchor, or `null` if there is none.
    /// @param row       The row clicked, or `null` to clear the selection.
    /// @param type      The modifier keys and whether multi-select is allowed.
    /// @param <T>       The row type.
    /// @return The new range anchor.
    public static <T> T select(final Selection<T> selection,
                               final List<T> rows,
                               final T anchor,
                               final T row,
                               final SelectionType type) {
        if (row == null) {
            selection.clear();
            return null;
        }

        if (type.isAllowMultiSelect() && type.isShiftPressed() && anchor != null) {
            // Without Ctrl, Shift starts a new range, so clear first.
            if (!type.isControlPressed()) {
                selection.clear();
            }

            final int index1 = rows.indexOf(anchor);
            final int index2 = rows.indexOf(row);
            if (index1 != -1 && index2 != -1) {
                final int start = Math.min(index1, index2);
                final int end = Math.max(index1, index2);
                for (int i = start; i <= end; i++) {
                    selection.setSelected(rows.get(i), true);
                }
                return anchor;
            } else if (type.isControlPressed()) {
                selection.setSelected(row, !selection.isSelected(row));
                return row;
            } else {
                selection.setSelected(row);
                return row;
            }
        } else if (type.isAllowMultiSelect() && type.isControlPressed()) {
            selection.setSelected(row, !selection.isSelected(row));
            return row;
        } else {
            selection.setSelected(row);
            return row;
        }
    }

    /// Whether a key or press counts as one with Ctrl held (design deviation D3): Ctrl, or on macOS
    /// Cmd, which does there what Ctrl does elsewhere.
    ///
    /// @param ctrlKey Whether Ctrl is held.
    /// @param metaKey Whether the meta key (Cmd on macOS, Windows elsewhere) is held.
    /// @param mac     Whether the browser is on macOS.
    /// @return Whether to act as for Ctrl.
    public static boolean isCtrl(final boolean ctrlKey, final boolean metaKey, final boolean mac) {
        return ctrlKey || (mac && metaKey);
    }

    /// Work out where a keyboard move lands, skipping rows that cannot be selected.
    ///
    /// @param rows       The rows on the current page, in display order.
    /// @param currentRow The current keyboard row, or `-1` if there is none.
    /// @param start      The first row to try.
    /// @param delta      `1` to move down, `-1` to move up.
    /// @param page       How many selectable rows to move past. `1` for an arrow key.
    /// @param selectable Whether a row can be selected.
    /// @param <T>        The row type.
    /// @return The new keyboard row. Equal to `currentRow` if no selectable row was found.
    public static <T> int move(final List<T> rows,
                               final int currentRow,
                               final int start,
                               final int delta,
                               final int page,
                               final Predicate<T> selectable) {
        if (rows.isEmpty()) {
            return currentRow;
        }

        int row = currentRow;
        int pos = Math.max(start, 0);
        pos = Math.min(pos, rows.size() - 1);

        for (int i = 0; i < page; i++) {
            for (; pos >= 0 && pos < rows.size(); pos += delta) {
                if (selectable.test(rows.get(pos))) {
                    row = pos;
                    break;
                }
            }
        }
        return row;
    }
}
