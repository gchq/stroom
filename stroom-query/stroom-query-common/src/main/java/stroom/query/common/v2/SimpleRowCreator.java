package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.Row;
import stroom.query.common.v2.format.Formatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.ref.ErrorConsumer;

import java.util.List;

public class SimpleRowCreator implements ItemMapper<Row> {

    private final KeyFactory keyFactory;
    private final ErrorConsumer errorConsumer;
    private final int[] columnIndexMapping;
    private final Formatter[] columnFormatters;

    private SimpleRowCreator(final int[] columnIndexMapping,
                             final Formatter[] columnFormatters,
                             final KeyFactory keyFactory,
                             final ErrorConsumer errorConsumer) {
        this.columnIndexMapping = columnIndexMapping;
        this.columnFormatters = columnFormatters;
        this.keyFactory = keyFactory;
        this.errorConsumer = errorConsumer;
    }

    public static ItemMapper<Row> create(final List<Column> originalColumns,
                                         final List<Column> newColumns,
                                         final FormatterFactory formatterFactory,
                                         final KeyFactory keyFactory,
                                         final ErrorConsumer errorConsumer) {
        final int[] columnIndexMapping = RowUtil.createColumnIndexMapping(originalColumns, newColumns);
        final Formatter[] formatters = RowUtil.createFormatters(newColumns, formatterFactory);
        return new SimpleRowCreator(
                columnIndexMapping,
                formatters,
                keyFactory,
                errorConsumer);
    }

    @Override
    public Row create(final Item item) {
        final List<String> stringValues = RowUtil.convertValuesDirectly(item, columnIndexMapping, columnFormatters);
        return Row.builder()
                .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                .values(stringValues)
                .depth(item.getKey().getDepth())
                .build();
    }
}
