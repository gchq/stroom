package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.ExpressionPredicateBuilder.QueryFieldIndex;
import stroom.query.common.v2.ExpressionPredicateBuilder.ValuesPredicate;
import stroom.query.common.v2.format.Formatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConditionalFormattingRowCreator implements ItemMapper<Row> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConditionalFormattingRowCreator.class);

    private final int[] columnIndexMapping;
    private final KeyFactory keyFactory;
    private final ErrorConsumer errorConsumer;
    private final DateTimeSettings dateTimeSettings;
    private final Formatter[] columnFormatters;
    private final ValuesPredicate rowFilter;
    private final List<RuleAndMatcher> rules;

    private ConditionalFormattingRowCreator(final int[] columnIndexMapping,
                                            final KeyFactory keyFactory,
                                            final ErrorConsumer errorConsumer,
                                            final DateTimeSettings dateTimeSettings,
                                            final Formatter[] columnFormatters,
                                            final ValuesPredicate rowFilter,
                                            final List<RuleAndMatcher> rules) {
        this.columnIndexMapping = columnIndexMapping;
        this.keyFactory = keyFactory;
        this.errorConsumer = errorConsumer;
        this.dateTimeSettings = dateTimeSettings;
        this.columnFormatters = columnFormatters;
        this.rowFilter = rowFilter;
        this.rules = rules;
    }

    public static Optional<ItemMapper<Row>> create(final List<Column> originalColumns,
                                                   final List<Column> newColumns,
                                                   final boolean applyValueFilters,
                                                   final FormatterFactory formatterFactory,
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
                final List<RuleAndMatcher> ruleAndMatchers = new ArrayList<>();
                final QueryFieldIndex queryFieldIndex = RowUtil.createColumnNameQueryFieldIndex(newColumns);
                for (final ConditionalFormattingRule rule : activeRules) {
                    final Optional<ValuesPredicate> optionalValuesPredicate =
                            ExpressionPredicateBuilder.create(rule.getExpression(), queryFieldIndex, dateTimeSettings);
                    optionalValuesPredicate.ifPresent(columnExpressionMatcher ->
                            ruleAndMatchers.add(new RuleAndMatcher(rule, columnExpressionMatcher)));
                }

                if (!ruleAndMatchers.isEmpty()) {
                    final int[] columnIndexMapping = RowUtil.createColumnIndexMapping(originalColumns, newColumns);
                    final Formatter[] formatters = RowUtil.createFormatters(newColumns, formatterFactory);
                    final Optional<ValuesPredicate> rowFilter = FilteredRowCreator
                            .createValuesPredicate(newColumns,
                                    applyValueFilters,
                                    rowFilterExpression,
                                    dateTimeSettings);

                    return Optional.of(new ConditionalFormattingRowCreator(
                            columnIndexMapping,
                            keyFactory,
                            errorConsumer,
                            dateTimeSettings,
                            formatters,
                            rowFilter.orElse(values -> true),
                            ruleAndMatchers));
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public final Row create(final Item item) {
        Row row = null;

        // Create values array.
        final Val[] values = RowUtil.createValuesArray(item, columnIndexMapping);
        final ValValues valValues = new ValValues(dateTimeSettings, values);
        if (rowFilter.test(valValues)) {
            // Find a matching rule.
            ConditionalFormattingRule matchingRule = null;
            for (final RuleAndMatcher ruleAndMatcher : rules) {
                try {
                    final boolean match = ruleAndMatcher.matcher.test(valValues);
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

            // Now apply formatting choices.
            final List<String> stringValues = RowUtil.convertValues(values, columnFormatters);


            try {
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
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
                errorConsumer.add(e);
            }
        }

        return row;
    }

    @Override
    public boolean hidesRows() {
        return true;
    }

    private record RuleAndMatcher(ConditionalFormattingRule rule, ValuesPredicate matcher) {

    }
}
