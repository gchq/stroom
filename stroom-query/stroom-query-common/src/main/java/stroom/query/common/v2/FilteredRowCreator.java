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
import stroom.query.api.v2.Row;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FilteredRowCreator implements ItemMapper<Row> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FilteredRowCreator.class);

    private final ColumnFormatter columnFormatter;
    private final KeyFactory keyFactory;
    private final ExpressionOperator rowFilter;
    private final ColumnExpressionMatcher expressionMatcher;
    private final ErrorConsumer errorConsumer;

    private FilteredRowCreator(final ColumnFormatter columnFormatter,
                               final KeyFactory keyFactory,
                               final ExpressionOperator rowFilter,
                               final ColumnExpressionMatcher expressionMatcher,
                               final ErrorConsumer errorConsumer) {
        this.columnFormatter = columnFormatter;
        this.keyFactory = keyFactory;
        this.rowFilter = rowFilter;
        this.expressionMatcher = expressionMatcher;
        this.errorConsumer = errorConsumer;
    }

    public static Optional<ItemMapper<Row>> create(final ColumnFormatter columnFormatter,
                                                   final KeyFactory keyFactory,
                                                   final ExpressionOperator rowFilter,
                                                   final List<Column> columns,
                                                   final DateTimeSettings dateTimeSettings,
                                                   final ErrorConsumer errorConsumer) {
        if (rowFilter != null) {
            final ColumnExpressionMatcher expressionMatcher =
                    new ColumnExpressionMatcher(columns, dateTimeSettings);
            return Optional.of(new FilteredRowCreator(
                    columnFormatter,
                    keyFactory,
                    rowFilter,
                    expressionMatcher,
                    errorConsumer));
        }
        return Optional.empty();
    }

    @Override
    public Row create(final List<Column> columns,
                      final Item item) {
        Row row = null;

        final Map<CIKey, Object> fieldIdToValueMap = new HashMap<>();
        final List<String> stringValues = new ArrayList<>(columns.size());
        int i = 0;
        for (final Column column : columns) {
            final Val val = item.getValue(i);
            final String string = columnFormatter.format(column, val);
            stringValues.add(string);
            fieldIdToValueMap.put(column.getIdAsCIKey(), string);
            fieldIdToValueMap.put(column.getNameAsCIKey(), string);
            i++;
        }

        try {
            // See if we can exit early by applying row filter.
            if (!expressionMatcher.match(fieldIdToValueMap, rowFilter)) {
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
