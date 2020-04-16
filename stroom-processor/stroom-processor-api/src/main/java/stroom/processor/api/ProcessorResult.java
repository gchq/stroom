package stroom.processor.api;

import stroom.util.shared.Severity;

public interface ProcessorResult {
    long getRead();

    long getWritten();

    long getMarkerCount(Severity... severity);
}
