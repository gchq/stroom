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


package stroom.ai.impl;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// The changes to a dashboard table that the agent can propose, and the user can apply with a button (see
/// `propose_dashboard_change`): group or ungroup a column, add, edit, move, hide or show a column, sort by a
/// column, filter a column, set the expression of the classic query that feeds the table, or add a table beside
/// it as a tab (fed by the same query), which the changes after it can set up by naming it as their `table`. Each
/// is read from the model's arguments, checked against the columns of the table it changes, and described in
/// plain language for the user, by Stroom.
public final class DashboardChanges {

    public static final String GROUP = "group";
    public static final String UNGROUP = "ungroup";
    public static final String ADD_COLUMN = "add_column";
    public static final String EDIT_COLUMN = "edit_column";
    public static final String HIDE_COLUMN = "hide_column";
    public static final String SHOW_COLUMN = "show_column";
    public static final String SORT = "sort";
    public static final String FILTER = "filter";
    /// Sets the expression of the classic query that feeds the table, from StroomQL conditions.
    public static final String SET_EXPRESSION = "set_expression";
    /// Moves a column to a position among the columns the user sees.
    public static final String MOVE_COLUMN = "move_column";
    /// Adds a table beside the table as a tab, fed by the same query, with its columns or none.
    public static final String ADD_TABLE = "add_table";
    /// The operations, in the order they are described to the model.
    public static final List<String> OPERATIONS = List.of(GROUP, UNGROUP, ADD_COLUMN, EDIT_COLUMN, MOVE_COLUMN,
            HIDE_COLUMN, SHOW_COLUMN, SORT, FILTER, SET_EXPRESSION, ADD_TABLE);
    /// The most tables one proposal may add.
    public static final int MAX_TABLES = 10;
    /// The deepest an expression may nest, and the most items it may have, as read back from a stored change.
    static final int MAX_EXPRESSION_DEPTH = 20;
    static final int MAX_EXPRESSION_ITEMS = 500;
    /// The formats a column can be given, as the column menu names them.
    public static final List<String> FORMATS = List.of("General", "Number", "Date Time", "Text");
    public static final String ASCENDING = "ascending";
    public static final String DESCENDING = "descending";
    /// A sort direction that stops sorting by the column.
    public static final String NONE = "none";
    public static final int MAX_CHANGES = 20;
    /// The longest a name, expression or filter may be.
    public static final int MAX_TEXT = 1_000;
    /// The deepest group level, or the furthest position or sort priority, that may be given.
    public static final int MAX_LEVEL = 50;

    private DashboardChanges() {
        // Static methods only.
    }

