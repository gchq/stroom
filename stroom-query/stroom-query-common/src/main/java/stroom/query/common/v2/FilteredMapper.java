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
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class FilteredMapper implements ItemMapper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FilteredMapper.class);

    private final ErrorConsumer errorConsumer;
    private final Predicate<Values> rowFilter;
    private final ItemMapper parentMapper;

    private FilteredMapper(final ErrorConsumer errorConsumer,
                           final Predicate<Values> rowFilter,
                           final ItemMapper parentMapper) {
        this.errorConsumer = errorConsumer;
        this.rowFilter = rowFilter;
        this.parentMapper = parentMapper;
    }

    public static ItemMapper create(final List<Column> newColumns,
                                    final boolean applyValueFilters,
                                    final ExpressionOperator rowFilterExpression,
                                    final DateTimeSettings dateTimeSettings,
                                    final ErrorConsumer errorConsumer,
                                    final ExpressionPredicateFactory expressionPredicateFactory,
                                    final ItemMapper parentMapper) {
        // Combine filters.
        final Optional<Predicate<Values>> optionalCombinedPredicate = createValuesPredicate(
                newColumns,
                applyValueFilters,
                rowFilterExpression,
                dateTimeSettings,
                expressionPredicateFactory);

        // If we have no predicate then return a simple row creator.
        if (optionalCombinedPredicate.isEmpty()) {
            return parentMapper;
        }

        return new FilteredMapper(
                errorConsumer,
                optionalCombinedPredicate.get(),
                parentMapper);
    }

    public static Optional<Predicate<Values>> createValuesPredicate(final List<Column> newColumns,
                                                                    final boolean applyValueFilters,
                                                                    final ExpressionOperator rowFilterExpression,
                                                                    final DateTimeSettings dateTimeSettings,
                                                                    final ExpressionPredicateFactory
                                                                            expressionPredicateFactory) {
        // Create column value filter expression.
        final Optional<Predicate<Values>> valuesPredicate = RowValueFilter.create(
                newColumns,
                applyValueFilters,
                dateTimeSettings,
                expressionPredicateFactory);

        // Apply having filters.
        final ValueFunctionFactories<Values> queryFieldIndex = RowUtil.createColumnNameValExtractor(newColumns);
        final Optional<Predicate<Values>> optionalRowFilterPredicate = expressionPredicateFactory.createOptional(
                rowFilterExpression,
                queryFieldIndex,
                dateTimeSettings);

        // Combine filters.
        return valuesPredicate
                .map(vp1 -> optionalRowFilterPredicate
                        .map(vp1::and)
                        .or(() -> Optional.of(vp1)))
                .orElse(optionalRowFilterPredicate);
    }

    @Override
    public final Stream<Item> create(final Item in) {
        return parentMapper
                .create(in)
                .filter(item -> {
                    try {
                        return rowFilter.test(item);
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                        errorConsumer.add(e);
                    }
                    return false;
                });
    }

    @Override
    public boolean hidesRows() {
        return true;
    }
}
