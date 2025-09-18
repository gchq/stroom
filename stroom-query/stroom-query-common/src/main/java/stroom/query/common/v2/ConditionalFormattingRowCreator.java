package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Row;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.format.Formatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class ConditionalFormattingRowCreator implements ItemMapper<Row> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConditionalFormattingRowCreator.class);

    private final int[] columnIndexMapping;
    private final KeyFactory keyFactory;
    private final ErrorConsumer errorConsumer;
    private final Formatter[] columnFormatters;
    private final Predicate<Val[]> rowFilter;
    private final List<RuleAndMatcher> rules;

    private ConditionalFormattingRowCreator(final int[] columnIndexMapping,
                                            final KeyFactory keyFactory,
                                            final ErrorConsumer errorConsumer,
                                            final Formatter[] columnFormatters,
                                            final Predicate<Val[]> rowFilter,
                                            final List<RuleAndMatcher> rules) {
        this.columnIndexMapping = columnIndexMapping;
        this.keyFactory = keyFactory;
        this.errorConsumer = errorConsumer;
        this.columnFormatters = columnFormatters;
        this.rowFilter = rowFilter;
        this.rules = rules;
    }

    public static ItemMapper<Row> create(final List<Column> originalColumns,
                                         final List<Column> newColumns,
                                         final boolean applyValueFilters,
                                         final FormatterFactory formatterFactory,
                                         final KeyFactory keyFactory,
                                         final ExpressionOperator rowFilterExpression,
                                         final List<ConditionalFormattingRule> rules,
                                         final DateTimeSettings dateTimeSettings,
                                         final ExpressionPredicateFactory expressionPredicateFactory,
                                         final ErrorConsumer errorConsumer) {
        // Create conditional formatting expression matcher.
        if (rules != null) {
            final List<ConditionalFormattingRule> activeRules = rules
                    .stream()
                    .filter(ConditionalFormattingRule::isEnabled)
                    .toList();
            if (!activeRules.isEmpty()) {
                final List<RuleAndMatcher> ruleAndMatchers = new ArrayList<>();
                final ValueFunctionFactories<Val[]> queryFieldIndex = RowUtil.createColumnNameValExtractor(newColumns);
                for (final ConditionalFormattingRule rule : activeRules) {
                    try {
                        final Optional<Predicate<Val[]>> optionalValuesPredicate =
                                expressionPredicateFactory.createOptional(
                                        rule.getExpression(),
                                        queryFieldIndex,
                                        dateTimeSettings);
                        final Predicate<Val[]> predicate = optionalValuesPredicate.orElse(t -> true);
                        ruleAndMatchers.add(new RuleAndMatcher(rule, predicate));
                    } catch (final RuntimeException e) {
                        throw new RuntimeException("Error evaluating conditional formatting rule: " +
                                                   rule.getExpression() +
                                                   " (" +
                                                   e.getMessage() +
                                                   ")", e);
                    }
                }

                final int[] columnIndexMapping = RowUtil.createColumnIndexMapping(originalColumns, newColumns);
                final Formatter[] formatters = RowUtil.createFormatters(newColumns, formatterFactory);
                final Optional<Predicate<Val[]>> rowFilter = FilteredRowCreator
                        .createValuesPredicate(newColumns,
                                applyValueFilters,
                                rowFilterExpression,
                                dateTimeSettings,
                                expressionPredicateFactory);

                return new ConditionalFormattingRowCreator(
                        columnIndexMapping,
                        keyFactory,
                        errorConsumer,
                        formatters,
                        rowFilter.orElse(values -> true),
                        ruleAndMatchers);
            }
        }

        // We didn't have any conditional formatting rules to apply so provide a filtered row creator.
        return FilteredRowCreator.create(
                originalColumns,
                newColumns,
                applyValueFilters,
                formatterFactory,
                keyFactory,
                rowFilterExpression,
                dateTimeSettings,
                errorConsumer,
                expressionPredicateFactory);
    }

    @Override
    public final Row create(final Item item) {
        Row row = null;

        // Create values array.
        final Val[] values = RowUtil.createValuesArray(item, columnIndexMapping);
        if (rowFilter.test(values)) {
            // Find a matching rule.
            ConditionalFormattingRule matchingRule = null;
            for (final RuleAndMatcher ruleAndMatcher : rules) {
                try {
                    final boolean match = ruleAndMatcher.matcher.test(values);
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
                        row = Row.builder()
                                .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                                .values(stringValues)
                                .depth(item.getKey().getDepth())
                                .matchingRule(matchingRule.getId())
                                .build();
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

    public record RuleAndMatcher(ConditionalFormattingRule rule, Predicate<Val[]> matcher) {

    }
}