    /// Reads the changes the model gave.
    ///
    /// @param value The changes argument: a list of objects, each with `op` and the operation's fields.
    /// @return The changes, in order.
    /// @throws IllegalArgumentException saying what is wrong, if they can't be read.
    public static List<Change> parse(final Object value) {
        if (!(value instanceof final List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Give changes as a list of operations, each with an op.");
        }
        if (list.size() > MAX_CHANGES) {
            throw new IllegalArgumentException("Give at most " + MAX_CHANGES + " changes at once.");
        }
        final List<Change> changes = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof final Map<?, ?> map)) {
                throw new IllegalArgumentException("Change " + (i + 1) + " isn't an object with an op.");
            }
            changes.add(change(i + 1, map));
        }
        return List.copyOf(changes);
    }

    /// Checks changes against the table's columns, following the changes before each (e.g. a column added, or one
    /// renamed), and gives each column its name exactly as the table has it.
    ///
    /// @param changes The changes, as read.
    /// @param columns The table's columns, by name, as the user has them.
    /// @return The changes with exact column names, and what is wrong with them, if anything.
    public static Checked check(final List<Change> changes, final List<String> columns) {
        return check(changes, columns, null, List.of());
    }

    /// Checks changes against the columns of the tables they change: the table the proposal is for, and any
    /// tables it adds (which start with its columns, or none). Each change follows the changes before it (e.g. a
    /// column added, or one renamed), and is given its column's name exactly as its table has it.
    ///
    /// @param changes    The changes, as read.
    /// @param columns    The columns of the table the proposal is for, by name, as the user has them.
    /// @param table      The name of the table the proposal is for, or null.
    /// @param tableNames The names of the dashboard's tables, which an added table may not take.
    /// @return The changes with exact column names, and what is wrong with them, if anything.
    public static Checked check(final List<Change> changes,
                                final List<String> columns,
                                final String table,
                                final List<String> tableNames) {
        // Each table's columns as the changes so far leave them, by table name (lower case); the proposal's own
        // table under the empty name.
        final Map<String, List<String>> tables = new LinkedHashMap<>();
        tables.put("", new ArrayList<>(NullSafe.list(columns)));
        final List<Change> checked = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            final Change change = changes.get(i);
            final String prefix = "Change " + (i + 1) + " (" + change.op() + "): ";
            if (SET_EXPRESSION.equals(change.op())) {
                // Of the table's query, not a column.
                checked.add(change);
                continue;
            }
            if (ADD_TABLE.equals(change.op())) {
                final String key = change.name().toLowerCase(Locale.ROOT);
                final boolean taken = tables.containsKey(key)
                                      || change.name().equalsIgnoreCase(table)
                                      || NullSafe.list(tableNames).stream().anyMatch(change.name()::equalsIgnoreCase);
                if (taken) {
                    problems.add(prefix + "the dashboard already has a table '" + change.name() + "'. Give "
                                 + "another name.");
                } else if (tables.size() > MAX_TABLES) {
                    problems.add(prefix + "at most " + MAX_TABLES + " tables may be added at once.");
                }
                tables.put(key, Boolean.TRUE.equals(change.copy())
                        ? new ArrayList<>(tables.get(""))
                        : new ArrayList<>());
                checked.add(change);
                continue;
            }
            final String target = change.table() == null || change.table().equalsIgnoreCase(table)
                    ? ""
                    : change.table().toLowerCase(Locale.ROOT);
            final List<String> names = tables.get(target);
            if (names == null) {
                problems.add(prefix + "there is no table '" + change.table() + "' to change. Changes are to table '"
                             + NullSafe.nonBlankStringElse(table, "?") + "', or to a table added before them with "
                             + "add_table.");
                checked.add(change);
                continue;
            }
            final String which = target.isEmpty()
                    ? "the table"
                    : "table '" + change.table() + "'";
            if (ADD_COLUMN.equals(change.op())) {
                if (find(names, change.name()).isPresent()) {
                    problems.add(prefix + which + " already has a column '" + change.name() + "'. Give another "
                                 + "name, or use edit_column.");
                }
                final int position = change.position() == null
                        ? names.size()
                        : Math.min(change.position() - 1, names.size());
                names.add(position, change.name());
                checked.add(change);
                continue;
            }
            final Optional<String> column = find(names, change.column());
            if (column.isEmpty()) {
                problems.add(prefix + which + " has no column '" + change.column() + "'. Its columns are: "
                             + (names.isEmpty()
                        ? "none yet"
                        : String.join(", ", names)) + ".");
                checked.add(change);
                continue;
            }
            final Change exact = change.withColumn(column.get());
            if (EDIT_COLUMN.equals(change.op()) && change.name() != null) {
                final Optional<String> clash = find(names, change.name());
                if (clash.isPresent() && !clash.get().equals(column.get())) {
                    problems.add(prefix + which + " already has a column '" + change.name() + "'.");
                }
                names.set(names.indexOf(column.get()), change.name());
            }
            if (MOVE_COLUMN.equals(change.op())) {
                names.remove(column.get());
                names.add(Math.min(change.position() - 1, names.size()), column.get());
            }
            checked.add(exact);
        }
        return new Checked(List.copyOf(checked), List.copyOf(problems));
    }

    /// @param changes Some changes.
    /// @return Each described in plain language for the user, e.g. "Group by 'EventType' (level 1)".
    public static List<String> describe(final List<Change> changes) {
        return NullSafe.list(changes).stream().map(Change::describe).toList();
    }

    // Finds a column by name: exactly, else ignoring case if only one matches.
    private static Optional<String> find(final List<String> names, final String name) {
        if (name == null) {
            return Optional.empty();
        }
        if (names.contains(name)) {
            return Optional.of(name);
        }
        final List<String> matches = names.stream().filter(name::equalsIgnoreCase).toList();
        return matches.size() == 1
                ? Optional.of(matches.getFirst())
                : Optional.empty();
    }

    private static Change change(final int number, final Map<?, ?> map) {
        final String prefix = "Change " + number + ": ";
        final String op = NullSafe.get(text(map, "op", prefix), value -> value.toLowerCase(Locale.ROOT));
        if (op == null || !OPERATIONS.contains(op)) {
            throw new IllegalArgumentException(prefix + "op must be one of " + String.join(", ", OPERATIONS)
                                               + ".");
        }
        final String column = text(map, "column", prefix);
        final String name = text(map, "name", prefix);
        final String expression = text(map, "expression", prefix);
        final String format = format(text(map, "format", prefix), prefix);
        final Integer level = number(map, "level", prefix);
        final Integer position = number(map, "position", prefix);
        final Integer priority = number(map, "priority", prefix);
        final String direction = NullSafe.get(text(map, "direction", prefix),
                value -> value.toLowerCase(Locale.ROOT));
        final Object filterValue = map.get("filter");
        final String filter = filterValue == null
                ? null
                : String.valueOf(filterValue);
        if (filter != null && filter.length() > MAX_TEXT) {
            throw new IllegalArgumentException(prefix + "filter may be at most " + MAX_TEXT + " characters.");
        }
        // Conditions are often written over several lines.
        final Object whereValue = map.get("where");
        final String where = whereValue == null
                ? null
                : text(Map.of("where", String.valueOf(whereValue).replaceAll("\\s+", " ")), "where", prefix);
        final ExpressionOperator query = map.get("query") == null
                ? null
                : expressionFromMap(map.get("query"), prefix);
        final String table = text(map, "table", prefix);
        final Boolean copy = map.get("copy") instanceof final Boolean value
                ? value
                : null;
        if (ADD_TABLE.equals(op)) {
            if (name == null) {
                throw new IllegalArgumentException(prefix + "add_table needs the new table's name.");
            }
            return new Change(op, null, null, name, null, null, null, null, null, null, null, null, null, copy);
        }
        if (SET_EXPRESSION.equals(op)) {
            if (where == null && query == null) {
                throw new IllegalArgumentException(prefix + "set_expression needs where: the query's conditions, "
                                                   + "as a StroomQL where clause has them.");
            }
            return new Change(op, null, null, null, null, null, null, null, null, null, where, query, null, null);
        }
        if (!ADD_COLUMN.equals(op) && column == null) {
            throw new IllegalArgumentException(prefix + op + " needs the column, by its name as the user sees it.");
        }
        switch (op) {
            case GROUP -> {
                if (level == null) {
                    throw new IllegalArgumentException(prefix + "group needs the level to group at, from 1.");
                }
            }
            case ADD_COLUMN -> {
                if (name == null || expression == null) {
                    throw new IllegalArgumentException(prefix + "add_column needs a name and an expression.");
                }
            }
            case EDIT_COLUMN -> {
                if (name == null && expression == null && format == null) {
                    throw new IllegalArgumentException(prefix + "edit_column needs a new name, expression or "
                                                       + "format.");
                }
            }
            case SORT -> {
                if (direction == null || !List.of(ASCENDING, DESCENDING, NONE).contains(direction)) {
                    throw new IllegalArgumentException(prefix + "sort needs a direction: ascending, descending, "
                                                       + "or none to stop sorting by the column.");
                }
            }
            case MOVE_COLUMN -> {
                if (position == null) {
                    throw new IllegalArgumentException(prefix + "move_column needs the position to move it to, "
                                                       + "from 1.");
                }
            }
            case FILTER -> {
                if (filter == null) {
                    throw new IllegalArgumentException(prefix + "filter needs the filter text, as the column's "
                                                       + "filter box takes it (empty to clear it).");
                }
            }
            default -> {
                // Only the column is needed.
            }
        }
        return new Change(op, column, level, name, expression, format, position, direction, priority, filter, null,
                null, table, null);
    }

    /// @param expression An expression.
    /// @return The expression as the browser is given it: an operator as `op`, `enabled` and `children`, and a term
    /// as `field`, `condition` (the condition's name), `value`, `enabled` and, if it names one, `doc`.
    public static Map<String, Object> expressionToMap(final ExpressionItem expression) {
        final Map<String, Object> map = new LinkedHashMap<>();
        if (expression instanceof final ExpressionOperator operator) {
            map.put("op", Objects.requireNonNullElse(operator.op(), Op.AND).name());
            if (!operator.enabled()) {
                map.put("enabled", false);
            }
            map.put("children", NullSafe.list(operator.getChildren()).stream()
                    .map(DashboardChanges::expressionToMap)
                    .toList());
        } else if (expression instanceof final ExpressionTerm term) {
            map.put("field", term.getField());
            NullSafe.consume(term.getCondition(), condition -> map.put("condition", condition.name()));
            NullSafe.consume(term.getValue(), value -> map.put("value", value));
            if (!term.enabled()) {
                map.put("enabled", false);
            }
            NullSafe.consume(term.getDocRef(), doc -> map.put("doc", Map.of("type", doc.getType(),
                    "uuid", doc.getUuid(), "name", NullSafe.string(doc.getName()))));
        }
        return map;
    }

    // Reads back an expression written by expressionToMap.
    private static ExpressionOperator expressionFromMap(final Object value, final String prefix) {
        final int[] items = new int[1];
        if (!(expressionItemFromMap(value, 0, items) instanceof final ExpressionOperator operator)) {
            throw new IllegalArgumentException(prefix + "the query's expression can't be read.");
        }
        return operator;
    }

    private static ExpressionItem expressionItemFromMap(final Object value, final int depth, final int[] items) {
        if (!(value instanceof final Map<?, ?> map) || depth > MAX_EXPRESSION_DEPTH
            || ++items[0] > MAX_EXPRESSION_ITEMS) {
            return null;
        }
        final Boolean enabled = Boolean.FALSE.equals(map.get("enabled"))
                ? Boolean.FALSE
                : null;
        if (map.get("op") instanceof final String op) {
            final List<ExpressionItem> children = new ArrayList<>();
            if (map.get("children") instanceof final List<?> list) {
                for (final Object child : list) {
                    final ExpressionItem item = expressionItemFromMap(child, depth + 1, items);
                    if (item == null) {
                        return null;
                    }
                    children.add(item);
                }
            }
            try {
                return ExpressionOperator.builder().op(Op.valueOf(op)).enabled(enabled).children(children).build();
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }
        if (map.get("field") instanceof final String field && map.get("condition") instanceof final String name) {
            final Condition condition;
            try {
                condition = Condition.valueOf(name);
            } catch (final IllegalArgumentException e) {
                return null;
            }
            final ExpressionTerm.Builder term = ExpressionTerm.builder()
                    .field(field)
                    .condition(condition)
                    .enabled(enabled);
            if (map.get("value") instanceof final String text) {
                term.value(text);
            }
            if (map.get("doc") instanceof final Map<?, ?> doc
                && doc.get("type") instanceof final String type && doc.get("uuid") instanceof final String uuid) {
                term.docRef(new DocRef(type, uuid, doc.get("name") instanceof final String docName
                        ? docName
                        : null));
            }
            return term.build();
        }
        return null;
    }

    private static String text(final Map<?, ?> map, final String key, final String prefix) {
        final Object value = map.get(key);
        if (value == null) {
            return null;
        }
        final String text = String.valueOf(value).strip();
        if (text.length() > MAX_TEXT) {
            throw new IllegalArgumentException(prefix + key + " may be at most " + MAX_TEXT + " characters.");
        }
        if (text.chars().anyMatch(chr -> Character.isISOControl(chr) && chr != '\t')) {
            throw new IllegalArgumentException(prefix + key + " may not have control characters.");
        }
        return text.isEmpty()
                ? null
                : text;
    }

    private static Integer number(final Map<?, ?> map, final String key, final String prefix) {
        final Object value = map.get(key);
        if (value == null) {
            return null;
        }
        try {
            final double number = Double.parseDouble(String.valueOf(value));
            if (number != Math.rint(number) || number < 1 || number > MAX_LEVEL) {
                throw new NumberFormatException();
            }
            return (int) number;
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(prefix + key + " must be a whole number from 1 to " + MAX_LEVEL
                                               + ".");
        }
    }

    private static String format(final String format, final String prefix) {
        if (format == null) {
            return null;
        }
        return FORMATS.stream()
                .filter(format::equalsIgnoreCase)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(prefix + "format must be one of "
                                                                + String.join(", ", FORMATS) + "."));
    }


    // --------------------------------------------------------------------------------


    /// One change to a dashboard table.
    ///
    /// @param op         What it does, one of [#OPERATIONS].
    /// @param column     The column it changes, by name, or null to add one.
    /// @param level      For group, the level to group at, from 1.
    /// @param name       For add_column, the new column's name; for edit_column, its new name, or null.
    /// @param expression For add_column or edit_column, the column's expression, or null.
    /// @param format     For add_column or edit_column, the column's format (one of [#FORMATS]), or null.
    /// @param position   For add_column, where to add it, from 1, or null for the end.
    /// @param direction  For sort, ascending, descending or none.
    /// @param priority   For sort, the sort's priority, from 1 (sorted by first), or null to sort after the
    ///                   table's other sorts, as the column menu does.
    /// @param filter     For filter, the column's filter text, or empty to clear it.
    /// @param where      For set_expression, the query's conditions as the model gave them, as a StroomQL where
    ///                   clause has them, or null.
    /// @param query      For set_expression, the query's expression built from them, or null until it has been.
    /// @param table      The table it changes, by name, if not the table the proposal is for: one an earlier
    ///                   add_table adds. Null for the proposal's table.
    /// @param copy       For add_table, true to start the new table with the proposal table's columns.
    public record Change(String op,
                         String column,
                         Integer level,
                         String name,
                         String expression,
                         String format,
                         Integer position,
                         String direction,
                         Integer priority,
                         String filter,
                         String where,
                         ExpressionOperator query,
                         String table,
                         Boolean copy) {

        /// @param column The column's name, exactly as the table has it.
        /// @return This change, of that column.
        Change withColumn(final String column) {
            return new Change(op, column, level, name, expression, format, position, direction, priority, filter,
                    where, query, table, copy);
        }

        /// @param query For set_expression, the query's expression, built from [#where()].
        /// @return This change, with the expression.
        public Change withQuery(final ExpressionOperator query) {
            return new Change(op, column, level, name, expression, format, position, direction, priority, filter,
                    where, query, table, copy);
        }

        /// @return The change in plain language, for the user.
        public String describe() {
            final String text = describeOp();
            return table == null
                    ? text
                    : "In table '" + table + "': " + text;
        }

        private String describeOp() {
            return switch (op) {
                case GROUP -> "Group by '" + column + "' (level " + level + ")";
                case UNGROUP -> "Stop grouping by '" + column + "'";
                case ADD_COLUMN -> "Add column '" + name + "' with expression " + expression
                                   + (format == null
                        ? ""
                        : ", formatted as " + format)
                                   + (position == null
                        ? ", at the end"
                        : ", at position " + position);
                case EDIT_COLUMN -> {
                    final List<String> parts = new ArrayList<>();
                    if (name != null) {
                        parts.add("rename it '" + name + "'");
                    }
                    if (expression != null) {
                        parts.add("set its expression to " + expression);
                    }
                    if (format != null) {
                        parts.add("format it as " + format);
                    }
                    yield "Change column '" + column + "': " + String.join(", ", parts);
                }
                case HIDE_COLUMN -> "Hide column '" + column + "'";
                case SHOW_COLUMN -> "Show column '" + column + "'";
                case SORT -> NONE.equals(direction)
                        ? "Stop sorting by '" + column + "'"
                        : "Sort by '" + column + "', " + direction + (priority == null
                                ? ""
                                : " (priority " + priority + ")");
                case FILTER -> filter == null || filter.isEmpty()
                        ? "Clear the filter on column '" + column + "'"
                        : "Filter column '" + column + "' by '" + filter + "'";
                case MOVE_COLUMN -> "Move column '" + column + "' to position " + position;
                case ADD_TABLE -> "Add table '" + name + "' as a tab beside this one, fed by the same query, "
                                  + (Boolean.TRUE.equals(copy)
                        ? "starting with this table's columns"
                        : "with no columns yet");
                case SET_EXPRESSION -> "Set the query to find: " + NullSafe.nonBlankStringElse(query == null
                        ? where
                        : ExpressionDescriber.describe(query), "everything");
                default -> op + " '" + column + "'";
            };
        }

        /// @return The change as the browser is given it, with only the fields it has.
        public Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("op", op);
            NullSafe.consume(column, value -> map.put("column", value));
            NullSafe.consume(level, value -> map.put("level", value));
            NullSafe.consume(name, value -> map.put("name", value));
            NullSafe.consume(expression, value -> map.put("expression", value));
            NullSafe.consume(format, value -> map.put("format", value));
            NullSafe.consume(position, value -> map.put("position", value));
            NullSafe.consume(direction, value -> map.put("direction", value));
            NullSafe.consume(priority, value -> map.put("priority", value));
            NullSafe.consume(filter, value -> map.put("filter", value));
            NullSafe.consume(where, value -> map.put("where", value));
            NullSafe.consume(query, value -> map.put("query", expressionToMap(value)));
            NullSafe.consume(table, value -> map.put("table", value));
            NullSafe.consume(copy, value -> map.put("copy", value));
            return map;
        }
    }


    // --------------------------------------------------------------------------------


    /// Changes checked against a table's columns.
    ///
    /// @param changes  The changes, each naming its column exactly as the table has it.
    /// @param problems What is wrong with them, if anything.
    public record Checked(List<Change> changes, List<String> problems) {

    }
}
