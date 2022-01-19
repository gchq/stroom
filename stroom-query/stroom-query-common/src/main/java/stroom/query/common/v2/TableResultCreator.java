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
import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.format.FieldFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TableResultCreator implements ResultCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TableResultCreator.class);

    private final FieldFormatter fieldFormatter;
    private final Sizes defaultMaxResultsSizes;
    private volatile List<Field> latestFields;

    public TableResultCreator(final FieldFormatter fieldFormatter,
                              final Sizes defaultMaxResultsSizes) {

        this.fieldFormatter = fieldFormatter;
        this.defaultMaxResultsSizes = defaultMaxResultsSizes;
    }

    @Override
    public Result create(final DataStore dataStore, final ResultRequest resultRequest) {
        final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
        final List<Row> resultList = new ArrayList<>();
        final AtomicInteger totalResults = new AtomicInteger();

        final int offset;
        final int length;
        final OffsetRange range = resultRequest.getRequestedRange();
        if (range != null) {
            offset = range.getOffset().intValue();
            length = range.getLength().intValue();
        } else {
            offset = 0;
            length = Integer.MAX_VALUE;
        }

        try {
            //What is the interaction between the paging and the maxResults? The assumption is that
            //maxResults defines the max number of records to come back and the paging can happen up to
            //that maxResults threshold

            Set<Key> openGroups = OpenGroupsConverter.convertSet(resultRequest.getOpenGroups());

            TableSettings tableSettings = resultRequest.getMappings().get(0);
            latestFields = tableSettings.getFields();
            // Create a set of sizes that are the minimum values for the combination of user provided sizes for
            // the table and the default maximum sizes.
            final Sizes maxResults = Sizes.min(Sizes.create(tableSettings.getMaxResults()), defaultMaxResultsSizes);

            // Create the row creator.
            Optional<RowCreator> optionalRowCreator =
                    ConditionalFormattingRowCreator.create(fieldFormatter, tableSettings);
            if (optionalRowCreator.isEmpty()) {
                optionalRowCreator = SimpleRowCreator.create(fieldFormatter);
            }
            final RowCreator rowCreator = optionalRowCreator.orElse(null);

            dataStore.getData(data ->
                    addTableResults(data,
                            latestFields.toArray(new Field[0]),
                            maxResults,
                            offset,
                            length,
                            openGroups,
                            resultList,
                            data.get(),
                            0,
                            totalResults,
                            rowCreator,
                            errorConsumer));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            errorConsumer.add(e);
        }

        return new TableResult(
                resultRequest.getComponentId(),
                latestFields,
                resultList,
                new OffsetRange(offset, resultList.size()),
                totalResults.get(),
                errorConsumer.getErrors());
    }

    private void addTableResults(final Data data,
                                 final Field[] fields,
                                 final Sizes maxResults,
                                 final int offset,
                                 final int length,
                                 final Set<Key> openGroups,
                                 final List<Row> resultList,
                                 final Items items,
                                 final int depth,
                                 final AtomicInteger pos,
                                 final RowCreator rowCreator,
                                 final ErrorConsumer errorConsumer) {
        int maxResultsAtThisDepth = maxResults.size(depth);
        int resultCountAtThisLevel = 0;

        for (final Item item : items) {
            boolean hide = false;

            // If the result is within the requested window (offset + length) then add it.
            if (pos.get() >= offset && resultList.size() < length) {
                final Row row = rowCreator.create(fields, item, depth, errorConsumer);
                if (row != null) {
                    resultList.add(row);
                } else {
                    hide = true;
                }
            } else if (rowCreator.hidesRows()) {
                final Row row = rowCreator.create(fields, item, depth, errorConsumer);
                if (row == null) {
                    hide = true;
                }
            }

            if (!hide) {
                // Increment the overall position.
                pos.incrementAndGet();

                // Add child results if a node is open.
                if (openGroups != null && openGroups.contains(item.getKey())) {
                    addTableResults(
                            data,
                            fields,
                            maxResults,
                            offset,
                            length,
                            openGroups,
                            resultList,
                            data.get(item.getKey()),
                            depth + 1,
                            pos,
                            rowCreator,
                            errorConsumer);
                }

                // Increment the total results at this depth.
                resultCountAtThisLevel++;
                // Stop adding results if we have reached the maximum for this level.
                if (resultCountAtThisLevel >= maxResultsAtThisDepth) {
                    break;
                }
            }
        }
    }

    private interface RowCreator {

        Row create(Field[] fields,
                   Item item,
                   int depth,
                   ErrorConsumer errorConsumer);

        boolean hidesRows();
    }

    private static class SimpleRowCreator implements RowCreator {

        private final FieldFormatter fieldFormatter;

        private SimpleRowCreator(final FieldFormatter fieldFormatter) {
            this.fieldFormatter = fieldFormatter;
        }

        public static Optional<RowCreator> create(final FieldFormatter fieldFormatter) {
            return Optional.of(new SimpleRowCreator(fieldFormatter));
        }

        @Override
        public Row create(final Field[] fields,
                          final Item item,
                          final int depth,
                          final ErrorConsumer errorConsumer) {
            final List<String> stringValues = new ArrayList<>(fields.length);
            for (int i = 0; i < fields.length; i++) {
                final Field field = fields[i];
                final Val val = item.getValue(i);
                final String string = fieldFormatter.format(field, val);
                stringValues.add(string);
            }

            return Row.builder()
                    .groupKey(OpenGroupsConverter.encode(item.getKey()))
                    .values(stringValues)
                    .depth(depth)
                    .build();
        }

        @Override
        public boolean hidesRows() {
            return false;
        }
    }

    private static class ConditionalFormattingRowCreator implements RowCreator {

        private final FieldFormatter fieldFormatter;
        private final List<ConditionalFormattingRule> rules;
        private final ConditionalFormattingExpressionMatcher expressionMatcher;

        private ConditionalFormattingRowCreator(final FieldFormatter fieldFormatter,
                                                final List<ConditionalFormattingRule> rules,
                                                final ConditionalFormattingExpressionMatcher expressionMatcher) {
            this.fieldFormatter = fieldFormatter;
            this.rules = rules;
            this.expressionMatcher = expressionMatcher;
        }

        public static Optional<RowCreator> create(final FieldFormatter fieldFormatter,
                                                  final TableSettings tableSettings) {
            // Create conditional formatting expression matcher.
            List<ConditionalFormattingRule> rules = tableSettings.getConditionalFormattingRules();
            if (rules != null) {
                rules = rules
                        .stream()
                        .filter(ConditionalFormattingRule::isEnabled)
                        .collect(Collectors.toList());
                if (rules.size() > 0) {
                    final ConditionalFormattingExpressionMatcher expressionMatcher =
                            new ConditionalFormattingExpressionMatcher(tableSettings.getFields());
                    return Optional.of(new ConditionalFormattingRowCreator(fieldFormatter, rules, expressionMatcher));
                }
            }

            return Optional.empty();
        }

        @Override
        public Row create(final Field[] fields,
                          final Item item,
                          final int depth,
                          final ErrorConsumer errorConsumer) {
            Row row = null;

            final Map<String, Object> fieldIdToValueMap = new HashMap<>();
            final List<String> stringValues = new ArrayList<>(fields.length);
            for (int i = 0; i < fields.length; i++) {
                final Field field = fields[i];
                final Val val = item.getValue(i);
                final String string = fieldFormatter.format(field, val);
                stringValues.add(string);
                fieldIdToValueMap.put(field.getName(), string);
            }

            // Find a matching rule.
            ConditionalFormattingRule matchingRule = null;

            try {
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
                            .groupKey(OpenGroupsConverter.encode(item.getKey()))
                            .values(stringValues)
                            .depth(depth);

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
                final Row.Builder builder = Row.builder()
                        .groupKey(OpenGroupsConverter.encode(item.getKey()))
                        .values(stringValues)
                        .depth(depth);

                row = builder.build();
            }

            return row;
        }

        @Override
        public boolean hidesRows() {
            return true;
        }
    }
}
