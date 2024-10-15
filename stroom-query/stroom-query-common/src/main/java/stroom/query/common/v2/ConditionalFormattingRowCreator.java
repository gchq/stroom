package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.PredicateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.base.Predicates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class ConditionalFormattingRowCreator extends FilteredRowCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConditionalFormattingRowCreator.class);

    private final Predicate<RowValueMap> rowFilter;
    private final List<RuleAndMatcher> rules;
    private final ErrorConsumer errorConsumer;

    private ConditionalFormattingRowCreator(final List<Column> originalColumns,
                                            final List<Column> newColumns,
                                            final ColumnFormatter columnFormatter,
                                            final KeyFactory keyFactory,
                                            final Predicate<RowValueMap> rowFilter,
                                            final List<RuleAndMatcher> rules,
                                            final ErrorConsumer errorConsumer) {
        super(originalColumns, newColumns, columnFormatter, keyFactory, rowFilter, errorConsumer);

        this.rowFilter = rowFilter;
        this.rules = rules;
        this.errorConsumer = errorConsumer;
    }

    public static Optional<ItemMapper<Row>> create(final List<Column> originalColumns,
                                                   final List<Column> newColumns,
                                                   final boolean applyValueFilters,
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
                final Optional<Predicate<RowValueMap>> optionalRowExpressionMatcher =
                        RowFilter.create(newColumns, dateTimeSettings, rowFilterExpression, new HashMap<>());
                Optional<Predicate<RowValueMap>> optionalRowValueFilter = Optional.empty();
                if (applyValueFilters) {
                    optionalRowValueFilter = RowValueFilter.create(newColumns, dateTimeSettings, new HashMap<>());
                }

                Predicate<RowValueMap> rowFilter = Predicates.alwaysTrue();
                if (optionalRowExpressionMatcher.isPresent() && optionalRowValueFilter.isPresent()) {
                    rowFilter = PredicateUtil.andPredicates(optionalRowExpressionMatcher.get(),
                            optionalRowValueFilter.get());
                } else if (optionalRowExpressionMatcher.isPresent()) {
                    rowFilter = optionalRowExpressionMatcher.get();
                } else if (optionalRowValueFilter.isPresent()) {
                    rowFilter = optionalRowValueFilter.get();
                }

                final List<RuleAndMatcher> ruleAndMatchers = new ArrayList<>();
                for (final ConditionalFormattingRule rule : rules) {
                    final Optional<Predicate<RowValueMap>> optionalRuleFilter =
                            RowFilter.create(newColumns,
                                    dateTimeSettings,
                                    rule.getExpression(),
                                    new HashMap<>());
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
                      final RowValueMap rowValueMap) {
        Row row = null;

        // Find a matching rule.
        ConditionalFormattingRule matchingRule = null;

        try {
            // See if we can exit early by applying row filter.
            if (!rowFilter.test(rowValueMap)) {
                return null;
            }

            for (final RuleAndMatcher ruleAndMatcher : rules) {
                try {
                    final boolean match = ruleAndMatcher.matcher.test(rowValueMap);
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

    private record RuleAndMatcher(ConditionalFormattingRule rule, Predicate<RowValueMap> matcher) {

    }
}
