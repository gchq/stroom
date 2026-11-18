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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Query;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;

import java.util.List;

/// Helpers for a query's time range.
final class TimeRanges {

    private TimeRanges() {
    }

    /// @param timeRange A query's time range, or `null`.
    /// @return True if it limits the query at either end.
    static boolean isSet(final TimeRange timeRange) {
        return timeRange != null
               && (!NullSafe.isBlankString(timeRange.getFrom()) || !NullSafe.isBlankString(timeRange.getTo()));
    }

    /// Works out how a query's time range applies to a search of a Data Connection. If the native query
    /// uses the time range itself, as with `:from` and `:to`, the expression is the query's own.
    /// Otherwise the time range is added to it as terms on the document's time column, if it has one,
    /// or ignored with a warning if it has none.
    ///
    /// @param query                    The query.
    /// @param nativeQueryUsesTimeRange True if the native query uses the time range itself.
    /// @param timeField                The document's time column, or `null`.
    /// @param ignoredWarning           The warning to give if a time range is set but can't be used.
    /// @return How the time range applies.
    static Applied apply(final Query query,
                         final boolean nativeQueryUsesTimeRange,
                         final String timeField,
                         final String ignoredWarning) {
        if (nativeQueryUsesTimeRange || !isSet(query.getTimeRange())) {
            return new Applied(query.getExpression(), false, List.of());
        }
        if (NullSafe.isBlankString(timeField)) {
            return new Applied(query.getExpression(), false, List.of(ignoredWarning));
        }
        return new Applied(addTimeRange(query.getExpression(), query.getTimeRange(), timeField), true, List.of());
    }

    /// Checks that the time column is one of the document's columns and holds dates, as the time range
    /// is applied to it.
    ///
    /// @param doc         The document.
    /// @param columns     Its columns.
    /// @param timeField   Its time column.
    /// @param noun        What its columns are called, e.g. `column` or `attribute`.
    /// @param missingHint What to do if the time column is not one, e.g. `Add it to the attributes.`, or
    ///                    blank.
    /// @throws IllegalArgumentException If the time column is not one of the columns, or not a date one.
    static void requireDateColumn(final DataConnectionDoc doc,
                                  final List<DataColumn> columns,
                                  final String timeField,
                                  final String noun,
                                  final String missingHint) {
        // Matched without regard to case, as fields are.
        final DataColumn column = columns.stream()
                .filter(dataColumn -> timeField.equalsIgnoreCase(dataColumn.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "The time range cannot be applied, as the time column '" + timeField + "' is not one of "
                        + "the " + noun + "s of Data Connection '" + doc.getName() + "'." + missingHint));
        if (column.getType() != DataColumnType.DATE) {
            throw new IllegalArgumentException("The time range cannot be applied, as the time column '"
                                               + timeField + "' of Data Connection '" + doc.getName()
                                               + "' is not a Date " + noun + ".");
        }
    }

    /// Adds the time range to the `where` expression as terms on a time column, so rows are filtered
    /// on it in Stroom.
    ///
    /// @param expression The query's `where` expression, or `null`.
    /// @param timeRange  The query's time range, or `null`.
    /// @param timeField  The document's time column, or `null`.
    /// @return The expression to filter rows with, or `null` for none. It is the given expression if
    ///         there is no time range or no time column.
    static ExpressionOperator addTimeRange(final ExpressionOperator expression,
                                           final TimeRange timeRange,
                                           final String timeField) {
        if (!isSet(timeRange) || NullSafe.isBlankString(timeField)) {
            return expression;
        }
        final QueryField timeQueryField = QueryField.createDate(timeField);
        final ExpressionOperator.Builder and = ExpressionOperator.builder().op(Op.AND);
        if (!NullSafe.isBlankString(timeRange.getFrom())) {
            and.addDateTerm(timeQueryField, Condition.GREATER_THAN_OR_EQUAL_TO, timeRange.getFrom());
        }
        if (!NullSafe.isBlankString(timeRange.getTo())) {
            and.addDateTerm(timeQueryField, Condition.LESS_THAN, timeRange.getTo());
        }
        if (expression != null) {
            and.addOperator(expression);
        }
        return and.build();
    }


    // --------------------------------------------------------------------------------


    /// How a query's time range applies to a search.
    ///
    /// @param expression          The expression to filter rows with, or `null` for none.
    /// @param filtersOnTimeColumn True if the time range was added on the document's time column.
    /// @param warnings            A warning if the time range was ignored, or none.
    record Applied(ExpressionOperator expression, boolean filtersOnTimeColumn, List<String> warnings) {

    }
}
