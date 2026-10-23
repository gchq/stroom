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

/// What a [DataGridWidget] is to assistive technology, and so how it is drawn and which keys it has
/// (`DATA_GRID_DESIGN.md` §11). The rows, their rendering, paging, selection and focus are the same
/// for every profile; a list is not told as a grid of one column.
public enum DataGridProfile {

    /// A grid of rows and cells (`grid`, or `treegrid` with an expander column), with a header: the
    /// keys move into a row's cells and up into the headers.
    GRID(null, "row", "gridcell", true),

    /// A list (`listbox`) of rows (`option`s), with no header. The row is the only stop: there are no
    /// cells to move into (a cell is drawing, with no role), and Right and Left do nothing (unless
    /// the selection manager changes page with them). Typing goes to the next row starting with what
    /// is typed.
    LISTBOX("listbox", "option", null, false),

    /// A tree (`tree`) of rows (`treeitem`s, each saying its level, whether it is expanded, and its
    /// place among its siblings), with no header and no cells to move into. Its rows come from a
    /// function (see [DataGridWidget#setTreeRows]); Right and Left expand and collapse, or move to
    /// the first child and the parent, and `*` expands every sibling. Typing goes to the next row
    /// starting with what is typed.
    TREE("tree", "treeitem", null, false);

    private final String role;
    private final String rowRole;
    private final String cellRole;
    private final boolean cells;

    DataGridProfile(final String role, final String rowRole, final String cellRole, final boolean cells) {
        this.role = role;
        this.rowRole = rowRole;
        this.cellRole = cellRole;
        this.cells = cells;
    }

    /// @return The grid's role, or null for a grid's own (`grid` or `treegrid`, by its columns).
    String getRole() {
        return role;
    }

    /// @return A row's role.
    String getRowRole() {
        return rowRole;
    }

    /// @return A cell's role, or null for none (a cell that is only drawing).
    String getCellRole() {
        return cellRole;
    }

    /// @return Whether the keys move into a row's cells and the header.
    boolean hasCells() {
        return cells;
    }
}
