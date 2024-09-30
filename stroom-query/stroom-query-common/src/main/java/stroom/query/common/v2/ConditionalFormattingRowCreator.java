package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.base.Predicates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class ConditionalFormattingRowCreator implements ItemMapper<Row> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConditionalFormattingRowCreator.class);

    private final ColumnFormatter fieldFormatter;
    private final KeyFactory keyFactory;
    private final Predicate<Map<String, Object>> rowFilter;
    private final List<RuleAndMatcher> rules;
    private final ErrorConsumer errorConsumer;

    private ConditionalFormattingRowCreator(final ColumnFormatter fieldFormatter,
                                            final KeyFactory keyFactory,
                                            final Predicate<Map<String, Object>> rowFilter,
                                            final List<RuleAndMatcher> rules,
                                            final ErrorConsumer errorConsumer) {
        this.fieldFormatter = fieldFormatter;
        this.keyFactory = keyFactory;
        this.rowFilter = rowFilter;
        this.rules = rules;
        this.errorConsumer = errorConsumer;
    }

    public static Optional<ItemMapper<Row>> create(final ColumnFormatter fieldFormatter,
                                                   final KeyFactory keyFactory,
                                                   final ExpressionOperator rowFilterExpression,
                                                   final List<ConditionalFormattingRule> rules,
                                                   final List<Column> columns,
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
                        RowExpressionMatcher.create(columns, dateTimeSettings, rowFilterExpression);
                final Predicate<Map<String, Object>> rowFilter = optionalRowExpressionMatcher
                        .map(orem -> (Predicate<Map<String, Object>>) orem)
                        .orElse(Predicates.alwaysTrue());

                final List<RuleAndMatcher> ruleAndMatchers = new ArrayList<>();
                for (final ConditionalFormattingRule rule : rules) {
                    final Optional<RowExpressionMatcher> optionalRuleFilter =
                            RowExpressionMatcher.create(columns, dateTimeSettings, rule.getExpression());
                    optionalRuleFilter.ifPresent(columnExpressionMatcher ->
                            ruleAndMatchers.add(new RuleAndMatcher(rule, columnExpressionMatcher)));
                }

                if (optionalRowExpressionMatcher.isPresent() || !ruleAndMatchers.isEmpty()) {
                    return Optional.of(new ConditionalFormattingRowCreator(
                            fieldFormatter,
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
    public Row create(final List<Column> columns,
                      final Item item) {
        Row row = null;

        final Map<String, Object> fieldIdToValueMap = new HashMap<>();
        final List<String> stringValues = new ArrayList<>(columns.size());
        int i = 0;
        for (final Column column : columns) {
            final Val val = item.getValue(i);
            final String string = fieldFormatter.format(column, val);
            stringValues.add(string);
            fieldIdToValueMap.put(column.getId(), string);
            fieldIdToValueMap.put(column.getName(), string);
            i++;
        }

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

    @Override
    public boolean hidesRows() {
        return true;
    }

    private record RuleAndMatcher(ConditionalFormattingRule rule, RowExpressionMatcher matcher) {

    }
}
