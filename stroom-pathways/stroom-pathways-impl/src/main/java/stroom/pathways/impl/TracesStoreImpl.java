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

package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetSpansRequest;
import stroom.pathways.shared.GetTraceOverviewRequest;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TraceHistogram;
import stroom.pathways.shared.TraceHistogramRequest;
import stroom.pathways.shared.TraceOverview;
import stroom.pathways.shared.TraceSpanPage;
import stroom.pathways.shared.TracesStore;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.planb.shared.PlanBDocument;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Routes each trace query to the implementation for the store's deployment, mirroring
 * {@link ShardManager#createShard}:
 * <ul>
 *     <li>a shared-filesystem doc ({@code sharedPath} set) is served locally by {@link SharedFileTracesStore},
 *     which fans out across shards + archives (and is cancellable) — it never proxies;</li>
 *     <li>a REST-legacy doc on a snapshot node is proxied by {@link ProxyTracesStore};</li>
 *     <li>a REST-legacy doc on a storage node is read from the authoritative single store by
 *     {@link RestTracesStore}.</li>
 * </ul>
 */
@Singleton
public class TracesStoreImpl implements TracesStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TracesStoreImpl.class);

    private final TracesDocLoader docLoader;
    private final ShardManager shardManager;
    private final SharedFileTracesStore sharedFileTracesStore;
    private final ProxyTracesStore proxyTracesStore;
    private final RestTracesStore restTracesStore;

    @Inject
    public TracesStoreImpl(final TracesDocLoader docLoader,
                           final ShardManager shardManager,
                           final SharedFileTracesStore sharedFileTracesStore,
                           final ProxyTracesStore proxyTracesStore,
                           final RestTracesStore restTracesStore) {
        this.docLoader = docLoader;
        this.shardManager = shardManager;
        this.sharedFileTracesStore = sharedFileTracesStore;
        this.proxyTracesStore = proxyTracesStore;
        this.restTracesStore = restTracesStore;
    }

    @Override
    public ResultPage<TraceRoot> findTraces(final FindTraceCriteria criteria) {
        return select(criteria.getDataSourceRef()).findTraces(criteria);
    }

    @Override
    public Trace getTrace(final GetTraceRequest request) {
        return select(request.getDataSourceRef()).getTrace(request);
    }

    @Override
    public TraceSpanPage getSpans(final GetSpansRequest request) {
        return select(request.getDataSourceRef()).getSpans(request);
    }

    @Override
    public TraceOverview getTraceOverview(final GetTraceOverviewRequest request) {
        return select(request.getDataSourceRef()).getTraceOverview(request);
    }

    @Override
    public TraceHistogram getTraceHistogram(final TraceHistogramRequest request) {
        return select(request.getDataSourceRef()).getTraceHistogram(request);
    }

    private TracesStore select(final DocRef docRef) {
        final PlanBDocument doc = docLoader.getPlanBDoc(docRef);
        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }
        if (doc.getSharedPath() != null && doc.getShardCount() > 0) {
            return sharedFileTracesStore;
        }
        if (shardManager.isSnapshotNode()) {
            return proxyTracesStore;
        }
        return restTracesStore;
    }
}
