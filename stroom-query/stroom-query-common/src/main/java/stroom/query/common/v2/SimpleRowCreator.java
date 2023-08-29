package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimpleRowCreator implements ItemMapper<Row> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SimpleRowCreator.class);

    private final FieldFormatter fieldFormatter;
    private final KeyFactory keyFactory;
    private final ErrorConsumer errorConsumer;

    private SimpleRowCreator(final FieldFormatter fieldFormatter,
                             final KeyFactory keyFactory,
                             final ErrorConsumer errorConsumer) {
        this.fieldFormatter = fieldFormatter;
        this.keyFactory = keyFactory;
        this.errorConsumer = errorConsumer;
    }

    public static Optional<ItemMapper<Row>> create(final FieldFormatter fieldFormatter,
                                                   final KeyFactory keyFactory,
                                                   final ErrorConsumer errorConsumer) {
        return Optional.of(new SimpleRowCreator(fieldFormatter, keyFactory, errorConsumer));
    }

    @Override
    public Row create(final List<Field> fields,
                      final Item item) {
        final List<String> stringValues = new ArrayList<>(fields.size());
        int i = 0;
        for (final Field field : fields) {
            try {
                final Val val = item.getValue(i);
                final String string = fieldFormatter.format(field, val);
                stringValues.add(string);
            } catch (final RuntimeException e) {
                LOGGER.error(LogUtil.message("Error getting field value for field {} at index {}", field, i), e);
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
