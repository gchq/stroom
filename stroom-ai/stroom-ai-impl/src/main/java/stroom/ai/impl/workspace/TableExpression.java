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

package stroom.ai.impl.workspace;

import stroom.query.api.DateTimeSettings;
import stroom.query.language.functions.Expression;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.ExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.ParamFactory;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValErr;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// An expression in Stroom's expression language (as in dashboards and StroomQL's eval), worked out over a table's
/// rows: its columns are its fields, e.g. `${bytes} / ${duration}` or `upperCase(substring(${host}, 0, 3))`. A cell
/// that is a number is given as one, and others as text, which functions convert to a date as they need. One with
/// an aggregate (e.g.
/// `round(sum(${bytes}) / count(), 2)`) is worked out over each group of rows.
///
/// Functions that reach outside the table (asking a model, reading a state store) aren't available, and nor are
/// those that pick out rows of a group (first, top...), as groups don't keep their rows.
public final class TableExpression {

    // How long a string some functions (e.g. joining) may build.
    private static final int MAX_STRING_LENGTH = 1_000;
    private static final ExpressionContext CONTEXT = new ExpressionContext(MAX_STRING_LENGTH,
            DateTimeSettings.builder().build(),
            (map, key, effectiveTimeMs) -> ValErr.create("getState isn't available here"),
            (model, systemPrompt, message) -> ValErr.create("askAi isn't available here"));
    private static final String ANNOTATION_FIELD_PREFIX = "annotation:";
    private static final Pattern LEADING_ZERO = Pattern.compile("^\\s*[+-]?0\\d");
    private static final Pattern INTEGER = Pattern.compile("\\s*[+-]?\\d+\\s*");
    private static final Pattern UNKNOWN_FUNCTION = Pattern.compile("Unknown function '([^']+)'");
    // E.g. "Invalid number of parameters supplied for 'year' function".
    private static final Pattern FUNCTION_NAMED = Pattern.compile("for '([^']+)' function");

    private final String text;
    private final Expression expression;
    private final ValueReferenceIndex valueReferences = new ValueReferenceIndex();
    private final Generator generator;
    // For each of the expression's fields, the column of the table it is.
    private final int[] columns;

    private TableExpression(final String text, final Expression expression, final int[] columns) {
        this.text = text;
        this.expression = expression;
        this.columns = columns;
        expression.addValueReferences(valueReferences);
        // Holds no state of its own (that is in each row's or group's stored values), so serves them all.
        this.generator = expression.createGenerator();
    }

    /// @param text   The expression.
    /// @param header The table's columns.
    /// @return The expression, ready to work out.
    /// @throws ExpressionException if it can't be read, names a column the table doesn't have or a function that
    ///                            doesn't exist or isn't available here.
    public static TableExpression compile(final String text, final List<String> header) {
        if (text == null || text.isBlank()) {
            throw new ExpressionException("Give an expression.");
        }
        ExpressionFunctions.unavailableIn(text).ifPresent(reason -> {
            throw new ExpressionException(reason);
        });
        final FieldIndex fieldIndex = new FieldIndex();
        final Expression expression;
        try {
            expression = new ExpressionParser(new ParamFactory(new HashMap<>())).parse(CONTEXT, fieldIndex, text);
        } catch (final RuntimeException | ParseException e) {
            throw new ExpressionException("The expression '" + text + "' can't be read: " + e.getMessage()
                                         + suggestion(e.getMessage()));
        }
        if (expression == null) {
            throw new ExpressionException("Give an expression.");
        }
        if (expression.requiresChildData()) {
            throw new ExpressionException("The expression '" + text + "' picks out rows of a group (e.g. first, "
                                         + "last, nth, top, bottom), which isn't available here: use orderBy and "
                                         + "limit instead.");
        }
        final int[] columns = new int[fieldIndex.size()];
        for (int pos = 0; pos < columns.length; pos++) {
            columns[pos] = column(fieldIndex.getField(pos), header, text);
        }
        return new TableExpression(text, expression, columns);
    }

