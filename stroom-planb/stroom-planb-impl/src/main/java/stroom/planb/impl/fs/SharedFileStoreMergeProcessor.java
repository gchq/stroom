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
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDocument;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Singleton
public class SharedFileStoreMergeProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedFileStoreMergeProcessor.class);

    private final ClusterLockService clusterLockService;
    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ExecutorService mergeExecutor;
    private final SharedFileStorePublisher publisher;
    private final List<SharedFileStoreOperation> operations;
    private final PlanBDocCache planBDocCache;

    @Inject
    public SharedFileStoreMergeProcessor(final ClusterLockService clusterLockService,
                                         final ByteBuffers byteBuffers,
                                         final ByteBufferFactory byteBufferFactory,
                                         final Provider<PlanBConfig> configProvider,
                                         final StatePaths statePaths,
                                         final NodeInfo nodeInfo,
                                         final SecurityContext securityContext,
                                         final TaskContextFactory taskContextFactory,
                                         final PlanBDocCache planBDocCache) {
        this.clusterLockService = clusterLockService;
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.mergeExecutor = createMergeExecutor(configProvider.get().getShardMergeThreadCount());
        this.publisher = new SharedFileStorePublisher(nodeInfo, byteBuffers, byteBufferFactory);
        this.operations = List.of(new RetentionOperation(), new ArchiveOperation(publisher));
        this.planBDocCache = planBDocCache;
    }

    private static ExecutorService createMergeExecutor(final int threadCount) {
        final AtomicInteger threadNo = new AtomicInteger();
        return new ThreadPoolExecutor(
                threadCount,
                threadCount,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    final Thread thread = new Thread(
                            runnable,
                            "Plan B Shard Merge #" + threadNo.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setPriority(3);
                    return thread;
                });
    }

    public void merge() {
        securityContext.asProcessingUser(() -> {
            LOGGER.info("Starting Plan B Shared FS Merge");

            final TaskContext taskContext = taskContextFactory.current();
            final List<PlanBDocument> planBDocs = planBDocCache.getAll();
            Collections.shuffle(planBDocs);

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

        final List<CompletableFuture<Void>> futures = new ArrayList<>();

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
                                mergeShard(doc, shardIndex, completeBatchDirs, sharedShardsDocDir)));

                futures.add(CompletableFuture
                        .runAsync(runnable, mergeExecutor)
                        .exceptionally(t -> {
                            LOGGER.error("Error processing shard {} for doc {}",
                                    shardIndexStr, doc.getName(), t);
                            return null;
                        }));
            }
        }

        // Wait for all shard merges to complete before returning.
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    private void mergeShard(final PlanBDocument doc,
                            final int shardIndex,
                            final List<Path> completeBatchDirs,
                            final Path sharedShardsDocDir) {
        // Span over the whole method — includes time spent waiting to acquire the
        // cluster lock as well as the merge/maintenance work itself.
        final Tracer tracer = GlobalOpenTelemetry.getTracer(SharedFileStoreMergeProcessor.class.getName());
        final Span span = tracer.spanBuilder("SharedFileStoreMergeProcessor.mergeShard")
                .setAttribute("planb.doc", doc.getName())
                .setAttribute("planb.shardIndex", (long) shardIndex)
                .setAttribute("planb.batchCount", (long) completeBatchDirs.size())
                .startSpan();
        try (final Scope scope = span.makeCurrent()) {
            final String lockName = PlanBConstants.getMergeLockName(doc.getUuid(), shardIndex);
            LOGGER.debug(() -> "Attempting to acquire lock " + lockName);
            clusterLockService.tryLock(lockName, () -> {
                try {
                    LOGGER.info("Acquired lock {}, starting merge/maintenance", lockName);

                    // Recover any orphaned .tmp_ / .old_ dirs left by an interrupted push.
                    // Must run before opening the shard so syncFromSharedStoreIfRequired()
                    // sees a consistent shard directory.
                    final Span recoverSpan = tracer
                            .spanBuilder("SharedFileStoreMergeProcessor.recoverOrphaned").startSpan();
                    try (final Scope recoverScope = recoverSpan.makeCurrent()) {
                        publisher.recoverOrphaned(sharedShardsDocDir, shardIndex);
                    } finally {
                        recoverSpan.end();
                    }

                    // Open the shard. This may sync a fresh copy of the shard down from the
                    // shared store to local disk (see SharedFileStoreShard.syncFromSharedStore).
                    final Span openSpan = tracer
                            .spanBuilder("SharedFileStoreMergeProcessor.openShard")
                            .setAttribute("planb.shardIndex", (long) shardIndex)
                            .startSpan();
                    final SharedFileStoreShard shard;
                    try (final Scope openScope = openSpan.makeCurrent()) {
                        shard = new SharedFileStoreShard(
                                byteBuffers, byteBufferFactory, configProvider, statePaths, doc, shardIndex,
                                statePaths.getMergingDir());
                    } finally {
                        openSpan.end();
                    }

                    try {
                        // Child span over just the batch-merge work, so it can be
                        // distinguished from lock wait and downstream maintenance.
                        final Span mergeSpan = tracer
                                .spanBuilder("SharedFileStoreMergeProcessor.mergeAllBatches")
                                .setAttribute("planb.batchCount", (long) completeBatchDirs.size())
                                .startSpan();
                        boolean modified;
                        try (final Scope mergeScope = mergeSpan.makeCurrent()) {
                            modified = mergeAllBatches(shard, completeBatchDirs);
                        } finally {
                            mergeSpan.end();
                        }

                        // Maintenance operations (retention, archival, etc.).
                        final SharedFileStoreOperationContext ctx = new SharedFileStoreOperationContext(
                                doc, shardIndex, shard, sharedShardsDocDir, lockName);
                        final Span opsSpan = tracer
                                .spanBuilder("SharedFileStoreMergeProcessor.maintenanceOperations").startSpan();
                        try (final Scope opsScope = opsSpan.makeCurrent()) {
                            for (final SharedFileStoreOperation op : operations) {
                                modified |= op.run(ctx);
                            }
                        } finally {
                            opsSpan.end();
                        }

                        if (modified) {
                            // Push the merged shard back up to the shared store
                            // (local disk -> shared store copy).
                            final Span pushSpan = tracer
                                    .spanBuilder("SharedFileStoreMergeProcessor.push")
                                    .setAttribute("planb.shardIndex", (long) shardIndex)
                                    .startSpan();
                            try (final Scope pushScope = pushSpan.makeCurrent()) {
                                publisher.push(doc, shardIndex, shard);
                            } finally {
                                pushSpan.end();
                            }
                        }

                        cleanUpMergedBatches(completeBatchDirs);
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
        } finally {
            span.end();
        }
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
        if (modified) {
            // Recompute derived trace-root fields (depth/services/totalSpans) over the
            // fully-merged spans, now that every batch of this cycle is present.
            shard.mergeComplete();
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
        final Tracer tracer = GlobalOpenTelemetry.getTracer(SharedFileStoreMergeProcessor.class.getName());
        final Span batchSpan = tracer.spanBuilder("SharedFileStoreMergeProcessor.mergeBatch")
                .setAttribute("planb.batchDir", batchDir.getFileName().toString())
                .startSpan();
        try (final Scope batchScope = batchSpan.makeCurrent()) {
            final Path localTempBatchDir = Files.createTempDirectory("planb_merge_");
            try {
                // Copy the batch's data.mdb from the (shared) batch dir down to local disk.
                // Measured as negligible (sub-ms), so it is not spanned separately; the
                // mergeBatch span time is dominated by the LMDB shard.merge below.
                copyIfExists(batchDir.resolve(PlanBConstants.DATA_FILE_NAME),
                        localTempBatchDir.resolve(PlanBConstants.DATA_FILE_NAME));
                shard.merge(localTempBatchDir);
                return true;
            } finally {
                FileUtil.deleteDir(localTempBatchDir);
            }
        } finally {
            batchSpan.end();
        }
    }

    /**
     * Writes {@code .merged} markers and immediately deletes batch directories.
     * Pathways processing is now triggered by the live shard's
     * {@code trace-pathways-pending} DBI rather than via file-system events,
     * so batch directories are no longer retained for downstream consumers.
     */
    private void cleanUpMergedBatches(final List<Path> batchDirs) {
        for (final Path batchDir : batchDirs) {
            try {
                Files.writeString(batchDir.resolve(PlanBConstants.MERGED_FILE_NAME),
                        Instant.now().toString());
            } catch (final IOException e) {
                LOGGER.error("Error writing .merged marker to {}", batchDir, e);
            }
            FileUtil.deleteDir(batchDir);
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

    private static void copyIfExists(final Path src, final Path dst) throws IOException {
        if (Files.exists(src)) {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
