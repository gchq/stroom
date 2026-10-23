/*
 * Copyright 2026 Crown Copyright
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

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentStoreRegistry;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracesDoc;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.dao.Db;
import stroom.planb.impl.dao.ShardKeyRouter;
import stroom.planb.impl.dao.trace.QueueItemWriter;
import stroom.planb.impl.dao.trace.TraceDb;
import stroom.planb.impl.fs.MergeCompletionStrategy;
import stroom.planb.impl.fs.ShardQueue;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hands finished traces from a trace store to the Pathways document it is linked to, as queue items.
 *
 * <p>Traces are sharded by trace id, because a trace's spans arrive separately and have to land
 * together. Pathways are sharded by operation name, because a pathway is keyed on the name and must
 * never be split across two owners. Those are two groupings of the same data, and this is where the
 * regrouping happens.
 *
 * <p>Costs nothing for a store that is not linked: the check is one field of a document the caller
 * already holds.
 */
@Singleton
public class TraceMergeCompletionStrategy implements MergeCompletionStrategy {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TraceMergeCompletionStrategy.class);

    /**
     * Items allowed to be waiting in one shard folder before hand-over shedding starts.
     *
     * <p>The queue is on a shared filesystem and holds whole spans, so a consumer that has stopped
     * would otherwise fill the mount. Past this, traces are dropped rather than queued, and the drop
     * is counted and logged — because the alternative, failing the write, stops the bucket publishing
     * and would hold trace ingest hostage to the health of pathways.
     *
     * <p>A fixed number rather than a setting because no screen offers one yet. It wants to become
     * per-document configuration.
     */
    static final int MAX_QUEUE_DEPTH_PER_SHARD = 1_000;

    private final Provider<DocumentStoreRegistry> documentStoreRegistryProvider;
    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final PlanBPaths planBPaths;

    @Inject
    public TraceMergeCompletionStrategy(final Provider<DocumentStoreRegistry> documentStoreRegistryProvider,
                                        final ByteBuffers byteBuffers,
                                        final ByteBufferFactory byteBufferFactory,
                                        final PlanBPaths planBPaths) {
        this.documentStoreRegistryProvider = documentStoreRegistryProvider;
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.planBPaths = planBPaths;
    }

    /**
     * Completes the merge and hands over whatever became newly complete.
     *
     * <p>Both in one call because only the merge knows which traces those are: the condition fires
     * once per trace, on the cycle its real root first appears, and nothing afterwards can recover the
     * answer.
     *
     * <p>Must be called while {@code db} is still open and <b>before</b> the bucket is published.
     * Everything here happens on a local copy that is thrown away if this throws, so a failed
     * hand-over means the bucket is not published and the whole cycle is retried — the traces are then
     * handed over again next time rather than lost.
     *
     * @param doc the trace store's document; a store of another type, or one with no Pathways
     *            document, just has its merge completed.
     */
    @Override
    public void completeMerge(final PlanBDocument doc, final Db<?, ?> db) throws IOException {
        if (!(db instanceof final TraceDb traceDb)) {
            db.mergeComplete();
            return;
        }
        final DocRef pathwaysDocRef = doc instanceof final TracesDoc tracesDoc
                ? tracesDoc.getPathwaysDocRef()
                : null;
        if (pathwaysDocRef == null) {
            // Nobody is listening, so the extra write is paid for nobody.
            traceDb.mergeComplete();
            return;
        }

        final List<byte[]> handedOver = new ArrayList<>();
        traceDb.mergeComplete(handedOver::add);
        if (handedOver.isEmpty()) {
            return;
        }

        // Stamped on every item written below, so that what is learnt from these traces says which
        // store they came from rather than leaving it to be inferred from whatever the configuration
        // names at the time someone asks.
        write(pathwaysDocRef, doc.getUuid(), traceDb, handedOver);
    }

    private void write(final DocRef pathwaysDocRef,
                       final String sourceUuid,
                       final TraceDb traceDb,
                       final List<byte[]> handedOver) throws IOException {
        final Optional<SharedFileStoreSettings> optSettings = sharedFileStoreOf(pathwaysDocRef);
        if (optSettings.isEmpty()) {
            LOGGER.warn(() -> LogUtil.message(
                    "Not handing over {} trace(s): Pathways document {} names no shared file store, so "
                    + "there is nowhere to write them.",
                    handedOver.size(), pathwaysDocRef));
            return;
        }
        final SharedFileStoreSettings settings = optSettings.get();
        final int shardCount = ShardQueue.shardCount(settings);

        final Map<Integer, List<byte[]>> byShard = groupByShard(traceDb, handedOver, shardCount);

        final long orderKey = System.currentTimeMillis();
        final QueueItemWriter writer = new QueueItemWriter(byteBuffers, byteBufferFactory, planBPaths);
        int itemsWritten = 0;
        int tracesWritten = 0;
        int tracesShed = 0;
        for (final Map.Entry<Integer, List<byte[]>> entry : byShard.entrySet()) {
            final ShardQueue queue = ShardQueue.of(settings, pathwaysDocRef.getUuid(), entry.getKey());
            if (queue.isDeeperThan(MAX_QUEUE_DEPTH_PER_SHARD)) {
                tracesShed += entry.getValue().size();
                LOGGER.warn(() -> LogUtil.message(
                        "Shedding {} trace(s) for shard {}: more than {} unread items are waiting in {}. "
                        + "Those traces will never reach pathways.",
                        entry.getValue().size(), entry.getKey(), MAX_QUEUE_DEPTH_PER_SHARD, queue));
                continue;
            }
            if (writer.write(traceDb, entry.getValue(), queue.dir(), orderKey, sourceUuid)
                    .isPresent()) {
                itemsWritten++;
                tracesWritten += entry.getValue().size();
            }
        }

        // The figures the design's cost estimate rests on, which nothing has measured yet: how many
        // items a cycle really writes, and how many traces they carry.
        final int items = itemsWritten;
        final int traces = tracesWritten;
        final int shed = tracesShed;
        LOGGER.debug(() -> LogUtil.message(
                "Handed over {} trace(s) in {} item(s) across {} shard(s) to {}{}",
                traces, items, byShard.size(), pathwaysDocRef.getName(),
                shed == 0
                        ? ""
                        : ", shedding " + shed));
    }

    // A pathway is one named operation, so the name is what decides the shard — every trace for one
    // operation has to land on the one process that holds its model. A trace whose root has no name is
    // dropped: the merge only reports traces with a real root, so this should not happen, and hashing
    // an empty name would gather unrelated operations onto one shard.
    private Map<Integer, List<byte[]>> groupByShard(final TraceDb traceDb,
                                                    final List<byte[]> handedOver,
                                                    final int shardCount) {
        final Map<Integer, List<byte[]>> byShard = new HashMap<>();
        traceDb.forEachRoot(handedOver, (traceIdBytes, root) -> {
            if (NullSafe.isBlankString(root.getName())) {
                LOGGER.warn(() -> "Not handing over trace " + root.getTraceId() + ": its root has no name");
                return;
            }
            byShard.computeIfAbsent(
                            ShardKeyRouter.computeShardIndex(root.getName(), shardCount),
                            k -> new ArrayList<>())
                    .add(traceIdBytes);
        });
        return byShard;
    }

    // Where the Pathways document keeps its queue, or empty where it has not been configured. Read
    // through the generic document store registry, because this module cannot depend on the one that
    // implements the Pathways store — that dependency runs the other way. Resolved per push rather
    // than cached: it is a keyed fetch of a document the store already has in memory.
    private Optional<SharedFileStoreSettings> sharedFileStoreOf(final DocRef pathwaysDocRef) {
        try {
            final Object doc = documentStoreRegistryProvider.get()
                    .getDocumentStore(PathwaysDoc.TYPE)
                    .readDocument(pathwaysDocRef);
            return doc instanceof final PathwaysDoc pathwaysDoc
                    ? Optional.ofNullable(pathwaysDoc.getSharedFileStore())
                    .filter(settings -> NullSafe.isNonBlankString(settings.getSharedPath()))
                    : Optional.empty();
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> "Could not read Pathways document " + pathwaysDocRef + ": " + e.getMessage(), e);
            return Optional.empty();
        }
    }
}
