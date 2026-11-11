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

package stroom.widget.util.client;

import java.util.Collection;
import java.util.List;

/// What a list held in the client selects after its rows change: after a removal, the row now in
/// the removed row's place, so the user can go on removing (UI-GUIDE.md, "What a list selects").
///
/// A list fetched from the server does the same with `DataGrid.selectNextAfterRemove`.
public final class ListSelection {

    private ListSelection() {
        // Static helpers only
    }

    /// The row to select once rows have been removed from a list.
    ///
    /// @param before  The rows before the removal.
    /// @param removed The rows removed.
    /// @param after   The rows after the removal.
    /// @param <T>     The row type.
    /// @return The row now at the first removed row's index, else the last row; `null` if none is
    /// left or no removed row was listed.
    public static <T> T nextAfterRemove(final List<? extends T> before,
                                        final Collection<? extends T> removed,
                                        final List<? extends T> after) {
        int first = -1;
        for (int i = 0; i < before.size() && first == -1; i++) {
            if (removed.contains(before.get(i))) {
                first = i;
            }
        }
        return rowAt(after, first);
    }

    /// @param rows  The rows.
    /// @param index An index, which may be past the last row.
    /// @param <T>   The row type.
    /// @return The row at the index, else the last row; `null` if there are none or the index is
    /// negative.
    public static <T> T rowAt(final List<? extends T> rows, final int index) {
        if (index < 0 || rows.isEmpty()) {
            return null;
        }
        return rows.get(Math.min(index, rows.size() - 1));
    }
}
