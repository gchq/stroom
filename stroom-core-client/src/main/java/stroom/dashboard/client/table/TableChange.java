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
import stroom.dashboard.shared.TableComponentSettings;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Format;
import stroom.query.api.Sort.SortDirection;

import java.util.List;

/// One change to a dashboard table, made programmatically rather than from its menus or Settings dialog (e.g.
/// one proposed elsewhere that the user chose to apply): a column (group, add, edit, move, hide, show, sort or
/// filter one), a conditional formatting rule, the expression of the classic query that feeds the table, a table
/// or Text widget added beside or below it, a Visualisation linked to it, or a component's name or settings. The
/// table applies its own changes as its column menus would ([#apply(Component, List)]); the dashboard applies the
/// rest.
public final class TableChange {

    public static final String GROUP = "group";
    public static final String UNGROUP = "ungroup";
    public static final String ADD_COLUMN = "add_column";
    public static final String EDIT_COLUMN = "edit_column";
    public static final String HIDE_COLUMN = "hide_column";
    public static final String SHOW_COLUMN = "show_column";
    public static final String SORT = "sort";
    public static final String FILTER = "filter";
    /// Sets the expression of the classic query that feeds the table.
    public static final String SET_EXPRESSION = "set_expression";
    /// Moves a column to a position among the columns the user sees.
    public static final String MOVE_COLUMN = "move_column";
    /// Adds a table beside the table as a tab, fed by the same query.
    public static final String ADD_TABLE = "add_table";
    /// Adds a Text widget below a table, showing the event of its selected row.
    public static final String ADD_TEXT = "add_text";
    /// Adds a Visualisation component linked to a table ([#getTable()], or the proposal's table if null), named
    /// [#getName()], showing the Visualisation [#getDoc()] with the settings [#getValue()] (a JSON object of control
    /// ids and values, or null), placed as [#getPlacement()] says.
    public static final String ADD_VIS = "add_vis";
    /// Changes a Visualisation component's settings ([#getTarget()], [#getTargetId()]): merges the JSON object
    /// [#getValue()] into them (a key whose value is empty is removed, so the control's default applies), and
    /// switches it to the Visualisation [#getDoc()] if that isn't null.
    public static final String SET_VIS_SETTINGS = "set_vis_settings";
    /// Changes an embedded query's StroomQL ([#getTargetId()]) to [#getValue()], and runs it, if it is still
    /// [#getRef()] (the text it was planned against), so a change made since isn't written over.
    public static final String SET_QUERY = "set_query";
    /// Adds a selection handler to a component ([#getTargetId()]): the filter [#getQuery()], whose values name a
    /// chart's selection, e.g. `${component.vis-1.selection.category}`. [#getValue()] says what it filters: a
    /// query's search (`query`, re-run with it) or a table's rows (`table`).
    public static final String ADD_SELECTION_FILTER = "add_selection_filter";
    /// Removes a selection handler from a component ([#getTargetId()]), by its id ([#getFilter()]).
    public static final String REMOVE_SELECTION_FILTER = "remove_selection_filter";
    /// Renames keys of a chart's selection ([#getTargetId()]) in every selection filter its clicks drive, as
    /// [#getValue()] gives them (`old=new` pairs, comma separated), once the chart is converted to another kind.
    public static final String REMAP_SELECTION = "remap_selection";
    /// For add_selection_filter: filters the query's search.
    public static final String QUERY_FILTER = "query";
    /// For add_selection_filter: filters a table's rows.
    public static final String TABLE_FILTER = "table";
    /// For add_vis: as a tab in the pane of a chart already on the dashboard (one linked to the same table if any),
    /// or in a new pane at the bottom of the dashboard if it has none (the default).
    public static final String CHARTS = "charts";
    /// For add_vis: in a new pane at the bottom of the dashboard.
    public static final String BOTTOM = "bottom";
    /// For add_vis: in a new pane below the table.
    public static final String BELOW = "below";
    /// For add_vis: as a tab beside the table.
    public static final String TAB = "tab";
    /// For add_vis, of several charts added together: in a new pane to the right of the one added before it.
    public static final String RIGHT = "right";
    /// For add_vis, of several charts added together: in a new pane below the first of the row before it.
    public static final String UNDER = "under";
    /// For add_vis, of several charts added together: as a tab in the pane of the one added before it.
    public static final String WITH = "with";
    /// Renames a component, and so its tab.
    public static final String RENAME = "rename";
    /// Changes one of a component's settings.
    public static final String SET_SETTING = "set_setting";
    /// Adds a conditional formatting rule to the table.
    public static final String ADD_FORMAT_RULE = "add_format_rule";
    /// Removes one of the table's conditional formatting rules, by its number.
    public static final String REMOVE_FORMAT_RULE = "remove_format_rule";
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
    private final ExpressionOperator query;
    private final String table;
    private final boolean copy;
    private final String target;
    private final String targetId;
    private final String setting;
    private final String value;
    private final String ref;
    private final DocRef doc;
    private final String style;
    private final String formatType;
    private final boolean bold;
    private final boolean italic;
    private final boolean hide;
    private final String placement;

