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

/// Works out the new sort list when a sortable column header is clicked (design requirement R-H1).
///
/// This replicates GWT's `AbstractCellTable.onBrowserEvent2` sort logic:
///
/// * Each column cycles unsorted → default direction → reversed → unsorted.
/// * A plain click replaces the whole sort list.
/// * A Ctrl click keeps the other sorted columns. A column it reverses moves to the end of the list.
///
/// The grid only fires the resulting sort list. Ordering the rows is the data model's job (R-H4a).
public final class SortCycle {

    private SortCycle() {
        // Utility class.
    }

    /// Work out the sort list after a header click.
    ///
    /// @param current          The current sort list, primary sort first.
    /// @param column           The clicked column.
    /// @param defaultAscending The clicked column's default direction.
    /// @param ctrl             Whether Ctrl was held.
    /// @param <C>              The type of the column objects.
    /// @return The new sort list, primary sort first.
    public static <C> List<SortEntry<C>> next(final List<SortEntry<C>> current,
                                              final C column,
                                              final boolean defaultAscending,
                                              final boolean ctrl) {
        final List<SortEntry<C>> list = new ArrayList<>(current);
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getColumn().equals(column)) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            final SortEntry<C> currentEntry = list.get(index);
            if (ctrl) {
                list.remove(index);
            } else {
                list.clear();
            }
            // Only a column still in its default direction is reversed. Otherwise it drops out.
            if (currentEntry.isAscending() == defaultAscending) {
                list.add(new SortEntry<>(column, !defaultAscending));
            }
        } else {
            if (!ctrl) {
                list.clear();
            }
            list.add(new SortEntry<>(column, defaultAscending));
        }
        return list;
    }

    /// Apply a screen's default sort when a click has left the sort list empty (decision D10,
    /// requirement R-H4b).
    ///
    /// @param next        The sort list after the click, from [#next].
    /// @param defaultSort The screen's default sort list. May be empty.
    /// @param <C>         The type of the column objects.
    /// @return `next` unless it is empty, in which case a copy of `defaultSort`.
    public static <C> List<SortEntry<C>> orDefault(final List<SortEntry<C>> next,
                                                   final List<SortEntry<C>> defaultSort) {
        if (next.isEmpty()) {
            return new ArrayList<>(defaultSort);
        }
        return next;
    }

    /// The 1-based position of a column in the sort list, shown in the header's sort icon (R-H3).
    ///
    /// @param sortList The sort list, primary sort first.
    /// @param column   The column.
    /// @param <C>      The type of the column objects.
    /// @return The position starting at `1`, or `0` if the column is not sorted.
    public static <C> int sortOrder(final List<SortEntry<C>> sortList, final C column) {
        for (int i = 0; i < sortList.size(); i++) {
            if (sortList.get(i).getColumn().equals(column)) {
                return i + 1;
            }
        }
        return 0;
    }
}
