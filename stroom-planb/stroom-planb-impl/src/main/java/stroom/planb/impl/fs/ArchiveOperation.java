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
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.ArchivalSettings;
import stroom.planb.shared.HasSharedFileStore;
import stroom.planb.shared.PlanBDocument;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * SharedFileStoreOperation that archives old entries from a {@link HasSharedFileStore} shard
 * into date-labelled archive shards on the shared file store.
 *
 * <p>Archival is only supported for doc types whose settings implement
 * {@link HasSharedFileStore}. All other doc types are skipped without error.
 *
 * <p>How often archival is checked comes from the doc's own
 * {@link ArchivalSettings#getCheckInterval()} — it is not derived from the archival lead time,
 * which decides only which data moves. After archiving, the main shard is compacted to reclaim
 * freed space.
 */
public class ArchiveOperation implements SharedFileStoreOperation {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(ArchiveOperation.class);

    private static final OperationMarker MARKER =
            new OperationMarker(PlanBConstants.ARCHIVAL_LAST_FILE_NAME);

    private final SharedFileStorePublisher publisher;
    private final Path archiveStagingDir;

    @Inject
    public ArchiveOperation(final SharedFileStorePublisher publisher,
                            final StatePaths statePaths) {
        this.publisher = publisher;
        this.archiveStagingDir = statePaths.getArchiveStagingDir();
    }

    // Runs after retention (priority 100) within a merge cycle.
    @Override
    public int priority() {
        return 200;
    }

    @Override
    public boolean isDue(final PlanBDocument doc,
                         final Path sharedShardsDocDir,
                         final int shardIndex) {
        final ArchivalSettings archival = archival(doc);
        if (archival == null || !archival.isEnabled()) {
            return false;
        }
        final Instant lastRun = MARKER.lastRun(sharedShardsDocDir, shardIndex);
        return lastRun == null
               || Instant.now().isAfter(SimpleDurationUtil.plus(lastRun, archival.getCheckInterval()));
    }

    private static ArchivalSettings archival(final PlanBDocument doc) {
        return doc.getSettings() instanceof final HasSharedFileStore s
               && s.getSharedFileStore() != null
                ? s.getSharedFileStore().getArchival()
                : null;
    }

    @Override
    public boolean run(final SharedFileStoreOperationContext ctx) throws IOException {
        if (!isDue(ctx.doc(), ctx.sharedShardsDocDir(), ctx.shardIndex())) {
            return false;
        }
        final SimpleDuration interval = archival(ctx.doc()).getCheckInterval();
        LOGGER.info("Running archival for {} (every {}, next due {})",
                ctx.lockName(), interval, SimpleDurationUtil.plus(Instant.now(), interval));

        // Staged under the configured Plan B root rather than the JVM system temp dir: these are LMDB
        // envs holding this run's archived data, and system temp is often small or tmpfs (i.e. RAM).
        final Path localArchiveBase = Files.createDirectories(
                archiveStagingDir.resolve("delta_" + UUID.randomUUID()));
        try {
            final long count = ctx.shard().archiveOldData(ctx.doc(), localArchiveBase);

            if (count == 0) {
                LOGGER.debug(() -> "No data to archive for " + ctx.lockName());
                MARKER.recordRun(ctx.sharedShardsDocDir(), ctx.shardIndex());
                return false;
            }

            // Collect the dated subdirs written by db.archiveOldData
            final List<StagedArchive> archiveShards;
            try (final Stream<Path> stream = Files.list(localArchiveBase)) {
                archiveShards = stream
                        .filter(Files::isDirectory)
                        .map(dir -> new StagedArchive(dir.getFileName().toString(), dir))
                        .toList();
            }

            LOGGER.info("Archiving {} date shard(s) for {}",
                    archiveShards.size(), ctx.lockName());

            // Push each archive shard. archiveOldData's pass 3 has already deleted the archived rows,
            // but only from the LOCAL merge shard — rethrowing makes SharedFileStoreMergeProcessor
            // .mergeShard skip publisher.push and discard that local shard, so the shared holding shard
            // keeps its data and the next cycle retries. Log at ERROR and skip compact/recordRun so
            // isDue() stays true and the operator is alerted, then rethrow. A partial success (bucket A
            // pushed, bucket B failed) re-pushes A next cycle, which is idempotent: span puts use
            // MDB_NOOVERWRITE and archiveMergeComplete recomputes totalSpans from the actual count.
            IOException firstFailure = null;
            for (final StagedArchive archiveShard : archiveShards) {
                try {
                    publisher.pushArchive(ctx.doc(), ctx.shardIndex(), archiveShard);
                    LOGGER.info("Pushed archive shard {} for {}",
                            archiveShard.dateLabel(), ctx.lockName());
                } catch (final IOException e) {
                    LOGGER.error("Failed to push archive shard {} for {} — the merged shard will not be " +
                                 "published, so the shared store keeps this data and archival will be " +
                                 "retried on the next cycle.",
                            archiveShard.dateLabel(), ctx.lockName(), e);
                    if (firstFailure == null) {
                        firstFailure = e;
                    }
                }
            }

            if (firstFailure != null) {
                throw firstFailure;
            }

            LOGGER.info("Compacting main shard after archival for {}", ctx.lockName());
            ctx.shard().compact();

            MARKER.recordRun(ctx.sharedShardsDocDir(), ctx.shardIndex());
            return true;

        } finally {
            FileUtil.deleteDir(localArchiveBase);
        }
    }

}
