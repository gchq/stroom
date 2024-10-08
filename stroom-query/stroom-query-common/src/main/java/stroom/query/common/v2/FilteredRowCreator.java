package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.format.ColumnFormatter;
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

public class FilteredRowCreator extends SimpleRowCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FilteredRowCreator.class);

    private final Predicate<Map<String, Object>> rowFilter;

    FilteredRowCreator(final List<Column> originalColumns,
                               final List<Column> newColumns,
                               final ColumnFormatter columnFormatter,
                               final KeyFactory keyFactory,
                               final Predicate<Map<String, Object>> rowFilter,
                               final ErrorConsumer errorConsumer) {
        super(originalColumns, newColumns, columnFormatter, keyFactory, errorConsumer);

        this.rowFilter = rowFilter;
    }

    public static Optional<ItemMapper<Row>> create(final List<Column> originalColumns,
                                                   final List<Column> newColumns,
                                                   final ColumnFormatter columnFormatter,
                                                   final KeyFactory keyFactory,
                                                   final ExpressionOperator rowFilterExpression,
                                                   final DateTimeSettings dateTimeSettings,
                                                   final ErrorConsumer errorConsumer) {
        if (ExpressionUtil.hasTerms(rowFilterExpression)) {
            final Optional<RowExpressionMatcher> optionalRowExpressionMatcher =
                    RowExpressionMatcher.create(newColumns, dateTimeSettings, rowFilterExpression);
            final Predicate<Map<String, Object>> rowFilter = optionalRowExpressionMatcher
                    .map(orem -> (Predicate<Map<String, Object>>) orem)
                    .orElse(Predicates.alwaysTrue());

            return Optional.of(new FilteredRowCreator(
                    originalColumns,
                    newColumns,
                    columnFormatter,
                    keyFactory,
                    rowFilter,
                    errorConsumer));
        }
        return Optional.empty();
    }

    @Override
    public final Row create(final Item item) {
        final Map<String, Object> fieldIdToValueMap = new HashMap<>();
        final List<String> stringValues = new ArrayList<>(functions.size());
        functions.forEach(f -> {
            final String string = f.apply(item);
            stringValues.add(string);
            fieldIdToValueMap.put(f.column.getId(), string);
            fieldIdToValueMap.put(f.column.getName(), string);
        });

        return create(item, stringValues, fieldIdToValueMap);
    }

    public Row create(final Item item,
                      final List<String> stringValues,
                      final Map<String, Object> fieldIdToValueMap) {
        Row row = null;
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