    /// Checks an expression for a dashboard table's column, with the parser the table tool's eval uses. Unlike
    /// [#compile(String, List)], every function is allowed (as a dashboard allows them), and its fields are those
    /// of the dashboard's data source.
    ///
    /// @param text   The expression, e.g. `count()` or `upperCase(${UserId})`.
    /// @param fields The names of the data source's fields, or null if they aren't known, when any field is
    ///               accepted. Annotation fields (`annotation:...`) are always accepted.
    /// @return Why it isn't valid, or empty if it is.
    public static Optional<String> dashboardProblem(final String text, final Collection<String> fields) {
        if (text == null || text.isBlank()) {
            return Optional.of("Give an expression.");
        }
        final FieldIndex fieldIndex = new FieldIndex();
        final Expression expression;
        try {
            expression = new ExpressionParser(new ParamFactory(new HashMap<>())).parse(CONTEXT, fieldIndex, text);
        } catch (final RuntimeException | ParseException e) {
            return Optional.of("The expression '" + text + "' can't be read: " + e.getMessage()
                               + suggestion(e.getMessage()));
        }
        if (expression == null) {
            return Optional.of("Give an expression.");
        }
        if (fields != null) {
            for (int pos = 0; pos < fieldIndex.size(); pos++) {
                final String field = fieldIndex.getField(pos);
                if (!field.startsWith(ANNOTATION_FIELD_PREFIX)
                    && fields.stream().noneMatch(field::equalsIgnoreCase)) {
                    return Optional.of("The expression '" + text + "' names a field '" + field + "' that the "
                                       + "dashboard's data source doesn't have.");
                }
            }
        }
        return Optional.empty();
    }

    /// @return True if it works out something over each group of rows, e.g. sum(...).
    public boolean isAggregate() {
        return expression.hasAggregate();
    }

    /// @return The expression as given.
    public String text() {
        return text;
    }

    /// @param row A row of the table.
    /// @return What the expression is for the row, as text: empty for no value, or the error if it fails.
    public String evaluate(final List<String> row) {
        final StoredValues values = valueReferences.createStoredValues();
        generator.set(values(row), values);
        return text(generator.eval(values, null));
    }

    /// @return A group to add rows to, and work the expression out over.
    public Group group() {
        return new Group();
    }

    private Val[] values(final List<String> row) {
        final Val[] values = new Val[columns.length];
        for (int pos = 0; pos < columns.length; pos++) {
            final int column = columns[pos];
            final String cell = column < row.size()
                    ? row.get(column)
                    : null;
            values[pos] = value(cell);
        }
        return values;
    }

    // A cell that is a number is given as one, as operators (e.g. +) only work on numbers, but one with a leading
    // zero (e.g. 007) is kept as text, as it is more likely an ID.
    static Val value(final String cell) {
        if (cell == null || cell.isEmpty()) {
            return ValNull.INSTANCE;
        }
        final Double number = TableEngine.number(cell);
        if (number == null || LEADING_ZERO.matcher(cell).find()) {
            return ValString.create(cell);
        }
        if (INTEGER.matcher(cell).matches()) {
            try {
                return ValLong.create(Long.parseLong(cell.strip()));
            } catch (final NumberFormatException e) {
                // Too big for a long.
            }
        }
        return ValDouble.create(number);
    }

    private static String text(final Val val) {
        if (val == null || val.type().isNull()) {
            return "";
        }
        // An error shows as Stroom shows it, e.g. "Err: ...".
        return val.toString();
    }

    private static int column(final String field, final List<String> header, final String text) {
        final int exact = header.indexOf(field);
        if (exact >= 0) {
            return exact;
        }
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).equalsIgnoreCase(field)) {
                return i;
            }
        }
        throw new ExpressionException("The expression '" + text + "' names a column '" + field + "' that isn't in "
                                     + "the table. Its columns are: " + String.join(", ", header) + ".");
    }

    // What to try instead: the closest functions to an unknown one, or what a function given the wrong arguments
    // takes.
    private static String suggestion(final String message) {
        if (message == null) {
            return "";
        }
        final Matcher named = FUNCTION_NAMED.matcher(message);
        if (named.find()) {
            return "\n\n" + ExpressionFunctions.help(List.of(named.group(1)));
        }
        final Matcher unknown = UNKNOWN_FUNCTION.matcher(message);
        if (!unknown.find()) {
            return "";
        }
        final List<String> closest = ExpressionFunctions.closestNames(unknown.group(1).toLowerCase(Locale.ROOT));
        return closest.isEmpty()
                ? ""
                : " Did you mean " + String.join(" or ", closest) + "? Use help for what a function takes.";
    }

    /// Rows worked out together, e.g. a group's, for an expression with an aggregate.
    public final class Group {

        private final StoredValues values = valueReferences.createStoredValues();

        private Group() {
        }

        /// @param row A row of the group.
        public void add(final List<String> row) {
            generator.set(TableExpression.this.values(row), values);
        }

        /// @return What the expression is over the group's rows, as text.
        public String result() {
            return text(generator.eval(values, null));
        }
    }
}
