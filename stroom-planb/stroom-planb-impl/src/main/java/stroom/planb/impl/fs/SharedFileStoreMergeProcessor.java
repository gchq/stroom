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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.data.archive.BucketGranularityUtil;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.BucketGranularity;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.StateType;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Drives the merge cycle for every doc held on a shared file store.
 *
 * <p>Owns only what every store type shares: finding the docs and shards with work, holding the
 * per-shard cluster lock, choosing complete batches, counting failed attempts against a batch,
 * deleting merged batches, and applying retention to the archive buckets. What actually happens to
 * a shard's batches inside the lock is the {@link MergeStrategy} bound for its
 * {@link StateType} — a store type with no strategy is not merged.
 */
@Singleton
public class SharedFileStoreMergeProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedFileStoreMergeProcessor.class);

    /**
     * Merge attempts to allow a single batch before it is quarantined. Retrying is normally right,
     * because most failures are transient or clear once space is reclaimed, but a batch that can
     * never merge must stop consuming a cycle's worth of copying and merging forever.
     */
    private static final int MAX_BATCH_MERGE_ATTEMPTS = 10;

    private static final OperationMarker RETENTION_MARKER =
            new OperationMarker(PlanBConstants.RETENTION_LAST_FILE_NAME);

    private final ClusterLockService clusterLockService;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ExecutorService mergeExecutor;
    private final PlanBDocCache planBDocCache;
    private final Map<StateType, MergeStrategy> mergeStrategies;

    @Inject
    public SharedFileStoreMergeProcessor(final ClusterLockService clusterLockService,
                                         final Provider<PlanBConfig> configProvider,
                                         final SecurityContext securityContext,
                                         final TaskContextFactory taskContextFactory,
                                         final PlanBDocCache planBDocCache,
                                         final Map<StateType, MergeStrategy> mergeStrategies) {
        this.clusterLockService = clusterLockService;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.mergeExecutor = createMergeExecutor(configProvider.get().getShardMergeThreadCount());
        this.planBDocCache = planBDocCache;
        this.mergeStrategies = mergeStrategies;
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
            LOGGER.debug("Starting Plan B Shared FS Merge");

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

            LOGGER.debug("Finished Plan B Shared FS Merge");
        });
    }

    private void mergeDoc(final PlanBDocument doc, final TaskContext parentTaskContext) {
        final MergeStrategy strategy = mergeStrategies.get(doc.getStateType());
        if (strategy == null) {
            LOGGER.debug(() -> "No merge strategy for " + doc.getStateType() + ", skipping " + doc.getName());
            return;
        }

        final Path processingDocDir = Path.of(doc.getSharedPath())
                .resolve(PlanBConstants.PROCESSING_DIR_NAME)
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

            // Evaluate outside the lock (read-only, safe to race). Re-validated inside the lock
            // before acting.
            if (!completeBatchDirs.isEmpty() || retentionDue(doc, shardIndex)) {
                final Runnable runnable = taskContextFactory.childContext(parentTaskContext,
                        "Merge doc " + doc.getName() + " shard " + shardIndexStr,
                        taskContext -> securityContext.asProcessingUser(() ->
                                mergeShard(strategy, doc, shardIndex, completeBatchDirs)));

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

    private void mergeShard(final MergeStrategy strategy,
                            final PlanBDocument doc,
                            final int shardIndex,
                            final List<Path> completeBatchDirs) {
        final String lockName = PlanBConstants.getMergeLockName(doc.getUuid(), shardIndex);
        LOGGER.debug(() -> "Attempting to acquire lock " + lockName);
        clusterLockService.tryLock(lockName, () -> {
            try {
                LOGGER.debug("Acquired lock {}, starting merge/maintenance", lockName);

                // Re-check now the lock is held: another node may have run retention since the
                // pre-lock check.
                final boolean retentionDue = retentionDue(doc, shardIndex);
                if (retentionDue) {
                    final SimpleDuration interval = checkInterval(doc);
                    LOGGER.debug(() -> LogUtil.message("Running retention for {} (every {}, next due {})",
                            lockName, interval, SimpleDurationUtil.plus(Instant.now(), interval)));
                }

                final MergeContext ctx = new MergeContext(doc, shardIndex, lockName, retentionDue);
                final MergeResult result = strategy.mergeShard(ctx, completeBatchDirs);

                if (retentionDue) {
                    // Runs regardless of whether anything currently writes buckets, so buckets left
                    // by a previous configuration are cleaned up too.
                    deleteExpiredArchiveShards(ctx);
                    RETENTION_MARKER.recordRun(archiveDocDir(doc), shardIndex);
                }

                result.failures().forEach(this::recordBatchFailure);
                cleanUpMergedBatches(result.mergedBatchDirs());

                if (!result.failures().isEmpty()) {
                    LOGGER.error(() -> LogUtil.message(
                                    "Completed merge/maintenance for {} with {} of {} batches failing",
                                    lockName,
                                    result.failures().size(),
                                    completeBatchDirs.size()),
                            result.firstFailure());
                } else {
                    LOGGER.debug("Successfully completed merge/maintenance for {}", lockName);
                }
            } catch (final IOException e) {
                LOGGER.error("Error during merge/maintenance for {}", lockName, e);
                throw new UncheckedIOException(e);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Retention
    // -----------------------------------------------------------------------

    /**
     * Whether retention is due for this shard. How often it is checked comes from the doc's own
     * {@link RetentionSettings#getCheckInterval()} — not from the retention period, which decides
     * only which data is deleted. Retention is therefore honoured to within that interval.
     *
     * <p>Safe to call outside the shard cluster lock: it only reads a marker file.
     */
    static boolean retentionDue(final PlanBDocument doc, final int shardIndex) {
        final RetentionSettings retention = retentionSettings(doc);
        if (retention == null || !retention.isEnabled()) {
            return false;
        }
        final Instant lastRun = RETENTION_MARKER.lastRun(archiveDocDir(doc), shardIndex);
        return lastRun == null
               || Instant.now().isAfter(SimpleDurationUtil.plus(lastRun, retention.getCheckInterval()));
    }

    static void deleteExpiredArchiveShards(final MergeContext ctx) {
        final RetentionSettings retention = retentionSettings(ctx.doc());
        if (retention == null || !retention.isEnabled()) {
            return;
        }
        final Instant retentionBefore =
                SimpleDurationUtil.minus(Instant.now(), retention.getDuration());

        final Path archiveShardDir = archiveDocDir(ctx.doc())
                .resolve(PlanBConstants.formatShardIndex(ctx.shardIndex()));

        if (!Files.exists(archiveShardDir)) {
            return;
        }

        try (final Stream<Path> dateDirs = Files.list(archiveShardDir)) {
            for (final Path dateDir : dateDirs.toList()) {
                if (!Files.isDirectory(dateDir)) {
                    continue;
                }
                final String dateLabel = dateDir.getFileName().toString();

                // The directory name records how the bucket was written, so it is the only thing that
                // can decode it: a doc whose granularity has since changed still has buckets in the
                // old layout.
                final BucketGranularity granularity = BucketGranularityUtil.detect(dateLabel);

                if (granularity == null) {
                    LOGGER.warn("Cannot determine granularity for archive dir {}, skipping", dateDir);
                    continue;
                }

                final Instant bucketEnd = BucketGranularityUtil.bucketEnd(granularity, dateLabel);
                if (bucketEnd == null) {
                    LOGGER.warn("Cannot parse bucket end for archive dir {}, skipping", dateDir);
                    continue;
                }

                if (retentionBefore.isAfter(bucketEnd)) {
                    LOGGER.debug("Deleting expired archive shard {} for {}", dateLabel, ctx.lockName());
                    FileUtil.deleteDir(dateDir);
                }
            }
        } catch (final IOException e) {
            LOGGER.error("Error scanning archive shards in {}: {}", archiveShardDir, e.getMessage(), e);
        }
    }

    /**
     * Where a doc's archive buckets live, and with them the {@code .retention.last} marker that gates
     * pruning them. {@link ArchiveShardLocator} only treats directories as buckets, so a marker file
     * alongside them is ignored.
     */
    private static Path archiveDocDir(final PlanBDocument doc) {
        return Path.of(doc.getSharedPath())
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(doc.getUuid());
    }

    private static RetentionSettings retentionSettings(final PlanBDocument doc) {
        return NullSafe.get(doc, PlanBDocument::getSettings, AbstractPlanBSettings::getRetention);
    }

    private static SimpleDuration checkInterval(final PlanBDocument doc) {
        return NullSafe.get(doc, PlanBDocument::getSettings, AbstractPlanBSettings::getRetention,
                RetentionSettings::getCheckInterval);
    }

    // -----------------------------------------------------------------------
    // Batch bookkeeping
    // -----------------------------------------------------------------------

    /**
     * Records that a batch failed to merge so that a batch which can never merge is eventually
     * quarantined by {@link #collectBatchDirs} instead of being retried on every cycle forever.
     */
    private void recordBatchFailure(final Path batchDir, final Exception failure) {
        final Path failedFile = batchDir.resolve(PlanBConstants.FAILED_FILE_NAME);
        final int attempts = readFailedAttempts(batchDir) + 1;
        try {
            Files.writeString(failedFile, attempts + "\n" + Instant.now() + "\n" + failure);
        } catch (final IOException e) {
            LOGGER.error("Error writing .failed marker to {}", batchDir, e);
        }
    }

    private int readFailedAttempts(final Path batchDir) {
        final Path failedFile = batchDir.resolve(PlanBConstants.FAILED_FILE_NAME);
        if (!Files.isRegularFile(failedFile)) {
            return 0;
        }
        try {
            final String first = Files.readString(failedFile).lines().findFirst().orElse("");
            return Integer.parseInt(first.trim());
        } catch (final IOException | NumberFormatException e) {
            LOGGER.debug(() -> LogUtil.message("Unreadable .failed marker in {}", batchDir), e);
            return 0;
        }
    }

    /**
     * Writes {@code .merged} markers and immediately deletes batch directories. Nothing downstream
     * reads a merged batch directory, so none is kept.
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
                    if (Files.exists(batchDir.resolve(PlanBConstants.VERSION_FILE_NAME))
                            && !Files.exists(batchDir.resolve(PlanBConstants.MERGED_FILE_NAME))) {
                        final int attempts = readFailedAttempts(batchDir);
                        if (attempts >= MAX_BATCH_MERGE_ATTEMPTS) {
                            LOGGER.warn(() -> LogUtil.message(
                                    "Quarantining batch {} after {} failed merge attempts. It will " +
                                    "not be retried until its {} marker is removed.",
                                    batchDir, attempts, PlanBConstants.FAILED_FILE_NAME));
                        } else {
                            completeBatchDirs.add(batchDir);
                        }
                    }
                });
            } catch (final IOException e) {
                LOGGER.error("Error listing batch directories in {}", shardBatchDir, e);
            }
        }
        return completeBatchDirs;
    }
}
