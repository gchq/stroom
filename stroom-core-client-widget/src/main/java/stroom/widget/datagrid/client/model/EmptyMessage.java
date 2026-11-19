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
/// rows at all (an exact count of none), never while it is still loading: not while a page is
/// fetched, and not after the filter changes until the rows for it arrive. It doesn't show while
/// another message covers the grid, e.g. its errors.
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
    // The filter changed and the rows for it haven't arrived, so the rows held are for the old one
    private boolean awaitingRows;
    private boolean covered;

    /// Makes the default message.
    public EmptyMessage() {
    }

    /// @param text The screen's own message, or `null` for none at all.
    public void setOwn(final String text) {
        own = true;
        ownText = text;
    }

    /// Says whether a filter is set on the rows. A change hides the message until the rows for it
    /// arrive ([#rowsArrived()]), as those held were filtered by the old one.
    ///
    /// @param filtered Whether a filter is set on the rows.
    public void setFiltered(final boolean filtered) {
        if (this.filtered != filtered) {
            this.filtered = filtered;
            awaitingRows = true;
        }
    }

    /// Says that rows, or a count of them, have arrived, e.g. for a changed filter.
    ///
    /// @return Whether it was waiting for them, so whether the message may now show.
    public boolean rowsArrived() {
        final boolean wasAwaiting = awaitingRows;
        awaitingRows = false;
        return wasAwaiting;
    }

    /// @param covered Whether another message covers the grid, e.g. its errors, so that this one
    ///                doesn't show under it.
    public void setCovered(final boolean covered) {
        this.covered = covered;
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

    /// @param rowDataSize   The number of rows held for the page.
    /// @param rowCount      The number of rows in all.
    /// @param rowCountExact Whether the row count is known, rather than still loading.
    /// @param cells         Whether the grid is a table (has cells), rather than a list or tree.
    /// @return Whether to show the message ([#getText(boolean)]).
    public boolean isShown(final int rowDataSize,
                           final int rowCount,
                           final boolean rowCountExact,
                           final boolean cells) {
        // The rows held may be none while a page is fetched, with the count still that of them all
        return rowDataSize == 0
               && rowCount == 0
               && rowCountExact
               && !awaitingRows
               && !covered
               && getText(cells) != null;
    }
}
