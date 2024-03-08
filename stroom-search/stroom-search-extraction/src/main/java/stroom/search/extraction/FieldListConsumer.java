package stroom.search.extraction;

import stroom.query.common.v2.StringFieldValue;

import java.util.List;

public interface FieldListConsumer {

    void acceptFieldValues(List<FieldValue> fieldValues);

    void acceptStringValues(List<StringFieldValue> stringValues);
}
