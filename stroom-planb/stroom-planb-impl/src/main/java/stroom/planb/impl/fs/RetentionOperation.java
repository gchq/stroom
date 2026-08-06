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

import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.data.archive.ArchivalGranularityUtil;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.ArchivalSettings;
import stroom.planb.shared.HasSharedFileStore;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.RetentionSettings;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * {@link SharedFileStoreOperation} that enforces data retention on a Plan B shard.
 *
 * <p>Tracks the last-run timestamp in a {@code .retention.last} file inside the canonical
 * shared shard directory. How often it is checked comes from the doc's own
 * {@link RetentionSettings#getCheckInterval()} — it is not derived from the retention period,
 * which decides only which data is deleted. Retention is therefore honoured to within that
 * interval, which is why {@code PlanBDocStoreImpl} rejects a check interval longer than the
 * retention period.
 *
 * <p>{@link #isDue} is safe to call outside the shard cluster lock (read-only).
 * {@link #run} must be called inside the lock.
 */
public class RetentionOperation implements SharedFileStoreOperation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RetentionOperation.class);

    private static final OperationMarker MARKER =
            new OperationMarker(PlanBConstants.RETENTION_LAST_FILE_NAME);

    // Runs before archival (priority 200) within a merge cycle.
    @Override
    public int priority() {
        return 100;
    }

    // -----------------------------------------------------------------------
    // SharedFileStoreOperation -- pre-lock check
    // -----------------------------------------------------------------------

    @Override
    public boolean isDue(final PlanBDocument doc,
                         final Path sharedShardsDocDir,
                         final int shardIndex) {
        final RetentionSettings retention = NullSafe.get(
                doc, PlanBDocument::getSettings, AbstractPlanBSettings::getRetention);
        if (retention == null || !retention.isEnabled()) {
            return false;
        }
        final Instant lastRun = MARKER.lastRun(sharedShardsDocDir, shardIndex);
        return lastRun == null
               || Instant.now().isAfter(SimpleDurationUtil.plus(lastRun, retention.getCheckInterval()));
    }

    // -----------------------------------------------------------------------
    // SharedFileStoreOperation -- in-lock execution
    // -----------------------------------------------------------------------

    /**
     * Re-checks whether retention is due (another node may have run it since
     * the pre-lock check), executes it, and records the run timestamp.
     * Returns {@code true} if records were deleted.
     */
    @Override
    public boolean run(final SharedFileStoreOperationContext ctx) throws IOException {
        if (!isDue(ctx.doc(), ctx.sharedShardsDocDir(), ctx.shardIndex())) {
            return false;
        }

        // Step 1: Apply retention to the main shard (existing behaviour)
        final SimpleDuration interval = NullSafe.get(
                ctx.doc(), PlanBDocument::getSettings, AbstractPlanBSettings::getRetention,
                RetentionSettings::getCheckInterval);
        LOGGER.info("Running retention on main shard for {} (every {}, next due {})",
                ctx.lockName(), interval, SimpleDurationUtil.plus(Instant.now(), interval));
        final long deleted = ctx.shard().deleteOldData(ctx.doc());
        if (deleted > 0) {
            LOGGER.info("Deleted {} records from main shard for {}", deleted, ctx.lockName());
        } else {
            LOGGER.debug(() -> "No records to delete from main shard for " + ctx.lockName());
        }

        // Step 2: Delete expired archive shards from the shared store.
        // Runs regardless of whether archival is currently enabled so that
        // archive shards created by a previous configuration are also cleaned up.
        deleteExpiredArchiveShards(ctx);

        MARKER.recordRun(ctx.sharedShardsDocDir(), ctx.shardIndex());
        return deleted > 0;
    }

    private void deleteExpiredArchiveShards(final SharedFileStoreOperationContext ctx) {
        final RetentionSettings retention = NullSafe.get(
                ctx.doc(), PlanBDocument::getSettings, AbstractPlanBSettings::getRetention);
        if (retention == null || !retention.isEnabled()) {
            return;
        }
        final Instant retentionBefore =
                SimpleDurationUtil.minus(Instant.now(), retention.getDuration());

        final ArchivalGranularity configuredGranularity =
                HasSharedFileStore.archivalSettings(ctx.doc().getSettings())
                        .map(ArchivalSettings::getGranularity)
                        .orElse(null);

        final String sharedPathStr = ctx.doc().getSharedPath();
        if (sharedPathStr == null) {
            return;
        }

        final Path archiveShardDir = Path.of(sharedPathStr)
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(ctx.doc().getUuid())
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

                final ArchivalGranularity granularity = configuredGranularity != null
                        ? configuredGranularity
                        : ArchivalGranularityUtil.detect(dateLabel);

                if (granularity == null) {
                    LOGGER.warn("Cannot determine granularity for archive dir {}, skipping",
                            dateDir);
                    continue;
                }

                final Instant bucketEnd = ArchivalGranularityUtil.bucketEnd(granularity, dateLabel);
                if (bucketEnd == null) {
                    LOGGER.warn("Cannot parse bucket end for archive dir {}, skipping", dateDir);
                    continue;
                }

                if (retentionBefore.isAfter(bucketEnd)) {
                    LOGGER.info("Deleting expired archive shard {} for {}",
                            dateLabel, ctx.lockName());
                    FileUtil.deleteDir(dateDir);
                }
            }
        } catch (final IOException e) {
            LOGGER.error("Error scanning archive shards in {}: {}",
                    archiveShardDir, e.getMessage(), e);
        }
    }

}
