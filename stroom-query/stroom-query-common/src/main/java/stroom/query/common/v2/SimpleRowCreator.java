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
import stroom.query.api.Row;
import stroom.query.api.SpecialColumns;
import stroom.query.common.v2.format.Formatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.List;

public class SimpleRowCreator implements RowCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SimpleRowCreator.class);

    private final KeyFactory keyFactory;
    private final ErrorConsumer errorConsumer;
    private final Formatter[] columnFormatters;
    private final int annotationIdIndex;

    private SimpleRowCreator(final Formatter[] columnFormatters,
                             final KeyFactory keyFactory,
                             final ErrorConsumer errorConsumer,
                             final int annotationIdIndex) {
        this.columnFormatters = columnFormatters;
        this.keyFactory = keyFactory;
        this.errorConsumer = errorConsumer;
        this.annotationIdIndex = annotationIdIndex;
    }

    public static RowCreator create(final List<Column> newColumns,
                                    final FormatterFactory formatterFactory,
                                    final KeyFactory keyFactory,
                                    final ErrorConsumer errorConsumer) {
        final Formatter[] formatters = RowUtil.createFormatters(newColumns, formatterFactory);
        final int annotationIdIndex = getColumnIndexById(newColumns, SpecialColumns.RESERVED_ID);
        return new SimpleRowCreator(
                formatters,
                keyFactory,
                errorConsumer,
                annotationIdIndex);
    }

    @Override
    public Row create(final Item item) {
        // Extract the annotation id to give to the row.
        Long annotationId = null;
        if (annotationIdIndex != -1) {
            final Val val = item.getValue(annotationIdIndex);
            if (val != null) {
                annotationId = val.toLong();
            }
        }

        // Extract the rule id to give to the row.
        String ruleId = null;
        if (item instanceof final ConditionalFormattedItem conditionalFormattedItem) {
            ruleId = conditionalFormattedItem.getRuleId();
        }

        final List<String> stringValues = convertValues(item, columnFormatters);
        return Row.builder()
                .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                .annotationId(annotationId)
                .values(stringValues)
                .depth(item.getKey().getDepth())
                .matchingRule(ruleId)
                .build();
    }

    private List<String> convertValues(final Values values,
                                       final Formatter[] columnFormatters) {
        final List<String> stringValues = new ArrayList<>(columnFormatters.length);
        for (int i = 0; i < columnFormatters.length; i++) {
            try {
                final Val val = values.getValue(i);
                stringValues.add(columnFormatters[i].format(val));
            } catch (final RuntimeException e) {
                LOGGER.error(LogUtil.message("Error getting column value for column index {}", i), e);
                throw e;
            }
        }
        return stringValues;
    }

    private static int getColumnIndexById(final List<Column> columns, final String id) {
        for (int i = 0; i < columns.size(); i++) {
            final Column column = columns.get(i);
            if (id.equals(column.getId())) {
                return i;
            }
        }
        return -1;
    }
}
