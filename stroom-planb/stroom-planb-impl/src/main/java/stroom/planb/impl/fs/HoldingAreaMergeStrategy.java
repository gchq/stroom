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
import stroom.planb.impl.db.PlanBEnv.Usage;
import stroom.planb.shared.HasHoldingAreaSettings;
import stroom.planb.shared.HoldingAreaSettings;
import stroom.planb.shared.PlanBDocument;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
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

/**
 * Merges batches through a holding shard: they are merged into one shard per doc and shard index on
 * the shared store, and a separate pass moves records on from there into the date-labelled archive
 * buckets that queries read.
 *
 * <p>The holding shard is not queried, so nothing is visible until it has been drained. That is why
 * the drain runs on every cycle rather than to a schedule — any delay here is query latency.
 */
@Singleton
public class HoldingAreaMergeStrategy implements MergeStrategy {

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
    public MergeResult mergeShard(final MergeContext ctx, final List<Path> batchDirs) throws IOException {
        // Undo the partial state left by an interrupted push before opening the shard, so the sync
        // down from the shared store sees a consistent directory.
        publisher.recoverOrphaned(holdingDocDir(ctx.doc()), ctx.shardIndex());

        // Copies the current shared shard down to local disk so this cycle's batches merge into it
        // rather than replacing it.
        final Path mergeShardDir = planBPaths.getMergingDir()
                .resolve(ctx.doc().getUuid() + "_" + ctx.shardIndex());
        MergeShard shard = null;
        try {
            shard = new MergeShard(
                    byteBuffers, byteBufferFactory, configProvider, ctx.doc(), ctx.shardIndex(),
                    mergeShardDir,
                    holdingDocDir(ctx.doc()).resolve(PlanBConstants.formatShardIndex(ctx.shardIndex())));
            final MergeResult result = mergeAllBatches(shard, batchDirs);
            boolean modified = !result.mergedBatchDirs().isEmpty();
            if (ctx.retentionDue()) {
                modified |= sweep(ctx, shard);
            }
            modified |= drain(ctx, shard);

            if (modified) {
                publisher.push(shard.getShardDir(), holdingDocDir(ctx.doc()), ctx.shardIndex());
            }
            return result;
        } finally {
            if (shard != null) {
                shard.dispose();
            }
            // The merge shard runs in an isolated subdirectory of mergingDir rather than shardDir.
            // Clean it up now that the merge is done and published.
            try {
                FileUtil.deleteDir(mergeShardDir);
            } catch (final Exception e) {
                LOGGER.warn("Failed to clean up merge directory {}: {}", mergeShardDir, e.getMessage());
            }
        }
    }

    /**
     * Merges each batch directory into the shard, isolating each one so a batch that cannot be
     * merged neither abandons the remaining batches nor discards the work already done by those
     * that succeeded.
     */
    private MergeResult mergeAllBatches(final MergeShard shard, final List<Path> batchDirs) {
        final Usage usage = shard.getUsage();
        if (usage.fraction() >= MERGE_MAX_USED_FRACTION) {
            LOGGER.warn(() -> LogUtil.message(
                    "Skipping {} batches because Plan B store '{}' shard {} is {}% full ({} of a max " +
                    "store size of {}). Raise the max store size for this doc, or reduce the data it " +
                    "holds via retention or archival.",
                    batchDirs.size(),
                    shard.getDoc().getName(),
                    shard.getShardIndex(),
                    Math.round(usage.fraction() * 100),
                    ModelStringUtil.formatIECByteSizeString(usage.usedBytes()),
                    ModelStringUtil.formatIECByteSizeString(usage.mapSize())));
            return MergeResult.none();
        }

        final List<Path> mergedBatchDirs = new ArrayList<>();
        final Map<Path, Exception> failures = new LinkedHashMap<>();

        for (final Path batchDir : batchDirs) {
            try {
                mergeSingleBatch(shard, batchDir);
                mergedBatchDirs.add(batchDir);
            } catch (final Exception e) {
                failures.put(batchDir, e);
                LOGGER.error(() -> LogUtil.message("Error merging batch {} into shard {} of doc {}",
                        batchDir, shard.getShardIndex(), shard.getDoc().getName()), e);
            }
        }

        if (!mergedBatchDirs.isEmpty()) {
            try {
                // Let the store recompute whatever it derives from its whole record set, now that
                // every batch of this cycle is present.
                shard.mergeComplete();
            } catch (final Exception e) {
                LOGGER.error(() -> LogUtil.message(
                        "Error completing merge for shard {} of doc {}, leaving {} batches to retry",
                        shard.getShardIndex(), shard.getDoc().getName(), mergedBatchDirs.size()), e);
                // Report nothing as merged so every batch is retried, but do not count the attempt
                // against them: the batches merged cleanly and quarantining them would lose their data.
                return new MergeResult(List.of(), failures);
            }
        }

        return new MergeResult(mergedBatchDirs, failures);
    }

