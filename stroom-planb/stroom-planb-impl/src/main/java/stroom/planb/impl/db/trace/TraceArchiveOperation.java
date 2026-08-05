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

package stroom.planb.impl.db.trace;

import stroom.planb.impl.fs.LocalArchive;
import stroom.planb.impl.fs.SharedFileStoreOperation;
import stroom.planb.impl.fs.SharedFileStoreOperationContext;
import stroom.planb.shared.ArchivalSettings;
import stroom.planb.shared.HasSharedFileStore;
import stroom.planb.shared.PlanBDocument;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.SimpleDurationUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * The incremental archive lifecycle for trace stores, which is what makes the archive buckets the
 * queryable copy of a trace rather than a cold copy of an aged one.
 *
 * <p>Runs two phases every merge cycle, deliberately <em>not</em> on the archival {@code checkInterval}:
 * query freshness is this operation's cadence, so waiting out that interval would delay traces becoming
 * visible.
 *
 * <ol>
 *   <li>Archive each rooted trace's spans into its bucket, leaving the root behind as the accumulator for
 *       late spans.</li>
 *   <li>Evict roots whose spans are already archived and which can no longer gain more.</li>
 * </ol>
 *
 * <p>Separate from {@code ArchiveOperation}, which owns the generic lead-time archival that every store
 * type shares. This one only applies to stores implementing {@link TraceArchiveCapable} — currently only
 * {@link TraceDb} — so it lives beside that store rather than putting trace vocabulary on the generic
 * shared-file-store classes.
 */
public class TraceArchiveOperation implements SharedFileStoreOperation {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(TraceArchiveOperation.class);

    private final LocalArchive localArchive;

    @Inject
    public TraceArchiveOperation(final LocalArchive localArchive) {
        this.localArchive = localArchive;
    }

    // Between retention (100) and the generic lead-time archival (200), so a trace's spans reach its
    // bucket before the aged pass considers the trace at all.
    @Override
    public int priority() {
        return 150;
    }

    /**
     * Always {@code false}, even though {@link #run} does work on every cycle.
     *
     * <p>{@code isDue} only decides whether a shard is worth taking the cluster lock for when no batches
     * have arrived; {@code run} is then called on every registered operation regardless. So returning
     * {@code false} means "never worth the lock on my own account", not "no work to do".
     *
     * <p>That is safe but not self-evident, because this operation's work is partly <em>time</em>-driven —
     * a root going quiet or crossing the cut-off happens with no new spans involved. Two things bound the
     * delay: new spans produce batches, which take the lock anyway; and {@code ArchiveOperation.isDue}
     * fires every archival {@code checkInterval}, which takes the lock and therefore runs this too. The
     * second is the one that matters when a shard goes idle, so <b>the archival check interval is an upper
     * bound on how late eviction can be</b> — keep it well below the root cut-off.
     */
    @Override
    public boolean isDue(final PlanBDocument doc,
                         final Path sharedShardsDocDir,
                         final int shardIndex) {
        return false;
    }

    @Override
    public boolean run(final SharedFileStoreOperationContext ctx) throws IOException {
        final ArchivalSettings archival = archival(ctx.doc());
        if (archival == null || !archival.isEnabled()) {
            return false;
        }
        boolean modified = archiveRootedSpans(ctx, archival);
        modified |= evictArchivedRoots(ctx, archival);
        return modified;
    }

    private boolean archiveRootedSpans(final SharedFileStoreOperationContext ctx,
                                       final ArchivalSettings archival) throws IOException {
        return localArchive.withLocalDir(ctx, localArchiveBase -> {
            final long removed = ctx.shard().writeWithDb(db -> db instanceof final TraceArchiveCapable trace
                    ? trace.archiveRootedSpans(archival.getGranularity(), localArchiveBase)
                    : 0L);
            // Gate on what was STAGED, not on what was removed. A trace whose only span is its root
            // removes nothing — only non-root spans are ever deleted — yet it still stages a delta that
            // has to be published. Gating on the removed count discarded that delta, and eviction then
            // deleted the trace having never archived it, losing it from every query.
            final int pushed = localArchive.pushAll(ctx, localArchiveBase);
            if (pushed == 0) {
                LOGGER.debug(() -> "Nothing to archive for " + ctx.lockName());
                return false;
            }
            LOGGER.info("Archived {} bucket(s) for {}, removing {} rooted span(s) from the live shard",
                    pushed, ctx.lockName(), removed);
            return true;
        });
    }

    private boolean evictArchivedRoots(final SharedFileStoreOperationContext ctx,
                                       final ArchivalSettings archival) {
        final Instant now = Instant.now();
        final Instant evictBefore = SimpleDurationUtil.minus(now, archival.getRootCutOff());
        // Backstop for a trace that never goes quiet, so it cannot pin its root here (and keep its
        // start-time bucket being re-pushed) indefinitely. The archival lead time is already the
        // "this data is old, deal with it" knob, and should be longer than the root cut-off.
        Instant hardEvictBefore = SimpleDurationUtil.minus(now, archival.getDuration());

        // Fail safe if the two are configured the wrong way round. A backstop at or after the normal
        // cut-off would be satisfied by every root old enough to consider, which skips the "has it gone
        // quiet" guard entirely and evicts roots out from under live traces. Settings validation rejects
        // this, but a doc built programmatically or imported from an older version can still reach here,
        // so push the backstop out of reach and let quiet be the only route.
        if (!hardEvictBefore.isBefore(evictBefore)) {
            LOGGER.warn("Root cut-off ({}) is not shorter than the archival lead time ({}) for {}; " +
                        "ignoring the backstop so a still-active trace cannot be evicted. Fix the " +
                        "settings on this doc.",
                    archival.getRootCutOff(), archival.getDuration(), ctx.lockName());
            hardEvictBefore = Instant.MIN;
        }

        final Instant backstop = hardEvictBefore;
        final long count = ctx.shard().writeWithDb(db -> db instanceof final TraceArchiveCapable trace
                ? trace.evictArchivedRoots(evictBefore, backstop)
                : 0L);
        if (count == 0) {
            LOGGER.debug(() -> "No trace roots to evict for " + ctx.lockName());
            return false;
        }
        // The count is rows removed — root entries plus their root spans and merge-time entries — not a
        // number of traces, so do not word it as one.
        LOGGER.info("Evicted {} row(s) for archived trace roots for {}", count, ctx.lockName());
        return true;
    }

    private static ArchivalSettings archival(final PlanBDocument doc) {
        return doc.getSettings() instanceof final HasSharedFileStore s
               && s.getSharedFileStore() != null
                ? s.getSharedFileStore().getArchival()
                : null;
    }
}
