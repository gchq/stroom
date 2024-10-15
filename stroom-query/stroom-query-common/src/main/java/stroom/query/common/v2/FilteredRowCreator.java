package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Row;
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

public class FilteredRowCreator extends SimpleRowCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FilteredRowCreator.class);

    private final Predicate<RowValueMap> rowFilter;

    FilteredRowCreator(final List<Column> originalColumns,
                       final List<Column> newColumns,
                       final ColumnFormatter columnFormatter,
                       final KeyFactory keyFactory,
                       final Predicate<RowValueMap> rowFilter,
                       final ErrorConsumer errorConsumer) {
        super(originalColumns, newColumns, columnFormatter, keyFactory, errorConsumer);

        this.rowFilter = rowFilter;
    }

    public static Optional<ItemMapper<Row>> create(final List<Column> originalColumns,
                                                   final List<Column> newColumns,
                                                   final boolean applyValueFilters,
                                                   final ColumnFormatter columnFormatter,
                                                   final KeyFactory keyFactory,
                                                   final ExpressionOperator rowFilterExpression,
                                                   final DateTimeSettings dateTimeSettings,
                                                   final ErrorConsumer errorConsumer) {
        final Optional<Predicate<RowValueMap>> optionalRowExpressionMatcher =
                RowFilter.create(newColumns, dateTimeSettings, rowFilterExpression, new HashMap<>());
        Optional<Predicate<RowValueMap>> optionalRowValueFilter = Optional.empty();
        if (applyValueFilters) {
            optionalRowValueFilter = RowValueFilter.create(newColumns, dateTimeSettings, new HashMap<>());
        }

        Predicate<RowValueMap> rowFilter = Predicates.alwaysTrue();
        if (optionalRowExpressionMatcher.isPresent() && optionalRowValueFilter.isPresent()) {
            rowFilter = PredicateUtil.andPredicates(optionalRowExpressionMatcher.get(), optionalRowValueFilter.get());
        } else if (optionalRowExpressionMatcher.isPresent()) {
            rowFilter = optionalRowExpressionMatcher.get();
        } else if (optionalRowValueFilter.isPresent()) {
            rowFilter = optionalRowValueFilter.get();
        } else {
            return Optional.empty();
        }

        return Optional.of(new FilteredRowCreator(
                originalColumns,
                newColumns,
                columnFormatter,
                keyFactory,
                rowFilter,
                errorConsumer));
    }

    @Override
    public final Row create(final Item item) {
        final RowValueMap rowValueMap = new RowValueMap();
        final List<String> stringValues = new ArrayList<>(functions.size());
        functions.forEach(f -> {
            final String string = f.apply(item);
            stringValues.add(string);
            rowValueMap.put(f.column.getId(), string);
            rowValueMap.put(f.column.getName(), string);
        });

        return create(item, stringValues, rowValueMap);
    }

    public Row create(final Item item,
                      final List<String> stringValues,
                      final RowValueMap rowValueMap) {
        Row row = null;
        try {
            // See if we can exit early by applying row filter.
            if (!rowFilter.test(rowValueMap)) {
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