    private TableChange(final Builder builder) {
        this.op = builder.op;
        this.column = builder.column;
        this.level = builder.level;
        this.name = builder.name;
        this.expression = builder.expression;
        this.format = builder.format;
        this.position = builder.position;
        this.direction = builder.direction;
        this.priority = builder.priority;
        this.filter = builder.filter;
        this.query = builder.query;
        this.table = builder.table;
        this.copy = builder.copy;
        this.target = builder.target;
        this.targetId = builder.targetId;
        this.setting = builder.setting;
        this.value = builder.value;
        this.ref = builder.ref;
        this.doc = builder.doc;
        this.style = builder.style;
        this.formatType = builder.formatType;
        this.bold = builder.bold;
        this.italic = builder.italic;
        this.hide = builder.hide;
        this.placement = builder.placement;
    }

    /// @param op What the change does, e.g. [#GROUP].
    /// @return A builder for a change, with only the fields its operation has set.
    public static Builder builder(final String op) {
        return new Builder(op);
    }

    /// @param table A dashboard component.
    /// @return The id of the query that feeds it, if it is a table, or null.
    public static String queryId(final Component table) {
        return table instanceof final TablePresenter tablePresenter
                ? tablePresenter.getTableComponentSettings().getQueryId()
                : null;
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
        return tablePresenter.applyChanges(changes);
    }

    /// @param component A dashboard component.
    /// @return A copy of its settings, if it is a table, to put back with
    /// [#restore(Component, TableComponentSettings)] if a change is undone; otherwise null.
    public static TableComponentSettings snapshot(final Component component) {
        return component instanceof final TablePresenter tablePresenter
                ? tablePresenter.settingsSnapshot()
                : null;
    }

    /// Puts back a table's settings as they were before a change, as an unsaved edit.
    ///
    /// @param component The table.
    /// @param snapshot  Its settings, from [#snapshot(Component)].
    /// @return True if they were put back.
    public static boolean restore(final Component component, final TableComponentSettings snapshot) {
        if (component instanceof final TablePresenter tablePresenter && snapshot != null) {
            tablePresenter.restoreSettings(snapshot);
            return true;
        }
        return false;
    }

    /// @return For set_expression, the query's new expression, or null.
    public ExpressionOperator getQuery() {
        return query;
    }

    /// @return The table it changes, by name, if not the table the proposal is for, or null.
    public String getTable() {
        return table;
    }

    /// @return For add_table, true to start the new table with the proposal table's columns.
    public boolean isCopy() {
        return copy;
    }

    /// @return For rename and set_setting, the component it changes, by name, or null for the proposal's table.
    public String getTarget() {
        return target;
    }

    /// @return For rename and set_setting, the component's id, or null for a table added by these changes.
    public String getTargetId() {
        return targetId;
    }

    /// @return For set_setting, the setting's key.
    public String getSetting() {
        return setting;
    }

    /// @return For set_setting, its value.
    public String getValue() {
        return value;
    }

    /// @return For set_setting of a component, that component's id, or null.
    public String getRef() {
        return ref;
    }

    /// @return For set_setting of a document, the document, or null.
    public DocRef getDoc() {
        return doc;
    }

    /// @return For add_format_rule, the colour, as the rule editor names it, or null.
    public String getStyle() {
        return style;
    }

    /// @return For add_format_rule, what the colour is given to: background or text.
    public String getFormatType() {
        return formatType;
    }

    /// @return For add_format_rule, true to make matching rows bold.
    public boolean isBold() {
        return bold;
    }

    /// @return For add_format_rule, true to make matching rows italic.
    public boolean isItalic() {
        return italic;
    }

    /// @return For add_format_rule, true to hide matching rows.
    public boolean isHide() {
        return hide;
    }

