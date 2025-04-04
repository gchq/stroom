package stroom.search.extraction;

import stroom.query.api.Query;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;

public interface AnnotationsDecoratorFactory {

    ValuesConsumer create(ValuesConsumer valuesConsumer, FieldIndex fieldIndex, Query query);
}
