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

/// What a grid says when it has no rows (`DATA_GRID_DESIGN.md` R-D5), and when it says it.
///
/// Every grid has a message unless its screen turns it off: its screen's own, else "No data" for a
/// table and "No items" for a list or tree. While a filter is set, "No matches" says the rows
/// were filtered out rather than missing. The message shows only once the grid knows it has no
/// rows (an exact count of none), never while it is still loading.
public final class EmptyMessage {

    /// A table's message when its screen gives none.
    public static final String NO_DATA = "No data";
    /// A list's or tree's message when its screen gives none.
    public static final String NO_ITEMS = "No items";
    /// The message while a filter is set.
    public static final String NO_MATCHES = "No matches";

    private boolean own;
    private String ownText;
    private boolean filtered;

    /// Makes the default message.
    public EmptyMessage() {
    }

    /// @param text The screen's own message, or `null` for none at all.
    public void setOwn(final String text) {
        own = true;
        ownText = text;
    }

    /// @param filtered Whether a filter is set on the rows.
    public void setFiltered(final boolean filtered) {
        this.filtered = filtered;
    }

    /// @param cells Whether the grid is a table (has cells), rather than a list or tree.
    /// @return The message, or `null` for none.
    public String getText(final boolean cells) {
        if (own && ownText == null) {
            return null;
        }
        if (filtered) {
            return NO_MATCHES;
        }
        if (own) {
            return ownText;
        }
        return cells
                ? NO_DATA
                : NO_ITEMS;
    }

    /// @param rowDataSize The number of rows on the page.
    /// @param rowCountExact Whether the row count is known, rather than still loading.
    /// @param text The message, or `null` for none.
    /// @return Whether to show the message.
    public static boolean isShown(final int rowDataSize, final boolean rowCountExact, final String text) {
        return rowDataSize == 0 && rowCountExact && text != null;
    }
}