    // Copies a batch directory to a local temp location, merges it into the shard, and cleans up
    // the temp copy.
    private void mergeSingleBatch(final MergeShard shard, final Path batchDir) throws IOException {
        LOGGER.info("Merging batch {}", batchDir);
        final Path localTempBatchDir = Files.createTempDirectory("planb_merge_");
        try {
            // Copy the batch's data.mdb from the (shared) batch dir down to local disk.
            final Path src = batchDir.resolve(PlanBConstants.DATA_FILE_NAME);
            if (Files.exists(src)) {
                Files.copy(src, localTempBatchDir.resolve(PlanBConstants.DATA_FILE_NAME),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            shard.merge(localTempBatchDir);
        } finally {
            FileUtil.deleteDir(localTempBatchDir);
        }
    }

    // Applies the doc's retention to the holding shard. Records that never reach an archive bucket,
    // such as those of a trace still waiting for its root, would otherwise stay here for good.
    boolean sweep(final MergeContext ctx, final MergeShard shard) {
        final long deleted = shard.runRetention(ctx.doc());
        if (deleted > 0) {
            LOGGER.info("Deleted {} records from holding shard for {}", deleted, ctx.lockName());
        } else {
            LOGGER.debug(() -> "No records to delete from holding shard for " + ctx.lockName());
        }
        return deleted > 0;
    }

    // Moves records on into the buckets queries read, then occasionally compacts the pages that
    // leaves behind.
    boolean drain(final MergeContext ctx, final MergeShard shard) throws IOException {
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
            LOGGER.info("Published {} row(s) for {}", count, ctx.lockName());

            compactIfDue(ctx, shard, holdingArea);
            return true;
        });
    }

    /** Where this doc's holding shards live. Known only here — no other store type keeps one. */
    private static Path holdingDocDir(final PlanBDocument doc) {
        return Path.of(doc.getSharedPath())
                .resolve(PlanBConstants.HOLDING_DIR_NAME)
                .resolve(doc.getUuid());
    }

    // Reclaiming space is worth a full env copy only occasionally; the drain has already run.
    private void compactIfDue(final MergeContext ctx,
                              final MergeShard shard,
                              final HoldingAreaSettings holdingArea) {
        final SimpleDuration interval = holdingArea.getCompactionFrequency();
        final Instant lastCompact = COMPACT_MARKER.lastRun(holdingDocDir(ctx.doc()), ctx.shardIndex());
        if (lastCompact != null
                && !Instant.now().isAfter(SimpleDurationUtil.plus(lastCompact, interval))) {
            return;
        }
        LOGGER.info("Compacting holding shard for {} (every {})", ctx.lockName(), interval);
        shard.compact();
        COMPACT_MARKER.recordRun(holdingDocDir(ctx.doc()), ctx.shardIndex());
    }
}
