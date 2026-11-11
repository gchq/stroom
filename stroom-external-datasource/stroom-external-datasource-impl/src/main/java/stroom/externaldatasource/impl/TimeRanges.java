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

package stroom.externaldatasource.impl;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;

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
}
