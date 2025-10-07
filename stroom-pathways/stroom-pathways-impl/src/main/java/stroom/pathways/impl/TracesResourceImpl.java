package stroom.pathways.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesResource;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.data.TracesStoreImpl;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
public class TracesResourceImpl implements TracesResource {

    private final Provider<TracesStoreImpl> tracesStoreProvider;

    @Inject
    TracesResourceImpl(final Provider<TracesStoreImpl> tracesStoreProvider) {
        this.tracesStoreProvider = tracesStoreProvider;
    }

    @Override
    public ResultPage<TraceRoot> findTraces(final FindTraceCriteria criteria) {
        return tracesStoreProvider.get().findTraces(criteria);
    }

    @Override
    public Trace findTrace(final GetTraceRequest request) {
        return tracesStoreProvider.get().getTrace(request);
    }
}
