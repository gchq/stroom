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
import java.util.List;
import java.util.Optional;

public class SimpleRowCreator implements ItemMapper<Row> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SimpleRowCreator.class);

    private final ColumnFormatter columnFormatter;
    private final KeyFactory keyFactory;
    private final ErrorConsumer errorConsumer;

    private SimpleRowCreator(final ColumnFormatter columnFormatter,
                             final KeyFactory keyFactory,
                             final ErrorConsumer errorConsumer) {
        this.columnFormatter = columnFormatter;
        this.keyFactory = keyFactory;
        this.errorConsumer = errorConsumer;
    }

    public static Optional<ItemMapper<Row>> create(final ColumnFormatter columnFormatter,
                                                   final KeyFactory keyFactory,
                                                   final ErrorConsumer errorConsumer) {
        return Optional.of(new SimpleRowCreator(columnFormatter, keyFactory, errorConsumer));
    }

    @Override
    public Row create(final List<Column> columns,
                      final Item item) {
        final List<String> stringValues = new ArrayList<>(columns.size());
        int i = 0;
        for (final Column column : columns) {
            try {
                final Val val = item.getValue(i);
                final String string = columnFormatter.format(column, val);
                stringValues.add(string);
            } catch (final RuntimeException e) {
                LOGGER.error(LogUtil.message("Error getting column value for column {} at index {}", column, i), e);
                throw e;
            }
            i++;
        }

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
