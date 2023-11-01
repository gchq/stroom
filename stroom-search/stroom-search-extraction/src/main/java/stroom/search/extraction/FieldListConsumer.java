package stroom.search.extraction;

import java.util.List;

public interface FieldListConsumer {

    void accept(List<FieldValue> fieldValues);
}
