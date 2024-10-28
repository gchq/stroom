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
import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class ConditionalFormattingRowCreator extends FilteredRowCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConditionalFormattingRowCreator.class);

    private final Predicate<Map<CIKey, Object>> rowFilter;
    private final List<RuleAndMatcher> rules;
    private final ErrorConsumer errorConsumer;

    private ConditionalFormattingRowCreator(final List<Column> originalColumns,
                                            final List<Column> newColumns,
                                            final ColumnFormatter columnFormatter,
                                            final KeyFactory keyFactory,
                                            final Predicate<Map<CIKey, Object>> rowFilter,
                                            final List<RuleAndMatcher> rules,
                                            final ErrorConsumer errorConsumer) {
        super(originalColumns, newColumns, columnFormatter, keyFactory, rowFilter, errorConsumer);

        this.rowFilter = rowFilter;
        this.rules = rules;
        this.errorConsumer = errorConsumer;
    }

    public static Optional<ItemMapper<Row>> create(final List<Column> originalColumns,
                                                   final List<Column> newColumns,
                                                   final ColumnFormatter columnFormatter,
                                                   final KeyFactory keyFactory,
                                                   final ExpressionOperator rowFilterExpression,
                                                   final List<ConditionalFormattingRule> rules,
                                                   final DateTimeSettings dateTimeSettings,
                                                   final ErrorConsumer errorConsumer) {
        // Create conditional formatting expression matcher.
        if (rules != null) {
            final List<ConditionalFormattingRule> activeRules = rules
                    .stream()
                    .filter(ConditionalFormattingRule::isEnabled)
                    .toList();
            if (!activeRules.isEmpty()) {
                final Optional<RowExpressionMatcher> optionalRowExpressionMatcher =
                        RowExpressionMatcher.create(newColumns, dateTimeSettings, rowFilterExpression);
                final Predicate<Map<CIKey, Object>> rowFilter = optionalRowExpressionMatcher
                        .map(orem -> (Predicate<Map<CIKey, Object>>) orem)
                        .orElse(RowExpressionMatcher.ALWAYS_TRUE_PREDICATE);

                final List<RuleAndMatcher> ruleAndMatchers = new ArrayList<>();
                for (final ConditionalFormattingRule rule : rules) {
                    final Optional<RowExpressionMatcher> optionalRuleFilter =
                            RowExpressionMatcher.create(newColumns, dateTimeSettings, rule.getExpression());
                    optionalRuleFilter.ifPresent(columnExpressionMatcher ->
                            ruleAndMatchers.add(new RuleAndMatcher(rule, columnExpressionMatcher)));
                }

                if (optionalRowExpressionMatcher.isPresent() || !ruleAndMatchers.isEmpty()) {
                    return Optional.of(new ConditionalFormattingRowCreator(
                            originalColumns,
                            newColumns,
                            columnFormatter,
                            keyFactory,
                            rowFilter,
                            ruleAndMatchers,
                            errorConsumer));
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public Row create(final Item item,
                      final List<String> stringValues,
                      final Map<CIKey, Object> fieldIdToValueMap) {
        Row row = null;

        // Removed in 7.5
//        final Map<String, Object> fieldIdToValueMap = new HashMap<>();
//        final List<String> stringValues = new ArrayList<>(columns.size());
//        int i = 0;
//        for (final Column column : columns) {
//            final Val val = item.getValue(i);
//            final String string = fieldFormatter.format(column, val);
//            stringValues.add(string);
//            fieldIdToValueMap.put(column.getId(), string);
//            fieldIdToValueMap.put(column.getName(), string);
//            i++;
//        }

        // Find a matching rule.
        ConditionalFormattingRule matchingRule = null;

        try {
            // See if we can exit early by applying row filter.
            if (!rowFilter.test(fieldIdToValueMap)) {
                return null;
            }

            for (final RuleAndMatcher ruleAndMatcher : rules) {
                try {
                    final boolean match = ruleAndMatcher.matcher.test(fieldIdToValueMap);
                    if (match) {
                        matchingRule = ruleAndMatcher.rule;
                        break;
                    }
                } catch (final RuntimeException e) {
                    final RuntimeException exception = new RuntimeException(
                            "Error applying conditional formatting rule: " +
                                    ruleAndMatcher.rule.toString() +
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

    private record RuleAndMatcher(ConditionalFormattingRule rule, RowExpressionMatcher matcher) {

    }
}
