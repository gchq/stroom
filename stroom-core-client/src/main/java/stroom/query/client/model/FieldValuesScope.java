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

package stroom.query.client.model;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.TimeRange;

/// What the value picker counts a field's values across: the dashboard's time range and the query's
/// expression. The data source decides which parts of the expression narrow the count, if any.
public final class FieldValuesScope {

    private final TimeRange timeRange;
    private final ExpressionOperator expression;

    /// @param timeRange  The time range, or null for all time.
    /// @param expression The query's expression, with parameters replaced, or null.
    public FieldValuesScope(final TimeRange timeRange, final ExpressionOperator expression) {
        this.timeRange = timeRange;
        this.expression = expression;
    }

    /// @return The time range, or null for all time.
    public TimeRange getTimeRange() {
        return timeRange;
    }

    /// @return The query's expression, with parameters replaced, or null.
    public ExpressionOperator getExpression() {
        return expression;
    }
}
