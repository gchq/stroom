package stroom.search.extraction;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.query.api.v2.Query;

public interface AnnotationsDecoratorFactory {

    ValuesConsumer create(ValuesConsumer valuesConsumer, FieldIndex fieldIndex, Query query);
}
