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

package stroom.widget.xsdbrowser.client;

import stroom.widget.util.client.CssProperties;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// The rows of an XSD node's children, with the tree lines that join them, as a CSS grid of a fixed
/// number of columns (`xsd-browser.css`, `--xsd-browser__node-grid-columns`). It replaced a GWT `Grid` (an HTML
/// table, used only for its layout), and keeps its cells in the page in the same order, a row at a
/// time.
///
/// Every cell of every row is made, empty until given a widget, so that a style set on a row
/// (e.g. a border under it, `xsd-browser__node-grid-cell--separator`) runs the width of the grid.
class XsdNodeGrid extends Composite {

    private static final String GRID = "xsd-browser__node-grid";
    private static final String CELL = GRID + "-cell";

    private final FlowPanel panel = new FlowPanel();
    private final int columnCount;
    private final List<List<SimplePanel>> rows = new ArrayList<>();

    /// @param columnCount The number of columns, which every row has.
    XsdNodeGrid(final int columnCount) {
        this.columnCount = columnCount;
        panel.setStyleName(GRID);
        CssProperties.set(panel.getElement(), "--xsd-browser__node-grid-columns", String.valueOf(columnCount));
        initWidget(panel);
    }

    /// @return The number of rows.
    int getRowCount() {
        return rows.size();
    }

    /// @return The number of columns.
    int getColumnCount() {
        return columnCount;
    }

    /// Adds empty rows until there are at least this many.
    ///
    /// @param rowCount The number of rows wanted.
    void ensureRows(final int rowCount) {
        while (rows.size() < rowCount) {
            final List<SimplePanel> row = new ArrayList<>(columnCount);
            for (int col = 0; col < columnCount; col++) {
                final SimplePanel cell = new SimplePanel();
                cell.setStyleName(CELL);
                panel.add(cell);
                row.add(cell);
            }
            rows.add(row);
        }
    }

    /// Puts a widget in a cell, in place of any it had.
    ///
    /// @param row    The cell's row, which must exist ([#ensureRows]).
    /// @param col    The cell's column.
    /// @param widget The widget.
    void setWidget(final int row, final int col, final Widget widget) {
        rows.get(row).get(col).setWidget(widget);
    }

    /// Adds a class to a cell.
    ///
    /// @param row       The cell's row.
    /// @param col       The cell's column.
    /// @param styleName The class, e.g. `xsd-browser__node-grid-cell--start`.
    void addCellStyleName(final int row, final int col, final String styleName) {
        rows.get(row).get(col).addStyleName(styleName);
    }

    /// Adds a class to every cell of a row.
    ///
    /// @param row       The row.
    /// @param styleName The class, e.g. `xsd-browser__node-grid-cell--separator`.
    void addRowStyleName(final int row, final String styleName) {
        for (int col = 0; col < columnCount; col++) {
            addCellStyleName(row, col, styleName);
        }
    }
}
