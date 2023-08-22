package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FilteredRowCreator implements ItemMapper<Row> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FilteredRowCreator.class);

    private final FieldFormatter fieldFormatter;
    private final KeyFactory keyFactory;
    private final ExpressionOperator rowFilter;
    private final FieldExpressionMatcher expressionMatcher;
    private final ErrorConsumer errorConsumer;

    private FilteredRowCreator(final FieldFormatter fieldFormatter,
                               final KeyFactory keyFactory,
                               final ExpressionOperator rowFilter,
                               final FieldExpressionMatcher expressionMatcher,
                               final ErrorConsumer errorConsumer) {
        this.fieldFormatter = fieldFormatter;
        this.keyFactory = keyFactory;
        this.rowFilter = rowFilter;
        this.expressionMatcher = expressionMatcher;
        this.errorConsumer = errorConsumer;
    }

    public static Optional<ItemMapper<Row>> create(final FieldFormatter fieldFormatter,
                                                   final KeyFactory keyFactory,
                                                   final ExpressionOperator rowFilter,
                                                   final List<Field> fields,
                                                   final ErrorConsumer errorConsumer) {
        if (rowFilter != null) {
            final FieldExpressionMatcher expressionMatcher = new FieldExpressionMatcher(fields);
            return Optional.of(new FilteredRowCreator(
                    fieldFormatter,
                    keyFactory,
                    rowFilter,
                    expressionMatcher,
                    errorConsumer));
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
            fieldIdToValueMap.put(field.getName(), string);
            i++;
        }

        try {
            // See if we can exit early by applying row filter.
            if (!expressionMatcher.match(fieldIdToValueMap, rowFilter)) {
                return null;
            }

            row = Row.builder()
                    .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                    .values(stringValues)
                    .depth(item.getKey().getDepth())
                    .build();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            errorConsumer.add(e);
        }

        return row;
    }

    @Override
    public boolean hidesRows() {
        return true;
    }
}
