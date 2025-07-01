package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.Row;
import stroom.query.common.v2.format.Formatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;

import java.util.List;

public class SimpleRowCreator implements ItemMapper<Row> {

    private final KeyFactory keyFactory;
    private final ErrorConsumer errorConsumer;
    private final int[] columnIndexMapping;
    private final Formatter[] columnFormatters;
    private final AnnotationsPostProcessor annotationsPostProcessor;

    private SimpleRowCreator(final int[] columnIndexMapping,
                             final Formatter[] columnFormatters,
                             final KeyFactory keyFactory,
                             final ErrorConsumer errorConsumer,
                             final AnnotationsPostProcessor annotationsPostProcessor) {
        this.columnIndexMapping = columnIndexMapping;
        this.columnFormatters = columnFormatters;
        this.keyFactory = keyFactory;
        this.errorConsumer = errorConsumer;
        this.annotationsPostProcessor = annotationsPostProcessor;
    }

    public static ItemMapper<Row> create(final List<Column> originalColumns,
                                         final List<Column> newColumns,
                                         final FormatterFactory formatterFactory,
                                         final KeyFactory keyFactory,
                                         final ErrorConsumer errorConsumer,
                                         final AnnotationsPostProcessor annotationsPostProcessor) {
        final int[] columnIndexMapping = RowUtil.createColumnIndexMapping(originalColumns, newColumns);
        final Formatter[] formatters = RowUtil.createFormatters(newColumns, formatterFactory);
        return new SimpleRowCreator(
                columnIndexMapping,
                formatters,
                keyFactory,
                errorConsumer,
                annotationsPostProcessor);
    }

    @Override
    public List<Row> create(final Item item) {
        final Val[] values = RowUtil.createValuesArray(item, columnIndexMapping);
        return annotationsPostProcessor
                .convert(values, errorConsumer, (annotationId, vals) -> {
                    final List<String> stringValues = RowUtil.convertValues(vals, columnFormatters);
                    return Row.builder()
                            .groupKey(keyFactory.encode(item.getKey(), errorConsumer))
                            .annotationId(annotationId)
                            .values(stringValues)
                            .depth(item.getKey().getDepth())
                            .build();
                });
    }
}
