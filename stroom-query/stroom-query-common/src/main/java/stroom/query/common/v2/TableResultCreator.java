/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultBuilder;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableResult.TableResultBuilderImpl;
import stroom.query.api.v2.TableResultBuilder;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LogUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class TableResultCreator implements ResultCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TableResultCreator.class);

    private final FieldFormatter fieldFormatter;

    private final ErrorConsumer errorConsumer = new ErrorConsumerImpl();

    public TableResultCreator(final FieldFormatter fieldFormatter) {
        this.fieldFormatter = fieldFormatter;
    }

    @Override
    public Result create(final DataStore dataStore,
                         final ResultRequest resultRequest) {
        final TableResultBuilderImpl tableResultBuilder = TableResult.builder();
        create(dataStore, resultRequest, tableResultBuilder);
        tableResultBuilder.errors(errorConsumer.getErrors());
        return tableResultBuilder.build();
    }

    @Override
    public void create(final DataStore dataStore,
                       final ResultRequest resultRequest,
                       final ResultBuilder<?> resultBuilder) {
        final TableResultBuilder tableResultBuilder = (TableResultBuilder) resultBuilder;

        final KeyFactory keyFactory = dataStore.getKeyFactory();
        final AtomicLong totalResults = new AtomicLong();
        final AtomicLong pageLength = new AtomicLong();
        final OffsetRange range = resultRequest.getRequestedRange();

        try {
            // What is the interaction between the paging and the maxResults? The assumption is that
            // maxResults defines the max number of records to come back and the paging can happen up to
            // that maxResults threshold
            final List<Field> fields = dataStore.getFields();
            TableSettings tableSettings = resultRequest.getMappings().get(0);

            tableResultBuilder.fields(fields);

            // Create the row creator.
            Optional<ItemMapper<Row>> optionalRowCreator = Optional.empty();
            if (tableSettings != null) {
                optionalRowCreator = ConditionalFormattingRowCreator.create(
                        fieldFormatter,
                        keyFactory,
                        tableSettings.getAggregateFilter(),
                        tableSettings.getConditionalFormattingRules(),
                        fields,
                        errorConsumer);
                if (optionalRowCreator.isEmpty()) {
                    optionalRowCreator = FilteredRowCreator.create(
                            fieldFormatter,
                            keyFactory,
                            tableSettings.getAggregateFilter(),
                            fields,
                            errorConsumer);
                }
            }

            if (optionalRowCreator.isEmpty()) {
                optionalRowCreator = SimpleRowCreator.create(fieldFormatter, keyFactory, errorConsumer);
            }

            final ItemMapper<Row> rowCreator = optionalRowCreator.orElse(null);
            final Set<Key> openGroups = keyFactory.decodeSet(resultRequest.getOpenGroups());
            dataStore.getData(data -> {
                final Items<Row> items = data.get(
                        range,
                        openGroups,
                        resultRequest.getTimeFilter(),
                        rowCreator);
                totalResults.set(items.totalRowCount());
                items.fetch(row -> {
                    tableResultBuilder.addRow(row);
                    pageLength.incrementAndGet();
                });
            });
        } catch (final UncheckedInterruptedException e) {
            LOGGER.debug(e.getMessage(), e);
            errorConsumer.add(e);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            errorConsumer.add(e);
        }

        long offset = 0;
        if (range != null) {
            offset = range.getOffset();
        }

        tableResultBuilder.componentId(resultRequest.getComponentId());
        tableResultBuilder.resultRange(new OffsetRange(offset, pageLength.get()));
        tableResultBuilder.totalResults(totalResults.get());
    }

    private static class SimpleRowCreator implements ItemMapper<Row> {

        private final FieldFormatter fieldFormatter;
        private final KeyFactory keyFactory;
        private final ErrorConsumer errorConsumer;

        private SimpleRowCreator(final FieldFormatter fieldFormatter,
                                 final KeyFactory keyFactory,
                                 final ErrorConsumer errorConsumer) {
            this.fieldFormatter = fieldFormatter;
            this.keyFactory = keyFactory;
            this.errorConsumer = errorConsumer;
        }

        public static Optional<ItemMapper<Row>> create(final FieldFormatter fieldFormatter,
                                                       final KeyFactory keyFactory,
                                                       final ErrorConsumer errorConsumer) {
            return Optional.of(new SimpleRowCreator(fieldFormatter, keyFactory, errorConsumer));
        }

        @Override
        public Row create(final List<Field> fields,
                          final Item item) {
            final List<String> stringValues = new ArrayList<>(fields.size());
            int i = 0;
            for (final Field field : fields) {
                try {
                    final Val val = item.getValue(i);
                    final String string = fieldFormatter.format(field, val);
                    stringValues.add(string);
                } catch (final RuntimeException e) {
                    LOGGER.error(LogUtil.message("Error getting field value for field {} at index {}", field, i), e);
                    throw e;
                }
                i++;
            }

            return Row.builder()
                    .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                    .values(stringValues)
                    .depth(item.getKey().getDepth())
                    .build();
        }

        @Override
        public boolean hidesRows() {
            return false;
        }
    }

    private static class FilteredRowCreator implements ItemMapper<Row> {

        private final FieldFormatter fieldFormatter;
        private final KeyFactory keyFactory;
        private final ExpressionOperator rowFilter;
        private final FieldExpressionMatcher expressionMatcher;
        private final ErrorConsumer errorConsumer;

        private FilteredRowCreator(final FieldFormatter fieldFormatter,
                                   final KeyFactory keyFactory,
                                   final ExpressionOperator rowFilter,
                                   final FieldExpressionMatcher expressionMatcher,
                                   final ErrorConsumer errorConsumer) {
            this.fieldFormatter = fieldFormatter;
            this.keyFactory = keyFactory;
            this.rowFilter = rowFilter;
            this.expressionMatcher = expressionMatcher;
            this.errorConsumer = errorConsumer;
        }

        public static Optional<ItemMapper<Row>> create(final FieldFormatter fieldFormatter,
                                                       final KeyFactory keyFactory,
                                                       final ExpressionOperator rowFilter,
                                                       final List<Field> fields,
                                                       final ErrorConsumer errorConsumer) {
            if (rowFilter != null) {
                final FieldExpressionMatcher expressionMatcher = new FieldExpressionMatcher(fields);
                return Optional.of(new FilteredRowCreator(
                        fieldFormatter,
                        keyFactory,
                        rowFilter,
                        expressionMatcher,
                        errorConsumer));
            }
            return Optional.empty();
        }

        @Override
        public Row create(final List<Field> fields,
                          final Item item) {
            Row row = null;

            final Map<String, Object> fieldIdToValueMap = new HashMap<>();
            final List<String> stringValues = new ArrayList<>(fields.size());
            int i = 0;
            for (final Field field : fields) {
                final Val val = item.getValue(i);
                final String string = fieldFormatter.format(field, val);
                stringValues.add(string);
                fieldIdToValueMap.put(field.getName(), string);
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

    private static class ConditionalFormattingRowCreator implements ItemMapper<Row> {

        private final FieldFormatter fieldFormatter;
        private final KeyFactory keyFactory;
        private final ExpressionOperator rowFilter;
        private final List<ConditionalFormattingRule> rules;
        private final FieldExpressionMatcher expressionMatcher;
        private final ErrorConsumer errorConsumer;

        private ConditionalFormattingRowCreator(final FieldFormatter fieldFormatter,
                                                final KeyFactory keyFactory,
                                                final ExpressionOperator rowFilter,
                                                final List<ConditionalFormattingRule> rules,
                                                final FieldExpressionMatcher expressionMatcher,
                                                final ErrorConsumer errorConsumer) {
            this.fieldFormatter = fieldFormatter;
            this.keyFactory = keyFactory;
            this.rowFilter = rowFilter;
            this.rules = rules;
            this.expressionMatcher = expressionMatcher;
            this.errorConsumer = errorConsumer;
        }

        public static Optional<ItemMapper<Row>> create(final FieldFormatter fieldFormatter,
                                                       final KeyFactory keyFactory,
                                                       final ExpressionOperator rowFilter,
                                                       final List<ConditionalFormattingRule> rules,
                                                       final List<Field> fields,
                                                       final ErrorConsumer errorConsumer) {
            // Create conditional formatting expression matcher.
            if (rules != null) {
                final List<ConditionalFormattingRule> activeRules = rules
                        .stream()
                        .filter(ConditionalFormattingRule::isEnabled)
                        .collect(Collectors.toList());
                if (activeRules.size() > 0) {
                    final FieldExpressionMatcher expressionMatcher =
                            new FieldExpressionMatcher(fields);
                    return Optional.of(new ConditionalFormattingRowCreator(
                            fieldFormatter,
                            keyFactory,
                            rowFilter,
                            activeRules,
                            expressionMatcher,
                            errorConsumer));
                }
            }

            return Optional.empty();
        }

        @Override
        public Row create(final List<Field> fields,
                          final Item item) {
            Row row = null;

            final Map<String, Object> fieldIdToValueMap = new HashMap<>();
            final List<String> stringValues = new ArrayList<>(fields.size());
            int i = 0;
            for (final Field field : fields) {
                final Val val = item.getValue(i);
                final String string = fieldFormatter.format(field, val);
                stringValues.add(string);
                fieldIdToValueMap.put(field.getName(), string);
                i++;
            }

            // Find a matching rule.
            ConditionalFormattingRule matchingRule = null;

            try {
                // See if we can exit early by applying row filter.
                if (rowFilter != null) {
                    if (!expressionMatcher.match(fieldIdToValueMap, rowFilter)) {
                        return null;
                    }
                }

                for (final ConditionalFormattingRule rule : rules) {
                    try {
                        final ExpressionOperator operator = rule.getExpression();
                        final boolean match = expressionMatcher.match(fieldIdToValueMap, operator);
                        if (match) {
                            matchingRule = rule;
                            break;
                        }
                    } catch (final RuntimeException e) {
                        final RuntimeException exception = new RuntimeException(
                                "Error applying conditional formatting rule: " +
                                        rule.toString() +
                                        " - " +
                                        e.getMessage());
                        LOGGER.debug(exception.getMessage(), exception);
                        errorConsumer.add(exception);
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
                errorConsumer.add(e);
            }

            if (matchingRule != null) {
                if (!matchingRule.isHide()) {
                    final Row.Builder builder = Row.builder()
                            .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                            .values(stringValues)
                            .depth(item.getKey().getDepth());

                    if (matchingRule.getBackgroundColor() != null
                            && !matchingRule.getBackgroundColor().isEmpty()) {
                        builder.backgroundColor(matchingRule.getBackgroundColor());
                    }
                    if (matchingRule.getTextColor() != null
                            && !matchingRule.getTextColor().isEmpty()) {
                        builder.textColor(matchingRule.getTextColor());
                    }

                    row = builder.build();
                }
            } else {
                row = Row.builder()
                        .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                        .values(stringValues)
                        .depth(item.getKey().getDepth())
                        .build();
            }

            return row;
        }

        @Override
        public boolean hidesRows() {
            return true;
        }
    }
}
