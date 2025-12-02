package stroom.planb.impl.data;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.otel.trace.Trace;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class TracesRemoteQueryResourceImpl implements TracesRemoteQueryResource {

    private final Provider<TracesStoreImpl> tracesQueryServiceProvider;

    @Inject
    public TracesRemoteQueryResourceImpl(final Provider<TracesStoreImpl> tracesQueryServiceProvider) {
        this.tracesQueryServiceProvider = tracesQueryServiceProvider;
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public TracesResultPage getTraces(final FindTraceCriteria criteria) {
        return tracesQueryServiceProvider.get().getLocalTraces(criteria);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Trace getTrace(final GetTraceRequest request) {
        return tracesQueryServiceProvider.get().getLocalTrace(request);
    }
}
