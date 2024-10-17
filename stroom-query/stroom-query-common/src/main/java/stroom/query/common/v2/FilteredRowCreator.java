/*
 * Copyright 2024 Crown Copyright
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

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class FilteredRowCreator extends SimpleRowCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FilteredRowCreator.class);

    private final Predicate<Map<CIKey, Object>> rowFilter;

    FilteredRowCreator(final List<Column> originalColumns,
                       final List<Column> newColumns,
                       final ColumnFormatter columnFormatter,
                       final KeyFactory keyFactory,
                       final Predicate<Map<CIKey, Object>> rowFilter,
                       final ErrorConsumer errorConsumer) {
        super(originalColumns, newColumns, columnFormatter, keyFactory, errorConsumer);

        this.rowFilter = rowFilter;
    }

    public static Optional<ItemMapper<Row>> create(final List<Column> originalColumns,
                                                   final List<Column> newColumns,
                                                   final ColumnFormatter columnFormatter,
                                                   final KeyFactory keyFactory,
                                                   final ExpressionOperator rowFilterExpression,
                                                   final DateTimeSettings dateTimeSettings,
                                                   final ErrorConsumer errorConsumer) {
        if (ExpressionUtil.hasTerms(rowFilterExpression)) {
            final Optional<RowExpressionMatcher> optRowExpressionMatcher =
                    RowExpressionMatcher.create(newColumns, dateTimeSettings, rowFilterExpression);
            final Predicate<Map<CIKey, Object>> rowFilter = optRowExpressionMatcher
                    .map(orem -> (Predicate<Map<CIKey, Object>>) orem)
                    .orElse(RowExpressionMatcher.ALWAYS_TRUE_PREDICATE);

            return Optional.of(new FilteredRowCreator(
                    originalColumns,
                    newColumns,
                    columnFormatter,
                    keyFactory,
                    rowFilter,
                    errorConsumer));
        }
        return Optional.empty();
    }

    @Override
    public final Row create(final Item item) {
        final Map<CIKey, Object> fieldIdToValueMap = new HashMap<>();
        final List<String> stringValues = new ArrayList<>(functions.size());
        functions.forEach(f -> {
            final String string = f.apply(item);
            stringValues.add(string);
            fieldIdToValueMap.put(f.column.getIdAsCIKey(), string);
            fieldIdToValueMap.put(f.column.getNameAsCIKey(), string);
        });

        return create(item, stringValues, fieldIdToValueMap);
    }

    public Row create(final Item item,
                      final List<String> stringValues,
                      final Map<CIKey, Object> fieldIdToValueMap) {
        Row row = null;
        try {
            // See if we can exit early by applying row filter.
            if (!rowFilter.test(fieldIdToValueMap)) {
                return null;
            }

            row = Row.builder()
                    .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                    .values(stringValues)
                    .depth(item.getKey().getDepth())
                    .build();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            errorConsumer.add(e);
        }

        return row;
    }

    @Override
    public boolean hidesRows() {
        return true;
    }
}
