package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
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

public class FilteredRowCreator implements ItemMapper<Row> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FilteredRowCreator.class);

    private final ColumnFormatter columnFormatter;
    private final KeyFactory keyFactory;
    private final Predicate<Map<String, Object>> rowFilter;
    private final ErrorConsumer errorConsumer;

    private FilteredRowCreator(final ColumnFormatter columnFormatter,
                               final KeyFactory keyFactory,
                               final Predicate<Map<String, Object>> rowFilter,
                               final ErrorConsumer errorConsumer) {
        this.columnFormatter = columnFormatter;
        this.keyFactory = keyFactory;
        this.rowFilter = rowFilter;
        this.errorConsumer = errorConsumer;
    }

    public static Optional<ItemMapper<Row>> create(final ColumnFormatter columnFormatter,
                                                   final KeyFactory keyFactory,
                                                   final ExpressionOperator rowFilterExpression,
                                                   final List<Column> columns,
                                                   final DateTimeSettings dateTimeSettings,
                                                   final ErrorConsumer errorConsumer) {
        if (ExpressionUtil.hasTerms(rowFilterExpression)) {
            final Optional<RowExpressionMatcher> optionalRowExpressionMatcher =
                    RowExpressionMatcher.create(columns, dateTimeSettings, rowFilterExpression);
            final Predicate<Map<String, Object>> rowFilter = optionalRowExpressionMatcher
                    .map(orem -> (Predicate<Map<String, Object>>) orem)
                    .orElse(Predicates.alwaysTrue());

            return Optional.of(new FilteredRowCreator(
                    columnFormatter,
                    keyFactory,
                    rowFilter,
                    errorConsumer));
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
            final String string = columnFormatter.format(column, val);
            stringValues.add(string);
            fieldIdToValueMap.put(column.getId(), string);
            fieldIdToValueMap.put(column.getName(), string);
            i++;
        }

        try {
            // See if we can exit early by applying row filter.
            if (!rowFilter.test(fieldIdToValueMap)) {
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
