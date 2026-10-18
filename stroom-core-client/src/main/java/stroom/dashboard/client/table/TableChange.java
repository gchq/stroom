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


package stroom.dashboard.client.table;

import stroom.dashboard.client.main.Component;
import stroom.query.api.Format;
import stroom.query.api.Sort.SortDirection;

import java.util.List;

/// One change to a dashboard table that the AI chat proposed and the user chose to apply (see the server's
/// `DashboardChanges`): group or ungroup a column, add, edit, hide or show a column, sort by a column, or filter a
/// column. The table applies them as its column menus would ([#apply(Component, List)]).
public final class TableChange {

    public static final String GROUP = "group";
    public static final String UNGROUP = "ungroup";
    public static final String ADD_COLUMN = "add_column";
    public static final String EDIT_COLUMN = "edit_column";
    public static final String HIDE_COLUMN = "hide_column";
    public static final String SHOW_COLUMN = "show_column";
    public static final String SORT = "sort";
    public static final String FILTER = "filter";
    public static final String ASCENDING = "ascending";
    public static final String DESCENDING = "descending";

    private final String op;
    private final String column;
    private final Integer level;
    private final String name;
    private final String expression;
    private final String format;
    private final Integer position;
    private final String direction;
    private final Integer priority;
    private final String filter;

    /// @param op         What it does, e.g. [#GROUP].
    /// @param column     The column it changes, by name, or null to add one.
    /// @param level      For group, the level to group at, from 1, or null.
    /// @param name       For add_column, the new column's name; for edit_column, its new name, or null.
    /// @param expression For add_column or edit_column, the column's expression, or null.
    /// @param format     For add_column or edit_column, the format as the column menu names it, or null.
    /// @param position   For add_column, where to add it, from 1, or null for the end.
    /// @param direction  For sort, ascending, descending or none.
    /// @param priority   For sort, its priority from 1, or null to sort after the table's other sorts.
    /// @param filter     For filter, the column's filter text, or empty to clear it.
    public TableChange(final String op,
                       final String column,
                       final Integer level,
                       final String name,
                       final String expression,
                       final String format,
                       final Integer position,
                       final String direction,
                       final Integer priority,
                       final String filter) {
        this.op = op;
        this.column = column;
        this.level = level;
        this.name = name;
        this.expression = expression;
        this.format = format;
        this.position = position;
        this.direction = direction;
        this.priority = priority;
        this.filter = filter;
    }

    /// Applies changes to a dashboard component, if it is a table, as its column menus would.
    ///
    /// @param component The component.
    /// @param changes   The changes, in order.
    /// @return Why they couldn't all be applied, or null if they were.
    public static String apply(final Component component, final List<TableChange> changes) {
        if (!(component instanceof final TablePresenter tablePresenter)) {
            return "The component isn't a table.";
        }
        return tablePresenter.applyAiChanges(changes);
    }

    /// @return What it does, e.g. [#GROUP].
    public String getOp() {
        return op;
    }

    /// @return The column it changes, by name, or null.
    public String getColumn() {
        return column;
    }

    /// @return For group, the level from 1, or null.
    public Integer getLevel() {
        return level;
    }

    /// @return The new column's name, or a column's new name, or null.
    public String getName() {
        return name;
    }

    /// @return The column's expression, or null.
    public String getExpression() {
        return expression;
    }

    /// @return The column's format, as its type, or null if none is given or it isn't known.
    public Format getFormat() {
        if (format == null) {
            return null;
        }
        for (final Format.Type type : Format.Type.values()) {
            if (type.getDisplayValue().equalsIgnoreCase(format)) {
                return Format.builder().type(type).build();
            }
        }
        return null;
    }

    /// @return Where to add a column, from 1, or null for the end.
    public Integer getPosition() {
        return position;
    }

    /// @return For sort, the direction, or null to stop sorting by the column.
    public SortDirection getSortDirection() {
        if (ASCENDING.equalsIgnoreCase(direction)) {
            return SortDirection.ASCENDING;
        } else if (DESCENDING.equalsIgnoreCase(direction)) {
            return SortDirection.DESCENDING;
        }
        return null;
    }

    /// @return For sort, its priority from 1, or null.
    public Integer getPriority() {
        return priority;
    }

    /// @return For filter, the filter text, or empty to clear it.
    public String getFilter() {
        return filter;
    }
}
