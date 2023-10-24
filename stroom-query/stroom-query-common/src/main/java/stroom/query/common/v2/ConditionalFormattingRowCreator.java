package stroom.query.common.v2;

import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ConditionalFormattingRowCreator implements ItemMapper<Row> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConditionalFormattingRowCreator.class);

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
            fieldIdToValueMap.put(field.getId(), string);
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
