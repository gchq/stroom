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
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ConditionalFormattingMapper implements ItemMapper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConditionalFormattingMapper.class);

    private final ErrorConsumer errorConsumer;
    private final List<RuleAndMatcher> rules;
    private final ItemMapper parentMapper;
    private final boolean hidesRows;
    private final String componentId;
    private final String componentName;

    private ConditionalFormattingMapper(final String componentId, final String componentName,
                                        final ErrorConsumer errorConsumer,
                                        final List<RuleAndMatcher> rules,
                                        final ItemMapper parentMapper,
                                        final boolean hidesRows) {
        this.componentId = componentId;
        this.componentName = componentName;
        this.errorConsumer = errorConsumer;
        this.rules = rules;
        this.parentMapper = parentMapper;
        this.hidesRows = hidesRows;
    }

    public static ItemMapper create(final String componentId, final String componentName,
                                    final List<Column> newColumns,
                                    final List<ConditionalFormattingRule> rules,
                                    final DateTimeSettings dateTimeSettings,
                                    final ExpressionPredicateFactory expressionPredicateFactory,
                                    final ErrorConsumer errorConsumer,
                                    final ItemMapper parentMapper) {
        // Create conditional formatting expression matcher.
        if (rules != null) {
            boolean hidesRows = parentMapper.hidesRows();
            final List<ConditionalFormattingRule> activeRules = rules
                    .stream()
                    .filter(ConditionalFormattingRule::isEnabled)
                    .toList();
            if (!activeRules.isEmpty()) {
                final List<RuleAndMatcher> ruleAndMatchers = new ArrayList<>();
                final ValueFunctionFactories<Values> queryFieldIndex = RowUtil.createColumnNameValExtractor(newColumns);
                for (final ConditionalFormattingRule rule : activeRules) {
                    try {
                        if (rule.isHide()) {
                            hidesRows = true;
                        }

                        final Optional<Predicate<Values>> optionalValuesPredicate =
                                expressionPredicateFactory.createOptional(
                                        rule.getExpression(),
                                        queryFieldIndex,
                                        dateTimeSettings);
                        final Predicate<Values> predicate = optionalValuesPredicate.orElse(t -> true);
                        ruleAndMatchers.add(new RuleAndMatcher(rule, predicate));
                    } catch (final RuntimeException e) {
                        throw new RuntimeException(
                                "Error evaluating conditional formatting rule" +
                                (componentName == null
                                        ? ""
                                        : " on \"" + componentName + "\" [" + componentId + "]") +
                                ": " + rule.getExpression() + " (" + e.getMessage() + ")", e);
                    }
                }

                return new ConditionalFormattingMapper(
                        componentId, componentName,
                        errorConsumer,
                        ruleAndMatchers,
                        parentMapper,
                        hidesRows);
            }
        }

        return parentMapper;
    }

    @Override
    public final Stream<Item> create(final Item itm) {
        return parentMapper
                .create(itm)
                .flatMap(item -> {
                    try {
                        // Find a matching conditional formatting rule.
                        final ConditionalFormattingRule matchingRule = findMatchingRule(item);
                        if (matchingRule != null) {
                            if (!matchingRule.isHide()) {
                                return Stream.of(new ConditionalFormattedItem(item, matchingRule.getId()));
                            } else {
                                return Stream.empty();
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                        errorConsumer.add(e);
                    }
                    return Stream.of(item);
                });
    }

    private ConditionalFormattingRule findMatchingRule(final Item values) {
        for (final RuleAndMatcher ruleAndMatcher : rules) {
            try {
                final boolean match = ruleAndMatcher.matcher.test(values);
                if (match) {
                    return ruleAndMatcher.rule;
                }
            } catch (final RuntimeException e) {
                final RuntimeException exception = new RuntimeException(
                        "Error applying conditional formatting rule" +
                        (componentName == null
                                ? ""
                                : " on \"" + componentName + "\" [" + componentId + "]") +
                        ": " + ruleAndMatcher.rule.toString() + " - " + e.getMessage());
                LOGGER.debug(exception.getMessage(), exception);
                errorConsumer.add(exception);
            }
        }
        return null;
    }

    @Override
    public boolean hidesRows() {
        return hidesRows;
    }

    public record RuleAndMatcher(ConditionalFormattingRule rule, Predicate<Values> matcher) {

    }
}
