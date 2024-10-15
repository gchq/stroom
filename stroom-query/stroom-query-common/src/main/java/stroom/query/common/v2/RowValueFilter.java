/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;
import stroom.util.NullSafe;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class RowValueFilter {

    public static Optional<Predicate<RowValueMap>> create(final List<Column> columns,
                                                          final DateTimeSettings dateTimeSettings,
                                                          final Map<String, Column> columnNameToColumnMap) {
        final ExpressionOperator.Builder valueFilterBuilder = ExpressionOperator.builder();
        columns.forEach(column -> {
            final String valueFilter = column.getValueFilter();
            if (NullSafe.isNonBlankString(valueFilter)) {
                Condition condition = Condition.CONTAINS;
                String term = valueFilter;
                if (valueFilter.length() > 2 && valueFilter.startsWith("/") && valueFilter.endsWith("/")) {
                    condition = Condition.MATCHES_REGEX;
                    term = valueFilter.substring(1, valueFilter.length() - 1);
                } else if (valueFilter.startsWith(">=")) {
                    condition = Condition.GREATER_THAN_OR_EQUAL_TO;
                    term = valueFilter.substring(2).trim();
                } else if (valueFilter.startsWith("<=")) {
                    condition = Condition.LESS_THAN_OR_EQUAL_TO;
                    term = valueFilter.substring(2).trim();
                } else if (valueFilter.startsWith(">")) {
                    condition = Condition.GREATER_THAN;
                    term = valueFilter.substring(1).trim();
                } else if (valueFilter.startsWith("<")) {
                    condition = Condition.LESS_THAN;
                    term = valueFilter.substring(1).trim();
                } else if (valueFilter.startsWith("=")) {
                    condition = Condition.EQUALS;
                    term = valueFilter.substring(1);
                }

                valueFilterBuilder.addTerm(column.getName(), condition, term);
            }
        });
        final ExpressionOperator valueFilter = valueFilterBuilder.build();
        if (!ExpressionUtil.hasTerms(valueFilter)) {
            return Optional.empty();
        }

        for (final Column column : NullSafe.list(columns)) {
            // Allow match by id and name.
            columnNameToColumnMap.putIfAbsent(column.getId(), column);
            columnNameToColumnMap.putIfAbsent(column.getName(), column);
        }

        final RowExpressionMatcher rowValueFilter =
                new RowExpressionMatcher(columnNameToColumnMap, dateTimeSettings, valueFilter);
        return Optional.of(rowValueFilter::test);
    }
}
