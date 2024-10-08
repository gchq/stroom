package stroom.query.common.v2;

import stroom.query.api.v2.Column;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SimpleRowCreator implements ItemMapper<Row> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SimpleRowCreator.class);

    protected final KeyFactory keyFactory;
    protected final ErrorConsumer errorConsumer;
    protected final List<ColumnFunction> functions;

    SimpleRowCreator(final List<Column> originalColumns,
                     final List<Column> newColumns,
                     final ColumnFormatter columnFormatter,
                     final KeyFactory keyFactory,
                     final ErrorConsumer errorConsumer) {
        this.keyFactory = keyFactory;
        this.errorConsumer = errorConsumer;

        // Map original columns to their position.
        final Map<Column, Integer> originalColumnIndex = new HashMap<>();
        for (int i = 0; i < originalColumns.size(); i++) {
            final Column column = originalColumns.get(i);
            originalColumnIndex.put(column, i);
        }

        // Map new columns to original columns as close as we can.
        final ColumnMap columnMap = new ColumnMap(originalColumns, newColumns);
        functions = new ArrayList<>();
        for (final Column newColumn : newColumns) {
            final Column originalColumn = columnMap.getOrignalColumnFromNewColumn(newColumn);
            if (originalColumn != null) {
                final int index = originalColumnIndex.get(originalColumn);
                final ColumnFunction f = new ColumnFunction(newColumn) {
                    @Override
                    public String apply(final Item item) {
                        try {
                            final Val val = item.getValue(index);
                            return columnFormatter.format(column, val);
                        } catch (final RuntimeException e) {
                            LOGGER.error(LogUtil.message(
                                    "Error getting column value for column {} at index {}",
                                    column,
                                    index), e);
                            throw e;
                        }
                    }
                };
                functions.add(f);

            } else {
                functions.add(new ColumnFunction(newColumn));
            }
        }
    }

    public static Optional<ItemMapper<Row>> create(final List<Column> originalColumns,
                                                   final List<Column> newColumns,
                                                   final ColumnFormatter columnFormatter,
                                                   final KeyFactory keyFactory,
                                                   final ErrorConsumer errorConsumer) {
        return Optional.of(new SimpleRowCreator(
                originalColumns,
                newColumns,
                columnFormatter,
                keyFactory,
                errorConsumer));
    }

    @Override
    public Row create(final Item item) {
        final List<String> stringValues = functions.stream().map(f -> f.apply(item)).toList();
        return Row.builder()
                .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                .values(stringValues)
                .depth(item.getKey().getDepth())
                .build();
    }

    @Override
    public boolean hidesRows() {
        return false;
    }
}
