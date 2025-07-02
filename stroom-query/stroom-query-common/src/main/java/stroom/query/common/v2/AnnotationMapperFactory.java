package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.language.functions.ref.ErrorConsumer;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public interface AnnotationMapperFactory {

    ItemMapper PASS_THOROUGH = Stream::of;

    AnnotationMapperFactory NO_OP = new AnnotationMapperFactory() {
        @Override
        public ItemMapper createMapper(final List<Column> newColumns,
                                             final ErrorConsumer errorConsumer,
                                             final ItemMapper parentMapper) {
            return PASS_THOROUGH;
        }

        @Override
        public AnnotationColumnValueProvider createValues(final List<Column> columns,
                                                          final int columnIndex) {
            return item -> Collections.singletonList(item.getValue(columnIndex));
        }
    };

    ItemMapper createMapper(List<Column> newColumns,
                                  ErrorConsumer errorConsumer,
                                  ItemMapper parentMapper);

    AnnotationColumnValueProvider createValues(List<Column> columns, int columnIndex);
}
