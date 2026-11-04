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

package stroom.widget.datagrid.client;

import stroom.cell.client.ActionCell;
import stroom.cell.client.ActionMenuCell;
import stroom.cell.client.InfoColumn;
import stroom.cell.expander.client.ExpanderCell;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.util.shared.NullSafe;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.user.cellview.client.Column;

/// Names for columns whose headers show no text, e.g. a column of icons or tick boxes, which
/// [GridHtml] gives screen readers as visually hidden text in the header, so that every column
/// header has a name (axe's `empty-table-header` rule).
final class ColumnNames {

    /// A column of tick boxes that choose rows.
    static final String SELECT = "Select";
    /// A column of expanders, which open and close rows in a tree.
    static final String EXPAND = "Expand";
    /// A column of info icons.
    static final String INFO = "Info";
    /// A column of action menu buttons.
    static final String ACTIONS = "Actions";

    private ColumnNames() {
        // Static utility
    }

    /// The name a column's kind gives it, from its cell (or the column itself, for an info column):
    /// a button cell's ([ActionCell]) is what its button does.
    ///
    /// @param column The column.
    /// @return The name, or null for a kind of column that has none.
    static String forKind(final Column<?, ?> column) {
        if (column instanceof InfoColumn) {
            return INFO;
        }
        final Cell<?> cell = column.getCell();
        if (cell instanceof TickBoxCell) {
            return SELECT;
        } else if (cell instanceof ExpanderCell) {
            return EXPAND;
        } else if (cell instanceof ActionMenuCell) {
            return ACTIONS;
        } else if (cell instanceof final ActionCell<?> actionCell) {
            return actionCell.getColumnName();
        }
        return null;
    }

    /// The hidden name to add to a column's header, if any.
    ///
    /// @param headerText The text the header has (including any hidden name it was given,
    ///                   `DataGrid.hiddenHeader`).
    /// @param kindName   The name the column's kind gives it ([#forKind]), or null.
    /// @return Null if the header has text of its own, otherwise the kind's name, or null if there is
    ///         none.
    static String hiddenName(final String headerText, final String kindName) {
        if (NullSafe.isNonBlankString(headerText) || NullSafe.isBlankString(kindName)) {
            return null;
        }
        return kindName;
    }
}
