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

    // Separate marker: the rooted-span phase runs every cycle, so it tracks its own "last ran" purely
    // to know which traces have been merged since, independently of the lead-time archival schedule.
    private static final OperationMarker SPAN_MARKER =
            new OperationMarker(PlanBConstants.SPAN_ARCHIVAL_LAST_FILE_NAME);

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

    /**
     * Runs two phases with deliberately different cadences.
     *
     * <p><b>Every cycle:</b> archive the spans of rooted traces into their buckets, keeping each root
     * behind as the accumulator for late spans. This is what makes the archive the queryable copy, so
     * it must not wait out {@code checkInterval} — query freshness is this phase's cadence.
     *
     * <p><b>Every cycle:</b> evict roots past the root cut-off, whose spans are already archived. The
     * cut-off is short by design, so this cannot wait out {@code checkInterval} either.
     *
     * <p><b>On {@code checkInterval}:</b> the original age-gated archival, which moves a root and its
     * remaining spans out once the root has aged past the archival lead time. In practice eviction wins
     * that race for any trace the rooted-span phase has handled, since the cut-off is far shorter than
     * the lead time; this phase still covers orphan spans, which have no root to derive a bucket from.
     */
    @Override
    public boolean run(final SharedFileStoreOperationContext ctx) throws IOException {
        boolean modified = archiveRootedSpans(ctx);
        modified |= evictArchivedRoots(ctx);
        if (isDue(ctx.doc(), ctx.sharedShardsDocDir(), ctx.shardIndex())) {
            modified |= archiveAgedData(ctx);
        }
        return modified;
    }

    private boolean evictArchivedRoots(final SharedFileStoreOperationContext ctx) {
        final long count = ctx.shard().evictArchivedRoots(ctx.doc());
        if (count == 0) {
            LOGGER.debug(() -> "No trace roots to evict for " + ctx.lockName());
            return false;
        }
        LOGGER.info("Evicted {} archived trace root entr(ies) for {}", count, ctx.lockName());
        return true;
    }

    private boolean archiveRootedSpans(final SharedFileStoreOperationContext ctx) throws IOException {
        final Instant since = SPAN_MARKER.lastRun(ctx.sharedShardsDocDir(), ctx.shardIndex());
        return withStagingDir(ctx, localArchiveBase -> {
            final long count = ctx.shard().archiveRootedSpans(ctx.doc(), localArchiveBase, since);
            if (count == 0) {
                LOGGER.debug(() -> "No rooted spans to archive for " + ctx.lockName());
                return false;
            }
            LOGGER.info("Archiving {} rooted span(s) for {}", count, ctx.lockName());
            pushAll(ctx, localArchiveBase);
            // Only recorded once every bucket pushed, so a failure re-sends the same traces next cycle
            // rather than skipping them.
            SPAN_MARKER.recordRun(ctx.sharedShardsDocDir(), ctx.shardIndex());
            return true;
        });
    }

    private boolean archiveAgedData(final SharedFileStoreOperationContext ctx) throws IOException {
        final SimpleDuration interval = archival(ctx.doc()).getCheckInterval();
        LOGGER.info("Running archival for {} (every {}, next due {})",
                ctx.lockName(), interval, SimpleDurationUtil.plus(Instant.now(), interval));

        return withStagingDir(ctx, localArchiveBase -> {
            final long count = ctx.shard().archiveOldData(ctx.doc(), localArchiveBase);
            if (count == 0) {
                LOGGER.debug(() -> "No data to archive for " + ctx.lockName());
                MARKER.recordRun(ctx.sharedShardsDocDir(), ctx.shardIndex());
                return false;
            }
            pushAll(ctx, localArchiveBase);

            LOGGER.info("Compacting main shard after archival for {}", ctx.lockName());
            ctx.shard().compact();

            MARKER.recordRun(ctx.sharedShardsDocDir(), ctx.shardIndex());
            return true;
        });
    }

    /**
     * Pushes every dated delta dir staged under {@code localArchiveBase} to its bucket.
     *
     * <p>The staged rows have already been deleted, but only from the LOCAL merge shard — rethrowing
     * makes {@code SharedFileStoreMergeProcessor.mergeShard} skip {@code publisher.push} and discard
     * that local shard, so the shared holding shard keeps its data and the next cycle retries. A
     * partial success (bucket A pushed, bucket B failed) re-pushes A next cycle, which is idempotent:
     * span puts use {@code MDB_NOOVERWRITE} and the bucket's root is recomputed from its own spans.
     */
    private void pushAll(final SharedFileStoreOperationContext ctx,
                         final Path localArchiveBase) throws IOException {
        final List<StagedArchive> archiveShards;
        try (final Stream<Path> stream = Files.list(localArchiveBase)) {
            archiveShards = stream
                    .filter(Files::isDirectory)
                    .map(dir -> new StagedArchive(dir.getFileName().toString(), dir))
                    .toList();
        }

        LOGGER.info("Archiving {} date shard(s) for {}", archiveShards.size(), ctx.lockName());

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
    }

    // Staged under the configured Plan B root rather than the JVM system temp dir: these are LMDB
    // envs holding this run's archived data, and system temp is often small or tmpfs (i.e. RAM).
    private boolean withStagingDir(final SharedFileStoreOperationContext ctx,
                                   final StagingWork work) throws IOException {
        final Path localArchiveBase = Files.createDirectories(
                archiveStagingDir.resolve("delta_" + UUID.randomUUID()));
        try {
            return work.run(localArchiveBase);
        } finally {
            try {
                FileUtil.deleteDir(localArchiveBase);
            } catch (final Exception e) {
                LOGGER.warn("Failed to clean up archive staging dir {} for {}: {}",
                        localArchiveBase, ctx.lockName(), e.getMessage());
            }
        }
    }

    private interface StagingWork {

        boolean run(Path localArchiveBase) throws IOException;
    }
}
