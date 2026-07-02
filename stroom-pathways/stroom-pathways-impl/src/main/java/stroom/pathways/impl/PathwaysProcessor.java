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

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.node.api.NodeInfo;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.PathwayResultPage;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.pathway.Pathway;
import stroom.planb.impl.data.ShardManager;
import stroom.planb.impl.db.Count;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.trace.PathwaysDb;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.shared.PlanBDocument;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class PathwaysProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PathwaysProcessor.class);

    /**
     * Traces whose root-span merge time is older than this threshold are
     * considered complete and eligible for pathways processing.
     */
    private static final long DEFAULT_GRACE_PERIOD_MS = 10_000L; // 10 seconds

    private final PathwaysStore pathwaysStore;
    private final MessageReceiverFactory messageReceiverFactory;
    private final ByteBuffers byteBuffers;
    private final Path dbPath;
    private final Map<String, PathwaysDb> pathwaysDbMap = new ConcurrentHashMap<>();
    private final PathwaySerde pathwaySerde;
    private final ShardManager shardManager;
    private final NodeInfo nodeInfo;
    private final ClusterLockService clusterLockService;

    @Inject
    public PathwaysProcessor(final PathwaysStore pathwaysStore,
                             final MessageReceiverFactory messageReceiverFactory,
                             final PathCreator pathCreator,
                             final ByteBuffers byteBuffers,
                             final PathwaySerde pathwaySerde,
                             final ShardManager shardManager,
                             final NodeInfo nodeInfo,
                             final ClusterLockService clusterLockService) {
        this.pathwaysStore = pathwaysStore;
        this.messageReceiverFactory = messageReceiverFactory;
        this.byteBuffers = byteBuffers;
        this.pathwaySerde = pathwaySerde;
        this.shardManager = shardManager;
        this.nodeInfo = nodeInfo;
        this.clusterLockService = clusterLockService;

        dbPath = pathCreator.toAppPath("${stroom.home}/pathways");
    }

    /**
     * Scheduled entry point. Computes the grace-period cutoff and delegates to
     * {@link #processCompletedTraces} for each PathwaysDoc assigned to this node.
     * Uses the {@code trace-roots-merge-time} DBI for an O(eligible) range scan,
     * processing only traces whose root span was merged more than
     * {@link #DEFAULT_GRACE_PERIOD_MS} ago.
     */
    public void exec() {
        final long cutoffMs = Instant.now().toEpochMilli() - DEFAULT_GRACE_PERIOD_MS;

        for (final DocRef docRef : NullSafe.list(pathwaysStore.list())) {
            final PathwaysDoc doc = pathwaysStore.readDocument(docRef);
            if (doc != null
                && doc.getTracesDocRef() != null
                && Objects.equals(doc.getProcessingNode(), nodeInfo.getThisNodeName())) {
                try {
                    processCompletedTraces(doc, cutoffMs);
                } catch (final Exception e) {
                    LOGGER.error("Error during trace completion processing for doc {}", doc.getName(), e);
                }
            }
        }
    }

    private PathwaysDb getPathwaysDb(final DocRef docRef) {
        return pathwaysDbMap.computeIfAbsent(docRef.getUuid(), k -> {
            try {
                final Path processingPath = dbPath.resolve("pathways").resolve(docRef.getUuid());
                Files.createDirectories(processingPath);
                return PathwaysDb.create(processingPath, byteBuffers, false);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public PathwayResultPage findPathways(final FindPathwayCriteria criteria) {
        final PathwaysDb pathwaysDb = getPathwaysDb(criteria.getDataSourceRef());
        final Count count = new Count();
        final List<Pathway> list = new ArrayList<>();
        final PageRequest pageRequest = criteria.getPageRequest();
        pathwaysDb.getPathways().iterate((key, val) -> {
            boolean match = false;
            if (NullSafe.isNonEmptyString(criteria.getFilter())) {
                final String string = ByteBufferUtils.byteBufferToString(key);
                if (string.contains(criteria.getFilter())) {
                    match = true;
                }
            } else {
                match = true;
            }

            if (match) {
                final long pos = count.getAndIncrement();
                if (pos >= criteria.getPageRequest().getOffset() &&
                    pos < criteria.getPageRequest().getOffset() + criteria.getPageRequest().getLength()) {
                    list.add(pathwaySerde.readPathway(val));
                }
            }
        });

        final PageResponse pageResponse = PageResponse
                .builder()
                .offset(pageRequest.getOffset())
                .length(list.size())
                .total(count.get())
                .exact(true)
                .build();
        return new PathwayResultPage(list, pageResponse);
    }

    /**
     * For a single PathwaysDoc, finds all eligible traces across every shard of
     * the linked TracesDoc and runs pathways processing on each one.
     *
     * <p>Handles both sharded ({@code shardCount > 0}) and unsharded TracesDoc
     * configurations. In the sharded case a per-shard lock is used so that in a
     * multi-node cluster, different nodes can process different shards concurrently
     * without blocking each other.
     */
    private void processCompletedTraces(final PathwaysDoc doc, final long cutoffMs) {
        if (shardManager.isSnapshotNode()) {
            // Trace completion runs only on merge (shard-owning) nodes.
            return;
        }

        final PlanBDocument tracesDoc = shardManager.getDoc(doc.getTracesDocRef().getName());
        if (tracesDoc == null) {
            LOGGER.warn("No PlanB doc found for traces doc ref '{}' — skipping for pathways doc {}",
                    doc.getTracesDocRef().getName(), doc.getName());
            return;
        }

        final PathwaysDb pathwaysDb = getPathwaysDb(doc.asDocRef());
        final DocRef infoFeed = doc.getInfoFeed();
        final boolean isSharded = tracesDoc.getSharedPath() != null && tracesDoc.getShardCount() > 0;

        if (isSharded) {
            for (int i = 0; i < tracesDoc.getShardCount(); i++) {
                final int shardIdx = i;
                // Per-shard lock: nodes in a cluster can process different shards in parallel.
                final String lockName = "pathways-write-" + doc.getUuid() + "-" + shardIdx;
                clusterLockService.tryLock(lockName, () ->
                        shardManager.get(doc.getTracesDocRef().getName(), shardIdx, db ->
                                processShardTraces(db, pathwaysDb, infoFeed, doc, cutoffMs)));
            }
        } else {
            final String lockName = "pathways-write-" + doc.getUuid();
            clusterLockService.tryLock(lockName, () ->
                    shardManager.get(doc.getTracesDocRef().getName(), db ->
                            processShardTraces(db, pathwaysDb, infoFeed, doc, cutoffMs)));
        }
    }

    /**
     * Processes eligible completed traces from a single TracesDoc shard into the
     * PathwaysDb. Must be called while the caller holds the appropriate
     * {@code pathways-write-*} cluster lock for this shard.
     */
    private Void processShardTraces(final Db<?, ?> db,
                                    final PathwaysDb pathwaysDb,
                                    final DocRef infoFeed,
                                    final PathwaysDoc doc,
                                    final long cutoffMs) {
        if (!(db instanceof final TraceDb traceDb)) {
            return null;
        }

        // Collect traceIds past the grace period. iterateRootsMergedBefore stops
        // early once the time-ordered key exceeds cutoffMs — O(eligible) scan.
        // TODO: Replace the full scan from the beginning of trace-roots-merge-time with a
        //  persistent cursor (watermark) stored in PathwaysDb. On each tick the scan would
        //  start from the last-processed (mergeTimeMs, traceId) key rather than the
        //  beginning of the index, making the cost O(new eligible) rather than
        //  O(all eligible since the shard was created). The PathwaysDb processingStatus DBI
        //  currently provides idempotency but not position tracking.
        final List<byte[]> eligible = new ArrayList<>();
        traceDb.iterateRootsMergedBefore(cutoffMs, eligible::add);

        if (eligible.isEmpty()) {
            LOGGER.debug("No traces ready for pathways completion for doc {}", doc.getName());
            return null;
        }

        LOGGER.debug("Processing {} completed trace(s) for pathways doc {}",
                eligible.size(), doc.getName());

        if (infoFeed != null && infoFeed.getName() != null) {
            messageReceiverFactory.create(infoFeed.getName(), messageReceiver -> {
                try (final LmdbWriter writer = pathwaysDb.createWriter()) {
                    final TraceProcessor traceProcessor =
                            new TraceProcessor(byteBuffers, pathwaySerde);
                    for (final byte[] traceId : eligible) {
                        traceProcessor.processTrace(
                                writer,
                                pathwaysDb,
                                traceId,
                                traceDb::findTrace,
                                doc,
                                messageReceiver);
                    }
                    writer.commit();
                }
            });
        }
        return null;
    }
}
