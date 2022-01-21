package stroom.search.extraction;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ValuesConsumer;

public interface ExtractionReceiver extends ValuesConsumer {

    FieldIndex getFieldIndex();
}
