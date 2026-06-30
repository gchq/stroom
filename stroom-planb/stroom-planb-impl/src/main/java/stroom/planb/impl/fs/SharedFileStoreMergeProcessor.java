/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.fs;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.node.api.NodeInfo;
import stroom.pathways.shared.PathwaysDoc;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDocument;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Singleton
public class SharedFileStoreMergeProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedFileStoreMergeProcessor.class);

    private final ClusterLockService clusterLockService;
    private final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider;
    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final Provider<EntityEventBus> entityEventBusProvider;
    private final TaskContextFactory taskContextFactory;
    private final SharedFileStorePublisher publisher;
    private final List<SharedFileStoreOperation> operations;
    private final PlanBDocCache planBDocCache;

    @Inject
    public SharedFileStoreMergeProcessor(final ClusterLockService clusterLockService,
                                         final Provider<Map<DocumentTypeName, DocumentActionHandler>>
                                                 documentActionHandlersProvider,
                                         final ByteBuffers byteBuffers,
                                         final ByteBufferFactory byteBufferFactory,
                                         final Provider<PlanBConfig> configProvider,
                                         final StatePaths statePaths,
                                         final NodeInfo nodeInfo,
                                         final SecurityContext securityContext,
                                         final Provider<EntityEventBus> entityEventBusProvider,
                                         final TaskContextFactory taskContextFactory,
                                         final PlanBDocCache planBDocCache) {
        this.clusterLockService = clusterLockService;
        this.documentActionHandlersProvider = documentActionHandlersProvider;
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.entityEventBusProvider = entityEventBusProvider;
        this.taskContextFactory = taskContextFactory;
        this.publisher = new SharedFileStorePublisher(nodeInfo);
        this.operations = List.of(new RetentionOperation(), new ArchiveOperation(publisher));
        this.planBDocCache = planBDocCache;
    }

    public void merge() {
        securityContext.asProcessingUser(() -> {
            taskContextFactory.context("Plan B Shared FS Merge", taskContext -> {
                LOGGER.info("Starting Plan B Shared FS Merge");

                final List<PlanBDocument> planBDocs = planBDocCache.getAll();
                for (final PlanBDocument doc : planBDocs) {
                    if (doc.getSharedPath() != null && doc.getShardCount() > 0) {
                        try {
                            mergeDoc(doc, taskContext);
                        } catch (final Exception e) {
                            LOGGER.error("Error merging shards for doc {}", doc.getName(), e);
                        }
                    }
                }

                LOGGER.info("Finished Plan B Shared FS Merge");
            }).run();
        });
    }

    private void mergeDoc(final PlanBDocument doc, final TaskContext parentTaskContext) {
        final Path sharedPath = Path.of(doc.getSharedPath());
        final Path processingDocDir = sharedPath
                .resolve(PlanBConstants.PROCESSING_DIR_NAME)
                .resolve(doc.getUuid());
        final Path sharedShardsDocDir = sharedPath
                .resolve(PlanBConstants.SHARDS_DIR_NAME)
                .resolve(doc.getUuid());

        // hasReferencingPathways() scans the full docstore; computing it once
        // per doc avoids O(shardCount) full scans per merge cycle.
        final boolean pathwaysExist = hasReferencingPathways(doc.asDocRef());

        // Shuffle shard indices so concurrent nodes naturally scatter across
        // different shards on each merge cycle. Without this, all nodes would
        // race for shard 0 first, causing O(N²) failed lock attempts per cycle
        // instead of O(N).  With a shuffle each node is likely to win a
        // different shard, giving close to 1:1 work distribution.
        final List<Integer> shardIndices = new ArrayList<>(doc.getShardCount());
        for (int i = 0; i < doc.getShardCount(); i++) {
            shardIndices.add(i);
        }
        Collections.shuffle(shardIndices);

        for (final int shardIndex : shardIndices) {
            final String shardIndexStr = PlanBConstants.formatShardIndex(shardIndex);

            final List<Path> completeBatchDirs = collectBatchDirs(processingDocDir, shardIndexStr);

            // Sort by name for deterministic merge order: when two batches write
            // the same key the last-named batch wins.
            completeBatchDirs.sort(Comparator.comparing(p -> p.getFileName().toString()));

            // Evaluate operations outside the lock (read-only, safe to race).
            // Results are re-validated inside the lock before acting.
            final boolean operationDue = operations.stream()
                    .anyMatch(op -> op.isDue(doc, sharedShardsDocDir, shardIndex));

            if (!completeBatchDirs.isEmpty() || operationDue) {
                final Runnable runnable = taskContextFactory.childContext(parentTaskContext,
                        "Merge doc " + doc.getName() + " shard " + shardIndexStr,
                        taskContext -> securityContext.asProcessingUser(() ->
                                mergeShard(doc, shardIndex, completeBatchDirs,
                                        sharedShardsDocDir, pathwaysExist)));
                try {
                    runnable.run();
                } catch (final Exception e) {
                    LOGGER.error("Error processing shard {} for doc {}",
                            shardIndexStr, doc.getName(), e);
                }
            }
        }
        cleanUpProcessedBatches(doc, pathwaysExist);
    }

    private void mergeShard(final PlanBDocument doc,
                            final int shardIndex,
                            final List<Path> completeBatchDirs,
                            final Path sharedShardsDocDir,
                            final boolean pathwaysExist) {
        final String lockName = PlanBConstants.getMergeLockName(doc.getUuid(), shardIndex);
        LOGGER.debug(() -> "Attempting to acquire lock " + lockName);
        clusterLockService.tryLock(lockName, () -> {
            try {
                LOGGER.info("Acquired lock {}, starting merge/maintenance", lockName);

                // Recover any orphaned .tmp_ / .old_ dirs left by an interrupted push.
                // Must run before opening the shard so syncFromSharedStoreIfRequired()
                // sees a consistent shard directory.
                publisher.recoverOrphaned(sharedShardsDocDir, shardIndex);

                final SharedFileStoreShard shard = new SharedFileStoreShard(
                        byteBuffers, byteBufferFactory, configProvider, statePaths, doc, shardIndex,
                        statePaths.getMergingDir());
                try {
                    boolean modified = mergeAllBatches(shard, completeBatchDirs);

                    final SharedFileStoreOperationContext ctx = new SharedFileStoreOperationContext(
                            doc, shardIndex, shard, sharedShardsDocDir, lockName);
                    for (final SharedFileStoreOperation op : operations) {
                        modified |= op.run(ctx);
                    }

                    if (modified) {
                        publisher.push(doc, shardIndex, shard);
                    }

                    fireBatchEvents(doc, shardIndex, completeBatchDirs, pathwaysExist);
                    LOGGER.info("Successfully completed merge/maintenance for {}", lockName);
                } finally {
                    final Path mergeShardDir = shard.getShardDir();
                    shard.dispose();
                    // The merge shard runs in an isolated subdirectory of mergingDir rather than
                    // shardDir. Clean it up now that the merge is done and published.
                    try {
                        FileUtil.deleteDir(mergeShardDir);
                    } catch (final Exception e) {
                        LOGGER.warn("Failed to clean up merge directory {}: {}", mergeShardDir, e.getMessage());
                    }
                }
            } catch (final IOException e) {
                LOGGER.error("Error during merge/maintenance for {}", lockName, e);
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Merges each batch directory into the shard. Returns {@code true} if at
     * least one batch was successfully merged.
     */
    private boolean mergeAllBatches(final SharedFileStoreShard shard,
                                    final List<Path> completeBatchDirs) throws IOException {
        boolean modified = false;
        for (final Path batchDir : completeBatchDirs) {
            modified |= mergeSingleBatch(shard, batchDir);
        }
        return modified;
    }

    /**
     * Copies a batch directory to a local temp location, merges it into the
     * shard, and cleans up the temp copy. Returns {@code true} if the shard
     * was modified.
     */
    private boolean mergeSingleBatch(final SharedFileStoreShard shard, final Path batchDir) throws IOException {
        LOGGER.info("Merging batch {}", batchDir);
        final Path localTempBatchDir = Files.createTempDirectory("planb_merge_");
        try {
            copyIfExists(batchDir.resolve(PlanBConstants.DATA_FILE_NAME),
                    localTempBatchDir.resolve(PlanBConstants.DATA_FILE_NAME));
            shard.merge(localTempBatchDir);
            return true;
        } finally {
            FileUtil.deleteDir(localTempBatchDir);
        }
    }

    /**
     * Writes {@code .merged} markers, fires entity update events for Pathways
     * consumers, and deletes batch directories that are fully processed.
     */
    private void fireBatchEvents(final PlanBDocument doc,
                                 final int shardIndex,
                                 final List<Path> batchDirs,
                                 final boolean pathwaysExist) {
        for (final Path batchDir : batchDirs) {
            try {
                Files.writeString(batchDir.resolve(PlanBConstants.MERGED_FILE_NAME),
                        Instant.now().toString());
            } catch (final IOException e) {
                LOGGER.error("Error writing .merged marker to {}", batchDir, e);
            }

            final String batchDirName = batchDir.getFileName().toString();
            if (!pathwaysExist) {
                FileUtil.deleteDir(batchDir);
            } else {
                final EntityEventBus eventBus = entityEventBusProvider.get();
                if (eventBus != null) {
                    final String newVersion = System.currentTimeMillis() + "_"
                            + nodeInfo.getThisNodeName();
                    LOGGER.info("Firing MERGE UPDATE event for doc: {}, shard: {}, batch: {}",
                            doc.getName(), shardIndex, batchDirName);
                    EntityEvent.fire(eventBus, doc.asDocRef(), EntityAction.UPDATE,
                            new SharedFileStoreMergeEventData(shardIndex, batchDirName, newVersion));
                }
            }
        }
    }

    /**
     * Collects complete, unmerged batch directories for the given shard.
     */
    private List<Path> collectBatchDirs(final Path processingDocDir,
                                        final String shardIndexStr) {
        final List<Path> completeBatchDirs = new ArrayList<>();
        final Path shardBatchDir = processingDocDir.resolve(shardIndexStr);
        if (Files.exists(shardBatchDir)) {
            try (final Stream<Path> batchStream = Files.list(shardBatchDir)) {
                batchStream.forEach(batchDir -> {
                    if (Files.exists(batchDir.resolve(PlanBConstants.COMPLETE_FILE_NAME))
                            && !Files.exists(batchDir.resolve(PlanBConstants.MERGED_FILE_NAME))) {
                        completeBatchDirs.add(batchDir);
                    }
                });
            } catch (final IOException e) {
                LOGGER.error("Error listing batch directories in {}", shardBatchDir, e);
            }
        }
        return completeBatchDirs;
    }

    private void cleanUpProcessedBatches(final PlanBDocument doc, final boolean pathwaysExist) {
        final Path sharedPath = Path.of(doc.getSharedPath());
        final Path processingDir = sharedPath
                .resolve(PlanBConstants.PROCESSING_DIR_NAME)
                .resolve(doc.getUuid());

        if (!Files.exists(processingDir)) {
            return;
        }

        try (final Stream<Path> shardDirStream = Files.list(processingDir)) {
            for (final Path shardDir : shardDirStream.toList()) {
                try (final Stream<Path> batchDirStream = Files.list(shardDir)) {
                    for (final Path batchDir : batchDirStream.toList()) {
                        final boolean isMerged = Files.exists(
                                batchDir.resolve(PlanBConstants.MERGED_FILE_NAME));
                        final boolean isPathwaysProcessed = Files.exists(
                                batchDir.resolve(PlanBConstants.PATHWAYS_PROCESSED_FILE_NAME));
                        if (isMerged && (!pathwaysExist || isPathwaysProcessed)) {
                            LOGGER.info("Cleaning up fully processed batch directory: {}", batchDir);
                            FileUtil.deleteDir(batchDir);
                        }
                    }
                } catch (final IOException e) {
                    LOGGER.error("Error listing batch directories for cleanup in {}", shardDir, e);
                }
            }
        } catch (final IOException e) {
            LOGGER.error("Error listing shard directories for cleanup in {}", processingDir, e);
        }
    }

    /**
     * Loads all documents assignable to {@code clazz} from the docstore by querying every
     * registered {@link ImportExportActionHandler}.
     */
    private <T> List<T> loadDocsByType(final Class<T> clazz) {
        final List<T> results = new ArrayList<>();
        documentActionHandlersProvider.get().values().stream()
                .filter(handler -> handler instanceof ImportExportActionHandler)
                .forEach(handler -> {
                    final ImportExportActionHandler ieHandler = (ImportExportActionHandler) handler;
                    try {
                        for (final DocRef docRef : ieHandler.listDocuments()) {
                            final Object doc = handler.readDocument(docRef);
                            if (clazz.isInstance(doc)) {
                                results.add(clazz.cast(doc));
                            }
                        }
                    } catch (final Exception e) {
                        LOGGER.error("Error reading documents from handler {}",
                                ieHandler.getType(), e);
                    }
                });
        return results;
    }

    private boolean hasReferencingPathways(final DocRef tracesDocRef) {
        return loadDocsByType(PathwaysDoc.class).stream()
                .anyMatch(p -> p.getTracesDocRef() != null
                        && Objects.equals(p.getTracesDocRef().getUuid(),
                                tracesDocRef.getUuid()));
    }

    private static void copyIfExists(final Path src, final Path dst) throws IOException {
        if (Files.exists(src)) {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
