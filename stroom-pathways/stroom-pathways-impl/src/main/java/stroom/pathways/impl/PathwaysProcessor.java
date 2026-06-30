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
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.node.api.NodeInfo;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.PathwayResultPage;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.pathway.Pathway;
import stroom.planb.impl.data.ShardManager;
import stroom.planb.impl.db.Count;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.trace.PathwaysDb;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.util.io.FileUtil;
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
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Singleton
public class PathwaysProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PathwaysProcessor.class);
    private static final ByteBuffer PROCESSED = ByteBuffer.allocate(0);

    private final PathwaysStore pathwaysStore;
    private final MessageReceiverFactory messageReceiverFactory;
    private final ByteBuffers byteBuffers;
    private final Path dbPath;
    private final Map<String, PathwaysDb> pathwaysDbMap = new ConcurrentHashMap<>();
    private final PathwaySerde pathwaySerde;
    private final ShardManager shardManager;
    private final NodeInfo nodeInfo;
    private final TracesDocStore tracesDocStore;
    private final ClusterLockService clusterLockService;
    private final ByteBufferFactory byteBufferFactory;

    @Inject
    public PathwaysProcessor(final PathwaysStore pathwaysStore,
                             final MessageReceiverFactory messageReceiverFactory,
                             final PathCreator pathCreator,
                             final ByteBuffers byteBuffers,
                             final PathwaySerde pathwaySerde,
                             final ShardManager shardManager,
                             final NodeInfo nodeInfo,
                             final TracesDocStore tracesDocStore,
                             final ClusterLockService clusterLockService,
                             final ByteBufferFactory byteBufferFactory) {
        this.pathwaysStore = pathwaysStore;
        this.messageReceiverFactory = messageReceiverFactory;
        this.byteBuffers = byteBuffers;
        this.pathwaySerde = pathwaySerde;
        this.shardManager = shardManager;
        this.nodeInfo = nodeInfo;
        this.tracesDocStore = tracesDocStore;
        this.clusterLockService = clusterLockService;
        this.byteBufferFactory = byteBufferFactory;

        dbPath = pathCreator.toAppPath("${stroom.home}/pathways");
    }

    public void exec() {
        final List<DocRef> docRefs = pathwaysStore.list();
        for (final DocRef docRef : NullSafe.list(docRefs)) {
            final PathwaysDoc doc = pathwaysStore.readDocument(docRef);
            if (doc != null &&
                doc.getTracesDocRef() != null &&
                Objects.equals(doc.getProcessingNode(), nodeInfo.getThisNodeName())) {

                // Check that this is the node that trace stores are likely to be located.
                if (shardManager.isSnapshotNode()) {
                    throw new RuntimeException("Attempt to run pathways processing on different node to trace store");
                }

                // Load pathways DB for doc.
                final PathwaysDb pathwaysDb = getPathwaysDb(docRef);

                final DocRef infoFeed = doc.getInfoFeed();
                if (infoFeed != null && infoFeed.getName() != null) {
                    messageReceiverFactory.create(infoFeed.getName(), messageReceiver -> {

                        shardManager.get(doc.getTracesDocRef().getName(), db -> {
                            if (db instanceof final TraceDb traceDb) {

                                try (final LmdbWriter writer = pathwaysDb.createWriter()) {
                                    final TraceProcessor traceProcessor =
                                            new TraceProcessor(byteBuffers, pathwaySerde);
                                    traceDb.iterateTraces((traceId, function) ->
                                            traceProcessor.processTrace(writer,
                                                    pathwaysDb,
                                                    traceId,
                                                    function,
                                                    doc,
                                                    messageReceiver));
                                    writer.commit();
                                }
                            }
                            return null;
                        });
                    });
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

    public void exec(final PathwaysDoc doc) {
        final String lockName = "pathways-write-" + doc.getUuid();
        LOGGER.info("Attempting to acquire cluster lock {} for pathways processing", lockName);

        boolean checkAgain = true;
        while (checkAgain) {
            checkAgain = false;

            final java.util.concurrent.atomic.AtomicBoolean acquired =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            clusterLockService.tryLock(lockName, () -> {
                acquired.set(true);
                LOGGER.info("Acquired cluster lock {}, starting pathways processing", lockName);
                processAllPendingBatches(doc);
            });

            if (acquired.get()) {
                if (hasPendingBatches(doc)) {
                    LOGGER.info("Pending batches still exist after releasing lock {}, looping again", lockName);
                    checkAgain = true;
                }
            } else {
                LOGGER.info("Failed to acquire lock {} (another node/thread is processing). Exiting safely.", lockName);
                break;
            }
        }
    }

    private void processAllPendingBatches(final PathwaysDoc doc) {
        try {
            // 1. Check that this is not a snapshot node
            if (shardManager.isSnapshotNode()) {
                throw new RuntimeException("Attempt to run pathways processing on snapshot node");
            }

            // 2. Resolve the processing directory path for this traces doc
            final DocRef tracesDocRef = doc.getTracesDocRef();
            final TracesDoc tracesDoc = tracesDocStore.readDocument(tracesDocRef);
            if (tracesDoc == null) {
                throw new RuntimeException("Traces document not found: " + tracesDocRef);
            }
            if (tracesDoc.getSharedPath() == null) {
                throw new RuntimeException("Traces document shared path not found for: " + tracesDocRef);
            }

            final Path processingDir = Path.of(tracesDoc.getSharedPath())
                    .resolve("processing")
                    .resolve(tracesDoc.getUuid());

            if (!Files.exists(processingDir)) {
                LOGGER.info("Processing directory does not exist: {}", processingDir);
                return;
            }

            // 3. Scan the processing directory for all shards and batches
            final List<Path> pendingBatches = new ArrayList<>();
            try (final Stream<Path> shardsStream = Files.list(processingDir)) {
                shardsStream.filter(Files::isDirectory).forEach(shardDir -> {
                    try (final Stream<Path> batchesStream = Files.list(shardDir)) {
                        batchesStream.filter(Files::isDirectory).forEach(batchDir -> {
                            final boolean isMerged = Files.exists(batchDir.resolve(".merged"));
                            final boolean isProcessed = Files.exists(batchDir.resolve(".pathways_processed"));
                            if (isMerged && !isProcessed) {
                                pendingBatches.add(batchDir);
                            }
                        });
                    } catch (final IOException e) {
                        LOGGER.error("Error listing batches in " + shardDir, e);
                    }
                });
            } catch (final IOException e) {
                LOGGER.error("Error listing shards in " + processingDir, e);
            }

            if (pendingBatches.isEmpty()) {
                LOGGER.info("No pending batches found for pathways doc: {}", doc.getName());
                return;
            }

            LOGGER.info("Found {} pending batches to process for pathways doc: {}",
                    pendingBatches.size(), doc.getName());

            // 4. Sync pathways DB from shared store and open it
            final PathwaysDb pathwaysDb = syncAndGetSharedPathwaysDb(doc);

            final DocRef infoFeed = doc.getInfoFeed();
            if (infoFeed != null && infoFeed.getName() != null) {
                messageReceiverFactory.create(infoFeed.getName(), messageReceiver -> {
                    try (final LmdbWriter writer = pathwaysDb.createWriter()) {
                        final TraceProcessor traceProcessor =
                                new TraceProcessor(byteBuffers, pathwaySerde);

                        for (final Path batchDir : pendingBatches) {
                            LOGGER.info("Opening read-only TraceDb for batch: {}", batchDir);
                            try (final TraceDb batchTraceDb = TraceDb.create(
                                    batchDir, byteBuffers, byteBufferFactory, tracesDoc, true)) {
                                batchTraceDb.iterateTraces((traceId, function) ->
                                        traceProcessor.processTrace(writer,
                                                pathwaysDb,
                                                traceId,
                                                function,
                                                doc,
                                                messageReceiver));
                            } catch (final Exception e) {
                                LOGGER.error("Error processing traces in batch " + batchDir, e);
                                // Continue processing other batches even if one fails
                            }
                        }
                        writer.commit();
                    }
                });
            }

            // 5. Close and push the updated pathways DB back to shared store
            pushPathwaysDbToSharedStore(doc, pathwaysDb);

            // 6. Write the pathways processed marker file inside the completed batch directories
            for (final Path batchDir : pendingBatches) {
                try {
                    Files.writeString(batchDir.resolve(".pathways_processed"), java.time.Instant.now().toString());
                    LOGGER.info("Successfully marked batch {} as pathways processed", batchDir.getFileName());
                } catch (final IOException e) {
                    LOGGER.error("Failed to write pathways processed marker to " + batchDir, e);
                }
            }

        } catch (final Exception e) {
            LOGGER.error("Error during pathways processing for doc " + doc.getName(), e);
            throw new RuntimeException(e);
        }
    }

    private boolean hasPendingBatches(final PathwaysDoc doc) {
        try {
            final TracesDoc tracesDoc = tracesDocStore.readDocument(doc.getTracesDocRef());
            if (tracesDoc == null || tracesDoc.getSharedPath() == null) {
                return false;
            }
            final Path processingDir = Path.of(tracesDoc.getSharedPath())
                    .resolve("processing")
                    .resolve(tracesDoc.getUuid());

            if (!Files.exists(processingDir)) {
                return false;
            }

            try (final Stream<Path> shardsStream = Files.list(processingDir)) {
                return shardsStream.filter(Files::isDirectory).anyMatch(shardDir -> {
                    try (final Stream<Path> batchesStream = Files.list(shardDir)) {
                        return batchesStream.filter(Files::isDirectory).anyMatch(batchDir -> {
                            final boolean isMerged = Files.exists(batchDir.resolve(".merged"));
                            final boolean isProcessed = Files.exists(batchDir.resolve(".pathways_processed"));
                            return isMerged && !isProcessed;
                        });
                    } catch (final IOException e) {
                        LOGGER.error("Error listing batches in " + shardDir, e);
                        return false;
                    }
                });
            }
        } catch (final Exception e) {
            LOGGER.error("Error checking for pending batches for doc " + doc.getName(), e);
            return false;
        }
    }

    private PathwaysDb syncAndGetSharedPathwaysDb(final PathwaysDoc doc) {
        try {
            final TracesDoc tracesDoc = tracesDocStore.readDocument(doc.getTracesDocRef());
            if (tracesDoc == null || tracesDoc.getSharedPath() == null) {
                throw new RuntimeException("Traces document shared path not found for: " + doc.getTracesDocRef());
            }

            final Path sharedPath = Path.of(tracesDoc.getSharedPath());
            final Path sharedPathwaysDir = sharedPath.resolve("pathways").resolve(doc.getUuid());
            final Path localPathwaysDir = dbPath.resolve("shared_sync").resolve(doc.getUuid());

            Files.createDirectories(localPathwaysDir);

            if (Files.exists(sharedPathwaysDir)) {
                final Path sharedVersionFile = sharedPathwaysDir.resolve(".version");
                if (Files.exists(sharedVersionFile)) {
                    final String sharedVersion = Files.readString(sharedVersionFile).trim();
                    final Path localVersionFile = localPathwaysDir.resolve(".version");
                    String localVersion = "";
                    if (Files.exists(localVersionFile)) {
                        localVersion = Files.readString(localVersionFile).trim();
                    }

                    if (!Objects.equals(sharedVersion, localVersion)) {
                        LOGGER.info("Syncing pathways DB from shared store. Shared version: {}, Local version: {}",
                                sharedVersion, localVersion);

                        final Path sharedData = sharedPathwaysDir.resolve("data.mdb");
                        if (Files.exists(sharedData)) {
                            Files.copy(sharedData,
                                    localPathwaysDir.resolve("data.mdb"),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        final Path sharedLock = sharedPathwaysDir.resolve("lock.mdb");
                        if (Files.exists(sharedLock)) {
                            Files.copy(sharedLock,
                                    localPathwaysDir.resolve("lock.mdb"),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        Files.writeString(localVersionFile, sharedVersion);
                    }
                }
            }

            return PathwaysDb.create(localPathwaysDir, byteBuffers, false);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void pushPathwaysDbToSharedStore(final PathwaysDoc doc, final PathwaysDb pathwaysDb) {
        try {
            pathwaysDb.close();

            final TracesDoc tracesDoc = tracesDocStore.readDocument(doc.getTracesDocRef());
            final Path sharedPath = Path.of(tracesDoc.getSharedPath());
            final Path sharedPathwaysDir = sharedPath.resolve("pathways").resolve(doc.getUuid());
            final Path localPathwaysDir = dbPath.resolve("shared_sync").resolve(doc.getUuid());

            final Path sharedTempDir = sharedPathwaysDir.resolveSibling(".tmp_pathways_" + System.currentTimeMillis());
            Files.createDirectories(sharedTempDir);

            final Path localData = localPathwaysDir.resolve("data.mdb");
            if (Files.exists(localData)) {
                Files.copy(localData,
                        sharedTempDir.resolve("data.mdb"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            final Path localLock = localPathwaysDir.resolve("lock.mdb");
            if (Files.exists(localLock)) {
                Files.copy(localLock,
                        sharedTempDir.resolve("lock.mdb"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            Files.writeString(sharedTempDir.resolve(".complete"), "");

            // Perform atomic move swap
            final Path oldDir = sharedPathwaysDir.resolveSibling(".old_pathways_" + System.currentTimeMillis());
            if (Files.exists(sharedPathwaysDir)) {
                try {
                    Files.move(sharedPathwaysDir, oldDir, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (final java.nio.file.AtomicMoveNotSupportedException e) {
                    LOGGER.warn("Atomic move not supported on shared store. Falling back to replace_existing.");
                    Files.move(sharedPathwaysDir, oldDir, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            try {
                Files.move(sharedTempDir, sharedPathwaysDir, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (final java.nio.file.AtomicMoveNotSupportedException e) {
                LOGGER.warn("Atomic move not supported on shared store. Falling back to replace_existing.");
                Files.move(sharedTempDir, sharedPathwaysDir, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(oldDir)) {
                FileUtil.deleteDir(oldDir);
            }

            // Write version file
            final String newVersion = System.currentTimeMillis() + "_" + nodeInfo.getThisNodeName();
            Files.writeString(sharedPathwaysDir.resolve(".version"), newVersion);
            Files.writeString(localPathwaysDir.resolve(".version"), newVersion);

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
