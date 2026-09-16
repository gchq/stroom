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

package stroom.proxy.app.pipeline.stage.aggregate;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.execution.LoopTask;
import stroom.proxy.app.handler.CanonicalGroupWriter;
import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.handler.ZipEntryGroup;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.repo.FeedKeyInterner;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.Metrics;
import stroom.util.shared.FeedKey;
import stroom.util.zip.ZipUtil;

import com.codahale.metrics.Histogram;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The aggregate stage ({@code designs/stages/aggregate.md}): packs many small file groups for one
 * feed into one canonical group before forwarding.
 * <p>
 * An open aggregate is a set of held queue claims. A <strong>claimer</strong> thread claims an
 * input, resolves it, reads its {@code proxy.entries} to learn its items and their sizes, and
 * assigns the items to the open aggregate for the input's feed key - or, when the next item would
 * breach a bound, closes that aggregate and opens the next. Nothing is moved or copied; the input
 * stays in its store and its message stays claimed. A closed aggregate goes to a bounded pool of
 * <strong>merge workers</strong>, which copy the items of every slice straight into a write on the
 * aggregate store, commit, publish to the forward queue, and delete each input whose last slice
 * is now published. The worker reports back and the claimer acknowledges the inputs' messages on
 * its next pass, because on Kafka a record may only be acknowledged by the thread that polled it.
 * </p>
 * <p>
 * The stage owns no durable state and does nothing at stop: what a claimer holds is released by
 * the mode - in-flight recovery on disk, the visibility lapse on SQS, reassignment on Kafka - so a
 * kill and a clean stop leave the same state, and nothing a claimer held is lost. A merge that
 * fails is not written; the aggregate's inputs are failed with the reason and redelivered.
 * </p>
 */
public final class AggregateStage {

