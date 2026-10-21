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
import stroom.query.api.ConditionalFormattingStyle;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    /// Adds a Text widget in a new pane below a table, showing the event of the table's selected row.
    public static final String ADD_TEXT = "add_text";
    /// Adds a conditional formatting rule to a table: rows matching conditions on its columns are coloured, made
    /// bold or italic, or hidden.
    public static final String ADD_FORMAT_RULE = "add_format_rule";
    /// Removes one of a table's conditional formatting rules, by its number.
    public static final String REMOVE_FORMAT_RULE = "remove_format_rule";
    /// The colours a rule can give, as the rule editor names them.
    public static final List<String> FORMAT_STYLES = Arrays.stream(ConditionalFormattingStyle.values())
            .filter(style -> style != ConditionalFormattingStyle.NONE)
            .map(ConditionalFormattingStyle::getDisplayValue)
            .toList();
    /// What a rule's colour is given to.
    public static final List<String> FORMAT_TYPES = List.of("background", "text");
    /// The conditions a rule's terms can have, as the model gives them, with the condition each is.
    public static final Map<String, Condition> RULE_CONDITIONS = ruleConditions();
    /// Renames a component (a table, query, text and so on), and so its tab.
    public static final String RENAME = "rename";
    /// Changes one of a component's settings, as its Settings dialog has them (see [DashboardSettings]).
    public static final String SET_SETTING = "set_setting";
    /// The operations, in the order they are described to the model.
    public static final List<String> OPERATIONS = List.of(GROUP, UNGROUP, ADD_COLUMN, EDIT_COLUMN, MOVE_COLUMN,
            HIDE_COLUMN, SHOW_COLUMN, SORT, FILTER, SET_EXPRESSION, ADD_TABLE, ADD_TEXT, RENAME, SET_SETTING,
            ADD_FORMAT_RULE, REMOVE_FORMAT_RULE);
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
    /// The most changes one proposal may make: enough for its tables with their columns and filters.
    public static final int MAX_CHANGES = 100;
    /// The most conditions a formatting rule may have.
    public static final int MAX_RULE_CONDITIONS = 20;
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
            throw new IllegalArgumentException("Give at most " + MAX_CHANGES + " changes at once. Leave out "
                                               + "changes that aren't needed rather than splitting them over "
                                               + "proposals: a later proposal can't be applied after an earlier "
                                               + "one.");
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
            if (SET_EXPRESSION.equals(change.op()) || RENAME.equals(change.op())
                || SET_SETTING.equals(change.op())) {
                // Of the table's query, or of a component: checked against the dashboard's components.
                checked.add(change);
                continue;
            }
            if (ADD_TEXT.equals(change.op())) {
                // The table it follows: the proposal's, or one added before it.
                if (change.table() != null && !change.table().equalsIgnoreCase(table)
                    && !tables.containsKey(change.table().toLowerCase(Locale.ROOT))) {
                    problems.add(prefix + "there is no table '" + change.table() + "' for it to follow. It follows "
                                 + "table '" + NullSafe.nonBlankStringElse(table, "?") + "', or a table added "
                                 + "before it with add_table.");
                }
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
            if (ADD_FORMAT_RULE.equals(change.op())) {
                // Its terms name columns: each must be one of the table's, as the table names it.
                final List<String> missing = new ArrayList<>();
                final ExpressionOperator exact = withColumnNames(change.query(), names, missing);
                if (!missing.isEmpty()) {
                    problems.add(prefix + which + " has no column " + String.join(", ", missing.stream()
                            .map(name -> "'" + name + "'")
                            .toList()) + ". Its columns are: " + (names.isEmpty()
                            ? "none yet"
                            : String.join(", ", names)) + ".");
                }
                checked.add(change.withQuery(exact));
                continue;
            }
            if (REMOVE_FORMAT_RULE.equals(change.op())) {
                checked.add(change);
                continue;
            }
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
        if (RENAME.equals(op) || SET_SETTING.equals(op)) {
            return componentChange(op, map, name, prefix);
        }
        if (ADD_FORMAT_RULE.equals(op)) {
            return formatRule(map, query, position, table, prefix);
        }
        if (REMOVE_FORMAT_RULE.equals(op)) {
            if (position == null) {
                throw new IllegalArgumentException(prefix + "remove_format_rule needs the rule's number (position), "
                                                   + "from 1, as the dashboard file lists them.");
            }
            return Change.builder(op).position(position).table(table).build();
        }
        if (ADD_TEXT.equals(op)) {
            if (name == null) {
                throw new IllegalArgumentException(prefix + "add_text needs the new Text widget's name.");
            }
            return Change.builder(op).name(name).table(table).build();
        }
        if (ADD_TABLE.equals(op)) {
            if (name == null) {
                throw new IllegalArgumentException(prefix + "add_table needs the new table's name.");
            }
            return Change.builder(op).name(name).copy(copy).build();
        }
        if (SET_EXPRESSION.equals(op)) {
            if (where == null && query == null) {
                throw new IllegalArgumentException(prefix + "set_expression needs where: the query's conditions, "
                                                   + "as a StroomQL where clause has them.");
            }
            return Change.builder(op).where(where).query(query).build();
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
        return Change.builder(op)
                .column(column)
                .level(level)
                .name(name)
                .expression(expression)
                .format(format)
                .position(position)
                .direction(direction)
                .priority(priority)
                .filter(filter)
                .table(table)
                .build();
    }

    // A conditional formatting rule: its conditions (as the model gives them, or as stored), and what it does.
    private static Change formatRule(final Map<?, ?> map,
                                     final ExpressionOperator stored,
                                     final Integer position,
                                     final String table,
                                     final String prefix) {
        ExpressionOperator expression = stored;
        if (expression == null) {
            if (!(map.get("conditions") instanceof final List<?> conditions) || conditions.isEmpty()
                || conditions.size() > MAX_RULE_CONDITIONS) {
                throw new IllegalArgumentException(prefix + "add_format_rule needs conditions: up to "
                                                   + MAX_RULE_CONDITIONS
                                                   + ", each with a column, a condition and (for most) a value.");
            }
            final String match = NullSafe.nonBlankStringElse(text(map, "match", prefix), "all");
            if (!List.of("all", "any").contains(match.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(prefix + "match must be all or any.");
            }
            final ExpressionOperator.Builder builder = ExpressionOperator.builder()
                    .op("any".equalsIgnoreCase(match)
                            ? Op.OR
                            : Op.AND);
            for (int i = 0; i < conditions.size(); i++) {
                final String termPrefix = prefix + "condition " + (i + 1) + ": ";
                if (!(conditions.get(i) instanceof final Map<?, ?> term)) {
                    throw new IllegalArgumentException(termPrefix + "give it as an object with column, condition "
                                                       + "and value.");
                }
                final String column = text(term, "column", termPrefix);
                final String conditionName = NullSafe.get(text(term, "condition", termPrefix),
                        name -> name.toLowerCase(Locale.ROOT));
                final Condition condition = conditionName == null
                        ? null
                        : RULE_CONDITIONS.get(conditionName);
                if (column == null || condition == null) {
                    throw new IllegalArgumentException(termPrefix + "needs a column and a condition, one of "
                                                       + String.join(", ", RULE_CONDITIONS.keySet()) + ".");
                }
                final Object valueObject = term.get("value");
                final String value = valueObject == null
                        ? null
                        : text(Map.of("value", String.valueOf(valueObject)), "value", termPrefix);
                if (value == null && condition != Condition.IS_NULL && condition != Condition.IS_NOT_NULL) {
                    throw new IllegalArgumentException(termPrefix + "needs a value.");
                }
                builder.addTerm(ExpressionTerm.builder().field(column).condition(condition).value(value).build());
            }
            expression = builder.build();
        }
        final String style = NullSafe.get(text(map, "style", prefix), name -> FORMAT_STYLES.stream()
                .filter(name::equalsIgnoreCase)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(prefix + "style must be one of "
                                                                + String.join(", ", FORMAT_STYLES) + ".")));
        final String formatType = NullSafe.nonBlankStringElse(NullSafe.get(text(map, "format_type", prefix),
                name -> name.toLowerCase(Locale.ROOT)), FORMAT_TYPES.getFirst());
        if (!FORMAT_TYPES.contains(formatType)) {
            throw new IllegalArgumentException(prefix + "format_type must be background or text.");
        }
        final boolean bold = Boolean.TRUE.equals(map.get("bold"));
        final boolean italic = Boolean.TRUE.equals(map.get("italic"));
        final boolean hide = Boolean.TRUE.equals(map.get("hide"));
        if (style == null && !bold && !italic && !hide) {
            throw new IllegalArgumentException(prefix + "add_format_rule needs something to do: a style, bold, "
                                               + "italic, or hide.");
        }
        return Change.builder(ADD_FORMAT_RULE)
                .position(position)
                .query(expression)
                .table(table)
                .rule(new FormatRule(style, formatType, bold, italic, hide))
                .build();
    }

    private static Map<String, Condition> ruleConditions() {
        final Map<String, Condition> conditions = new LinkedHashMap<>();
        conditions.put("=", Condition.EQUALS);
        conditions.put("!=", Condition.NOT_EQUALS);
        conditions.put(">", Condition.GREATER_THAN);
        conditions.put(">=", Condition.GREATER_THAN_OR_EQUAL_TO);
        conditions.put("<", Condition.LESS_THAN);
        conditions.put("<=", Condition.LESS_THAN_OR_EQUAL_TO);
        conditions.put("contains", Condition.CONTAINS);
        conditions.put("starts with", Condition.STARTS_WITH);
        conditions.put("ends with", Condition.ENDS_WITH);
        conditions.put("between", Condition.BETWEEN);
        conditions.put("in", Condition.IN);
        conditions.put("matches regex", Condition.MATCHES_REGEX);
        conditions.put("is null", Condition.IS_NULL);
        conditions.put("is not null", Condition.IS_NOT_NULL);
        return Collections.unmodifiableMap(conditions);
    }

    // An expression with each term's field given as the column is named, adding any it doesn't name to missing.
    private static ExpressionOperator withColumnNames(final ExpressionOperator expression,
                                                      final List<String> names,
                                                      final List<String> missing) {
        if (expression == null) {
            return null;
        }
        final List<ExpressionItem> children = new ArrayList<>();
        for (final ExpressionItem child : NullSafe.list(expression.getChildren())) {
            if (child instanceof final ExpressionOperator operator) {
                children.add(withColumnNames(operator, names, missing));
            } else if (child instanceof final ExpressionTerm term) {
                final Optional<String> column = find(names, term.getField());
                if (column.isEmpty()) {
                    missing.add(term.getField());
                    children.add(term);
                } else {
                    children.add(term.copy().field(column.get()).build());
                }
            }
        }
        return expression.copy().children(children).build();
    }

    // A change to a component: a rename, or one of its settings, with what was resolved for it if it was.
    private static Change componentChange(final String op, final Map<?, ?> map, final String name,
                                          final String prefix) {
        final String target = text(map, "target", prefix);
        final String targetId = text(map, "target_id", prefix);
        if (RENAME.equals(op)) {
            if (name == null) {
                throw new IllegalArgumentException(prefix + "rename needs the new name.");
            }
            return Change.builder(op).name(name).target(target).targetId(targetId).build();
        }
        final String setting = text(map, "setting", prefix);
        if (setting == null || DashboardSettings.SETTINGS.stream().noneMatch(s -> s.key().equals(setting))) {
            throw new IllegalArgumentException(prefix + "set_setting needs a setting, one of "
                                               + String.join(", ", DashboardSettings.SETTINGS.stream()
                    .map(DashboardSettings.Setting::key)
                    .toList()) + ".");
        }
        final Object valueObject = map.get("value");
        final String value = valueObject == null
                ? null
                : text(Map.of("value", String.valueOf(valueObject)), "value", prefix);
        if (value == null) {
            throw new IllegalArgumentException(prefix + "set_setting needs the value.");
        }
        final DocRef doc = map.get("doc") instanceof final Map<?, ?> docMap
                           && docMap.get("type") instanceof final String type
                           && docMap.get("uuid") instanceof final String uuid
                ? new DocRef(type, uuid, docMap.get("name") instanceof final String docName
                        ? docName
                        : null)
                : null;
        return Change.builder(op)
                .target(target)
                .targetId(targetId)
                .setting(setting)
                .value(value)
                .ref(text(map, "ref", prefix))
                .doc(doc)
                .build();
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
    /// @param target     For rename and set_setting, the component it changes, by name, or null for the
    ///                   proposal's table.
    /// @param targetId   For rename and set_setting, the component's id, once resolved, or null for a table
    ///                   added by these changes (known by its name).
    /// @param setting    For set_setting, the setting's key (see [DashboardSettings]).
    /// @param value      For set_setting, its value: as given, then as checked.
    /// @param ref        For set_setting of a component (e.g. the query that feeds a table), its id, once resolved.
    /// @param doc        For set_setting of a document (e.g. a pipeline), the document, once resolved.
    /// @param rule       For add_format_rule, what the rule does; its conditions are in `query`, and where to add
    ///                   it in `position` (default the end).
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
                         Boolean copy,
                         String target,
                         String targetId,
                         String setting,
                         String value,
                         String ref,
                         DocRef doc,
                         FormatRule rule) {

        /// @param column The column's name, exactly as the table has it.
        /// @return This change, of that column.
        Change withColumn(final String column) {
            return toBuilder().column(column).build();
        }

        /// @param query For set_expression, the query's expression, built from [#where()].
        /// @return This change, with the expression.
        public Change withQuery(final ExpressionOperator query) {
            return toBuilder().query(query).build();
        }

        /// @param targetId For rename and set_setting, the component's id, or null for an added table.
        /// @param value    For set_setting, the value as checked.
        /// @param ref      For set_setting of a component, its id.
        /// @param doc      For set_setting of a document, the document.
        /// @return This change, with what was resolved for it.
        public Change withResolved(final String targetId, final String value, final String ref, final DocRef doc) {
            return toBuilder().targetId(targetId).value(value).ref(ref).doc(doc).build();
        }

        /// @param op What the change does, one of [#OPERATIONS].
        /// @return A builder for a change, with only the fields its operation has set.
        public static Builder builder(final String op) {
            return new Builder(op);
        }

        /// @return A builder holding this change's fields, to make a changed copy of it.
        public Builder toBuilder() {
            return new Builder(op)
                    .column(column)
                    .level(level)
                    .name(name)
                    .expression(expression)
                    .format(format)
                    .position(position)
                    .direction(direction)
                    .priority(priority)
                    .filter(filter)
                    .where(where)
                    .query(query)
                    .table(table)
                    .copy(copy)
                    .target(target)
                    .targetId(targetId)
                    .setting(setting)
                    .value(value)
                    .ref(ref)
                    .doc(doc)
                    .rule(rule);
        }

        /// @return The change in plain language, for the user.
        public String describe() {
            final String text = describeOp();
            return table == null || ADD_TEXT.equals(op)
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
                case ADD_FORMAT_RULE -> "Add a formatting rule" + (position == null
                        ? ""
                        : " at position " + position) + ": rows where "
                                         + NullSafe.nonBlankStringElse(ExpressionDescriber.describe(query),
                        "anything matches") + " " + NullSafe.get(rule, FormatRule::describe);
                case REMOVE_FORMAT_RULE -> "Remove formatting rule " + position;
                case ADD_TEXT -> "Add a Text widget '" + name + "' below " + (table == null
                        ? "this table"
                        : "table '" + table + "'") + ", showing the event of its selected row";
                case RENAME -> "Rename " + (target == null
                        ? "this table"
                        : "'" + target + "'") + " to '" + name + "'";
                case SET_SETTING -> {
                    final String label = DashboardSettings.SETTINGS.stream()
                            .filter(s -> s.key().equals(setting))
                            .findFirst()
                            .map(s -> s.label().replaceAll(" \\(.*\\)$", "") + " (" + s.key() + ")")
                            .orElse(setting);
                    final String shown = doc != null
                            ? NullSafe.nonBlankStringElse(doc.getName(), doc.getUuid())
                            : DashboardSettings.SETTINGS.stream()
                                    .filter(s -> s.key().equals(setting))
                                    .findFirst()
                                    .map(s -> DashboardSettings.describe(s, value))
                                    .orElse(value);
                    yield "Set " + label + " of " + (target == null
                            ? "this table"
                            : "'" + target + "'") + " to " + shown;
                }
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
            NullSafe.consume(copy, v -> map.put("copy", v));
            NullSafe.consume(target, v -> map.put("target", v));
            NullSafe.consume(targetId, v -> map.put("target_id", v));
            NullSafe.consume(setting, v -> map.put("setting", v));
            NullSafe.consume(value, v -> map.put("value", v));
            NullSafe.consume(ref, v -> map.put("ref", v));
            NullSafe.consume(doc, d -> map.put("doc", Map.of("type", d.getType(), "uuid", d.getUuid(),
                    "name", NullSafe.string(d.getName()))));
            if (rule != null) {
                NullSafe.consume(rule.style(), v -> map.put("style", v));
                map.put("format_type", rule.formatType());
                map.put("bold", rule.bold());
                map.put("italic", rule.italic());
                map.put("hide", rule.hide());
            }
            return map;
        }

        // ----------------------------------------------------------------------------


        /// Builds a [Change], setting only the fields its operation has.
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
            private String where;
            private ExpressionOperator query;
            private String table;
            private Boolean copy;
            private String target;
            private String targetId;
            private String setting;
            private String value;
            private String ref;
            private DocRef doc;
            private FormatRule rule;

            private Builder(final String op) {
                this.op = op;
            }

            /// @param column The column it changes, by name.
            /// @return This builder.
            public Builder column(final String column) {
                this.column = column;
                return this;
            }

            /// @param level For group, the level, from 1.
            /// @return This builder.
            public Builder level(final Integer level) {
                this.level = level;
                return this;
            }

            /// @param name A new column's, table's, Text widget's or component's name.
            /// @return This builder.
            public Builder name(final String name) {
                this.name = name;
                return this;
            }

            /// @param expression A column's expression.
            /// @return This builder.
            public Builder expression(final String expression) {
                this.expression = expression;
                return this;
            }

            /// @param format A column's format.
            /// @return This builder.
            public Builder format(final String format) {
                this.format = format;
                return this;
            }

            /// @param position A position, from 1.
            /// @return This builder.
            public Builder position(final Integer position) {
                this.position = position;
                return this;
            }

            /// @param direction For sort, the direction.
            /// @return This builder.
            public Builder direction(final String direction) {
                this.direction = direction;
                return this;
            }

            /// @param priority For sort, its priority, from 1.
            /// @return This builder.
            public Builder priority(final Integer priority) {
                this.priority = priority;
                return this;
            }

            /// @param filter For filter, the filter text.
            /// @return This builder.
            public Builder filter(final String filter) {
                this.filter = filter;
                return this;
            }

            /// @param where For set_expression, the conditions as given.
            /// @return This builder.
            public Builder where(final String where) {
                this.where = where;
                return this;
            }

            /// @param query An expression: the query's, or a rule's conditions.
            /// @return This builder.
            public Builder query(final ExpressionOperator query) {
                this.query = query;
                return this;
            }

            /// @param table The table it changes or follows, if not the proposal's.
            /// @return This builder.
            public Builder table(final String table) {
                this.table = table;
                return this;
            }

            /// @param copy For add_table, true to start with the proposal table's columns.
            /// @return This builder.
            public Builder copy(final Boolean copy) {
                this.copy = copy;
                return this;
            }

            /// @param target For rename and set_setting, the component, by name.
            /// @return This builder.
            public Builder target(final String target) {
                this.target = target;
                return this;
            }

            /// @param targetId For rename and set_setting, the component's id.
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

            /// @param value For set_setting, its value.
            /// @return This builder.
            public Builder value(final String value) {
                this.value = value;
                return this;
            }

            /// @param ref For set_setting of a component, its id.
            /// @return This builder.
            public Builder ref(final String ref) {
                this.ref = ref;
                return this;
            }

            /// @param doc For set_setting of a document, the document.
            /// @return This builder.
            public Builder doc(final DocRef doc) {
                this.doc = doc;
                return this;
            }

            /// @param rule For add_format_rule, what the rule does.
            /// @return This builder.
            public Builder rule(final FormatRule rule) {
                this.rule = rule;
                return this;
            }

            /// @return The change.
            public Change build() {
                return new Change(op, column, level, name, expression, format, position, direction, priority, filter,
                        where, query, table, copy, target, targetId, setting, value, ref, doc, rule);
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// What a conditional formatting rule does to the rows it matches.
    ///
    /// @param style      The colour (one of [#FORMAT_STYLES]), or null for none.
    /// @param formatType What the colour is given to: background or text.
    /// @param bold       True to make the rows bold.
    /// @param italic     True to make the rows italic.
    /// @param hide       True to hide the rows.
    public record FormatRule(String style, String formatType, boolean bold, boolean italic, boolean hide) {

        /// @return What it does, in plain language, e.g. "get a Red background, bold".
        public String describe() {
            if (hide) {
                return "are hidden";
            }
            final List<String> parts = new ArrayList<>();
            NullSafe.consume(style, colour -> parts.add("a " + colour + " " + formatType));
            if (bold) {
                parts.add("bold");
            }
            if (italic) {
                parts.add("italic");
            }
            return "get " + String.join(", ", parts);
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
