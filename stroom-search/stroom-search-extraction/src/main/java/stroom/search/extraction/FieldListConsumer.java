package stroom.search.extraction;

import stroom.pipeline.filter.FieldValue;

import java.util.List;

public interface FieldListConsumer {

    void accept(List<FieldValue> fieldValues);
}
