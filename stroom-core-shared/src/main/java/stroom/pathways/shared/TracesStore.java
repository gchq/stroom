package stroom.pathways.shared;

import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.util.shared.ResultPage;

public interface TracesStore {

    ResultPage<TraceRoot> findTraces(FindTraceCriteria criteria);

    Trace getTrace(GetTraceRequest request);
}
