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
import stroom.planb.impl.data.ArchivalGranularityUtil;
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
import stroom.util.time.StroomDuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * {@link SharedFileStoreOperation} that enforces data retention on a Plan B shard.
 *
 * <p>Tracks the last-run timestamp in a {@code .retention.last} file inside
 * the canonical shared shard directory. The check interval is 10% of the
 * configured retention duration, clamped between 1 minute and 1 day.
 *
 * <p>{@link #isDue} is safe to call outside the shard cluster lock (read-only).
 * {@link #run} must be called inside the lock.
 */
public class RetentionOperation implements SharedFileStoreOperation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RetentionOperation.class);

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
        final Path lastFile = lastRunFile(sharedShardsDocDir, shardIndex);
        try {
            final Instant lastRun = Instant.parse(
                    Files.readString(lastFile, StandardCharsets.UTF_8).trim());
            return Instant.now().isAfter(nextDue(lastRun, retention.getDuration()));
        } catch (final NoSuchFileException e) {
            return true; // never run -- due immediately
        } catch (final Exception e) {
            LOGGER.warn("Could not read retention last-run file {}, treating as due: {}",
                    lastFile, e.getMessage());
            return true;
        }
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
        LOGGER.info("Running retention on main shard for {}", ctx.lockName());
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

        writeLastRun(ctx.sharedShardsDocDir(), ctx.shardIndex());
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
                ctx.doc().getSettings() instanceof final HasSharedFileStore s
                        && s.getSharedFileStore() != null
                        && s.getSharedFileStore().getArchival() != null
                        ? s.getSharedFileStore().getArchival().getGranularity()
                        : null;

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

    // -----------------------------------------------------------------------
    // Scheduling helpers
    // -----------------------------------------------------------------------

    private void writeLastRun(final Path sharedShardsDocDir, final int shardIndex) {
        final Path lastFile = lastRunFile(sharedShardsDocDir, shardIndex);
        try {
            Files.createDirectories(lastFile.getParent());
            Files.writeString(lastFile, Instant.now().toString(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            LOGGER.error("Failed to write retention last-run file: {}", lastFile, e);
        }
    }

    private static Path lastRunFile(final Path sharedShardsDocDir, final int shardIndex) {
        return sharedShardsDocDir
                .resolve(PlanBConstants.formatShardIndex(shardIndex))
                .resolve(PlanBConstants.RETENTION_LAST_FILE_NAME);
    }

    /**
     * Check interval = 10% of the retention duration,
     * clamped between 1 minute and 1 day.
     */
    private static Instant nextDue(final Instant lastRun, final SimpleDuration duration) {
        final long durationMs = toMillis(duration);
        final long intervalMs = Math.min(
                Math.max(durationMs / 10L, 60_000L),
                86_400_000L);
        return lastRun.plusMillis(intervalMs);
    }

    private static long toMillis(final SimpleDuration duration) {
        final StroomDuration d = SimpleDurationUtil.convertToStroomDuration(duration);
        return d != null ? d.toMillis() : 0L;
    }
}
