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
import stroom.planb.shared.ArchivalSettings;
import stroom.planb.shared.HasSharedFileStore;
import stroom.planb.shared.PlanBDocument;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Archives entries from a {@link HasSharedFileStore} shard into date-labelled buckets on the shared file
 * store, then occasionally compacts the shard it emptied.
 *
 * <p>Archival runs on every merge cycle rather than on a schedule of its own, because the archive is the
 * queryable copy and any delay here is query latency. It does not ask for a cycle when there is nothing to
 * merge — see {@link #isDue}. Compaction is throttled separately: {@code shard.compact()} is a full LMDB env
 * copy under the shard's write lock, so {@link ArchivalSettings#getCheckInterval()} limits it via the
 * {@code .compaction.last} marker.
 */
public class ArchiveOperation implements SharedFileStoreOperation {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(ArchiveOperation.class);

    private static final OperationMarker COMPACT_MARKER =
            new OperationMarker(PlanBConstants.COMPACTION_LAST_FILE_NAME);

    private final LocalArchive localArchive;

    @Inject
    public ArchiveOperation(final LocalArchive localArchive) {
        this.localArchive = localArchive;
    }

    // Runs after retention (priority 100) within a merge cycle.
    @Override
    public int priority() {
        return 200;
    }

    /**
     * Never asks for a cycle of its own. A merge cycle already happens whenever there are batches to merge,
     * and {@link #run} is unconditional, so anything merged is archived in that same cycle — which is what
     * keeps the archive current. With nothing merged there is nothing new to archive, and claiming the
     * cluster lock to copy the shard down and rescan it would achieve nothing.
     *
     * <p>The trade: work archival defers is deferred until data next arrives. Spans held back waiting for a
     * real root stay in the holding area, unqueryable, and roots past the cut-off stay un-retired. Nothing
     * is lost, and the next batch drains them.
     */
    @Override
    public boolean isDue(final PlanBDocument doc,
                         final Path sharedShardsDocDir,
                         final int shardIndex) {
        return false;
    }

    @Override
    public boolean run(final SharedFileStoreOperationContext ctx) throws IOException {
        final ArchivalSettings archival = HasSharedFileStore.archivalSettings(ctx.doc().getSettings())
                .orElseThrow(() -> new IllegalStateException(
                        "No shared file store settings for " + ctx.lockName()));

        return localArchive.withLocalDir(ctx, localArchiveBase -> {
            final long count = ctx.shard().runArchival(ctx.doc(), localArchiveBase);
            if (count == 0) {
                LOGGER.debug(() -> "Nothing to archive for " + ctx.lockName());
                return false;
            }
            localArchive.pushAll(ctx, localArchiveBase);
            LOGGER.info("Archived {} row(s) for {}", count, ctx.lockName());

            compactIfDue(ctx, archival);
            return true;
        });
    }

    // Reclaiming space is worth a full env copy only occasionally; archival itself has already run.
    private void compactIfDue(final SharedFileStoreOperationContext ctx,
                              final ArchivalSettings archival) {
        final SimpleDuration interval = archival.getCheckInterval();
        final Instant lastCompact = COMPACT_MARKER.lastRun(ctx.sharedShardsDocDir(), ctx.shardIndex());
        if (lastCompact != null
                && !Instant.now().isAfter(SimpleDurationUtil.plus(lastCompact, interval))) {
            return;
        }
        LOGGER.info("Compacting main shard for {} (every {})", ctx.lockName(), interval);
        ctx.shard().compact();
        COMPACT_MARKER.recordRun(ctx.sharedShardsDocDir(), ctx.shardIndex());
    }

}
