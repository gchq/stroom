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

package stroom.sqldatasource.impl;

import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValuesConsumer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/// Turns the rows of a SQL result into rows of query fields, drops those that fail the query's
/// `where` expression and passes the rest on.
///
/// Each query field takes the column of the same name. A name matches exactly, or failing that
/// without regard to case, since some databases change the case of names that are not quoted. Only
/// the columns the query uses are read. A field used in the `where` expression must be a column;
/// any other field with no column is left empty and listed by [#getUnmatchedFields()].
///
/// It is used for every connection type, so it reads values through a [ColumnReader].
public class SqlRowConsumer {

    private final int rowSize;
    // For each position in the field index, the column it reads, or -1.
    private final int[] columnByField;
    private final Predicate<Val[]> predicate;
    private final ValuesConsumer consumer;
    private final List<String> unmatchedFields;

    private SqlRowConsumer(final int[] columnByField,
                           final Predicate<Val[]> predicate,
                           final ValuesConsumer consumer,
                           final List<String> unmatchedFields) {
        this.rowSize = columnByField.length;
        this.columnByField = columnByField;
        this.predicate = predicate;
        this.consumer = consumer;
        this.unmatchedFields = unmatchedFields;
    }

    /// Prepares to read rows with the given columns.
    ///
    /// @param columns                    The columns the SQL returns.
    /// @param fieldIndex                 The query's fields. Fields used by the expression are added.
    /// @param expression                 The `where` expression, with any time range terms, or `null`.
    /// @param dateTimeSettings           Settings for reading dates in the expression.
    /// @param expressionPredicateFactory Builds the predicate from the expression.
    /// @param consumer                   Takes each row that passes the expression.
    /// @return The row consumer.
    /// @throws IllegalArgumentException If the expression uses a field that is not a column.
    public static SqlRowConsumer create(final List<SqlColumn> columns,
                                        final FieldIndex fieldIndex,
                                        final ExpressionOperator expression,
                                        final DateTimeSettings dateTimeSettings,
                                        final ExpressionPredicateFactory expressionPredicateFactory,
                                        final ValuesConsumer consumer) {
        final Set<String> expressionFields = new LinkedHashSet<>(expression == null
                ? List.of()
                : ExpressionUtil.fields(expression));
        for (final String field : expressionFields) {
            if (findColumn(columns, field) < 0) {
                throw new IllegalArgumentException("The where clause uses '" + field + "', which is not a column " +
                                                   "returned by the SQL. The columns are: " + columnNames(columns)
                                                   + ".");
            }
            fieldIndex.create(field);
        }

        final String[] fields = fieldIndex.getFields();
        final int[] columnByField = new int[fields.length];
        final List<String> unmatchedFields = new ArrayList<>();
        for (int pos = 0; pos < fields.length; pos++) {
            if (isAnnotationField(fields[pos])) {
                // Filled in later by Stroom's annotation decoration, as for every other data source.
                columnByField[pos] = -1;
            } else {
                columnByField[pos] = findColumn(columns, fields[pos]);
                if (columnByField[pos] < 0 && !isSpecialField(fields[pos])) {
                    unmatchedFields.add(fields[pos]);
                }
            }
        }

        final Optional<Predicate<Val[]>> predicate = expressionPredicateFactory.createOptional(
                expression,
                field -> {
                    final Integer pos = fieldIndex.getPos(field);
                    final SqlColumn column = columns.get(columnByField[pos]);
                    return new RowValueFunctionFactory(pos, column.fieldType());
                },
                dateTimeSettings);

        return new SqlRowConsumer(
                columnByField,
                predicate.orElse(null),
                consumer,
                Collections.unmodifiableList(unmatchedFields));
    }

    /// Reads one row and passes it on if it passes the expression.
    ///
    /// @param reader Reads a column of the current row.
    /// @return True if the row was passed on.
    /// @throws SQLException If a column cannot be read.
    public boolean accept(final ColumnReader reader) throws SQLException {
        final Val[] row = new Val[rowSize];
        for (int pos = 0; pos < rowSize; pos++) {
            final int column = columnByField[pos];
            row[pos] = column < 0
                    ? ValNull.INSTANCE
                    : reader.read(column);
        }
        if (predicate == null || predicate.test(row)) {
            consumer.accept(row);
            return true;
        }
        return false;
    }

    /// @return The query's fields that no column supplies, which will be empty.
    public List<String> getUnmatchedFields() {
        return unmatchedFields;
    }

    /// Finds the column for a field.
    ///
    /// @param columns The columns the SQL returns.
    /// @param field   The field's name.
    /// @return The column's index, counted from 0, or -1 if no column matches, or more than one
    ///         matches without regard to case and none exactly.
    static int findColumn(final List<SqlColumn> columns, final String field) {
        int caseInsensitiveMatch = -1;
        int caseInsensitiveCount = 0;
        for (int i = 0; i < columns.size(); i++) {
            final String name = columns.get(i).name();
            if (name.equals(field)) {
                return i;
            } else if (name.equalsIgnoreCase(field)) {
                caseInsensitiveMatch = i;
                caseInsensitiveCount++;
            }
        }
        return caseInsensitiveCount == 1
                ? caseInsensitiveMatch
                : -1;
    }

    private static String columnNames(final List<SqlColumn> columns) {
        return columns.stream()
                .map(SqlColumn::name)
                .collect(Collectors.joining(", "));
    }

    // Fields Stroom adds to tables for linking to the source data, which a SQL result never has
    // unless it names them itself.
    private static boolean isSpecialField(final String field) {
        return FieldIndex.DEFAULT_TIME_FIELD_NAME.equals(field)
               || FieldIndex.DEFAULT_STREAM_ID_FIELD_NAME.equals(field)
               || FieldIndex.DEFAULT_EVENT_ID_FIELD_NAME.equals(field)
               || FieldIndex.FALLBACK_STREAM_ID_FIELD_NAME.equals(field)
               || FieldIndex.FALLBACK_EVENT_ID_FIELD_NAME.equals(field);
    }

    private static boolean isAnnotationField(final String field) {
        return field != null && field.startsWith(AnnotationDecorationFields.ANNOTATION_FIELD_PREFIX);
    }


    // --------------------------------------------------------------------------------


    /// Reads a column of the current row.
    @FunctionalInterface
    public interface ColumnReader {

        /// @param columnIndex The column, counted from 0.
        /// @return The value.
        /// @throws SQLException If the column cannot be read.
        Val read(int columnIndex) throws SQLException;
    }
}
