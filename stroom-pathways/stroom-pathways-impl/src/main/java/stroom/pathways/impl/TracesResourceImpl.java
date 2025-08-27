package stroom.pathways.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.TracesResource;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
public class TracesResourceImpl implements TracesResource {

    private final Provider<TracesStore> tracesStoreProvider;

    @Inject
    TracesResourceImpl(final Provider<TracesStore> tracesStoreProvider) {
        this.tracesStoreProvider = tracesStoreProvider;
    }

    @Override
    public ResultPage<Trace> findTraces(final FindTraceCriteria criteria) {
        return tracesStoreProvider.get().findTraces(criteria);
    }
}
