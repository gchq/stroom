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
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.data.archive.ArchiveShardLocator;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.shared.PlanBDocument;
import stroom.query.api.GroupSelection;
import stroom.query.common.v2.ExpressionPredicateFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Query implementation for a REST-legacy (non-shared-filesystem) trace store, read directly against the
 * authoritative single store held on this storage node (a {@code RestStoreShard}). No shard fan-out and no
 * archives (a REST store has no shared file store, hence no archival), so no {@link stroom.task.api.TaskContext}
 * and no archive-merge — every read is served from the shard alone.
 */
@Singleton
class RestTracesStore extends AbstractTracesStore {

    @Inject
    RestTracesStore(final TracesDocLoader docLoader,
                    final ShardManager shardManager,
                    final ArchiveShardLocator archiveShardLocator,
                    final MergedCheckpointCache mergedCheckpointCache,
                    final ExpressionPredicateFactory expressionPredicateFactory) {
        super(docLoader, shardManager, archiveShardLocator, mergedCheckpointCache, expressionPredicateFactory);
    }

    @Override
    public TracesResultPage findTraces(final FindTraceCriteria criteria) {
        final Predicate<TraceRoot> filterPredicate = buildFilterPredicate(criteria.getFilter());
        return shardManager.get(criteria.getDataSourceRef().getName(), reader -> {
            if (reader instanceof final TraceDb traceDb) {
                return traceDb.findTraces(criteria, filterPredicate);
            }
            throw new IllegalStateException("Unexpected value: " + reader);
        });
    }

    @Override
    public Trace getTrace(final GetTraceRequest request) {
        final Optional<Trace> shardTrace = readTrace(request);
        if (shardTrace.isPresent() && shardTrace.get().root() != null) {
            return shardTrace.get();
        }
        final List<Trace> sources = new ArrayList<>();
        shardTrace.ifPresent(sources::add);
        final Trace merged = mergeTraces(request.getTraceId(), sources);
        if (merged != null) {
            return merged;
        }
        throw new NotFoundException("No spans found for trace " + request.getTraceId());
    }

    @Override
    public TraceSpanPage getSpans(final GetSpansRequest request) {
        final GroupSelection groupSelection = request.getGroupSelection();
        final TraceDb.SpanOpenTest openTest = openTest(groupSelection);
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());
        final String docName = request.getDataSourceRef().getName();
        if (hasRoot(docName, request.getTraceId(), traceIdBytes)) {
            return rootedSpanPage(request, groupSelection, openTest);
        }
        // No root and no archives (REST stores have none): serve whatever spans the store holds.
        return mergedSpanPage(request, Collections.emptyList(), openTest);
    }

    @Override
    public TraceOverview getTraceOverview(final GetTraceOverviewRequest request) {
        return new TraceOverview(new ArrayList<>(readOverview(request).values()));
    }

    @Override
    public TraceHistogram getTraceHistogram(final TraceHistogramRequest request) {
        final PlanBDocument doc = getPlanBDoc(request.getDataSourceRef());
        final HistogramSpec spec = histogramSpec(request, doc);
        if (!spec.available()) {
            return TraceHistogram.unavailable(spec.maxWindowMs());
        }
        final Predicate<TraceRoot> filterPredicate = buildFilterPredicate(request.getFilter());
        final long[] totals = shardManager.get(request.getDataSourceRef().getName(), reader -> {
            if (reader instanceof final TraceDb traceDb) {
                return traceDb.histogram(
                        spec.timeFilter(), spec.bucketWidthMs(), spec.nBuckets(), filterPredicate);
            }
            throw new IllegalStateException("Unexpected value: " + reader);
        });
        return assembleHistogram(spec, totals);
    }
}
