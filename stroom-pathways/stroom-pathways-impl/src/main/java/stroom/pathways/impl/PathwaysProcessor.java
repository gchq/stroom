/*
 * Copyright 2026 Crown Copyright
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

package stroom.pathways.impl;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.pathways.impl.TraceProcessor.ApplyOutcome;
import stroom.pathways.shared.PathwaysDoc;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.dao.LmdbWriter;
import stroom.planb.impl.dao.trace.IgnoredSpans;
import stroom.planb.impl.dao.trace.PathwaysDb;
import stroom.planb.impl.dao.trace.QueueItem;
import stroom.planb.impl.dao.trace.QueueItemReader;
import stroom.planb.impl.fs.MergeContext;
import stroom.planb.impl.fs.MergeResult;
import stroom.planb.impl.fs.MergeStrategy;
import stroom.planb.impl.fs.OperationMarker;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.Metrics;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Applies the traces a trace store has handed over, several shards at a time.
 *
 * <p>Every node runs this. A shard is claimed by taking its cluster lock, so only one node works a
 * given shard at a time and which node that is does not matter — the queue and the state it builds
 * both live on the shared filesystem.
 */
@Singleton
public class PathwaysProcessor implements MergeStrategy {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PathwaysProcessor.class);

    /**
     * How much one lock hold takes on.
     *
     * <p>Bounded both ways because either bound alone can be wrong: a few enormous items outlast a
     * count bound, and a flood of tiny ones outlasts nothing. Kept well under the minute at which
     * {@code DbClusterLock} warns about a long hold, which also narrows the window in which a lease
     * lost part way through a hold can matter.
     *
     * <p>Both numbers are guesses until a run against real traffic replaces them.
     */
    static final int MAX_ITEMS_PER_HOLD = 50;
    static final Duration MAX_TIME_PER_HOLD = Duration.ofSeconds(30);


    /** When each shard was last narrowed, kept beside the shard on the shared file store. */
    private static final OperationMarker NARROWED_MARKER =
            new OperationMarker(PlanBConstants.NARROWED_LAST_FILE_NAME);

    private final PathwaysShardStore shardStore;
    private final MessageReceiverFactory messageReceiverFactory;
    private final PathwaySerde pathwaySerde;
    private final MutationLog mutationLog;
    private final PathwayNarrower pathwayNarrower;
    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;

    private final Meter tracesApplied;
    private final Counter itemsFailed;

    /**
     * What the last pass saw, so the gauges can report it without listing the shared filesystem
     * themselves. At most one cycle stale, which is fine for a backlog, and it keeps a metrics scrape
     * off the shared mount and out of the permission checks that reading a document needs.
     */
    private final Map<String, Backlog> backlogByShard = new ConcurrentHashMap<>();

    @Inject
    public PathwaysProcessor(final MutationLog mutationLog,
                             final PathwaysShardStore shardStore,
                             final MessageReceiverFactory messageReceiverFactory,
                             final PathwaySerde pathwaySerde,
                             final PathwayNarrower pathwayNarrower,
                             final ByteBuffers byteBuffers,
                             final ByteBufferFactory byteBufferFactory,
                             final Metrics metrics) {
        this.mutationLog = mutationLog;
        this.shardStore = shardStore;
        this.messageReceiverFactory = messageReceiverFactory;
        this.pathwaySerde = pathwaySerde;
        this.pathwayNarrower = pathwayNarrower;
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;

        this.tracesApplied = metrics.registrationBuilder(getClass())
                .addNamePart("tracesApplied")
                .meter()
                .createAndRegister();
        this.itemsFailed = metrics.registrationBuilder(getClass())
                .addNamePart("itemsFailed")
                .counter()
                .createAndRegister();
        metrics.registrationBuilder(getClass())
                .addNamePart("queueDepth")
                .addNamePart(Metrics.COUNT)
                .gauge(() -> backlogByShard.values().stream().mapToLong(Backlog::items).sum())
                .register();
        metrics.registrationBuilder(getClass())
                .addNamePart("oldestItem")
                .addNamePart(Metrics.AGE_MS)
                .gauge(() -> backlogByShard.values().stream().mapToLong(Backlog::oldestAgeMs).max().orElse(0))
                .register();
    }

    /**
     * Whether this shard's model is due a narrowing — the only work a pathways document has that does
     * not arrive as an item. A shard that has gone quiet still has to be opened for it, because
     * narrowing is exactly what a model nobody is exercising any more needs: its paths should be
     * falling away.
     */
    @Override
    public boolean maintenanceDue(final PlanBDocument doc, final int shardIndex) {
        final PathwaysDoc pathwaysDoc = (PathwaysDoc) doc;
        final SharedFileStoreSettings settings = pathwaysDoc.getSharedFileStore();
        return settings != null
               && NullSafe.isNonBlankString(settings.getSharedPath())
               && narrowingDue(shardStore.sharedDocDir(pathwaysDoc), shardIndex, Instant.now());
    }

    /**
     * The same lock the screens take when they write to a shard — see {@code PathwaysService} — so
     * that a reader editing a model and a pass applying traces to it cannot hold one shard at once.
     */
    @Override
    public String lockName(final PlanBDocument doc, final int shardIndex) {
        return lockName(doc.getUuid(), shardIndex);
    }

    /**
     * Applies this shard's queued traces to its model, narrows the model if that is due, and
     * publishes it — inside the cluster lock the caller is holding.
     *
     * @param queueDirs the traces handed over to this shard, oldest first. Empty where the shard was
     *                  opened only because its narrowing fell due.
     * @return what went in, so the caller can delete it now the model holding it is published, and
     * what failed, so the attempt is counted against it. What is left out is offered again.
     */
    @Override
    public MergeResult mergeShard(final MergeContext ctx, final List<Path> queueDirs) {
        final PathwaysDoc doc = (PathwaysDoc) ctx.doc();
        // Asked again once the lock was held, so this is not what was decided before it: on a cluster
        // another node may have narrowed this shard and stamped the marker since.
        final boolean narrowNow = ctx.maintenanceDue();

        recordBacklog(doc, ctx.shardIndex(), queueDirs);

        final List<Path> applied = new ArrayList<>();
        final Map<Path, Exception> failures = new LinkedHashMap<>();
        try {
            shardStore.withShard(doc, ctx.shardIndex(),
                    localDir -> work(doc, queueDirs, narrowNow, localDir, applied, failures));
        } catch (final IOException e) {
            // The model could not be taken down or put back, so nothing here was committed. Saying
            // nothing was applied is the whole point: every item is offered again next cycle rather
            // than being deleted here.
            LOGGER.error(() -> LogUtil.message("Could not work shard {} of {}, leaving {} item(s): {}",
                    ctx.shardIndex(), doc.getName(), queueDirs.size(), e.getMessage()), e);
            return MergeResult.none();
        }

        if (narrowNow) {
            // Stamped after the shard is safely back, and whether or not the narrowing changed
            // anything — the work was done either way, and a model already as narrow as its window
            // does not need looking at again today.
            shardStore.sharedDocDir(doc)
                    .ifPresent(dir -> NARROWED_MARKER.recordRun(dir, ctx.shardIndex()));
        }
        // What went in, so the caller can mark those merged and delete them now the model holding
        // them is published. What is left out is offered again next cycle.
        return new MergeResult(applied, failures);
    }

    // What was waiting for this shard when it was opened, for the gauges. Kept per shard rather than
    // summed, because a shard another node is working is not reported here at all and its last known
    // depth is a better answer than nothing.
    private void recordBacklog(final PathwaysDoc doc, final int shardIndex, final List<Path> queueDirs) {
        long oldestAgeMs = 0L;
        if (!queueDirs.isEmpty()) {
            try {
                oldestAgeMs = System.currentTimeMillis() - QueueItem.orderKeyOf(queueDirs.get(0));
            } catch (final RuntimeException e) {
                // Something in the folder this pass is about to work is not named like an item.
                // Worth saying, but not worth giving up the shard for: a gauge is not the work.
                LOGGER.warn(() -> "Could not read the age of " + queueDirs.get(0) + ": " + e.getMessage());
            }
        }
        backlogByShard.put(doc.getUuid() + "/" + shardIndex,
                new Backlog(queueDirs.size(), oldestAgeMs));
    }

    // Whether this shard has been narrowed today. A shard that never has, or whose marker cannot be
    // read, is due — which is also how deleting the marker file asks for a narrowing by hand.
    private boolean narrowingDue(final Optional<Path> docDir, final int shard, final Instant now) {
        return docDir
                .map(dir -> PathwayNarrower.narrowingDue(NARROWED_MARKER.lastRun(dir, shard), now))
                .orElse(false);
    }

    static String lockName(final String pathwaysDocUuid, final int shard) {
        return lockPrefix(pathwaysDocUuid) + PlanBConstants.formatShardIndex(shard);
    }

    /**
     * Every shard lock this document will ever take. Lock rows are created on demand and removed only
     * by name, so deleting the document has to delete these too or they accumulate in
     * {@code cluster_lock} for good.
     */
    public static String lockPrefix(final String pathwaysDocUuid) {
        return "pathways-" + pathwaysDocUuid + "-";
    }

    // Everything this hold does to the shard, in the order it happens: the waiting traces widen the
    // model, and then the narrowing takes back whatever has fallen outside the window — so a run of
    // widenings and the narrowing that answers them land in the history in that order.
    private boolean work(final PathwaysDoc doc,
                         final List<Path> queueDirs,
                         final boolean narrowNow,
                         final Path localDir,
                         final List<Path> applied,
                         final Map<Path, Exception> failures) {
        // A shard opened only because its narrowing is due has nothing waiting, and applying nothing
        // still opens the document's feed.
        boolean changed = !queueDirs.isEmpty()
                          && applyQueuedTraces(doc, queueDirs, localDir, applied, failures);
        if (narrowNow) {
            changed = narrow(doc, localDir) || changed;
        }
        return changed;
    }

    private boolean narrow(final PathwaysDoc doc, final Path localDir) {
        // Collected rather than written as the narrowing goes: opening the feed opens a stream
        // whether or not anything is put in it, and a model already as narrow as its window has
        // nothing to say — which is most models most days.
        final List<String> lines = new ArrayList<>();
        final boolean changed = pathwayNarrower.narrow(localDir,
                PathwayNarrower.oldestDayInWindow(doc, Instant.now()),
                (severity, message) -> lines.add(message.get()));
        if (!lines.isEmpty()) {
            withMessageReceiver(messageReceiverFactory, doc, messages ->
                    lines.forEach(line -> messages.log(Severity.INFO, () -> line)));
        }
        return changed;
    }

    // Fills `applied` with the items that went in, so the caller can delete exactly those once the
    // model is safely back on the shared store. Returns whether the local model changed, which is what
    // decides whether it is worth pushing.
    private boolean applyQueuedTraces(final PathwaysDoc doc,
                               final List<Path> queueDirs,
                               final Path localDir,
                               final List<Path> applied,
                               final Map<Path, Exception> failures) {
        final Instant deadline = Instant.now().plus(MAX_TIME_PER_HOLD);
        final Counts counts = new Counts();

        try (final PathwaysDb pathwaysDb = PathwaysDb.create(localDir, byteBuffers, false)) {
            withMessageReceiver(messageReceiverFactory, doc, messageReceiver -> {
                try (final LmdbWriter writer = pathwaysDb.createWriter()) {
                    final TraceProcessor traceProcessor = new TraceProcessor(mutationLog, byteBuffers,
                            pathwaySerde,
                            new IgnoredAttributes(doc.getIgnoredAttributes()),
                            new IgnoredSpans(doc.getIgnoredSpanNames()));
                    for (final Path item : queueDirs) {
                        if (Thread.currentThread().isInterrupted()) {
                            // The lock's heartbeat interrupts us when it cannot renew. Carrying on
                            // regardless is how two nodes end up working one shard, so stop and leave
                            // the rest for next time.
                            LOGGER.warn(() -> "Interrupted part way through shard "
                                              + localDir + ", leaving the rest");
                            break;
                        }
                        if (applied.size() >= MAX_ITEMS_PER_HOLD || Instant.now().isAfter(deadline)) {
                            break;
                        }
                        try {
                            if (apply(item, pathwaysDb, writer, traceProcessor, doc,
                                    messageReceiver, counts)) {
                                applied.add(item);
                            } else {
                                // Nothing here applied and deleting is irreversible, so it is handed
                                // back as a failure: the attempt is counted against it, and once it
                                // has failed enough times it stops being offered.
                                itemsFailed.inc();
                                failures.put(item, new IllegalStateException(
                                        "The item holds a trace with no root span"));
                            }
                        } catch (final Exception e) {
                            itemsFailed.inc();
                            failures.put(item, e);
                        }
                    }
                    // Once the hold's traces are in, before the commit that ends it.
                    traceProcessor.writeHistories(writer, pathwaysDb);
                    traceProcessor.writeObservedValues(writer, pathwaysDb);
                    writer.commit();
                }
            });
        }

        tracesApplied.mark(counts.traces);

        final int itemCount = applied.size();
        final long traceCount = counts.traces;
        LOGGER.debug(() -> LogUtil.message("Applied {} item(s) holding {} trace(s) into {}",
                itemCount, traceCount, localDir));

        // Only worth copying the model back up if a trace actually landed in it. Traces
        // this shard had already seen changes nothing, and pushing then would move the whole file to
        // say so.
        return counts.changed;
    }

    /**
     * @return whether every trace in the item was dealt with, so the item can be deleted once the
     * model is back on the shared store. False where one could not be, which is the caller's signal to
     * keep it rather than destroy it.
     */
    private boolean apply(final Path item,
                          final PathwaysDb pathwaysDb,
                          final LmdbWriter writer,
                          final TraceProcessor traceProcessor,
                          final PathwaysDoc doc,
                          final MessageReceiver messageReceiver,
                          final Counts counts) {
        final boolean[] allDealtWith = {true};
        try (final QueueItemReader reader = new QueueItemReader(item, byteBuffers, byteBufferFactory)) {
            reader.forEachTrace((root, trace) -> {
                counts.traces++;
                // The trace is already in hand, so what the old path fetched from an archive bucket is
                // supplied directly. One applying path, whichever side the trace came from.
                final ApplyOutcome outcome = traceProcessor.processTrace(
                        writer,
                        pathwaysDb,
                        HexStringUtil.decode(root.getTraceId()),
                        traceId -> Optional.of(trace),
                        doc,
                        messageReceiver,
                        reader.getSource());
                counts.changed |= outcome == ApplyOutcome.APPLIED;
                allDealtWith[0] &= outcome != ApplyOutcome.NOT_APPLICABLE;
            });
        }
        return allDealtWith[0];
    }

    // Findings go to the document's info feed, as one stream per run of work. A document with no feed
    // still learns; it just has nowhere to report what it found, which beats not learning at all.
    //
    // Static and shared, because the narrowing reports what it changed to the same feed and by the
    // same rule. Two copies of the rule would mean a document that reported one and not the other.
    static void withMessageReceiver(final MessageReceiverFactory messageReceiverFactory,
                                    final PathwaysDoc doc,
                                    final Consumer<MessageReceiver> work) {
        final DocRef infoFeed = doc.getInfoFeed();
        if (infoFeed == null || infoFeed.getName() == null) {
            work.accept((severity, message) -> {
            });
        } else {
            messageReceiverFactory.create(infoFeed.getName(), work::accept);
        }
    }


    // --------------------------------------------------------------------------------


    /** Mutable across the lambdas that walk the waiting traces. */
    private static final class Counts {

        private long traces;
        private boolean changed;
    }


    // --------------------------------------------------------------------------------


    /** What was waiting for one shard when it was opened. */
    private record Backlog(long items, long oldestAgeMs) {

    }
}
