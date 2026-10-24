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

import stroom.cell.expander.client.ExpanderCell;
import stroom.util.shared.Expander;
import stroom.widget.datagrid.client.model.TreePositions;

import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.cellview.client.Column;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/// A grid's tree rows: a grid with a column of expanders (an [ExpanderCell]'s) is a treegrid. Its
/// rows say their level and whether they are expanded (`aria-level`, `aria-expanded`), and the keys
/// expand and collapse them ([DataGridKeyboard]) through the expander column's own field updater,
/// as a click on an expander does, so a screen needs nothing more than its expander column. A tree
/// (see [DataGridProfile#TREE]) has its rows' expanders from a function instead, and says each
/// row's place among its siblings (`aria-posinset`, `aria-setsize`).
///
/// @param <R> The type of the grid's rows.
final class TreeRows<R> {

    private final DataGrid<R> grid;
    // The column of expanders, or -1 if the grid isn't a tree
    private int column = -1;
    // A tree's rows' expanders and what expands or collapses one, from the screen, or null
    private Function<R, Expander> source;
    private BiConsumer<R, Boolean> expandAction;
    // Each row's place among its siblings and how many there are, for the rows last drawn
    private TreePositions positions = TreePositions.of(new int[0]);

    TreeRows(final DataGrid<R> grid) {
        this.grid = grid;
    }

    /// Finds the expander column again, e.g. after the columns changed.
    ///
    /// @return Whether the grid is a tree (has an expander column).
    boolean update() {
        column = -1;
        if (source != null) {
            return true;
        }
        for (int i = 0; i < grid.getColumnCount(); i++) {
            if (grid.getColumn(i).getCell() instanceof ExpanderCell) {
                column = i;
                break;
            }
        }
        return column >= 0;
    }

    /// Takes a tree's rows from a function rather than an expander column.
    ///
    /// @param source       Gives a row's expander (its depth, and whether it is a leaf or expanded).
    /// @param expandAction Expands (true) or collapses (false) a row, as clicking its expander does.
    void setSource(final Function<R, Expander> source, final BiConsumer<R, Boolean> expandAction) {
        this.source = source;
        this.expandAction = expandAction;
    }

    /// Works out each row's place among its siblings (its parent's children, or the top level's
    /// rows) and how many siblings there are, for the rows about to be drawn.
    ///
    /// @param rows The page's rows.
    void placeRows(final List<R> rows) {
        final int[] depths = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            final Expander expander = expanderOf(rows.get(i));
            depths[i] = expander == null
                    ? 0
                    : expander.getDepth();
        }
        positions = TreePositions.of(depths);
    }

    private Expander expanderOf(final R row) {
        if (source != null) {
            return row == null
                    ? null
                    : source.apply(row);
        }
        if (column < 0 || column >= grid.getColumnCount() || row == null) {
            return null;
        }
        final Object value = grid.getColumn(column).getValue(row);
        return value instanceof final Expander expander
                ? expander
                : null;
    }

    /// Says a row's level and whether it is expanded, or nothing if the grid isn't a tree.
    ///
    /// @param rowElement The row's element.
    /// @param row        The row.
    void setAttributes(final Element rowElement, final R row, final int indexOnPage) {
        final Expander expander = expanderOf(row);
        if (source != null && positions.getSize(indexOnPage) > 0) {
            rowElement.setAttribute("aria-posinset", String.valueOf(positions.getPosition(indexOnPage)));
            rowElement.setAttribute("aria-setsize", String.valueOf(positions.getSize(indexOnPage)));
        }
        if (expander == null) {
            rowElement.removeAttribute("aria-level");
            rowElement.removeAttribute("aria-expanded");
        } else {
            rowElement.setAttribute("aria-level", String.valueOf(expander.getDepth() + 1));
            if (expander.isLeaf()) {
                rowElement.removeAttribute("aria-expanded");
            } else {
                rowElement.setAttribute("aria-expanded", String.valueOf(expander.isExpanded()));
            }
        }
    }

    /// @param indexOnPage The page-relative row index.
    /// @return The row's expander, or `null` if the grid isn't a tree or there is no such row.
    Expander getExpander(final int indexOnPage) {
        if (indexOnPage < 0 || indexOnPage >= grid.getVisibleItemCount()) {
            return null;
        }
        return expanderOf(grid.getVisibleItem(indexOnPage));
    }

    /// Expand or collapse a row, as a click on its expander does.
    ///
    /// @param indexOnPage The page-relative row index.
    /// @param expanded    Whether to expand it.
    /// @return `true` if the row was changed; `false` if it has no children, is already as asked,
    /// or the grid isn't a tree.
    boolean setExpanded(final int indexOnPage, final boolean expanded) {
        final Expander expander = getExpander(indexOnPage);
        if (expander == null || expander.isLeaf() || expander.isExpanded() == expanded) {
            return false;
        }
        if (source != null) {
            expandAction.accept(grid.getVisibleItem(indexOnPage), expanded);
            return true;
        }
        @SuppressWarnings("unchecked") final Column<R, Expander> expanderColumn =
                (Column<R, Expander>) grid.getColumn(column);
        final FieldUpdater<R, Expander> updater = expanderColumn.getFieldUpdater();
        if (updater == null) {
            return false;
        }
        // The expander column's updater toggles the row, as a click on the expander does
        updater.update(grid.getPageStart() + indexOnPage, grid.getVisibleItem(indexOnPage), expander);
        return true;
    }

    /// @param indexOnPage The page-relative row index.
    /// @return The page-relative index of the row's first child, or -1 if it has none shown (it is a
    /// leaf, or isn't expanded, or its children aren't loaded yet).
    int getFirstChildRow(final int indexOnPage) {
        final Expander expander = getExpander(indexOnPage);
        final Expander next = getExpander(indexOnPage + 1);
        return expander != null && next != null && next.getDepth() == expander.getDepth() + 1
                ? indexOnPage + 1
                : -1;
    }

    /// Expands every collapsed sibling of a row, and the row, as `*` does in a tree.
    ///
    /// @param indexOnPage The page-relative row index.
    void expandSiblings(final int indexOnPage) {
        final Expander expander = getExpander(indexOnPage);
        if (expander == null) {
            return;
        }
        final int parent = getParentRow(indexOnPage);
        final List<R> toExpand = new ArrayList<>();
        for (int i = parent + 1; i < grid.getVisibleItemCount(); i++) {
            final Expander other = getExpander(i);
            if (other == null || other.getDepth() < expander.getDepth()) {
                break;
            }
            if (other.getDepth() == expander.getDepth() && !other.isLeaf() && !other.isExpanded()) {
                toExpand.add(grid.getVisibleItem(i));
            }
        }
        // Collected first: expanding one may draw the rows again
        for (final R row : toExpand) {
            if (source != null) {
                expandAction.accept(row, true);
            } else {
                setExpanded(grid.getVisibleItems().indexOf(row), true);
            }
        }
    }

    /// @param indexOnPage The page-relative row index.
    /// @return The page-relative index of the row's parent (the nearest row above it at a lower
    /// level), or -1 if it has none on the page or the grid isn't a tree.
    int getParentRow(final int indexOnPage) {
        final Expander expander = getExpander(indexOnPage);
        if (expander == null) {
            return -1;
        }
        for (int i = indexOnPage - 1; i >= 0; i--) {
            final Expander above = getExpander(i);
            if (above != null && above.getDepth() < expander.getDepth()) {
                return i;
            }
        }
        return -1;
    }
}
