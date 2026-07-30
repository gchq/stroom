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

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetSpansRequest;
import stroom.pathways.shared.GetTraceOverviewRequest;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TraceHistogram;
import stroom.pathways.shared.TraceHistogramRequest;
import stroom.pathways.shared.TraceOverview;
import stroom.pathways.shared.TraceSpanPage;
import stroom.pathways.shared.TraceSpanRow;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.TracesStore;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.data.archive.ArchiveShardLocator;
import stroom.planb.impl.data.archive.ArchiveShardRef;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.planb.impl.db.ShardKeyRouter;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.db.trace.TraceSecondaryIndex;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.TraceSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.GroupSelection;
import stroom.query.api.TimeFilter;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.query.common.v2.ValueFunctionFactoriesImpl;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.ExecutorProvider;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.time.SimpleDuration;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class TracesStoreImpl implements TracesStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TracesStoreImpl.class);

    // Quick-filter field mappers: bare terms fuzzy-match either (OR'd); 'operation:'/'traceid:' qualify;
    // 'iserror:' matches the boolean error flag (iserror:true / iserror:false).
    private static final ValueFunctionFactoriesImpl<TraceRoot> FILTER_VALUE_FUNCTIONS =
            new ValueFunctionFactoriesImpl<TraceRoot>()
                    .put(FindTraceCriteria.FIELD_DEF_OPERATION, TraceRoot::getName)
                    .put(FindTraceCriteria.FIELD_DEF_TRACE_ID, TraceRoot::getTraceId)
                    .put(FindTraceCriteria.FIELD_DEF_IS_ERROR, root -> Boolean.toString(root.isError()));
    private static final FieldProvider FILTER_FIELD_PROVIDER =
            new FieldProviderImpl(FindTraceCriteria.FIELD_DEFINITIONS);

    private final PlanBDocCache planBDocCache;
    private final Provider<PlanBConfig> configProvider;
    private final ShardManager shardManager;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;
    private final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider;
    private final SecurityContext securityContext;
    private final Executor executor;
    private final ArchiveShardLocator archiveShardLocator;
    private final MergedCheckpointCache mergedCheckpointCache;
    private final ExpressionPredicateFactory expressionPredicateFactory;

    @Inject
    public TracesStoreImpl(final PlanBDocCache planBDocCache,
                           final Provider<PlanBConfig> configProvider,
                           final ShardManager shardManager,
                           final Provider<NodeService> nodeServiceProvider,
                           final Provider<NodeInfo> nodeInfoProvider,
                           final Provider<WebTargetFactory> webTargetFactoryProvider,
                           final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider,
                           final SecurityContext securityContext,
                           final ExecutorProvider executorProvider,
                           final ArchiveShardLocator archiveShardLocator,
                           final MergedCheckpointCache mergedCheckpointCache,
                           final ExpressionPredicateFactory expressionPredicateFactory) {
        this.planBDocCache = planBDocCache;
        this.configProvider = configProvider;
        this.shardManager = shardManager;
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
        this.documentActionHandlersProvider = documentActionHandlersProvider;
        this.securityContext = securityContext;
        this.executor = executorProvider.get();
        this.archiveShardLocator = archiveShardLocator;
        this.mergedCheckpointCache = mergedCheckpointCache;
        this.expressionPredicateFactory = expressionPredicateFactory;
    }

    // Builds the quick-filter predicate for a TraceRoot from the criteria's filter string, or null when
    // the filter is blank (so the query can use its O(1)/key-only count fast paths).
    private Predicate<TraceRoot> buildFilterPredicate(final String filter) {
        if (NullSafe.isBlankString(filter)) {
            return null;
        }
        return expressionPredicateFactory.create(
                filter, FILTER_FIELD_PROVIDER, FILTER_VALUE_FUNCTIONS, DateTimeSettings.builder().build());
    }

    @Override
    public TracesResultPage findTraces(final FindTraceCriteria criteria) {
        final DocRef docRef = criteria.getDataSourceRef();
        final PlanBDocument doc = getPlanBDoc(docRef);

        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }
        final boolean local = !shardManager.isSnapshotNode();
        return findTraces(criteria, local);
    }

    private TracesResultPage findTraces(final FindTraceCriteria criteria,
                                        final boolean local) {
        if (local) {
            // If we are allowing snapshots or if this node stores the data then query locally.
            return getLocalTraces(criteria);

        } else {
            // Snapshot node: proxy the query to a configured storage node.
            // In a shared-filesystem deployment all storage nodes hold identical data, so
            // querying the first is sufficient; that node's getLocalTraces() will in turn
            // fan out across its local shards.
            final List<String> nodes = NullSafe.list(configProvider.get().getNodeList());
            if (nodes.isEmpty()) {
                throw new RuntimeException("No Plan B storage nodes are configured");
            }

            final String nodeName = nodes.getFirst();
            final String url = NodeCallUtil
                    .getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                            TracesRemoteQueryResource.BASE_PATH,
                            TracesRemoteQueryResource.GET_TRACES_PATH);
            try {
                final WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(criteria));
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }
                return response.readEntity(TracesResultPage.class);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    public TracesResultPage getLocalTraces(final FindTraceCriteria criteria) {
        final DocRef docRef = criteria.getDataSourceRef();
        final PlanBDocument doc = getPlanBDoc(docRef);

        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }

        final Predicate<TraceRoot> filterPredicate = buildFilterPredicate(criteria.getFilter());

        if (doc.getSharedPath() != null && doc.getShardCount() > 0) {
            final UserIdentity userIdentity = securityContext.getUserIdentity();
            final List<CompletableFuture<TracesResultPage>> futures = new ArrayList<>();

            // Bound per-shard results to (offset + length): each shard sorts by the same
            // secondary index and returns at most this many rows, so the global merge of
            // N shards has at most N×(offset+length) rows to sort — far fewer than all rows.
            final int callerOffset = criteria.getPageRequest() != null
                    ? criteria.getPageRequest().getOffset() : 0;
            final int callerLength = criteria.getPageRequest() != null
                    ? criteria.getPageRequest().getLength() : Integer.MAX_VALUE;
            // Guard against integer overflow when offset + length > MAX_VALUE.
            final int shardPageSize = callerLength == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : callerOffset + callerLength;

            final FindTraceCriteria shardCriteria = new FindTraceCriteria(
                    new PageRequest(0, shardPageSize),
                    criteria.getSortList(),
                    criteria.getDataSourceRef(),
                    criteria.getFilter(),
                    criteria.getPathway(),
                    criteria.getTemporalOrderingTolerance(),
                    criteria.getTimeRange());

            // Resolve the time filter once for archive fan-out.
            final TimeFilter timeFilter = resolveTimeFilter(criteria.getTimeRange());
            validateTimeRangeLimit(doc, timeFilter);

            for (int i = 0; i < doc.getShardCount(); i++) {
                final int shardIndex = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return securityContext.asUserResult(userIdentity, () ->
                                shardManager.get(doc.getName(), shardIndex, reader -> {
                                    if (reader instanceof final TraceDb traceDb) {
                                        return traceDb.findTraces(shardCriteria, filterPredicate);
                                    }
                                    throw new IllegalStateException("Unexpected value: " + reader);
                                }));
                    } catch (final Exception e) {
                        LOGGER.error("Error querying shard " + shardIndex + " for doc " + doc.getName(), e);
                        return null;
                    }
                }, executor));
            }

            // --- Archive shard fan-out ---
            // Distinct archive bucket time-labels overlapping the window, used to decide whether an exact
            // distinct-trace count is cheap enough (see MAX_EXACT_COUNT_BUCKETS).
            final Set<String> archiveLabels = new HashSet<>();
            if (timeFilter != null) {
                for (int i = 0; i < doc.getShardCount(); i++) {
                    final int shardIndex = i;
                    final List<ArchiveShardRef> archiveRefs = archiveShardLocator.findRelevantShards(
                            doc, shardIndex,
                            timeFilter.getFrom(), timeFilter.getTo());
                    for (final ArchiveShardRef ref : archiveRefs) {
                        archiveLabels.add(ref.dateLabel());
                        futures.add(CompletableFuture.supplyAsync(() -> {
                            try {
                                return securityContext.asUserResult(userIdentity,
                                        () -> queryArchive(ref, shardIndex, shardCriteria, doc, filterPredicate));
                            } catch (final Exception e) {
                                LOGGER.error("Error querying archive shard " + ref.dateLabel() +
                                        " for doc " + doc.getName(), e);
                                return null;
                            }
                        }, executor));
                    }
                }
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            final TracesResultPage merged = mergeAndPaginate(futures, criteria);

            // The summed/deduped total above is only approximate when a split trace is double-counted
            // across live + archive. When the window overlaps few enough archive buckets, replace it with
            // an exact distinct-trace count (union of traceIds across the stores).
            if (timeFilter != null
                    && NullSafe.isBlankString(criteria.getFilter())
                    && !archiveLabels.isEmpty()
                    && archiveLabels.size() <= MAX_EXACT_COUNT_BUCKETS) {
                // With a quick filter active the exact distinct-count path (windowTraceIds) is key-only and
                // can't apply the filter, so fall back to the summed filter-aware per-shard totals instead.
                final long exactTotal = countDistinctTraces(doc, timeFilter, userIdentity);
                final stroom.util.shared.PageResponse pr = merged.getPageResponse();
                return new TracesResultPage(merged.getValues(),
                        new stroom.util.shared.PageResponse(
                                pr.getOffset(), pr.getLength(), exactTotal, true));
            }
            return merged;
        } else {
            return shardManager.get(criteria.getDataSourceRef().getName(), reader -> {
                if (reader instanceof final TraceDb traceDb) {
                    return traceDb.findTraces(criteria, filterPredicate);
                }
                throw new IllegalStateException("Unexpected value: " + reader);
            });
        }
    }

    // Exact distinct-trace counting only runs when the window overlaps at most this many archive buckets
    // (~one archival-granularity period straddles two buckets); a wider window keeps the approximate "?".
    private static final int MAX_EXACT_COUNT_BUCKETS = 2;

    // Exact count of distinct traces with a start time in the window, unioning traceIds across every live
    // shard + relevant archive bucket (key-only START_TIME scans). Dedupes split traces (archived real
    // root + live orphan) that the per-store summed total double-counts.
    private long countDistinctTraces(final PlanBDocument doc,
                                     final TimeFilter timeFilter,
                                     final UserIdentity userIdentity) {
        final List<CompletableFuture<Set<String>>> futures = new ArrayList<>();
        for (int i = 0; i < doc.getShardCount(); i++) {
            final int shardIndex = i;
            futures.add(CompletableFuture.supplyAsync(() ->
                    securityContext.asUserResult(userIdentity, () ->
                            shardManager.get(doc.getName(), shardIndex, reader -> {
                                if (reader instanceof final TraceDb traceDb) {
                                    return traceDb.windowTraceIds(timeFilter);
                                }
                                throw new IllegalStateException("Unexpected value: " + reader);
                            })), executor));
            for (final ArchiveShardRef ref : archiveShardLocator.findRelevantShards(
                    doc, shardIndex, timeFilter.getFrom(), timeFilter.getTo())) {
                futures.add(CompletableFuture.supplyAsync(() ->
                        securityContext.asUserResult(userIdentity, () ->
                                shardManager.getArchive(doc, shardIndex, ref, reader -> {
                                    if (reader instanceof final TraceDb traceDb) {
                                        return traceDb.windowTraceIds(timeFilter);
                                    }
                                    throw new IllegalStateException("Unexpected value: " + reader);
                                })), executor));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        final Set<String> distinct = new HashSet<>();
        for (final CompletableFuture<Set<String>> future : futures) {
            try {
                final Set<String> ids = future.get();
                if (ids != null) {
                    distinct.addAll(ids);
                }
            } catch (final Exception e) {
                LOGGER.error("Failed to collect window traceIds for doc " + doc.getName(), e);
            }
        }
        return distinct.size();
    }

    /**
     * Collects results from completed futures (per-shard or per-node), merges all
     * {@link TraceRoot} values, sorts by {@code startTime} descending (most recent first)
     * with {@code traceId} as a stable tiebreaker, then applies the caller's page request.
     */
    private TracesResultPage mergeAndPaginate(
            final List<CompletableFuture<TracesResultPage>> futures,
            final FindTraceCriteria criteria) {

        final List<TraceRoot> allTraceRoots = new ArrayList<>();
        long total = 0;
        boolean exact = true;

        for (final CompletableFuture<TracesResultPage> future : futures) {
            try {
                final TracesResultPage page = future.get();
                if (page != null) {
                    if (page.getValues() != null) {
                        allTraceRoots.addAll(page.getValues());
                    }
                    if (page.getPageResponse() != null) {
                        total += page.getPageResponse().getTotal();
                        if (!page.getPageResponse().isExact()) {
                            exact = false;
                        }
                    }
                }
            } catch (final Exception e) {
                LOGGER.error("Failed to retrieve query result page from future", e);
            }
        }

        // Dedupe by traceId across live + archive shards. A trace whose root has aged out to an
        // archive appears twice — as the archived real-root row AND as the live synthesized orphan
        // row. Keep one row per traceId, preferring the real root (see preferred(...)). Done on the
        // full collected set (not post-pagination) because the two rows carry different start times
        // and so sort to different, non-adjacent positions.
        final Map<String, TraceRoot> byTraceId = new LinkedHashMap<>();
        for (final TraceRoot root : allTraceRoots) {
            byTraceId.merge(root.getTraceId(), root, TracesStoreImpl::preferred);
        }
        final List<TraceRoot> deduped = new ArrayList<>(byTraceId.values());

        // Correct the summed total for duplicates removed within the fetched window. Collisions
        // beyond the window can't be observed, so mark the count approximate when any were removed.
        final int duplicatesRemoved = allTraceRoots.size() - deduped.size();
        if (duplicatesRemoved > 0) {
            total = Math.max(deduped.size(), total - duplicatesRemoved);
            exact = false;
        }

        // Sort the deduped set using the same comparator as the per-shard secondary index.
        deduped.sort(buildMergeComparator(criteria));

        // Apply the caller's offset/length over the full deduped set.
        final int offset = criteria.getPageRequest() != null
                ? criteria.getPageRequest().getOffset()
                : 0;
        final int length = criteria.getPageRequest() != null
                ? criteria.getPageRequest().getLength()
                : Integer.MAX_VALUE;

        final List<TraceRoot> paginatedList;
        if (offset >= deduped.size()) {
            paginatedList = Collections.emptyList();
        } else {
            paginatedList = deduped.subList(offset, Math.min(offset + length, deduped.size()));
        }

        final stroom.util.shared.PageResponse pageResponse = new stroom.util.shared.PageResponse(
                (long) offset,
                paginatedList.size(),
                total,
                exact
        );
        return new TracesResultPage(paginatedList, pageResponse);
    }

    // De-dup precedence for a traceId seen in >1 shard (archived real-root row + live orphan row):
    // keep the real root over an orphan, else the most recently active. Order-independent, so safe
    // as a Map#merge remapping function.
    //
    // A split trace's archived spans (in the real-root row) and its trailing live spans (in the orphan
    // row) are disjoint sets, so the true Total Spans is their SUM. Keep the real root's identity but add
    // the orphan's span count, otherwise the trailing live spans would be dropped from the reported total.
    private static TraceRoot preferred(final TraceRoot a, final TraceRoot b) {
        if (a.isOrphan() != b.isOrphan()) {
            final TraceRoot real = a.isOrphan() ? b : a;
            final TraceRoot orphan = a.isOrphan() ? a : b;
            final int combined = real.getTotalSpans() + orphan.getTotalSpans();
            return combined == real.getTotalSpans()
                    ? real
                    : real.copy().totalSpans(combined).build();
        }
        return a.getLastActivityMs() >= b.getLastActivityMs() ? a : b;
    }

    /**
     * Builds a {@link Comparator} for the inter-shard merge that matches the sort order
     * used by the per-shard secondary LMDB indexes.
     *
     * <p>The default (no sort criteria) is {@code Trace Start} descending — newest first.
     */
    private static Comparator<TraceRoot> buildMergeComparator(final FindTraceCriteria criteria) {
        final stroom.util.shared.CriteriaFieldSort firstSort =
                stroom.util.shared.NullSafe.get(criteria.getSortList(),
                        sorts -> sorts.isEmpty() ? null : sorts.getFirst());
        final String field = firstSort != null
                ? firstSort.getId()
                : stroom.planb.impl.db.trace.TraceRootField.TRACE_START;
        final boolean desc = firstSort == null || firstSort.isDesc();

        // Match the per-shard secondary-index order. TRACE_ID is served by the primary
        // trace-roots DBI (not a secondary index) so it is handled explicitly; every
        // other field (and the default) comes from the single TraceSecondaryIndex source.
        final Comparator<TraceRoot> base;
        if (stroom.planb.impl.db.trace.TraceRootField.TRACE_ID.equals(field)) {
            base = Comparator.comparing(TraceRoot::getTraceId,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            final TraceSecondaryIndex index = TraceSecondaryIndex.forField(field);
            base = (index != null ? index : TraceSecondaryIndex.START_TIME).comparator();
        }

        final Comparator<TraceRoot> ordered = desc ? base.reversed() : base;
        // Stable tiebreaker: ascending traceId.
        return ordered.thenComparing(TraceRoot::getTraceId,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    @Override
    public Trace getTrace(final GetTraceRequest request) {
        final DocRef docRef = request.getDataSourceRef();
        final PlanBDocument doc = getPlanBDoc(docRef);

        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }
        final boolean local = !shardManager.isSnapshotNode();
        return findTrace(request, local);
    }

    private Trace findTrace(final GetTraceRequest request,
                            final boolean local) {
        if (local) {
            // If we are allowing snapshots or if this node stores the data then query locally.
            return getLocalTrace(request);

        } else {
            // Otherwise perform a remote query.
            final List<String> nodes = NullSafe.list(configProvider.get().getNodeList());
            if (nodes.isEmpty()) {
                throw new RuntimeException("No Plan B storage nodes are configured");
            }

            final String nodeName = nodes.getFirst();
            final String url = NodeCallUtil
                                       .getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), nodeName)
                               + ResourcePaths.buildAuthenticatedApiPath(
                    TracesRemoteQueryResource.BASE_PATH, TracesRemoteQueryResource.GET_TRACE_PATH);
            try {
                // A different node to make a rest call to the required node
                final WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(request));
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(Trace.class);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    public Trace getLocalTrace(final GetTraceRequest request) {
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());

        // 1. Live shard. Use findTrace (returns empty rather than throwing).
        final Optional<Trace> live = shardManager.get(
                request.getDataSourceRef().getName(), request.getTraceId(), reader -> {
                    if (reader instanceof final TraceDb traceDb) {
                        return traceDb.findTrace(traceIdBytes);
                    }
                    throw new IllegalStateException("Unexpected value: " + reader);
                });

        // Fast path: a live trace that still has its root span is fully present — a present root
        // means it was not archived (archival removes the root with the trace). No archive needed.
        if (live.isPresent() && live.get().root() != null) {
            return live.get();
        }

        // 2. Otherwise the trace may be split across storage: its root archived while late spans
        //    stayed live (rootless live fragment), or fully archived. Union the spans from the live
        //    shard AND every relevant archive bucket so the detail view shows the whole trace rather
        //    than a fragment. Archives are labelled by trace start time; if the caller supplied the
        //    start time we open only the matching bucket, otherwise we scan the trace's shard.
        final List<Trace> sources = new ArrayList<>();
        live.ifPresent(sources::add);

        final PlanBDocument doc = getPlanBDoc(request.getDataSourceRef());
        if (doc != null && doc.getSharedPath() != null && doc.getShardCount() > 0) {
            final int shardIndex = ShardKeyRouter.computeShardIndex(
                    request.getTraceId(), doc.getShardCount());
            final long fromMs = request.getStartTimeMs() != null
                    ? request.getStartTimeMs() : Long.MIN_VALUE;
            final long toMs = request.getStartTimeMs() != null
                    ? request.getStartTimeMs() : Long.MAX_VALUE;
            final List<ArchiveShardRef> refs =
                    archiveShardLocator.findRelevantShards(doc, shardIndex, fromMs, toMs);
            for (final ArchiveShardRef ref : refs) {
                final Trace archived = getTraceFromArchive(ref, shardIndex, traceIdBytes, doc);
                if (archived != null) {
                    sources.add(archived);
                }
            }
        }

        final Trace merged = mergeTraces(request.getTraceId(), sources);
        if (merged != null) {
            return merged;
        }

        throw new NotFoundException("No spans found for trace " + request.getTraceId());
    }

    @Override
    public TraceSpanPage getSpans(final GetSpansRequest request) {
        final DocRef docRef = request.getDataSourceRef();
        final PlanBDocument doc = getPlanBDoc(docRef);
        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }
        if (!shardManager.isSnapshotNode()) {
            return getLocalSpans(request);
        }
        return queryStorageNode(TracesRemoteQueryResource.GET_SPANS_PATH, request, TraceSpanPage.class);
    }

    // Serves a bounded, tree-order window of a trace's spans. A trace whose root is in the LIVE shard is
    // served wholly from live with random (offset) access via checkpoints. If the root is NOT live it
    // has been archived (with the bulk of the trace) while trailing spans remain live — page it as a
    // MERGED live+archive tree with a sequential cursor (archives have no checkpoints, and children of an
    // archived node can be in either store, so the child streams must be merged).
    public TraceSpanPage getLocalSpans(final GetSpansRequest request) {
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());
        final String docName = request.getDataSourceRef().getName();

        // Expand/collapse: the client sends a GroupSelection only when the view actually prunes something
        // (a collapsed span or a reduced expand-level); null ⇒ fully expanded ⇒ the unfiltered walk, which
        // keeps the fast on-disk-checkpoint path. The "group key" is the span's spanId (hex).
        final GroupSelection groupSelection = request.getGroupSelection();
        final boolean pruned = groupSelection != null;
        final TraceDb.SpanOpenTest openTest = groupSelection == null
                ? TraceDb.SpanOpenTest.ALL
                : (spanId, depth) -> groupSelection.isGroupOpen(HexStringUtil.encode(spanId), depth);

        final boolean liveHasRoot = Boolean.TRUE.equals(shardManager.get(docName, request.getTraceId(),
                reader -> {
                    if (reader instanceof final TraceDb traceDb) {
                        return traceDb.rootSpan(traceIdBytes).isPresent();
                    }
                    throw new IllegalStateException("Unexpected value: " + reader);
                }));

        if (liveHasRoot && !pruned) {
            // Fully-expanded, live-rooted: offset/random via the on-disk checkpoints; the TraceRoot total is
            // exact, so no total is sent.
            final TraceDb.SpanPage page = shardManager.get(docName, request.getTraceId(), reader -> {
                if (reader instanceof final TraceDb traceDb) {
                    return traceDb.getSpanPageAtOffset(
                            traceIdBytes, request.getOffset(), request.getLimit());
                }
                throw new IllegalStateException("Unexpected value: " + reader);
            });
            return toSpanPage(page, false, null);
        }

        if (liveHasRoot) {
            // Live-rooted but pruned: the on-disk checkpoints are for the UNFILTERED tree, so build a
            // filtered in-memory checkpoint index (cached by trace + live version + selection) and page by
            // offset. Total comes from the filtered index so the pager count reflects the collapsed view.
            final PlanBDocument doc = getPlanBDoc(request.getDataSourceRef());
            final int shardIndex = archiveShardIndex(doc, request.getTraceId());
            final String cacheKey = checkpointCacheKey(doc, shardIndex, request.getTraceId(), List.of())
                    + groupSelectionKey(groupSelection);
            return shardManager.get(docName, request.getTraceId(), reader -> {
                if (!(reader instanceof final TraceDb traceDb)) {
                    throw new IllegalStateException("Unexpected value: " + reader);
                }
                return traceDb.read(txn -> {
                    final TraceDb.ChildCursor cursor =
                            new TraceDb.SingleStoreChildCursor(traceDb, txn, traceIdBytes);
                    final TraceDb.CheckpointIndex index = mergedCheckpointCache.getOrBuild(
                            cacheKey, () -> TraceDb.buildCheckpoints(cursor, openTest));
                    final TraceDb.SpanPage page = TraceDb.getSpanPageAtOffset(
                            cursor, index, request.getOffset(), request.getLimit(), openTest);
                    return toSpanPage(page, false, index.total());
                });
            });
        }

        // Split/archived: merge the live shard with the relevant archive bucket(s).
        final PlanBDocument doc = getPlanBDoc(request.getDataSourceRef());
        final long fromMs = request.getStartTimeMs() != null ? request.getStartTimeMs() : Long.MIN_VALUE;
        final long toMs = request.getStartTimeMs() != null ? request.getStartTimeMs() : Long.MAX_VALUE;
        final List<ArchiveShardRef> refs = relevantArchiveShards(doc, request.getTraceId(), fromMs, toMs);
        final int shardIndex = archiveShardIndex(doc, request.getTraceId());
        final List<byte[]> cursorPath = decodeCursor(request.getCursor());
        final String cacheKey = checkpointCacheKey(doc, shardIndex, request.getTraceId(), refs)
                + groupSelectionKey(groupSelection);

        return shardManager.get(docName, request.getTraceId(), liveReader -> {
            if (!(liveReader instanceof final TraceDb liveDb)) {
                throw new IllegalStateException("Unexpected value: " + liveReader);
            }
            return liveDb.read(liveTxn -> {
                final List<TraceDb.ChildCursor> cursors = new ArrayList<>();
                cursors.add(new TraceDb.SingleStoreChildCursor(liveDb, liveTxn, traceIdBytes));
                return openArchivesAndPage(doc, shardIndex, refs, 0, traceIdBytes, cursors,
                        cursorPath, request.getOffset(), request.getLimit(), cacheKey, openTest);
            });
        });
    }

    // Deterministic cache-key suffix for a GroupSelection (empty when fully expanded / null). Sorted so it
    // is stable regardless of set iteration order.
    private static String groupSelectionKey(final GroupSelection groupSelection) {
        if (groupSelection == null) {
            return "";
        }
        final String open = groupSelection.getOpenGroups().stream().sorted().collect(Collectors.joining(","));
        final String closed = groupSelection.getClosedGroups().stream().sorted()
                .collect(Collectors.joining(","));
        return "|gs=" + groupSelection.getExpandedDepth() + ";o=" + open + ";c=" + closed;
    }

    // Recursively nests the getArchive callbacks (each holds the archive shard's read lock + a read txn)
    // to hold live + every relevant archive bucket open at once, then serves the page from the merged
    // pre-order DFS: by opaque cursor when one was supplied (cheap sequential next/prev), by offset via a
    // cached merged checkpoint index when a jump/last is requested (built once, O(n)), else from the root.
    private TraceSpanPage openArchivesAndPage(final PlanBDocument doc,
                                              final int shardIndex,
                                              final List<ArchiveShardRef> refs,
                                              final int i,
                                              final byte[] traceIdBytes,
                                              final List<TraceDb.ChildCursor> cursors,
                                              final List<byte[]> cursorPath,
                                              final int offset,
                                              final int limit,
                                              final String cacheKey,
                                              final TraceDb.SpanOpenTest openTest) {
        if (i >= refs.size()) {
            final TraceDb.MergedChildCursor merged = new TraceDb.MergedChildCursor(cursors);
            final TraceDb.SpanPage page;
            final Integer total;
            if (cursorPath != null) {
                // Sequential resume — cheap, no checkpoints needed.
                page = TraceDb.getSpanPage(merged, cursorPath, limit, openTest);
                final TraceDb.CheckpointIndex cached = mergedCheckpointCache.getIfPresent(cacheKey);
                total = cached != null ? cached.total() : null;
            } else if (offset > 0) {
                // Random-access jump/last — seek via the merged checkpoint index (built + cached once).
                final TraceDb.CheckpointIndex index = mergedCheckpointCache.getOrBuild(
                        cacheKey, () -> TraceDb.buildCheckpoints(merged, openTest));
                page = TraceDb.getSpanPageAtOffset(merged, index, offset, limit, openTest);
                total = index.total();
            } else {
                // First page (offset 0, no cursor) — walk from the root; don't force a build.
                page = TraceDb.getSpanPage(merged, null, limit, openTest);
                final TraceDb.CheckpointIndex cached = mergedCheckpointCache.getIfPresent(cacheKey);
                total = cached != null ? cached.total() : null;
            }
            return toSpanPage(page, true, total);
        }
        return shardManager.getArchive(doc, shardIndex, refs.get(i), archiveReader -> {
            if (!(archiveReader instanceof final TraceDb archiveDb)) {
                throw new IllegalStateException("Unexpected value: " + archiveReader);
            }
            return archiveDb.read(archiveTxn -> {
                cursors.add(new TraceDb.SingleStoreChildCursor(archiveDb, archiveTxn, traceIdBytes));
                return openArchivesAndPage(doc, shardIndex, refs, i + 1, traceIdBytes, cursors,
                        cursorPath, offset, limit, cacheKey, openTest);
            });
        });
    }

    private TraceSpanPage toSpanPage(final TraceDb.SpanPage page,
                                     final boolean sequential,
                                     final Integer totalSpans) {
        final List<TraceSpanRow> rows = new ArrayList<>();
        boolean more = false;
        List<byte[]> next = null;
        if (page != null) {
            if (page.rows() != null) {
                for (final TraceDb.SpanRow row : page.rows()) {
                    rows.add(new TraceSpanRow(row.span(), row.depth(), row.hasChildren()));
                }
            }
            more = page.more();
            next = page.nextCursor();
        }
        // The merged (split) path always exposes a resume cursor so next/prev stay cheap after any page;
        // the live offset path leaves it null (the client uses offsets there).
        final String nextCursor = (sequential && more) ? encodeCursor(next) : null;
        return new TraceSpanPage(rows, more, nextCursor, totalSpans);
    }

    // Cache key for a split trace's merged checkpoint index: identifies the trace and the versions of
    // every contributing store (live shard + archive buckets), so the entry self-invalidates when any of
    // them changes.
    private String checkpointCacheKey(final PlanBDocument doc,
                                      final int shardIndex,
                                      final String traceId,
                                      final List<ArchiveShardRef> refs) {
        final StringBuilder sb = new StringBuilder()
                .append(doc == null ? "" : doc.getUuid()).append('_')
                .append(shardIndex).append('_')
                .append(traceId).append("|live=")
                .append(liveShardVersion(doc, shardIndex));
        for (final ArchiveShardRef ref : refs) {
            sb.append(";arch=").append(ref.dateLabel()).append('=').append(readVersion(ref.dir()));
        }
        return sb.toString();
    }

    private String liveShardVersion(final PlanBDocument doc, final int shardIndex) {
        if (doc == null || doc.getSharedPath() == null || shardIndex < 0) {
            return "";
        }
        return readVersion(Path.of(doc.getSharedPath())
                .resolve(PlanBConstants.SHARDS_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(String.format("%04d", shardIndex)));
    }

    private static String readVersion(final Path dir) {
        final Path versionFile = dir.resolve(PlanBConstants.VERSION_FILE_NAME);
        try {
            return Files.exists(versionFile) ? Files.readString(versionFile).trim() : "";
        } catch (final IOException e) {
            return "";
        }
    }

    private int archiveShardIndex(final PlanBDocument doc, final String traceId) {
        if (doc == null || doc.getSharedPath() == null || doc.getShardCount() <= 0) {
            return -1;
        }
        return ShardKeyRouter.computeShardIndex(traceId, doc.getShardCount());
    }

    private List<ArchiveShardRef> relevantArchiveShards(final PlanBDocument doc,
                                                        final String traceId,
                                                        final long fromMs,
                                                        final long toMs) {
        final int shardIndex = archiveShardIndex(doc, traceId);
        if (shardIndex < 0) {
            return Collections.emptyList();
        }
        return archiveShardLocator.findRelevantShards(doc, shardIndex, fromMs, toMs);
    }

    private static String encodeCursor(final List<byte[]> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        return Base64.getEncoder().encodeToString(TraceDb.encodePath(path));
    }

    private static List<byte[]> decodeCursor(final String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        return TraceDb.decodePath(Base64.getDecoder().decode(cursor));
    }

    @Override
    public TraceOverview getTraceOverview(final GetTraceOverviewRequest request) {
        final DocRef docRef = request.getDataSourceRef();
        final PlanBDocument doc = getPlanBDoc(docRef);
        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }
        if (!shardManager.isSnapshotNode()) {
            return getLocalTraceOverview(request);
        }
        return queryStorageNode(
                TracesRemoteQueryResource.GET_TRACE_OVERVIEW_PATH, request, TraceOverview.class);
    }

    // Builds the downsampled whole-trace overview (one streaming pass per store, bounded memory).
    // Extents are supplied by the caller from the already-known TraceRoot, so the axis is whole before
    // any span is loaded. A split/archived trace (root not in the live shard) unions the live shard with
    // the relevant archive bucket(s), deduped by spanId; a fully-live trace reads live only (no archive
    // copy triggered).
    public TraceOverview getLocalTraceOverview(final GetTraceOverviewRequest request) {
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());
        final String docName = request.getDataSourceRef().getName();

        final Map<String, Span> bySpanId = new LinkedHashMap<>();
        final List<Span> live = shardManager.get(docName, request.getTraceId(), reader -> {
            if (reader instanceof final TraceDb traceDb) {
                return traceDb.getOverviewSpans(
                        traceIdBytes, request.getFromMs(), request.getToMs(), request.getMaxBars());
            }
            throw new IllegalStateException("Unexpected value: " + reader);
        });
        if (live != null) {
            live.forEach(s -> bySpanId.putIfAbsent(s.getSpanId(), s));
        }

        final boolean liveHasRoot = Boolean.TRUE.equals(shardManager.get(docName, request.getTraceId(),
                reader -> {
                    if (reader instanceof final TraceDb traceDb) {
                        return traceDb.rootSpan(traceIdBytes).isPresent();
                    }
                    throw new IllegalStateException("Unexpected value: " + reader);
                }));
        if (!liveHasRoot) {
            final PlanBDocument doc = getPlanBDoc(request.getDataSourceRef());
            final List<ArchiveShardRef> refs = relevantArchiveShards(
                    doc, request.getTraceId(), request.getFromMs(), request.getToMs());
            final int shardIndex = archiveShardIndex(doc, request.getTraceId());
            for (final ArchiveShardRef ref : refs) {
                final List<Span> archived = shardManager.getArchive(doc, shardIndex, ref, reader -> {
                    if (reader instanceof final TraceDb traceDb) {
                        return traceDb.getOverviewSpans(
                                traceIdBytes, request.getFromMs(), request.getToMs(), request.getMaxBars());
                    }
                    throw new IllegalStateException("Unexpected value: " + reader);
                });
                if (archived != null) {
                    archived.forEach(s -> bySpanId.putIfAbsent(s.getSpanId(), s));
                }
            }
        }
        return new TraceOverview(new ArrayList<>(bySpanId.values()));
    }

    public TraceHistogram getTraceHistogram(final TraceHistogramRequest request) {
        final DocRef docRef = request.getDataSourceRef();
        final PlanBDocument doc = getPlanBDoc(docRef);
        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }
        if (!shardManager.isSnapshotNode()) {
            return getLocalTraceHistogram(request);
        }
        return queryStorageNode(
                TracesRemoteQueryResource.GET_TRACE_HISTOGRAM_PATH, request, TraceHistogram.class);
    }

    // Counts traces per equal time-bucket over the requested window, fanning out across live shards +
    // overlapping archive buckets and summing per-bucket counts. The window is capped to one archival-
    // granularity bucket so it never overlaps more than 1-2 archive buckets; a wider or unbounded range
    // returns an unavailable histogram without scanning.
    public TraceHistogram getLocalTraceHistogram(final TraceHistogramRequest request) {
        final DocRef docRef = request.getDataSourceRef();
        final PlanBDocument doc = getPlanBDoc(docRef);
        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }

        final long maxWindowMs = maxHistogramWindowMs(doc);
        final TimeFilter timeFilter = resolveTimeFilter(request.getTimeRange());
        if (timeFilter == null || timeFilter.getTo() - timeFilter.getFrom() > maxWindowMs) {
            return TraceHistogram.unavailable(maxWindowMs);
        }

        final long fromMs = timeFilter.getFrom();
        final long toMs = timeFilter.getTo();
        // Bucket by an integer width of at least 1ms so buckets never go sub-millisecond (which would
        // make a bar's range un-representable as an ms window and break drill-down). The bucket count is
        // capped so width * count still covers the window.
        final int requestedBuckets = Math.max(1, request.getBucketCount());
        final long span = Math.max(1L, toMs - fromMs);
        final long bucketWidthMs = Math.max(1L, (span + requestedBuckets - 1) / requestedBuckets);
        final int nBuckets = (int) Math.min(
                (long) requestedBuckets, (span + bucketWidthMs - 1) / bucketWidthMs);
        final Predicate<TraceRoot> filterPredicate = buildFilterPredicate(request.getFilter());

        final long[] totals;
        if (doc.getSharedPath() != null && doc.getShardCount() > 0) {
            final UserIdentity userIdentity = securityContext.getUserIdentity();
            final List<CompletableFuture<long[]>> futures = new ArrayList<>();
            for (int i = 0; i < doc.getShardCount(); i++) {
                final int shardIndex = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return securityContext.asUserResult(userIdentity, () ->
                                shardManager.get(doc.getName(), shardIndex, reader -> {
                                    if (reader instanceof final TraceDb traceDb) {
                                        return traceDb.histogram(
                                                timeFilter, bucketWidthMs, nBuckets, filterPredicate);
                                    }
                                    throw new IllegalStateException("Unexpected value: " + reader);
                                }));
                    } catch (final Exception e) {
                        LOGGER.error("Error histogramming shard " + shardIndex
                                + " for doc " + doc.getName(), e);
                        return null;
                    }
                }, executor));

                for (final ArchiveShardRef ref : archiveShardLocator.findRelevantShards(
                        doc, shardIndex, fromMs, toMs)) {
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            return securityContext.asUserResult(userIdentity, () ->
                                    shardManager.getArchive(doc, shardIndex, ref, reader -> {
                                        if (reader instanceof final TraceDb traceDb) {
                                            return traceDb.histogram(
                                                timeFilter, bucketWidthMs, nBuckets, filterPredicate);
                                        }
                                        throw new IllegalStateException("Unexpected value: " + reader);
                                    }));
                        } catch (final Exception e) {
                            LOGGER.error("Error histogramming archive shard " + ref.dateLabel()
                                    + " for doc " + doc.getName(), e);
                            return null;
                        }
                    }, executor));
                }
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            totals = new long[nBuckets];
            for (final CompletableFuture<long[]> future : futures) {
                try {
                    final long[] counts = future.get();
                    if (counts != null) {
                        for (int b = 0; b < nBuckets && b < counts.length; b++) {
                            totals[b] += counts[b];
                        }
                    }
                } catch (final Exception e) {
                    LOGGER.error("Failed to collect histogram counts for doc " + doc.getName(), e);
                }
            }
        } else {
            totals = shardManager.get(docRef.getName(), reader -> {
                if (reader instanceof final TraceDb traceDb) {
                    return traceDb.histogram(timeFilter, bucketWidthMs, nBuckets, filterPredicate);
                }
                throw new IllegalStateException("Unexpected value: " + reader);
            });
        }

        final List<Long> counts = new ArrayList<>(nBuckets);
        for (int b = 0; b < nBuckets; b++) {
            counts.add(totals[b]);
        }
        return new TraceHistogram(true, fromMs, toMs, bucketWidthMs, maxWindowMs, counts);
    }

    // Widest window the histogram will serve: one archival-granularity bucket (so at most 1-2 archive
    // buckets are ever touched). Defaults to DAY when archival is not configured.
    private static long maxHistogramWindowMs(final PlanBDocument doc) {
        ArchivalGranularity granularity = ArchivalGranularity.DAY;
        if (doc.getSettings() instanceof final TraceSettings ts
                && ts.getSharedFileStore() != null
                && ts.getSharedFileStore().getArchival() != null) {
            granularity = ts.getSharedFileStore().getArchival().getGranularity();
        }
        return switch (granularity) {
            case HOUR -> 60L * 60 * 1000;
            case DAY -> 24L * 60 * 60 * 1000;
            case WEEK -> 7L * 24 * 60 * 60 * 1000;
        };
    }

    // Proxies a query to the first configured Plan B storage node (used by a snapshot node, which
    // holds no data of its own). Mirrors the remote-call handling of findTrace/findTraces.
    private <T> T queryStorageNode(final String path,
                                   final Object request,
                                   final Class<T> responseType) {
        final List<String> nodes = NullSafe.list(configProvider.get().getNodeList());
        if (nodes.isEmpty()) {
            throw new RuntimeException("No Plan B storage nodes are configured");
        }
        final String nodeName = nodes.getFirst();
        final String url = NodeCallUtil
                .getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(TracesRemoteQueryResource.BASE_PATH, path);
        try {
            final WebTarget webTarget = webTargetFactoryProvider.get().create(url);
            final Response response = webTarget
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(request));
            if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                throw new NotFoundException(response);
            } else if (response.getStatus() != Status.OK.getStatusCode()) {
                throw new WebApplicationException(response);
            }
            return response.readEntity(responseType);
        } catch (final Throwable e) {
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }

    // Merges the spans of several partial Traces for the same traceId (live fragment + archive
    // buckets) into one, de-duplicating by spanId. Each source's parentSpanIdMap keying is preserved,
    // so the root resolves normally if its span is present. Returns null if there are no spans anywhere.
    private Trace mergeTraces(final String traceId, final List<Trace> sources) {
        final Map<String, List<Span>> merged = new HashMap<>();
        final Set<String> seenSpanIds = new HashSet<>();
        for (final Trace source : sources) {
            if (source == null || source.getParentSpanIdMap() == null) {
                continue;
            }
            for (final Map.Entry<String, List<Span>> entry : source.getParentSpanIdMap().entrySet()) {
                for (final Span span : entry.getValue()) {
                    if (seenSpanIds.add(span.getSpanId())) {
                        merged.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(span);
                    }
                }
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        return Trace.builder().traceId(traceId).parentSpanIdMap(merged).build();
    }

    /**
     * Returns the assembled {@link Trace} for {@code traceIdBytes} from an archive bucket, or
     * {@code null} if the archive does not contain it. Reads via a cached, read-only, idle-evicted
     * local copy of the bucket ({@link ShardManager#getArchive}) rather than copying the whole bucket
     * to a temp dir per call.
     */
    private Trace getTraceFromArchive(final ArchiveShardRef ref,
                                      final int shardIndex,
                                      final byte[] traceIdBytes,
                                      final PlanBDocument doc) {
        try {
            return shardManager.getArchive(doc, shardIndex, ref, reader -> {
                if (reader instanceof final TraceDb traceDb) {
                    return traceDb.findTrace(traceIdBytes).orElse(null);
                }
                throw new IllegalStateException("Unexpected value: " + reader);
            });
        } catch (final Exception e) {
            LOGGER.error(() -> "Error reading trace from archive shard " + ref.dateLabel() +
                    " for doc " + doc.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    private PlanBDocument getPlanBDoc(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        if (TracesDoc.TYPE.equals(docRef.getType())) {
            try {
                final DocumentActionHandler<?> handler = documentActionHandlersProvider.get()
                        .get(new DocumentTypeName(TracesDoc.TYPE));
                if (handler == null) {
                    throw new IllegalStateException("No handler found for type: " + TracesDoc.TYPE);
                }
                return (PlanBDocument) handler.readDocument(docRef);
            } catch (final Exception e) {
                LOGGER.error("Failed to read TracesDoc " + docRef, e);
                throw new RuntimeException("Failed to read TracesDoc '" + docRef.getName() + "'", e);
            }
        } else {
            return planBDocCache.get(docRef.getName());
        }
    }

    /**
     * Resolves the criteria time range to a {@link TimeFilter} (epoch-ms bounds),
     * or returns {@code null} if no time range is set.
     */
    @Nullable
    private static TimeFilter resolveTimeFilter(@Nullable final TimeRange timeRange) {
        if (timeRange == null) {
            return null;
        }
        return DateExpressionParser.getTimeFilter(timeRange, DateTimeSettings.builder().build());
    }

    /**
     * Validates that the resolved time filter does not exceed the configured
     * {@code maxQueryTimeRange} on the document's settings. Throws
     * {@link IllegalArgumentException} if the limit is exceeded.
     */
    private static void validateTimeRangeLimit(final PlanBDocument doc,
                                               final TimeFilter timeFilter) {
        if (timeFilter == null) {
            return;
        }
        final SimpleDuration maxQueryTimeRange;
        if (doc.getSettings() instanceof final TraceSettings ts) {
            maxQueryTimeRange = ts.getMaxQueryTimeRange();
        } else {
            maxQueryTimeRange = null;
        }
        if (maxQueryTimeRange == null) {
            return;
        }
        final long windowMs = timeFilter.getTo() - timeFilter.getFrom();
        final long limitMs = toMillis(maxQueryTimeRange);
        if (windowMs > limitMs) {
            throw new IllegalArgumentException(
                    "Query time range (" + windowMs / 1000 + "s) exceeds the configured " +
                    "maximum of " + maxQueryTimeRange + " for this data source. " +
                    "Please narrow the time range.");
        }
    }

    /**
     * Converts a {@link SimpleDuration} to epoch milliseconds.
     */
    private static long toMillis(final SimpleDuration duration) {
        final long time = duration.getTime();
        return switch (duration.getTimeUnit()) {
            case NANOSECONDS  -> time / 1_000_000;
            case MILLISECONDS -> time;
            case SECONDS      -> time * 1_000;
            case MINUTES      -> time * 60 * 1_000;
            case HOURS        -> time * 60 * 60 * 1_000;
            case DAYS         -> time * 24 * 60 * 60 * 1_000;
            case WEEKS        -> time * 7 * 24 * 60 * 60 * 1_000;
            case MONTHS       -> time * 30L * 24 * 60 * 60 * 1_000;
            case YEARS        -> time * 365L * 24 * 60 * 60 * 1_000;
        };
    }

    /**
     * Runs {@code findTraces()} against an archive bucket via a cached, read-only, idle-evicted local
     * copy ({@link ShardManager#getArchive}) rather than copying the whole bucket to a temp dir per call.
     */
    private TracesResultPage queryArchive(final ArchiveShardRef ref,
                                          final int shardIndex,
                                          final FindTraceCriteria criteria,
                                          final PlanBDocument doc,
                                          final Predicate<TraceRoot> filterPredicate) {
        try {
            return shardManager.getArchive(doc, shardIndex, ref, reader -> {
                if (reader instanceof final TraceDb traceDb) {
                    return traceDb.findTraces(criteria, filterPredicate);
                }
                throw new IllegalStateException("Unexpected value: " + reader);
            });
        } catch (final Exception e) {
            LOGGER.error(() -> "Error querying archive shard " + ref.dateLabel() +
                    " for doc " + doc.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }
}
