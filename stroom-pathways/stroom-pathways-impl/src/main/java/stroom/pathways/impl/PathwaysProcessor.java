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
import stroom.planb.impl.fs.ShardQueue;
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
import java.util.Set;
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
     * lost mid-batch can matter.
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
    private final Counter itemsQuarantined;

    /**
     * What the last pass saw, so the gauges can report it without listing the shared filesystem
     * themselves. At most one cycle stale, which is fine for a backlog, and it keeps a metrics scrape
     * off the shared mount and out of the permission checks that reading a document needs.
     */
    private final Map<String, Backlog> backlogByDoc = new ConcurrentHashMap<>();

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
        this.itemsQuarantined = metrics.registrationBuilder(getClass())
                .addNamePart("itemsQuarantined")
                .counter()
                .createAndRegister();
        metrics.registrationBuilder(getClass())
                .addNamePart("queueDepth")
                .addNamePart(Metrics.COUNT)
                .gauge(() -> backlogByDoc.values().stream().mapToLong(Backlog::items).sum())
                .register();
        metrics.registrationBuilder(getClass())
                .addNamePart("oldestItem")
                .addNamePart(Metrics.AGE_MS)
                .gauge(() -> backlogByDoc.values().stream().mapToLong(Backlog::oldestAgeMs).max().orElse(0))
                .register();
    }

    /**
     * What Plan B's merge asks for when it reaches a pathways document: the shards with traces to
     * apply or a narrowing falling due, and what to do with each.
     *
     * <p>A pathways document has no batches waiting for it — nothing writes to a processing folder on
     * its behalf — so everything it has to do arrives this way.
     */
    @Override
    public Set<Integer> shardsWithOwnWork(final PlanBDocument doc) {
        return shardsWithWork((PathwaysDoc) doc);
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
     * @param batchDirs always empty. A pathways document has no batches: nothing writes to a
     *                  processing folder on its behalf, and what it has to do is found by
     *                  {@link #shardsWithOwnWork}.
     * @return nothing merged and nothing failed. The queue items that went in are deleted here
     * instead, because they can only go once the model that consumed them is safely published.
     */
    @Override
    public MergeResult mergeShard(final MergeContext ctx, final List<Path> batchDirs) {
        final PathwaysDoc doc = (PathwaysDoc) ctx.doc();
        final SharedFileStoreSettings settings = doc.getSharedFileStore();
        if (settings != null && NullSafe.isNonBlankString(settings.getSharedPath())) {
            // Looked at again now the lock is held rather than carried from before it: items arrive
            // while a pass runs, and on a cluster another node may have narrowed this shard since.
            drain(doc, ctx.shardIndex(), queued(doc, settings, ctx.shardIndex(), Instant.now()));
        }
        return MergeResult.none();
    }

    // The shards of one pathways document with traces queued or a narrowing falling due.
    private Set<Integer> shardsWithWork(final PathwaysDoc doc) {
        final SharedFileStoreSettings settings = doc.getSharedFileStore();
        if (settings == null || NullSafe.isBlankString(settings.getSharedPath())) {
            // Nothing has been configured, so nothing was ever written for it.
            return Set.of();
        }

        final Survey survey = survey(doc, settings);
        // What was waiting when the document was looked at, not what is left now. A shard that turns
        // out to be held elsewhere still counts, because its items are still queued.
        backlogByDoc.put(doc.getUuid(), survey.backlog());
        return survey.shards().keySet();
    }

    // One shard's queue and whether its model is due a narrowing, as they stand right now.
    private Queued queued(final PathwaysDoc doc,
                          final SharedFileStoreSettings settings,
                          final int shardIndex,
                          final Instant now) {
        final ShardQueue queue = ShardQueue.of(settings, doc.getUuid(), shardIndex);
        return new Queued(queue,
                queue.itemsOldestFirst().size(),
                narrowingDue(shardStore.sharedDocDir(doc), shardIndex, now));
    }

    // One look at every shard's queue folder: which have items, how many, and how far behind the
    // document is as a whole. Done before any lock is taken, because looking is the only way to know
    // a shard has work and a contended tryLock costs a warning and a query against cluster_lock to
    // report who holds it — taking the lock only where there is work is most of the difference
    // between quiet logs and sixty a minute.
    private Survey survey(final PathwaysDoc doc, final SharedFileStoreSettings settings) {
        final Map<Integer, Queued> shards = new LinkedHashMap<>();
        long items = 0;
        long oldestAgeMs = 0;
        final Instant now = Instant.now();
        final Optional<Path> docDir = shardStore.sharedDocDir(doc);
        for (int shard = 0; shard < ShardQueue.shardCount(settings); shard++) {
            if (Thread.currentThread().isInterrupted()) {
                LOGGER.debug("Interrupted, looking at no more shards this pass");
                break;
            }
            final ShardQueue queue = ShardQueue.of(settings, doc.getUuid(), shard);
            final List<Path> waiting = queue.itemsOldestFirst();
            // A shard that has gone quiet still has to be opened, because narrowing is exactly what a
            // model nobody is exercising any more needs: its paths should be falling away.
            final boolean narrowingDue = narrowingDue(docDir, shard, now);
            if (!waiting.isEmpty() || narrowingDue) {
                shards.put(shard, new Queued(queue, waiting.size(), narrowingDue));
                items += waiting.size();
                if (!waiting.isEmpty()) {
                    oldestAgeMs = Math.max(oldestAgeMs,
                            now.toEpochMilli() - QueueItem.orderKeyOf(waiting.getFirst()));
                }
            }
        }
        return new Survey(shards, new Backlog(items, oldestAgeMs));
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

    // Applies what this hold has time for into a local copy of the shard's model, pushes the model
    // back, then deletes exactly what went into it.
    private void drain(final PathwaysDoc doc,
                       final int shardIndex,
                       final Queued shard) {
        // Asked again now the lock is held. Whether a shard was due was worked out before locking,
        // and on a cluster every node works that out for itself — so by the time this one has the
        // shard, another may have narrowed it and stamped the marker a second ago.
        final boolean narrowNow = shard.narrowingDue()
                                  && narrowingDue(shardStore.sharedDocDir(doc), shardIndex, Instant.now());

        final List<Path> applied = new ArrayList<>();
        try {
            shardStore.withShard(doc, shardIndex,
                    localDir -> work(doc, shard.queue(), shard.items(), narrowNow, localDir, applied));
        } catch (final IOException e) {
            // The model could not be taken down or put back, so nothing here was committed. Leaving
            // the items queued is the whole point: they are applied again by whoever gets the shard
            // next, rather than being dropped on the floor here.
            LOGGER.error(() -> LogUtil.message("Could not work shard {} of {}, leaving {} item(s) queued: {}",
                    shardIndex, doc.getName(), applied.size(), e.getMessage()), e);
            return;
        }
        shard.queue().delete(applied);
        if (narrowNow) {
            // Stamped after the shard is safely back, and whether or not the narrowing changed
            // anything — the work was done either way, and a model already as narrow as its window
            // does not need looking at again today.
            shardStore.sharedDocDir(doc).ifPresent(dir -> NARROWED_MARKER.recordRun(dir, shardIndex));
        }
    }

    // Everything this hold does to the shard, in the order it happens: the queued traces widen the
    // model, and then the narrowing takes back whatever has fallen outside the window — so a night's
    // widenings and the narrowing that answers them land in the history in the order they happened.
    private boolean work(final PathwaysDoc doc,
                         final ShardQueue queue,
                         final int queued,
                         final boolean narrowNow,
                         final Path localDir,
                         final List<Path> applied) {
        // Only where there is something to apply. A shard opened because its narrowing is due has an
        // empty queue, and applying nothing still opens the document's feed — see below.
        boolean changed = queued > 0 && applyBatch(doc, queue, localDir, applied);
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
    private boolean applyBatch(final PathwaysDoc doc,
                               final ShardQueue queue,
                               final Path localDir,
                               final List<Path> applied) {
        final Instant deadline = Instant.now().plus(MAX_TIME_PER_HOLD);
        final Counts counts = new Counts();

        try (final PathwaysDb pathwaysDb = PathwaysDb.create(localDir, byteBuffers, false)) {
            withMessageReceiver(messageReceiverFactory, doc, messageReceiver -> {
                try (final LmdbWriter writer = pathwaysDb.createWriter()) {
                    final TraceProcessor traceProcessor = new TraceProcessor(mutationLog, byteBuffers,
                            pathwaySerde,
                            new IgnoredAttributes(doc.getIgnoredAttributes()),
                            new IgnoredSpans(doc.getIgnoredSpanNames()));
                    for (final Path item : queue.itemsOldestFirst()) {
                        if (Thread.currentThread().isInterrupted()) {
                            // The lock's heartbeat interrupts us when it cannot renew. Carrying on
                            // regardless is how two nodes end up working one shard, so stop and leave
                            // the rest queued.
                            LOGGER.warn(() -> "Interrupted part way through " + queue
                                              + ", leaving the rest queued");
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
                                // Deleting is irreversible and nothing here applied, so the item is
                                // set aside where it can still be looked at.
                                itemsQuarantined.inc();
                                queue.quarantine(item, "it holds a trace with no root span");
                            }
                        } catch (final Exception e) {
                            itemsQuarantined.inc();
                            queue.quarantine(item, e);
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
        LOGGER.debug(() -> LogUtil.message("Applied {} item(s) holding {} trace(s) from {}",
                itemCount, traceCount, queue));

        // Only worth copying the model back up if a trace actually landed in it. A batch of traces
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


    /** Mutable across the lambdas that walk a batch. */
    private static final class Counts {

        private long traces;
        private boolean changed;
    }


    // --------------------------------------------------------------------------------


    /** What one look at a document's queue folders found. */
    private record Survey(Map<Integer, Queued> shards, Backlog backlog) {

    }


    // --------------------------------------------------------------------------------


    /** One shard's queue, how deep it was when it was looked at, and whether it is due a narrowing. */
    private record Queued(ShardQueue queue, int items, boolean narrowingDue) {

    }


    // --------------------------------------------------------------------------------


    /** What one document had waiting at the start of a pass. */
    private record Backlog(long items, long oldestAgeMs) {

        private static final Backlog NONE = new Backlog(0, 0);
    }
}