    /// @return For add_vis, where to put the Visualisation: [#BELOW] or [#TAB]; null for below.
    public String getPlacement() {
        return placement;
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


    // --------------------------------------------------------------------------------


    /// Builds a [TableChange], setting only the fields its operation has.
    public static final class Builder {

        private final String op;
        private String column;
        private Integer level;
        private String name;
        private String expression;
        private String format;
        private Integer position;
        private String direction;
        private Integer priority;
        private String filter;
        private ExpressionOperator query;
        private String table;
        private boolean copy;
        private String target;
        private String targetId;
        private String setting;
        private String value;
        private String ref;
        private DocRef doc;
        private String style;
        private String formatType;
        private boolean bold;
        private boolean italic;
        private boolean hide;
        private String placement;

        private Builder(final String op) {
            this.op = op;
        }

        /// @param column The column it changes, by name, or null to add one.
        /// @return This builder.
        public Builder column(final String column) {
            this.column = column;
            return this;
        }

        /// @param level For group, the level to group at, from 1, or null.
        /// @return This builder.
        public Builder level(final Integer level) {
            this.level = level;
            return this;
        }

        /// @param name For add_column, the new column's name; for edit_column, its new name; for add_table, add_text,
        ///             add_vis and rename, the new name.
        /// @return This builder.
        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        /// @param expression For add_column or edit_column, the column's expression, or null.
        /// @return This builder.
        public Builder expression(final String expression) {
            this.expression = expression;
            return this;
        }

        /// @param format For add_column or edit_column, the format as the column menu names it, or null.
        /// @return This builder.
        public Builder format(final String format) {
            this.format = format;
            return this;
        }

        /// @param position For add_column, move_column and add_format_rule, a position from 1; for remove_format_rule,
        ///                 the rule's number.
        /// @return This builder.
        public Builder position(final Integer position) {
            this.position = position;
            return this;
        }

        /// @param direction For sort, ascending, descending or none.
        /// @return This builder.
        public Builder direction(final String direction) {
            this.direction = direction;
            return this;
        }

        /// @param priority For sort, its priority from 1, or null to sort after the table's other sorts.
        /// @return This builder.
        public Builder priority(final Integer priority) {
            this.priority = priority;
            return this;
        }

        /// @param filter For filter, the column's filter text, or empty to clear it.
        /// @return This builder.
        public Builder filter(final String filter) {
            this.filter = filter;
            return this;
        }

        /// @param query For set_expression, the query's new expression; for add_format_rule, the rule's conditions.
        /// @return This builder.
        public Builder query(final ExpressionOperator query) {
            this.query = query;
            return this;
        }

        /// @param table The table it changes (or, for add_text, follows), if not the proposal's: one an earlier
        ///              add_table adds.
        /// @return This builder.
        public Builder table(final String table) {
            this.table = table;
            return this;
        }

        /// @param copy For add_table, true to start the new table with the proposal table's columns.
        /// @return This builder.
        public Builder copy(final boolean copy) {
            this.copy = copy;
            return this;
        }

        /// @param target For rename and set_setting, the component it changes, by name, or null for the proposal's
        ///               table.
        /// @return This builder.
        public Builder target(final String target) {
            this.target = target;
            return this;
        }

        /// @param targetId For rename and set_setting, the component's id, or null for a component added by these
        ///                 changes.
        /// @return This builder.
        public Builder targetId(final String targetId) {
            this.targetId = targetId;
            return this;
        }

        /// @param setting For set_setting, the setting's key.
        /// @return This builder.
        public Builder setting(final String setting) {
            this.setting = setting;
            return this;
        }

        /// @param value For set_setting, its value, as the server checked it. For add_vis and set_vis_settings, the
        ///              Visualisation's settings, as a JSON object of control ids and values.
        /// @return This builder.
        public Builder value(final String value) {
            this.value = value;
            return this;
        }

        /// @param ref For set_setting of a component, that component's id, or null.
        /// @return This builder.
        public Builder ref(final String ref) {
            this.ref = ref;
            return this;
        }

        /// @param doc For set_setting of a document, the document; for add_vis, the Visualisation to show; for
        ///            set_vis_settings, the Visualisation to switch to; or null.
        /// @return This builder.
        public Builder doc(final DocRef doc) {
            this.doc = doc;
            return this;
        }

        /// @param style For add_format_rule, the colour, as the rule editor names it, or null.
        /// @return This builder.
        public Builder style(final String style) {
            this.style = style;
            return this;
        }

        /// @param formatType For add_format_rule, what the colour is given to: background or text.
        /// @return This builder.
        public Builder formatType(final String formatType) {
            this.formatType = formatType;
            return this;
        }

        /// @param bold For add_format_rule, true to make matching rows bold.
        /// @return This builder.
        public Builder bold(final boolean bold) {
            this.bold = bold;
            return this;
        }

        /// @param italic For add_format_rule, true to make matching rows italic.
        /// @return This builder.
        public Builder italic(final boolean italic) {
            this.italic = italic;
            return this;
        }

        /// @param hide For add_format_rule, true to hide matching rows.
        /// @return This builder.
        public Builder hide(final boolean hide) {
            this.hide = hide;
            return this;
        }

        /// @param placement For add_vis, where to put the Visualisation: [#BELOW] (the default, if null) or [#TAB].
        /// @return This builder.
        public Builder placement(final String placement) {
            this.placement = placement;
            return this;
        }

        /// @return The change.
        public TableChange build() {
            return new TableChange(this);
        }
    }
}
