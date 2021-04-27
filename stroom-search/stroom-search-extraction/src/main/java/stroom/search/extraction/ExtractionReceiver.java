package stroom.search.extraction;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.query.common.v2.Receiver;

public interface ExtractionReceiver extends Receiver {
    FieldIndex getFieldMap();
}
