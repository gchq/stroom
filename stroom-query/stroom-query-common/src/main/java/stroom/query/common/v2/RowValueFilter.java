/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.query.api.ColumnValueSelection;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.query.language.functions.Values;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class RowValueFilter {

    public static boolean matches(final List<Column> columns) {
        // If any columns have a value filter that is not inverted but empty then reject everything.
        final Optional<Column> optional = columns
                .stream()
                .filter(column -> Objects.nonNull(column.getColumnValueSelection()))
                .filter(column -> !column.getColumnValueSelection().isInvert())
                .filter(column -> column.getColumnValueSelection().getValues() == null ||
                                  column.getColumnValueSelection().getValues().isEmpty())
                .findAny();
        return optional.isEmpty();
    }

    public static Optional<Predicate<Values>> create(final List<Column> columns,
                                                     final boolean applyValueFilters,
                                                     final DateTimeSettings dateTimeSettings,
                                                     final ExpressionPredicateFactory expressionPredicateFactory) {
        // Create column value filter expression.
        final Optional<ExpressionOperator> optionalExpressionOperator =
                create(columns, applyValueFilters);
        return optionalExpressionOperator.flatMap(expressionOperator -> {
            // Create the field position map for the new columns.
            final ValueFunctionFactories<Values> queryFieldIndex = createColumnIdValExtractors(columns);
            return expressionPredicateFactory.createOptional(
                    expressionOperator,
                    queryFieldIndex,
                    dateTimeSettings);
        });
    }

    private static ValueFunctionFactories<Values> createColumnIdValExtractors(final List<Column> newColumns) {
        // Create the field position map for the new columns.
        final Map<String, ValueFunctionFactory<Values>> fieldPositionMap = new HashMap<>();
        for (int i = 0; i < newColumns.size(); i++) {
            final Column column = newColumns.get(i);
            fieldPositionMap.put(column.getId(), new ValuesFunctionFactory(column, i));
        }
        return fieldPositionMap::get;
    }

    private static Optional<ExpressionOperator> create(final List<Column> columns,
                                                       final boolean applyValueFilters) {
        final ExpressionOperator.Builder valueFilterBuilder = ExpressionOperator.builder();
        columns.forEach(column -> {
            final ColumnFilter columnFilter = column.getColumnFilter();
            if (applyValueFilters && columnFilter != null) {
                final Optional<ExpressionOperator> operator = SimpleStringExpressionParser.create(
                        new SingleFieldProvider(column.getId()),
                        columnFilter.getFilter());
                operator.ifPresent(valueFilterBuilder::addOperator);
            }

            final ColumnValueSelection columnValueSelection = column.getColumnValueSelection();
            if (columnValueSelection != null && columnValueSelection.isEnabled()) {
                final List<ExpressionTerm> terms = columnValueSelection
                        .getValues()
                        .stream()
                        .map(value -> ExpressionTerm
                                .builder()
                                .field(column.getId())
                                .condition(Condition.EQUALS)
                                .value(value)
                                .build())
                        .toList();

                ExpressionOperator expressionOperator = ExpressionOperator
                        .builder()
                        .op(Op.OR)
                        .addTerms(terms)
                        .build();
                if (columnValueSelection.isInvert()) {
                    expressionOperator = ExpressionOperator
                            .builder()
                            .op(Op.NOT)
                            .addOperators(expressionOperator)
                            .build();
                }
                valueFilterBuilder.addOperator(expressionOperator);
            }
        });
        final ExpressionOperator valueFilter = valueFilterBuilder.build();
        if (!ExpressionUtil.hasTerms(valueFilter)) {
            return Optional.empty();
        }

        return Optional.of(valueFilter);
    }
}
