package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.util.shared.ResultPage;

public interface TracesStore {

    ResultPage<TraceRoot> findTraces(FindTraceCriteria criteria);

    Trace findTrace(GetTraceRequest request);

    void addSpan(DocRef docRef, Span span);
}
