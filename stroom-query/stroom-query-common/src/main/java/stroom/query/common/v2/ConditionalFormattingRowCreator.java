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
    private final AnnotationsPostProcessor annotationsPostProcessor;

    private ConditionalFormattingRowCreator(final int[] columnIndexMapping,
                                            final KeyFactory keyFactory,
                                            final ErrorConsumer errorConsumer,
                                            final Formatter[] columnFormatters,
                                            final Predicate<Val[]> rowFilter,
                                            final List<RuleAndMatcher> rules,
                                            final AnnotationsPostProcessor annotationsPostProcessor) {
        this.columnIndexMapping = columnIndexMapping;
        this.keyFactory = keyFactory;
        this.errorConsumer = errorConsumer;
        this.columnFormatters = columnFormatters;
        this.rowFilter = rowFilter;
        this.rules = rules;
        this.annotationsPostProcessor = annotationsPostProcessor;
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
                                         final ErrorConsumer errorConsumer,
                                         final AnnotationsPostProcessor annotationsPostProcessor) {
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
                        ruleAndMatchers,
                        annotationsPostProcessor);
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
                expressionPredicateFactory,
                annotationsPostProcessor);
    }

    @Override
    public final List<Row> create(final Item item) {
        // Create values array.
        final Val[] values = RowUtil.createValuesArray(item, columnIndexMapping);
        return annotationsPostProcessor
                .convert(values, errorConsumer, (annotationId, vals) -> {
                    try {
                        if (rowFilter.test(vals)) {
                            // Find a matching conditional formatting rule.
                            final ConditionalFormattingRule matchingRule = findMatchingRule(vals);
                            if (matchingRule != null) {
                                if (!matchingRule.isHide()) {
                                    // Now apply formatting choices.
                                    final List<String> stringValues = RowUtil.convertValues(vals, columnFormatters);
                                    return Row.builder()
                                            .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                                            .annotationId(annotationId)
                                            .values(stringValues)
                                            .depth(item.getKey().getDepth())
                                            .matchingRule(matchingRule.getId())
                                            .build();
                                }
                            } else {
                                // Now apply formatting choices.
                                final List<String> stringValues = RowUtil.convertValues(vals, columnFormatters);
                                return Row.builder()
                                        .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                                        .annotationId(annotationId)
                                        .values(stringValues)
                                        .depth(item.getKey().getDepth())
                                        .build();
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                        errorConsumer.add(e);
                    }
                    return null;
                });
    }

    private ConditionalFormattingRule findMatchingRule(final Val[] values) {
        for (final RuleAndMatcher ruleAndMatcher : rules) {
            try {
                final boolean match = ruleAndMatcher.matcher.test(values);
                if (match) {
                    return ruleAndMatcher.rule;
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
        return null;
    }

    @Override
    public boolean hidesRows() {
        return true;
    }

    private record RuleAndMatcher(ConditionalFormattingRule rule, Predicate<Val[]> matcher) {

    }
}
