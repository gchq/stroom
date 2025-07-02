package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.Row;
import stroom.query.common.v2.format.Formatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.List;

public class SimpleRowCreator implements RowCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SimpleRowCreator.class);

    private final KeyFactory keyFactory;
    private final ErrorConsumer errorConsumer;
    private final Formatter[] columnFormatters;

    private SimpleRowCreator(final Formatter[] columnFormatters,
                             final KeyFactory keyFactory,
                             final ErrorConsumer errorConsumer) {
        this.columnFormatters = columnFormatters;
        this.keyFactory = keyFactory;
        this.errorConsumer = errorConsumer;
    }

    public static RowCreator create(final List<Column> newColumns,
                                    final FormatterFactory formatterFactory,
                                    final KeyFactory keyFactory,
                                    final ErrorConsumer errorConsumer) {
        final Formatter[] formatters = RowUtil.createFormatters(newColumns, formatterFactory);
        return new SimpleRowCreator(
                formatters,
                keyFactory,
                errorConsumer);
    }

    @Override
    public Row create(final Item item) {
        Long annotationId = null;
        String ruleId = null;
        if (item instanceof final AnnotatedItem annotatedItem) {
            annotationId = annotatedItem.getAnnotationId();
        } else if (item instanceof final ConditionalFormattedItem conditionalFormattedItem) {
            if (conditionalFormattedItem.getItem() instanceof final AnnotatedItem annotatedItem) {
                annotationId = annotatedItem.getAnnotationId();
            }
            ruleId = conditionalFormattedItem.getRuleId();
        }

        final List<String> stringValues = convertValues(item, columnFormatters);
        return Row.builder()
                .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                .annotationId(annotationId)
                .values(stringValues)
                .depth(item.getKey().getDepth())
                .matchingRule(ruleId)
                .build();
    }

    private List<String> convertValues(final Values values,
                                       final Formatter[] columnFormatters) {
        final List<String> stringValues = new ArrayList<>(columnFormatters.length);
        for (int i = 0; i < columnFormatters.length; i++) {
            try {
                final Val val = values.getValue(i);
                stringValues.add(columnFormatters[i].format(val));
            } catch (final RuntimeException e) {
                LOGGER.error(LogUtil.message("Error getting column value for column index {}", i), e);
                throw e;
            }
        }
        return stringValues;
    }
}
