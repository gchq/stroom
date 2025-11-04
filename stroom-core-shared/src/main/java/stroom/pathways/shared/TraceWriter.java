package stroom.pathways.shared;

import stroom.pathways.shared.otel.trace.Span;

public interface TraceWriter extends AutoCloseable {

    void addSpan(Span span);

    @Override
    void close();
}
