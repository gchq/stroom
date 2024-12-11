package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.format.Formatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class FilteredRowCreator implements ItemMapper<Row> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FilteredRowCreator.class);

    private final int[] columnIndexMapping;
    private final KeyFactory keyFactory;
    private final ErrorConsumer errorConsumer;
    private final Formatter[] columnFormatters;
    private final Predicate<Val[]> rowFilter;

    private FilteredRowCreator(final int[] columnIndexMapping,
                               final KeyFactory keyFactory,
                               final ErrorConsumer errorConsumer,
                               final Formatter[] columnFormatters,
                               final Predicate<Val[]> rowFilter) {
        this.columnIndexMapping = columnIndexMapping;
        this.keyFactory = keyFactory;
        this.errorConsumer = errorConsumer;
        this.columnFormatters = columnFormatters;
        this.rowFilter = rowFilter;
    }

    public static Optional<ItemMapper<Row>> create(final List<Column> originalColumns,
                                                   final List<Column> newColumns,
                                                   final boolean applyValueFilters,
                                                   final FormatterFactory formatterFactory,
                                                   final KeyFactory keyFactory,
                                                   final ExpressionOperator rowFilterExpression,
                                                   final DateTimeSettings dateTimeSettings,
                                                   final ErrorConsumer errorConsumer,
                                                   final ExpressionPredicateFactory expressionPredicateFactory) {
        // Combine filters.
        final Optional<Predicate<Val[]>> optionalCombinedPredicate = createValuesPredicate(
                newColumns,
                applyValueFilters,
                rowFilterExpression,
                dateTimeSettings,
                expressionPredicateFactory);
        // If we have no predicate then return an empty optional.
        if (optionalCombinedPredicate.isEmpty()) {
            return Optional.empty();
        }

        final int[] columnIndexMapping = RowUtil.createColumnIndexMapping(originalColumns, newColumns);
        final Formatter[] formatters = RowUtil.createFormatters(newColumns, formatterFactory);

        return Optional.of(new FilteredRowCreator(
                columnIndexMapping,
                keyFactory,
                errorConsumer,
                formatters,
                optionalCombinedPredicate.get()));
    }

    public static Optional<Predicate<Val[]>> createValuesPredicate(final List<Column> newColumns,
                                                                   final boolean applyValueFilters,
                                                                   final ExpressionOperator rowFilterExpression,
                                                                   final DateTimeSettings dateTimeSettings,
                                                                   final ExpressionPredicateFactory
                                                                           expressionPredicateFactory) {
        Optional<Predicate<Val[]>> valuesPredicate = Optional.empty();

        // Apply value filters.
        if (applyValueFilters) {
            // Create column value filter expression.
            final Optional<ExpressionOperator> optionalExpressionOperator = RowValueFilter.create(newColumns);
            valuesPredicate = optionalExpressionOperator.flatMap(expressionOperator -> {
                // Create the field position map for the new columns.
                final ValueFunctionFactories<Val[]> queryFieldIndex = RowUtil.createColumnIdValExtractors(newColumns);
                return expressionPredicateFactory.create(
                        expressionOperator,
                        queryFieldIndex,
                        dateTimeSettings);
            });
        }

        // Apply having filters.
        final ValueFunctionFactories<Val[]> queryFieldIndex = RowUtil.createColumnNameValExtractor(newColumns);
        final Optional<Predicate<Val[]>> optionalRowFilterPredicate = expressionPredicateFactory.create(
                rowFilterExpression,
                queryFieldIndex,
                dateTimeSettings);

        // Combine filters.
        return valuesPredicate
                .map(vp1 -> optionalRowFilterPredicate
                        .map(vp1::and)
                        .or(() -> Optional.of(vp1)))
                .orElse(optionalRowFilterPredicate);
    }

    @Override
    public final Row create(final Item item) {
        Row row = null;

        // Create values array.
        final Val[] values = RowUtil.createValuesArray(item, columnIndexMapping);
        if (rowFilter.test(values)) {
            // Now apply formatting choices.
            final List<String> stringValues = RowUtil.convertValues(values, columnFormatters);


            try {
                row = Row.builder()
                        .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                        .values(stringValues)
                        .depth(item.getKey().getDepth())
                        .build();
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
}