    public static final String CLAIMER_LOOP = "stage-" + PipelineStageName.AGGREGATE.getConfigName();
    public static final String MERGE_LOOP = CLAIMER_LOOP + "-merge";
    static final Duration POLL_WAIT = Duration.ofSeconds(1);

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AggregateStage.class);
    private static final String METRIC_NAME_PART = "aggregate";

    private final FileGroupQueue input;
    private final FileStoreRegistry stores;
    private final FileStore output;
    private final FileGroupQueue forward;
    private final AggregateBounds bounds;
    private final String producerId;
    private final Histogram itemCountHistogram;
    private final Histogram byteSizeHistogram;
    private final Histogram ageHistogram;

    /** Closed aggregates waiting for a worker. Full, it blocks the claimer: that is the backpressure. */
    private final BlockingQueue<Aggregate> toMerge;
    /** Each claimer thread's own aggregates; no other thread touches them. */
    private final ThreadLocal<Claimer> claimers = ThreadLocal.withInitial(Claimer::new);

    public AggregateStage(final FileGroupQueue input,
                          final FileStoreRegistry stores,
                          final FileStore output,
                          final FileGroupQueue forward,
                          final AggregateBounds bounds,
                          final int mergeThreads,
                          final String producerId,
                          final Metrics metrics) {
        this.input = Objects.requireNonNull(input, "input");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.output = Objects.requireNonNull(output, "output");
        this.forward = Objects.requireNonNull(forward, "forward");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.producerId = Objects.requireNonNull(producerId, "producerId");
        if (mergeThreads < 1) {
            throw new IllegalArgumentException("mergeThreads must be at least 1, got " + mergeThreads);
        }
        this.toMerge = new ArrayBlockingQueue<>(mergeThreads);
        Objects.requireNonNull(metrics, "metrics");
        this.itemCountHistogram = metrics.registrationBuilder(getClass())
                .addNamePart(METRIC_NAME_PART)
                .addNamePart(Metrics.COUNT)
                .histogram()
                .createAndRegister();
        this.byteSizeHistogram = metrics.registrationBuilder(getClass())
                .addNamePart(METRIC_NAME_PART)
                .addNamePart(Metrics.SIZE_IN_BYTES)
                .histogram()
                .createAndRegister();
        this.ageHistogram = metrics.registrationBuilder(getClass())
                .addNamePart(METRIC_NAME_PART)
                .addNamePart(Metrics.AGE_MS)
                .histogram()
                .createAndRegister();
    }

    /**
     * The claimer's task: acknowledge what the workers have finished, close what has aged, claim
     * one input and add it. Registered as {@link #CLAIMER_LOOP}.
     */
    public LoopTask claimer() {
        return () -> claimers.get().pass();
    }

    /**
     * A worker's task: take one closed aggregate, merge, commit, publish, delete its finished
     * inputs, and report back. Registered as {@link #MERGE_LOOP}.
     */
    public LoopTask merger() {
        return this::mergeNext;
    }


    // --------------------------------------------------------------------------------


    /**
     * What one claimer thread holds. Every method runs on that thread.
     */
    private final class Claimer {

        private final Map<FeedKey, Aggregate> open = new HashMap<>();
        private final BlockingQueue<MergeResult> finished = new LinkedBlockingQueue<>();

        private LoopTask.Outcome pass() throws Exception {
            settle();
            closeAged();
            final Optional<FileGroupQueueItem> next = input.next(POLL_WAIT);
            if (next.isEmpty()) {
                return LoopTask.Outcome.NOTHING;
            }
            add(next.get());
            return LoopTask.Outcome.PROCESSED;
        }

        /**
         * Acknowledge the inputs of every aggregate published since the last pass; fail those of
         * every merge that failed.
         */
        private void settle() throws InterruptedException {
            for (MergeResult next = finished.poll(); next != null; next = finished.poll()) {
                final MergeResult result = next;
                final List<Held> helds = result.aggregate().helds();
                if (result.failure() == null) {
                    for (final Held held : helds) {
                        held.acknowledgeIfDone();
                    }
                } else {
                    LOGGER.error(() -> LogUtil.message(
                            "Aggregate for {} failed to merge; failing its {} input(s) so they are delivered again: {}",
                            result.aggregate().key, helds.size(),
                            LogUtil.exceptionMessage(result.failure())), result.failure());
                    for (final Held held : helds) {
                        held.fail(result.failure());
                    }
                    dropFailed();
                }
            }
        }

        /** A failed input's remaining items leave the open aggregates: they come back with the redelivery. */
        private void dropFailed() throws InterruptedException {
            final Iterator<Aggregate> iterator = open.values().iterator();
            while (iterator.hasNext()) {
                final Aggregate aggregate = iterator.next();
                if (aggregate.dropFailedSlices()) {
                    iterator.remove();
                }
            }
        }

        private void closeAged() throws InterruptedException {
            final Instant cutoff = Instant.now().minus(bounds.maxAge());
            final List<Aggregate> aged = new ArrayList<>();
            for (final Aggregate aggregate : open.values()) {
                if (aggregate.openedAt.isBefore(cutoff)) {
                    aged.add(aggregate);
                }
            }
            for (final Aggregate aggregate : aged) {
                close(aggregate);
            }
        }

        private void close(final Aggregate aggregate) throws InterruptedException {
            open.remove(aggregate.key, aggregate);
            toMerge.put(aggregate);
        }

        private void add(final FileGroupQueueItem item) throws Exception {
            final FileGroupQueueMessage message = item.getMessage();
            if (message.feed() == null) {
                // A multi-feed group that should have gone to split-zip. It cannot be aggregated.
                failAlone(item, new IOException(LogUtil.message(
                        "Message {} carries no feed, so it cannot be aggregated; a group of several feeds "
                        + "belongs on the split-zip queue", message.messageId())));
                return;
            }
            final FeedKey key = FeedKey.of(message.feed(), message.type());
            final Path path;
            try {
                path = stores.resolve(message);
            } catch (final FileGroupNotFoundException e) {
                // Absence means the work was done (R12).
                LOGGER.info(() -> LogUtil.message(
                        "Message {} resolves to no data, so its work was already done; acknowledging it",
                        message.messageId()));
                acknowledgeAlone(item);
                return;
            } catch (final IOException | RuntimeException e) {
                // The store, not the input: give the message back rather than hold a claim to nothing.
                failAlone(item, e);
                return;
            }
            final Held held = new Held(item, new FileGroup(path));
            final long[] sizes;
            try {
                held.group.requireComplete("Aggregate input '" + message.messageId() + "'");
                try (final ZipFile zip = ZipUtil.createZipFile(held.group.getZip())) {
                    // Everything the merge will ask of this input is asked now, before it joins
                    // anything: the zip opens, the entries parse, and every entry they name is there.
                    sizes = itemSizes(held.group.getEntries(), zip);
                }
                if (sizes.length == 0) {
                    throw new IOException("proxy.entries lists no items");
                }
            } catch (final IOException | RuntimeException e) {
                failAlone(item, e);
                return;
            }
            place(key, held, sizes);
        }

        /**
         * Assign the input's items to consecutive aggregates for its key. The current aggregate takes
         * items until the next would breach a bound, then closes and the next takes over.
         */
        private void place(final FeedKey key, final Held held, final long[] sizes) throws InterruptedException {
            // Nothing is handed to a worker until every slice of this input exists. A worker that
            // publishes an aggregate deletes each input whose slices are all published, so an
            // aggregate handed over mid-walk could see the input's slice count reach zero while the
            // rest of the input was still being placed, and delete what the later slices need.
            final List<Aggregate> closed = new ArrayList<>();
            Aggregate current = open.computeIfAbsent(key, Aggregate::new);
            int sliceStart = 0;
            for (int i = 0; i < sizes.length; i++) {
                final long size = sizes[i];
                if (current.items > 0
                    && (current.items + 1 > bounds.maxItems()
                        || ZipEntryGroup.addSaturating(current.bytes, size) > bounds.maxBytes())) {
                    if (i > sliceStart) {
                        current.add(held.slice(sliceStart, i, sizes));
                    }
                    closed.add(current);
                    current = new Aggregate(key);
                    open.put(key, current);
                    sliceStart = i;
                }
                current.items++;
                current.bytes = ZipEntryGroup.addSaturating(current.bytes, size);
            }
            current.add(held.slice(sliceStart, sizes.length, sizes));
            if (current.items >= bounds.maxItems() || current.bytes >= bounds.maxBytes()) {
                closed.add(current);
            }
            for (final Aggregate aggregate : closed) {
                close(aggregate);
            }
        }

        /**
         * Acknowledge an item this claimer never held an aggregate for. An acknowledgement that throws
         * releases the item, as {@link Held#acknowledgeIfDone()} and the queue worker do: left
         * neither completed nor closed, the claim would be held by nobody for the life of the
         * process - kept invisible by its SQS heartbeat, or blocking the commit prefix of its Kafka
         * partition (Q4).
         */
        private void acknowledgeAlone(final FileGroupQueueItem item) {
            try {
                item.acknowledge();
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(() -> LogUtil.message(
                        "Failed to acknowledge input {}; releasing it, so it will be delivered again",
                        item.getMessage().messageId()), e);
                release(item);
            }
        }

        private void failAlone(final FileGroupQueueItem item, final Exception reason) {
            LOGGER.error(() -> LogUtil.message(
                    "Refusing input {}: {}", item.getMessage().messageId(), LogUtil.exceptionMessage(reason)), reason);
            try {
                item.fail(reason);
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(() -> LogUtil.message(
                        "Failed to fail input {}; releasing it instead", item.getMessage().messageId()), e);
                release(item);
            }
        }

        /** The holder has stopped holding an item it could not complete; the claim goes back (Q4). */
        private void release(final FileGroupQueueItem item) {
            try {
                item.close();
            } catch (final IOException | RuntimeException closeFailure) {
                LOGGER.error(() -> LogUtil.message(
                        "Failed to release input {}; the mode will release it", item.getMessage().messageId()),
                        closeFailure);
            }
        }
    }

    /**
     * The declared uncompressed size of every item in an entries file, in order, having checked that
     * every entry an item names is in the zip. Reading the whole file before placing anything means
     * an input that cannot be read is refused whole.
     */
    private static long[] itemSizes(final Path entriesFile, final ZipFile zip) throws IOException {
        final List<Long> sizes = new ArrayList<>();
        final FeedKeyInterner interner = FeedKeyInterner.create();
        forEachItem(entriesFile, (index, line) -> {
            final ZipEntryGroup item = ZipEntryGroup.read(line, interner);
            for (final ZipEntryGroup.Entry entry : new ZipEntryGroup.Entry[]{
                    item.getManifestEntry(), item.getMetaEntry(), item.getContextEntry(), item.getDataEntry()}) {
                if (entry != null && zip.getEntry(entry.getName()) == null) {
                    throw new IOException(LogUtil.message(
                            "proxy.entries names '{}', which is not in the zip beside it", entry.getName()));
                }
            }
            sizes.add(item.getTotalUncompressedSize());
            return true;
        });
        final long[] result = new long[sizes.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = sizes.get(i);
        }
        return result;
    }

    private static void forEachItem(final Path entriesFile, final ItemLineConsumer consumer) throws IOException {
        try (final BufferedReader reader = Files.newBufferedReader(entriesFile)) {
            int index = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (!consumer.accept(index++, line)) {
                    return;
                }
            }
        }
    }

    @FunctionalInterface
    private interface ItemLineConsumer {

        /** @return false to stop reading. */
        boolean accept(int index, String line) throws IOException;
    }


    // --------------------------------------------------------------------------------


    private LoopTask.Outcome mergeNext() throws InterruptedException {
        final Aggregate aggregate = toMerge.poll(POLL_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        if (aggregate == null) {
            return LoopTask.Outcome.NOTHING;
        }
        Throwable failure = null;
        try {
            merge(aggregate);
        } catch (final Throwable e) {
            // Whatever went wrong, the claimer must hear of it, or the aggregate's inputs stay
            // claimed until the mode releases them.
            failure = e;
        }
        aggregate.owner.finished.add(new MergeResult(aggregate, failure));
        if (failure instanceof Error error) {
            throw error;
        }
        return failure == null
                ? LoopTask.Outcome.PROCESSED
                : LoopTask.Outcome.FAILED;
    }

    /**
     * Merge the aggregate's slices into a fresh group, commit it, publish it, then delete every
     * input whose last slice this was.
     */
    private void merge(final Aggregate aggregate) throws IOException {
        // An input failed since this aggregate closed - another merge it was part of failed - is
        // being delivered again whole, so its slice here would only be a duplicate.
        final List<Slice> slices = new ArrayList<>();
        for (final Slice slice : aggregate.slices) {
            if (!slice.held().failed) {
                slices.add(slice);
            }
        }
        if (slices.isEmpty()) {
            LOGGER.info(() -> LogUtil.message(
                    "Aggregate for {} has nothing left to merge: every input in it has been failed", aggregate.key));
            return;
        }
        final FileStoreLocation location;
        try (final FileStoreWrite write = output.newWrite()) {
            AttributeMap common = null;
            try (final CanonicalGroupWriter writer = new CanonicalGroupWriter(write.getPath())) {
                for (final Slice slice : slices) {
                    final FileGroup group = slice.held().group;
                    final AttributeMap meta = new AttributeMap();
                    AttributeMapUtil.read(group.getMeta(), meta);
                    common = intersect(common, meta);
                    final FeedKeyInterner interner = FeedKeyInterner.create();
                    try (final ZipFile zip = ZipUtil.createZipFile(group.getZip())) {
                        forEachItem(group.getEntries(), (index, line) -> {
                            if (index >= slice.to()) {
                                return false;
                            }
                            if (index >= slice.from()) {
                                writer.add(zip, ZipEntryGroup.read(line, interner), aggregate.key);
                            }
                            return true;
                        });
                    }
                }
                AttributeMapUtil.addFeedAndType(common, aggregate.key.feed(), aggregate.key.type());
                writer.finish(common);
            }
            location = write.commit();
        }

        final FileGroupQueueMessage message = FileGroupQueueMessage.create(
                location,
                aggregate.key.feed(),
                aggregate.key.type(),
                PipelineStageName.AGGREGATE.getConfigName(),
                producerId,
                null,
                Map.of());
        forward.publish(message);
        LOGGER.debug(() -> LogUtil.message(
                "Published aggregate {} for {}: {} item(s), {} byte(s), {} slice(s), open {}",
                message.messageId(), aggregate.key, aggregate.items, aggregate.bytes, aggregate.slices.size(),
                Duration.between(aggregate.openedAt, Instant.now())));

        for (final Slice slice : slices) {
            slice.held().published(this);
        }
        itemCountHistogram.update(aggregate.items);
        byteSizeHistogram.update(aggregate.bytes);
        ageHistogram.update(Duration.between(aggregate.openedAt, Instant.now()).toMillis());
    }

    /**
     * The input is done with: delete it from its store, and the path the store lent for it. On a
     * filesystem store those are the same directory and the second call finds nothing; on an
     * object store the second is the per-resolve download, which is this caller's to remove (C3).
     */
    private void deleteInput(final Held held) {
        final FileStoreLocation location = held.item.getMessage().fileStoreLocation();
        try {
            stores.requireFileStore(location.storeName()).delete(location);
        } catch (final IOException | RuntimeException e) {
            LOGGER.warn(() -> LogUtil.message(
                    "Failed to delete aggregated input {} after publishing it. The aggregate is committed and "
                    + "published, so this is an orphan the store's housekeeping clears rather than data at risk.",
                    location), e);
        }
        final Path lent = held.group.getParentDir();
        if (Files.exists(lent) && !FileUtil.deleteDir(lent)) {
            LOGGER.warn(() -> LogUtil.message(
                    "Failed to delete the resolved copy of aggregated input {} at {}. It is disk left behind, "
                    + "cleared at the next start, rather than data at risk.", location, lent));
        }
    }

    /** The headers every input agrees on. */
    private static AttributeMap intersect(final AttributeMap common, final AttributeMap meta) {
        if (common == null) {
            return new AttributeMap(meta);
        }
        // Removed by key rather than through the entry iterator: the map is case-insensitive and its
        // entry set is not a live view.
        final List<String> differing = new ArrayList<>();
        for (final Map.Entry<String, String> entry : common.entrySet()) {
            if (!Objects.equals(entry.getValue(), meta.get(entry.getKey()))) {
                differing.add(entry.getKey());
            }
        }
        differing.forEach(common::remove);
        return common;
    }


    // --------------------------------------------------------------------------------


    /**
     * An aggregate: open while its claimer is still adding to it, then handed to a worker whole.
     * Only the claimer touches it while open; only one worker touches it after.
     */
    private final class Aggregate {

        private final FeedKey key;
        private final Instant openedAt = Instant.now();
        private final Claimer owner = claimers.get();
        private final List<Slice> slices = new ArrayList<>();
        private int items;
        private long bytes;

        private Aggregate(final FeedKey key) {
            this.key = key;
        }

        private void add(final Slice slice) {
            slices.add(slice);
        }

        private List<Held> helds() {
            final Set<Held> helds = new LinkedHashSet<>();
            for (final Slice slice : slices) {
                helds.add(slice.held());
            }
            return new ArrayList<>(helds);
        }

        /** Remove the slices of failed inputs and recount. @return true if nothing is left. */
        private boolean dropFailedSlices() {
            if (slices.removeIf(slice -> slice.held().failed)) {
                items = 0;
                bytes = 0;
                for (final Slice slice : slices) {
                    items += slice.items();
                    bytes = ZipEntryGroup.addSaturating(bytes, slice.bytes());
                }
            }
            return slices.isEmpty();
        }
    }

    /**
     * Items {@code from} (inclusive) to {@code to} (exclusive) of one held input.
     */
    private record Slice(Held held, int from, int to, long bytes) {

        private int items() {
            return to - from;
        }
    }

    /**
     * A claimed input: its queue item, its resolved group, and how many of its slices are still to
     * be published. Deleted by the worker that publishes its last slice; acknowledged by its
     * claimer afterwards; failed by its claimer if any merge it was part of failed.
     */
    private static final class Held {

        private final FileGroupQueueItem item;
        private final FileGroup group;
        private final AtomicInteger unpublishedSlices = new AtomicInteger();
        private volatile boolean failed;
        private boolean acknowledged;

        private Held(final FileGroupQueueItem item, final FileGroup group) {
            this.item = item;
            this.group = group;
        }

        private Slice slice(final int from, final int to, final long[] sizes) {
            unpublishedSlices.incrementAndGet();
            long bytes = 0;
            for (int i = from; i < to; i++) {
                bytes = ZipEntryGroup.addSaturating(bytes, sizes[i]);
            }
            return new Slice(this, from, to, bytes);
        }

        /** Worker: one more slice is published; the last one deletes the input. */
        private void published(final AggregateStage stage) {
            if (unpublishedSlices.decrementAndGet() == 0) {
                // Excludes the claimer failing this input between the check and the delete: a failed
                // input is redelivered whole, and a redelivery that finds nothing is acknowledged.
                synchronized (this) {
                    if (!failed) {
                        stage.deleteInput(this);
                    }
                }
            }
        }

        /** Claimer: acknowledge once every slice is published. */
        private void acknowledgeIfDone() {
            if (acknowledged || failed || unpublishedSlices.get() != 0) {
                return;
            }
            acknowledged = true;
            try {
                item.acknowledge();
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(() -> LogUtil.message(
                        "Failed to acknowledge aggregated input {}; releasing it, so it will be delivered again "
                        + "and duplicated", item.getMessage().messageId()), e);
                release();
            }
        }

        /** The holder has stopped holding an item it could not complete; the claim goes back (Q4). */
        private void release() {
            try {
                item.close();
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(() -> LogUtil.message(
                        "Failed to release aggregated input {}; the mode will release it",
                        item.getMessage().messageId()), e);
            }
        }

        /** Claimer: a merge this input was part of failed. */
        private void fail(final Throwable reason) {
            synchronized (this) {
                if (failed || acknowledged) {
                    return;
                }
                failed = true;
            }
            try {
                item.fail(reason);
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(() -> LogUtil.message(
                        "Failed to fail aggregated input {}; releasing it instead",
                        item.getMessage().messageId()), e);
                release();
            }
        }
    }

    private record MergeResult(Aggregate aggregate, Throwable failure) {

    }
}
