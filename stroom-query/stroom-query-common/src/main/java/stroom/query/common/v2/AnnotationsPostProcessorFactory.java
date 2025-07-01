package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.Row;

import java.util.Collections;
import java.util.List;

public interface AnnotationsPostProcessorFactory {

    AnnotationsPostProcessor PASS_THOROUGH = (values, errorConsumer, mapping) -> {
        final Row row = mapping.apply(null, values);
        if (row == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(row);
    };

    AnnotationsPostProcessorFactory NO_OP = new AnnotationsPostProcessorFactory() {
        @Override
        public AnnotationsPostProcessor createProcessor(final List<Column> columns) {
            return PASS_THOROUGH;
        }

        @Override
        public AnnotationColumnValueProvider createValues(final List<Column> columns, final int columnIndex) {
            return item -> Collections.singletonList(item.getValue(columnIndex));
        }
    };

    AnnotationsPostProcessor createProcessor(List<Column> columns);

    AnnotationColumnValueProvider createValues(List<Column> columns, int columnIndex);
}
