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

package stroom.planb.impl.data;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.TracesStore;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.db.ShardKeyRouter;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.db.trace.TraceSecondaryIndex;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.TraceSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.TimeFilter;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.DateExpressionParser;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.ExecutorProvider;
import stroom.util.io.FileUtil;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Singleton
public class TracesStoreImpl implements TracesStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TracesStoreImpl.class);

    private final PlanBDocCache planBDocCache;
    private final Provider<PlanBConfig> configProvider;
    private final ShardManager shardManager;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;
    private final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider;
    private final SecurityContext securityContext;
    private final Executor executor;
    private final stroom.bytebuffer.impl6.ByteBuffers byteBuffers;
    private final stroom.bytebuffer.impl6.ByteBufferFactory byteBufferFactory;
    private final ArchiveShardLocator archiveShardLocator;

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
                           final stroom.bytebuffer.impl6.ByteBuffers byteBuffers,
                           final stroom.bytebuffer.impl6.ByteBufferFactory byteBufferFactory,
                           final ArchiveShardLocator archiveShardLocator) {
        this.planBDocCache = planBDocCache;
        this.configProvider = configProvider;
        this.shardManager = shardManager;
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
        this.documentActionHandlersProvider = documentActionHandlersProvider;
        this.securityContext = securityContext;
        this.executor = executorProvider.get();
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.archiveShardLocator = archiveShardLocator;
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
                                        return traceDb.findTraces(shardCriteria);
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
            if (timeFilter != null) {
                for (int i = 0; i < doc.getShardCount(); i++) {
                    final int shardIndex = i;
                    final List<ArchiveShardRef> archiveRefs = archiveShardLocator.findRelevantShards(
                            doc, shardIndex,
                            timeFilter.getFrom(), timeFilter.getTo());
                    for (final ArchiveShardRef ref : archiveRefs) {
                        futures.add(CompletableFuture.supplyAsync(() -> {
                            try {
                                return securityContext.asUserResult(userIdentity,
                                        () -> queryArchive(ref, shardCriteria, doc));
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
            return mergeAndPaginate(futures, criteria);
        } else {
            return shardManager.get(criteria.getDataSourceRef().getName(), reader -> {
                if (reader instanceof final TraceDb traceDb) {
                    return traceDb.findTraces(criteria);
                }
                throw new IllegalStateException("Unexpected value: " + reader);
            });
        }
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
        int total = 0;
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

        // Sort the merged set using the same comparator as the per-shard secondary index.
        allTraceRoots.sort(buildMergeComparator(criteria));

        // Apply the caller's offset/length over the full merged set.
        final int offset = criteria.getPageRequest() != null
                ? criteria.getPageRequest().getOffset()
                : 0;
        final int length = criteria.getPageRequest() != null
                ? criteria.getPageRequest().getLength()
                : Integer.MAX_VALUE;

        final List<TraceRoot> paginatedList;
        if (offset >= allTraceRoots.size()) {
            paginatedList = Collections.emptyList();
        } else {
            paginatedList = allTraceRoots.subList(offset, Math.min(offset + length, allTraceRoots.size()));
        }

        final stroom.util.shared.PageResponse pageResponse = new stroom.util.shared.PageResponse(
                (long) offset,
                paginatedList.size(),
                (long) total,
                exact
        );
        return new TracesResultPage(paginatedList, pageResponse);
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

        // 1. Live shard first. Use findTrace (returns empty rather than throwing)
        //    so we can fall back to archives when the trace has been purged.
        final Optional<Trace> live = shardManager.get(
                request.getDataSourceRef().getName(), request.getTraceId(), reader -> {
                    if (reader instanceof final TraceDb traceDb) {
                        return traceDb.findTrace(traceIdBytes);
                    }
                    throw new IllegalStateException("Unexpected value: " + reader);
                });
        if (live.isPresent()) {
            return live.get();
        }

        // 2. Archive fallback. Archives are labelled by trace start time, so if the
        //    caller supplied the start time we open only the matching bucket; otherwise
        //    we scan every archive bucket for this trace's shard.
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
                final Trace trace = getTraceFromArchive(ref, traceIdBytes, doc);
                if (trace != null) {
                    return trace;
                }
            }
        }

        throw new NotFoundException("No spans found for trace " + request.getTraceId());
    }

    /**
     * Copies an archive shard's {@code data.mdb} to a local temp dir, opens it
     * read-only and returns the assembled {@link Trace} for {@code traceIdBytes},
     * or {@code null} if the archive does not contain that trace. Cleans up the
     * temp dir on exit.
     */
    private Trace getTraceFromArchive(final ArchiveShardRef ref,
                                      final byte[] traceIdBytes,
                                      final PlanBDocument doc) {
        final Path tempDir;
        try {
            tempDir = Files.createTempDirectory("planb_arch_");
        } catch (final IOException e) {
            LOGGER.error(() -> "Failed to create temp dir for archive shard " +
                    ref.dateLabel() + ": " + e.getMessage());
            return null;
        }
        try {
            final Path srcData = ref.dir().resolve(PlanBConstants.DATA_FILE_NAME);
            if (!Files.exists(srcData)) {
                LOGGER.warn(() -> "Archive shard " + ref.dir() + " has no data.mdb — skipping");
                return null;
            }
            Files.copy(srcData, tempDir.resolve(PlanBConstants.DATA_FILE_NAME));

            try (final TraceDb archiveDb =
                         TraceDb.create(tempDir, byteBuffers, byteBufferFactory, doc, true)) {
                return archiveDb.findTrace(traceIdBytes).orElse(null);
            }
        } catch (final Exception e) {
            LOGGER.error(() -> "Error reading trace from archive shard " + ref.dateLabel() +
                    " for doc " + doc.getName() + ": " + e.getMessage(), e);
            return null;
        } finally {
            FileUtil.deleteDir(tempDir);
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
     * Copies the archive shard's {@code data.mdb} to a local temp directory,
     * opens it as a read-only {@link TraceDb}, runs {@code findTraces()} and
     * returns the result. Cleans up the temp directory on exit.
     */
    private TracesResultPage queryArchive(final ArchiveShardRef ref,
                                          final FindTraceCriteria criteria,
                                          final PlanBDocument doc) {
        final Path tempDir;
        try {
            tempDir = Files.createTempDirectory("planb_arch_");
        } catch (final IOException e) {
            LOGGER.error(() -> "Failed to create temp dir for archive shard " +
                    ref.dateLabel() + ": " + e.getMessage());
            return null;
        }
        try {
            final Path srcData = ref.dir().resolve(PlanBConstants.DATA_FILE_NAME);
            if (!Files.exists(srcData)) {
                LOGGER.warn(() -> "Archive shard " + ref.dir() + " has no data.mdb — skipping");
                return null;
            }
            Files.copy(srcData, tempDir.resolve(PlanBConstants.DATA_FILE_NAME));

            try (final TraceDb archiveDb =
                         TraceDb.create(tempDir, byteBuffers, byteBufferFactory, doc, true)) {
                return archiveDb.findTraces(criteria);
            }
        } catch (final Exception e) {
            LOGGER.error(() -> "Error querying archive shard " + ref.dateLabel() +
                    " for doc " + doc.getName() + ": " + e.getMessage(), e);
            return null;
        } finally {
            FileUtil.deleteDir(tempDir);
        }
    }
}
