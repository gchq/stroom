/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.planb.impl.data.query;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetSpansRequest;
import stroom.pathways.shared.GetTraceOverviewRequest;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TraceHistogram;
import stroom.pathways.shared.TraceHistogramRequest;
import stroom.pathways.shared.TraceOverview;
import stroom.pathways.shared.TraceSpanPage;
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

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public TraceSpanPage getSpans(final GetSpansRequest request) {
        return tracesQueryServiceProvider.get().getLocalSpans(request);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public TraceOverview getTraceOverview(final GetTraceOverviewRequest request) {
        return tracesQueryServiceProvider.get().getLocalTraceOverview(request);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public TraceHistogram getTraceHistogram(final TraceHistogramRequest request) {
        return tracesQueryServiceProvider.get().getLocalTraceHistogram(request);
    }
}
