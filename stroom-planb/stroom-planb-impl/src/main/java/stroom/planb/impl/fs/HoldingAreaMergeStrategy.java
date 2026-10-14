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
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.dao.PlanBEnv.Usage;
import stroom.planb.impl.data.archive.ArchiveShardLocator;
import stroom.planb.impl.data.archive.BucketGranularityUtil;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.BucketGranularity;
import stroom.planb.shared.HasHoldingAreaSettings;
import stroom.planb.shared.HoldingAreaSettings;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.RetentionSettings;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Merges the waiting work through a holding shard: it is merged into one shard per doc and shard index on
 * the shared store, and a separate pass moves records on from there into the date-labelled archive
 * buckets that queries read.
 *
 * <p>The holding shard is not queried, so nothing is visible until it has been drained. That is why
 * the drain runs on every cycle rather than to a schedule — any delay here is query latency.
 */
@Singleton
public class HoldingAreaMergeStrategy implements MergeStrategy {

    /** When this doc's archive buckets were last swept, kept beside them. */
    private static final OperationMarker RETENTION_MARKER =
            new OperationMarker(PlanBConstants.RETENTION_LAST_FILE_NAME);

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HoldingAreaMergeStrategy.class);

    /**
     * Fraction of a shard's LMDB map that, once allocated, makes a merge to it futile. A write txn
     * needs spare pages to copy the pages it modifies, so a merge attempted with almost no headroom
     * fails partway rather than merging a bit less.
     */
    private static final double MERGE_MAX_USED_FRACTION = 0.95;

    private static final OperationMarker COMPACT_MARKER =
            new OperationMarker(PlanBConstants.COMPACTION_LAST_FILE_NAME);

    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final Provider<PlanBConfig> configProvider;
    private final PlanBPaths planBPaths;
    private final SharedFileStorePublisher publisher;
    private final LocalArchive localArchive;

    @Inject
    public HoldingAreaMergeStrategy(final ByteBuffers byteBuffers,
                                    final ByteBufferFactory byteBufferFactory,
                                    final Provider<PlanBConfig> configProvider,
                                    final PlanBPaths planBPaths,
                                    final SharedFileStorePublisher publisher,
                                    final LocalArchive localArchive) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.configProvider = configProvider;
        this.planBPaths = planBPaths;
        this.publisher = publisher;
        this.localArchive = localArchive;
    }

    @Override
    public MergeResult mergeShard(final MergeContext ctx, final List<Path> queueDirs) throws IOException {
        // Undo the partial state left by an interrupted push before opening the shard, so the sync
        // down from the shared store sees a consistent directory.
        publisher.recoverOrphaned(shardsDocDir(ctx.doc()), ctx.shardIndex());

        // Copies the current shared shard down to local disk so this cycle's work merges into it
        // rather than replacing it.
        final Path localShardDir = planBPaths.getMergingDir()
                .resolve(ctx.doc().getUuid() + "_" + ctx.shardIndex());
        HoldingShard shard = null;
        try {
            shard = new HoldingShard(
                    byteBuffers, byteBufferFactory, configProvider, ctx.doc(), ctx.shardIndex(),
                    localShardDir,
                    shardsDocDir(ctx.doc()).resolve(PlanBConstants.formatShardIndex(ctx.shardIndex())));
            final MergeResult result = mergeAll(shard, queueDirs);
            boolean modified = !result.appliedQueueDirs().isEmpty();
            if (ctx.maintenanceDue()) {
                modified |= sweep(ctx, shard);
            }
            modified |= drain(ctx, shard);

            if (modified) {
                publisher.push(shard.getShardDir(), shardsDocDir(ctx.doc()), ctx.shardIndex());
            }

            if (ctx.maintenanceDue()) {
                // After the push, and whether or not anything was deleted: the buckets are swept
                // regardless of whether anything currently writes them, so buckets left by a previous
                // configuration go too.
                deleteExpiredArchiveShards(ctx);
                RETENTION_MARKER.recordRun(archiveDocDir(ctx.doc()), ctx.shardIndex());
            }
            return result;
        } finally {
            if (shard != null) {
                shard.dispose();
            }
            // The holding shard's local copy lives in an isolated subdirectory of mergingDir rather
            // than shardDir.
            // Clean it up now that the merge is done and published.
            try {
                FileUtil.deleteDir(localShardDir);
            } catch (final Exception e) {
                LOGGER.warn("Failed to clean up local holding shard dir {}: {}", localShardDir, e.getMessage());
            }
        }
    }

    /**
     * Merges each waiting directory into the shard, isolating each one so one that cannot be
     * merged neither abandons the rest nor discards the work already done by those
     * that succeeded.
     */
    private MergeResult mergeAll(final HoldingShard shard, final List<Path> queueDirs) {
        final Usage usage = shard.getUsage();
        if (usage.fraction() >= MERGE_MAX_USED_FRACTION) {
            LOGGER.warn(() -> LogUtil.message(
                    "Skipping {} queued item(s) because Plan B store '{}' shard {} is {}% full ({} of a max " +
                    "store size of {}). Raise the max store size for this doc, or reduce the data it " +
                    "holds via retention or publishing.",
                    queueDirs.size(),
                    shard.getDoc().getName(),
                    shard.getShardIndex(),
                    Math.round(usage.fraction() * 100),
                    ModelStringUtil.formatIECByteSizeString(usage.usedBytes()),
                    ModelStringUtil.formatIECByteSizeString(usage.mapSize())));
            return MergeResult.none();
        }

        final List<Path> appliedQueueDirs = new ArrayList<>();
        final Map<Path, Exception> failures = new LinkedHashMap<>();

        for (final Path queueDir : queueDirs) {
            try {
                mergeOne(shard, queueDir);
                appliedQueueDirs.add(queueDir);
            } catch (final Exception e) {
                failures.put(queueDir, e);
                LOGGER.error(() -> LogUtil.message("Error merging {} into shard {} of doc {}",
                        queueDir, shard.getShardIndex(), shard.getDoc().getName()), e);
            }
        }

        if (!appliedQueueDirs.isEmpty()) {
            try {
                // Let the store recompute whatever it derives from its whole record set, now that
                // every item of this cycle is present.
                shard.mergeComplete();
            } catch (final Exception e) {
                LOGGER.error(() -> LogUtil.message(
                        "Error completing merge for shard {} of doc {}, leaving {} item(s) to retry",
                        shard.getShardIndex(), shard.getDoc().getName(), appliedQueueDirs.size()), e);
                // Report nothing as applied so every item is offered again, but do not count the attempt
                // against them: they merged cleanly and setting them aside would lose their data.
                return new MergeResult(List.of(), failures);
            }
        }

        return new MergeResult(appliedQueueDirs, failures);
    }

    // Copies a queue item to a local temp location, merges it into the shard, and cleans up
    // the temp copy.
    private void mergeOne(final HoldingShard shard, final Path queueDir) throws IOException {
        LOGGER.debug("Merging {}", queueDir);
        final Path localCopy = Files.createTempDirectory("planb_merge_");
        try {
            // Copy the item's data.mdb down from the shared store to local disk.
            final Path src = queueDir.resolve(PlanBConstants.DATA_FILE_NAME);
            if (Files.exists(src)) {
                Files.copy(src, localCopy.resolve(PlanBConstants.DATA_FILE_NAME),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            shard.merge(localCopy);
        } finally {
            FileUtil.deleteDir(localCopy);
        }
    }

    // Applies the doc's retention to the holding shard. Records that never reach an archive bucket,
    // such as those of a trace still waiting for its root, would otherwise stay here for good.
    boolean sweep(final MergeContext ctx, final HoldingShard shard) {
        final long deleted = shard.runRetention(ctx.doc());
        if (deleted > 0) {
            LOGGER.debug("Deleted {} records from holding shard for {}", deleted, ctx.lockName());
        } else {
            LOGGER.debug(() -> "No records to delete from holding shard for " + ctx.lockName());
        }
        return deleted > 0;
    }

    // Moves records on into the buckets queries read, then occasionally compacts the pages that
    // leaves behind.
    boolean drain(final MergeContext ctx, final HoldingShard shard) throws IOException {
        final HoldingAreaSettings holdingArea =
                HasHoldingAreaSettings.holdingAreaSettings(ctx.doc().getSettings())
                        .orElseThrow(() -> new IllegalStateException(
                                "No holding area settings for " + ctx.lockName()));

        return localArchive.withLocalDir(ctx, localArchiveBase -> {
            final long count = shard.publish(ctx.doc(), localArchiveBase);
            if (count == 0) {
                LOGGER.debug(() -> "Nothing to archive for " + ctx.lockName());
                return false;
            }
            localArchive.pushAll(ctx, localArchiveBase);
            LOGGER.debug("Published {} row(s) for {}", count, ctx.lockName());

            compactIfDue(ctx, shard, holdingArea);
            return true;
        });
    }

    /** Where this doc's shards live on the shared store. */
    private static Path shardsDocDir(final PlanBDocument doc) {
        return SharedFileStore.rootOf(doc)
                .resolve(PlanBConstants.SHARDS_DIR_NAME)
                .resolve(doc.getUuid());
    }

    // Reclaiming space is worth a full env copy only occasionally; the drain has already run.
    private void compactIfDue(final MergeContext ctx,
                              final HoldingShard shard,
                              final HoldingAreaSettings holdingArea) {
        final SimpleDuration interval = holdingArea.getCompactionFrequency();
        final Instant lastCompact = COMPACT_MARKER.lastRun(shardsDocDir(ctx.doc()), ctx.shardIndex());
        if (lastCompact != null
                && !Instant.now().isAfter(SimpleDurationUtil.plus(lastCompact, interval))) {
            return;
        }
        LOGGER.debug("Compacting holding shard for {} (every {})", ctx.lockName(), interval);
        shard.compact();
        COMPACT_MARKER.recordRun(shardsDocDir(ctx.doc()), ctx.shardIndex());
    }


    // -----------------------------------------------------------------------
    // Retention
    // -----------------------------------------------------------------------

    /**
     * Retention over this doc's archive buckets, which is the only work this store type has that does
     * not arrive as a queue item.
     *
     * <p>How often it is checked is the doc's own {@link RetentionSettings#getCheckInterval()} — not
     * the retention period, which decides only which data is deleted. Retention is therefore honoured
     * to within that interval.
     */
    @Override
    public boolean maintenanceDue(final PlanBDocument doc, final int shardIndex) {
        return retentionDue(doc, shardIndex);
    }

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
     * pruning them. {@link ArchiveShardLocator} only treats directories as buckets, so a marker
     * file alongside them is ignored.
     */
    private static Path archiveDocDir(final PlanBDocument doc) {
        return SharedFileStore.rootOf(doc)
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(doc.getUuid());
    }

    private static RetentionSettings retentionSettings(final PlanBDocument doc) {
        return NullSafe.get(doc, PlanBDocument::getSettings, AbstractPlanBSettings::getRetention);
    }

}
