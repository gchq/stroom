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
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.execution.LoopTask.Outcome;
import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.handler.TestDataUtil;
import stroom.proxy.app.handler.ZipEntryGroup;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
import stroom.proxy.repo.FeedKeyInterner;
import stroom.test.common.MockMetrics;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.shared.FeedKey;
import stroom.util.zip.ZipUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The aggregation contract, A1 to A8 ({@code designs/stages/aggregate.md}), driven by hand: the
 * claimer's and a worker's passes are run from the test thread in the order the test needs.
 */
class TestAggregateStage extends StroomUnitTest {

    private static final FeedKey FEED = FeedKey.of("TEST_FEED", "Raw Events");
    private static final FeedKey OTHER_FEED = FeedKey.of("OTHER_FEED", "Raw Events");
    private static final Duration LONG = Duration.ofHours(1);
    private static final Duration SHORT = Duration.ofMillis(1);

    private Path root;
    private FilesystemFileStore inputStore;
    private FilesystemFileStore outputStore;
    private LocalFileGroupQueue inputQueue;
    private LocalFileGroupQueue forwardQueue;

    @BeforeEach
    void setUp() throws IOException {
        root = getCurrentTestDir();
        inputStore = new FilesystemFileStore("receiveStore", root.resolve("receive-store"));
        outputStore = new FilesystemFileStore("aggregateStore", root.resolve("aggregate-store"));
        inputQueue = new LocalFileGroupQueue("aggregateInput", Files.createDirectories(root.resolve("in")));
        forwardQueue = new LocalFileGroupQueue("forwardingInput", Files.createDirectories(root.resolve("forward")));
    }

    private AggregateStage stage(final int maxItems, final long maxBytes, final Duration maxAge) {
        return stage(outputStore, maxItems, maxBytes, maxAge);
    }

    /**
     * The hand-off queue between claimer and workers is bounded by the pool size and a full one
     * blocks the claimer, which is the backpressure. These tests drive both by hand on one thread, so
     * a pass that closes several aggregates needs room for all of them.
     */
    private static final int MERGE_THREADS = 8;

    private AggregateStage stage(final FileStore output,
                                 final int maxItems,
                                 final long maxBytes,
                                 final Duration maxAge) {
        return new AggregateStage(inputQueue, new FileStoreRegistry(List.of(inputStore)), output, forwardQueue,
                new AggregateBounds(maxItems, maxBytes, maxAge), MERGE_THREADS, "node", new MockMetrics());
    }

    // --------------------------------------------------------------------------------
    // A1, A6: ordering

    @Test
    void testInputsStayClaimedAndInTheirStoreUntilTheAggregateIsPublished() throws Exception {
        final AggregateStage stage = stage(10, Long.MAX_VALUE, LONG);
        final FileStoreLocation first = publish(2, FEED);
        final FileStoreLocation second = publish(2, FEED);

        assertThat(stage.claimer().run()).isEqualTo(Outcome.PROCESSED);
        assertThat(stage.claimer().run()).isEqualTo(Outcome.PROCESSED);

        assertThat(inputQueue.getApproximateInFlightCount()).as("both held").isEqualTo(2);
        assertThat(inputQueue.getApproximatePendingCount()).isZero();
        assertThat(inputStore.resolve(first)).isDirectory();
        assertThat(inputStore.resolve(second)).isDirectory();
        assertThat(forwardQueue.getApproximatePendingCount()).as("nothing closed yet").isZero();
    }

    @Test
    void testCloseCommitsPublishesDeletesThenAcknowledges() throws Exception {
        final List<String> calls = new ArrayList<>();
        final FileStore recordingOutput = recordingStore(calls);
        final AggregateStage stage = stage(recordingOutput, 4, Long.MAX_VALUE, LONG);
        final FileStoreLocation a = publish(2, FEED);
        final FileStoreLocation b = publish(2, FEED);

        stage.claimer().run();
        stage.claimer().run();                      // fills the aggregate, which closes
        assertThat(calls).as("the claimer merges nothing").isEmpty();
        assertThat(stage.merger().run()).isEqualTo(Outcome.PROCESSED);

        assertThat(calls).containsExactly("commit", "publish", "delete", "delete");
        assertThat(inputQueue.getApproximateInFlightCount())
                .as("acknowledged only on the claimer's next pass")
                .isEqualTo(2);
        assertThat(stage.claimer().run()).isEqualTo(Outcome.NOTHING);
        assertThat(inputQueue.getApproximateInFlightCount()).isZero();
        assertThat(inputQueue.getApproximatePendingCount()).isZero();
        assertThatThrownBy(() -> inputStore.resolve(a)).isInstanceOf(FileGroupNotFoundException.class);
        assertThatThrownBy(() -> inputStore.resolve(b)).isInstanceOf(FileGroupNotFoundException.class);
    }

    @Test
    void testTheAggregateIsCanonicalAndCarriesTheCommonHeadersAndTheKey() throws Exception {
        final AggregateStage stage = stage(6, Long.MAX_VALUE, LONG);
        publish(2, FEED, Map.of("Environment", "test", "Host", "one"));
        publish(2, FEED, Map.of("Environment", "test", "Host", "two"));
        publish(2, FEED, Map.of("Environment", "test", "Host", "three"));
        stage.claimer().run();
        stage.claimer().run();
        stage.claimer().run();
        stage.merger().run();

        final List<FileGroupQueueMessage> out = drain(forwardQueue);
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().feed()).isEqualTo(FEED.feed());
        assertThat(out.getFirst().type()).isEqualTo(FEED.type());
        assertThat(out.getFirst().producingStage()).isEqualTo("aggregate");
        final Path dir = outputStore.resolve(out.getFirst().fileStoreLocation());
        assertCanonical(dir, 6, FEED);
        final AttributeMap meta = new AttributeMap();
        AttributeMapUtil.read(new FileGroup(dir).getMeta(), meta);
        assertThat(meta.get("Environment")).as("common to every input").isEqualTo("test");
        assertThat(meta.get("Host")).as("differs between inputs, so it is not common").isNull();
    }

    // --------------------------------------------------------------------------------
    // A4: bounds and ranges

    @Test
    void testALargeInputIsDividedByRangeAndAcknowledgedAfterItsLastSlice() throws Exception {
        final AggregateStage stage = stage(4, Long.MAX_VALUE, SHORT);
        final FileStoreLocation input = publish(10, FEED);

        stage.claimer().run();                      // 4, 4 closed; 2 open
        assertThat(stage.merger().run()).isEqualTo(Outcome.PROCESSED);
        assertThat(stage.merger().run()).isEqualTo(Outcome.PROCESSED);
        assertThat(stage.merger().run()).isEqualTo(Outcome.NOTHING);

        assertThat(inputQueue.getApproximateInFlightCount()).as("its last slice is still open").isEqualTo(1);
        assertThat(inputStore.resolve(input)).isDirectory();
        stage.claimer().run();                      // settles the two published, nothing to acknowledge yet
        assertThat(inputQueue.getApproximateInFlightCount()).isEqualTo(1);

        Thread.sleep(5);
        stage.claimer().run();                      // the remainder has aged: closed
        assertThat(stage.merger().run()).isEqualTo(Outcome.PROCESSED);
        stage.claimer().run();                      // acknowledged

        assertThat(inputQueue.getApproximateInFlightCount()).isZero();
        assertThatThrownBy(() -> inputStore.resolve(input)).isInstanceOf(FileGroupNotFoundException.class);
        final List<FileGroupQueueMessage> out = drain(forwardQueue);
        assertThat(out).hasSize(3);
        assertCanonical(outputStore.resolve(out.get(0).fileStoreLocation()), 4, FEED);
        assertCanonical(outputStore.resolve(out.get(1).fileStoreLocation()), 4, FEED);
        assertCanonical(outputStore.resolve(out.get(2).fileStoreLocation()), 2, FEED);
    }

    @Test
    void testAnItemThatWouldBreachTheCountClosesTheAggregateAndOpensTheNextWithItself() throws Exception {
        final AggregateStage stage = stage(4, Long.MAX_VALUE, LONG);
        publish(3, FEED);
        publish(3, FEED);
        stage.claimer().run();
        stage.claimer().run();                      // items 1..4 close; 5, 6 open
        stage.merger().run();

        final List<FileGroupQueueMessage> out = drain(forwardQueue);
        assertThat(out).hasSize(1);
        assertCanonical(outputStore.resolve(out.getFirst().fileStoreLocation()), 4, FEED);
        assertThat(inputQueue.getApproximateInFlightCount()).as("the second input continues").isEqualTo(2);
    }

    @Test
    void testAnItemLargerThanTheByteBoundShipsAlone() throws Exception {
        final AggregateStage stage = stage(100, 1, LONG);
        publish(3, FEED);
        stage.claimer().run();
        for (int i = 0; i < 3; i++) {
            assertThat(stage.merger().run()).isEqualTo(Outcome.PROCESSED);
        }

        final List<FileGroupQueueMessage> out = drain(forwardQueue);
        assertThat(out).hasSize(3);
        for (final FileGroupQueueMessage message : out) {
            assertCanonical(outputStore.resolve(message.fileStoreLocation()), 1, FEED);
        }
    }

    @Test
    void testFeedsAggregateSeparately() throws Exception {
        final AggregateStage stage = stage(2, Long.MAX_VALUE, LONG);
        publish(1, FEED);
        publish(1, OTHER_FEED);
        publish(1, FEED);
        stage.claimer().run();
        stage.claimer().run();
        stage.claimer().run();
        stage.merger().run();
        stage.claimer().run();                      // acknowledges the two published

        final List<FileGroupQueueMessage> out = drain(forwardQueue);
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().feed()).isEqualTo(FEED.feed());
        assertThat(inputQueue.getApproximateInFlightCount()).as("the other feed's input waits").isEqualTo(1);
    }

    @Test
    void testAgeIsMeasuredFromOpenNotFromTheMessage() throws Exception {
        final AggregateStage stage = stage(10, Long.MAX_VALUE, Duration.ofMillis(200));
        final FileStoreLocation location = writeInput(1, FEED, Map.of());
        inputQueue.publish(new FileGroupQueueMessage(FileGroupQueueMessage.CURRENT_SCHEMA_VERSION, "old",
                location, FEED.feed(), FEED.type(), "receive", "node",
                java.time.Instant.now().minus(Duration.ofDays(1)), null, Map.of()));

        stage.claimer().run();
        assertThat(stage.claimer().run()).as("an old message does not close on arrival").isEqualTo(Outcome.NOTHING);
        assertThat(stage.merger().run()).isEqualTo(Outcome.NOTHING);

        Thread.sleep(250);
        stage.claimer().run();
        assertThat(stage.merger().run()).as("closed on age, on an empty poll").isEqualTo(Outcome.PROCESSED);
    }

    // --------------------------------------------------------------------------------
    // A3, A6, A8: refusals and absence

    @Test
    void testAMessageWithNoKeyIsFailedWithoutBeingResolved() throws Exception {
        final AtomicBoolean resolved = new AtomicBoolean();
        final FilesystemFileStore watchingInput = new FilesystemFileStore("receiveStore",
                root.resolve("watching-store")) {
            @Override
            public Path resolve(final FileStoreLocation location) throws IOException {
                resolved.set(true);
                return super.resolve(location);
            }
        };
        final LocalFileGroupQueue strictQueue = new LocalFileGroupQueue("strict",
                Files.createDirectories(root.resolve("strict")), new FileGroupQueueMessageCodec(), 1);
        final AggregateStage stage = new AggregateStage(strictQueue, new FileStoreRegistry(List.of(watchingInput)),
                outputStore, forwardQueue, new AggregateBounds(10, Long.MAX_VALUE, LONG), MERGE_THREADS, "node",
                new MockMetrics());
        strictQueue.publish(FileGroupQueueMessage.create(
                FileStoreLocation.filesystem("receiveStore", root.resolve("nowhere")),
                null, null, "receive", "node", null, Map.of()));

        stage.claimer().run();

        assertThat(resolved).isFalse();
        assertThat(strictQueue.getApproximateInFlightCount()).isZero();
        assertThat(strictQueue.getApproximateFailedCount()).as("given up after one attempt").isEqualTo(1);
    }

    @Test
    void testARedeliveredMessageWhoseInputIsGoneIsAcknowledged() throws Exception {
        final AggregateStage stage = stage(10, Long.MAX_VALUE, LONG);
        final FileStoreLocation input = publish(1, FEED);
        inputStore.delete(input);

        assertThat(stage.claimer().run()).isEqualTo(Outcome.PROCESSED);

        assertThat(inputQueue.getApproximateInFlightCount()).isZero();
        assertThat(inputQueue.getApproximatePendingCount()).isZero();
    }

    /**
     * The R12 acknowledgement is the one completion the claimer makes outside a {@code Held}, and it
     * used to be the one with no release behind it: an acknowledgement that threw escaped the pass and
     * left the item neither completed nor closed - held by nobody for the life of the process, kept
     * invisible by its SQS heartbeat or blocking the commit prefix of its Kafka partition (Q4). Now it
     * is released like every other completion that throws, so the message is delivered again.
     */
    @Test
    void testAnR12AcknowledgementThatThrowsReleasesTheItem() throws Exception {
        final LocalFileGroupQueue unacknowledgeable = new LocalFileGroupQueue("aggregateInput",
                Files.createDirectories(root.resolve("unack"))) {
            @Override
            public Optional<FileGroupQueueItem> next(final Duration maxWait) throws IOException {
                return super.next(maxWait).map(item -> new FileGroupQueueItem() {
                    @Override
                    public String getId() {
                        return item.getId();
                    }

                    @Override
                    public FileGroupQueueMessage getMessage() {
                        return item.getMessage();
                    }

                    @Override
                    public int getDeliveryAttempt() {
                        return item.getDeliveryAttempt();
                    }

                    @Override
                    public void acknowledge() throws IOException {
                        throw new IOException("the broker is away");
                    }

                    @Override
                    public void fail(final Throwable error) throws IOException {
                        item.fail(error);
                    }

                    @Override
                    public void close() throws IOException {
                        item.close();
                    }
                });
            }
        };
        final AggregateStage stage = new AggregateStage(unacknowledgeable,
                new FileStoreRegistry(List.of(inputStore)), outputStore, forwardQueue,
                new AggregateBounds(10, Long.MAX_VALUE, LONG), MERGE_THREADS, "node", new MockMetrics());
        final FileStoreLocation input = writeInput(1, FEED, Map.of());
        unacknowledgeable.publish(FileGroupQueueMessage.create(
                input, FEED.feed(), FEED.type(), "receive", "node", null, Map.of()));
        inputStore.delete(input);

        assertThat(stage.claimer().run())
                .as("the pass completes rather than throwing the acknowledgement out of the loop")
                .isEqualTo(Outcome.PROCESSED);

        assertThat(unacknowledgeable.getApproximateInFlightCount())
                .as("released, not held by nobody")
                .isZero();
        assertThat(unacknowledgeable.getApproximatePendingCount())
                .as("delivered again, with the attempt counted")
                .isEqualTo(1);
        assertThat(unacknowledgeable.next().orElseThrow().getDeliveryAttempt()).isEqualTo(2);
    }

    @Test
    void testAnUnreadableInputIsFailedAloneAndTheRestAggregate() throws Exception {
        final AggregateStage stage = stage(2, Long.MAX_VALUE, LONG);
        final FileStoreLocation bad = publish(1, FEED);
        Files.writeString(new FileGroup(inputStore.resolve(bad)).getZip(), "not a zip");
        publish(1, FEED);
        publish(1, FEED);

        stage.claimer().run();                      // the bad one, failed alone
        assertThat(inputQueue.getApproximatePendingCount()).as("redelivered later, attempt counted").isEqualTo(3);
        assertThat(inputQueue.getApproximateInFlightCount()).isZero();
        assertThat(inputStore.resolve(bad)).as("nothing is deleted").isDirectory();

        stage.claimer().run();
        stage.claimer().run();
        assertThat(stage.merger().run()).as("the two good ones close").isEqualTo(Outcome.PROCESSED);
        assertThat(drain(forwardQueue)).hasSize(1);
    }

    @Test
    void testAMergeFailureFailsEveryMemberAndWritesNothing() throws Exception {
        final AtomicBoolean failCommit = new AtomicBoolean(true);
        final FileStore flakyOutput = new FilesystemFileStore("aggregateStore", root.resolve("flaky-store")) {
            @Override
            public FileStoreWrite newWrite() throws IOException {
                final FileStoreWrite delegate = super.newWrite();
                return new FileStoreWrite() {
                    @Override
                    public Path getPath() {
                        return delegate.getPath();
                    }

                    @Override
                    public FileStoreLocation commit() throws IOException {
                        if (failCommit.get()) {
                            throw new IOException("store down");
                        }
                        return delegate.commit();
                    }

                    @Override
                    public void close() throws IOException {
                        delegate.close();
                    }
                };
            }
        };
        final AggregateStage stage = stage(flakyOutput, 2, Long.MAX_VALUE, LONG);
        publish(1, FEED);
        publish(1, FEED);
        stage.claimer().run();
        stage.claimer().run();
        assertThat(stage.merger().run()).isEqualTo(Outcome.FAILED);
        assertThat(forwardQueue.getApproximatePendingCount()).isZero();

        assertThat(stage.claimer().run()).as("settles the failure").isEqualTo(Outcome.PROCESSED);
        assertThat(inputQueue.getApproximatePendingCount() + inputQueue.getApproximateInFlightCount())
                .as("both are delivered again, nothing lost")
                .isEqualTo(2);

        failCommit.set(false);
        stage.claimer().run();
        assertThat(stage.merger().run()).isEqualTo(Outcome.PROCESSED);
        assertThat(drain(forwardQueue)).hasSize(1);
        stage.claimer().run();
        assertThat(inputQueue.getApproximateInFlightCount()).isZero();
        assertThat(inputQueue.getApproximatePendingCount()).isZero();
        assertThat(inputQueue.next().map(FileGroupQueueItem::getDeliveryAttempt)).isEmpty();
    }

    // --------------------------------------------------------------------------------
    // A2, A7: no state of its own; a kill loses nothing

    @Test
    void testAKillMidAggregateLeavesEveryInputClaimableWithTheAttemptCounted() throws Exception {
        final AggregateStage stage = stage(10, Long.MAX_VALUE, LONG);
        publish(1, FEED);
        publish(1, FEED);
        stage.claimer().run();
        stage.claimer().run();
        assertThat(inputQueue.getApproximateInFlightCount()).isEqualTo(2);

        inputQueue.close();
        final LocalFileGroupQueue reopened = new LocalFileGroupQueue("aggregateInput", root.resolve("in"));

        assertThat(reopened.getApproximatePendingCount()).isEqualTo(2);
        assertThat(reopened.next().orElseThrow().getDeliveryAttempt()).isEqualTo(2);
    }

    @Test
    void testTheStageWritesNothingOfItsOwn() throws Exception {
        final AggregateStage stage = stage(2, Long.MAX_VALUE, LONG);
        publish(1, FEED);
        publish(1, FEED);
        stage.claimer().run();
        stage.claimer().run();
        stage.merger().run();
        stage.claimer().run();

        try (final var children = Files.list(root)) {
            assertThat(children.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrder("receive-store", "aggregate-store", "in", "forward");
        }
    }


    // --------------------------------------------------------------------------------
    // A1 with a worker running: the hand-off race; lent copies

    @Test
    void testAnInputDividedAcrossAggregatesIsNeverDeletedBeforeItsLastSliceIsPlaced() throws Exception {
        final AggregateStage stage = stage(10, Long.MAX_VALUE, SHORT);
        final int inputs = 20;
        for (int i = 0; i < inputs; i++) {
            publish(35, FEED);                       // 35 items: 3 full aggregates and a remainder each
        }
        final java.util.concurrent.atomic.AtomicReference<Throwable> workerFailure =
                new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        final Thread worker = new Thread(() -> {
            try {
                while (!stop.get()) {
                    stage.merger().run();
                }
            } catch (final Throwable t) {
                workerFailure.set(t);
            }
        }, "merge-worker");
        worker.start();
        try {
            for (int i = 0; i < inputs; i++) {
                stage.claimer().run();
            }
            Thread.sleep(5);
            final long deadline = System.currentTimeMillis() + 30_000;
            while (inputQueue.getApproximateInFlightCount() > 0 && System.currentTimeMillis() < deadline) {
                stage.claimer().run();               // ages the remainders out, settles, acknowledges
            }
        } finally {
            stop.set(true);
            worker.join(10_000);
        }

        assertThat(workerFailure.get()).isNull();
        assertThat(inputQueue.getApproximateInFlightCount()).isZero();
        assertThat(inputQueue.getApproximatePendingCount()).as("nothing was failed and redelivered").isZero();
        long items = 0;
        for (final FileGroupQueueMessage message : drain(forwardQueue)) {
            items += ZipEntryGroup.read(new FileGroup(outputStore.resolve(message.fileStoreLocation())).getEntries(),
                    FeedKeyInterner.create()).size();
        }
        assertThat(items).as("every item reached the forward queue exactly once").isEqualTo(35L * inputs);
    }

    @Test
    void testAStoreThatLendsACopyHasTheCopyRemovedOncePublished() throws Exception {
        final Path scratch = Files.createDirectories(root.resolve("scratch"));
        inputStore = new FilesystemFileStore("receiveStore", root.resolve("receive-store")) {
            @Override
            public Path resolve(final FileStoreLocation location) throws IOException {
                // As an object store does: a fresh download per resolve, the caller's to remove.
                final Path committed = super.resolve(location);
                final Path copy = Files.createTempDirectory(scratch, "resolve-");
                for (final Path file : new FileGroup(committed).items()) {
                    Files.copy(file, copy.resolve(file.getFileName()));
                }
                return copy;
            }
        };
        final AggregateStage stage = stage(2, Long.MAX_VALUE, LONG);
        publish(1, FEED);
        publish(1, FEED);
        stage.claimer().run();
        stage.claimer().run();
        try (final var copies = Files.list(scratch)) {
            assertThat(copies.count()).as("held as copies").isEqualTo(2);
        }
        stage.merger().run();

        try (final var copies = Files.list(scratch)) {
            assertThat(copies.count()).as("the copies go with the inputs").isZero();
        }
        assertThat(drain(forwardQueue)).hasSize(1);
    }

    @Test
    void testAnEntriesFileNamingAnEntryTheZipDoesNotHoldIsRefusedAtClaim() throws Exception {
        final AggregateStage stage = stage(10, Long.MAX_VALUE, LONG);
        final FileStoreLocation bad = publish(2, FEED);
        final FileGroup group = new FileGroup(inputStore.resolve(bad));
        Files.writeString(group.getEntries(), Files.readString(group.getEntries())
                .replace("0000000002.dat", "0000000099.dat"));

        stage.claimer().run();

        assertThat(inputQueue.getApproximateInFlightCount()).as("failed alone, before it joined anything").isZero();
        assertThat(inputQueue.getApproximatePendingCount()).isEqualTo(1);
        assertThat(inputStore.resolve(bad)).isDirectory();
    }

    @Test
    void testAMergeFailureOnASlicedInputDropsItsRemainderAndLosesNothing() throws Exception {
        final AtomicBoolean failCommit = new AtomicBoolean(true);
        final FileStore flakyOutput = new FilesystemFileStore("aggregateStore", root.resolve("flaky-store")) {
            @Override
            public FileStoreWrite newWrite() throws IOException {
                final FileStoreWrite delegate = super.newWrite();
                return new FileStoreWrite() {
                    @Override
                    public Path getPath() {
                        return delegate.getPath();
                    }

                    @Override
                    public FileStoreLocation commit() throws IOException {
                        if (failCommit.getAndSet(false)) {
                            throw new IOException("store down, once");
                        }
                        return delegate.commit();
                    }

                    @Override
                    public void close() throws IOException {
                        delegate.close();
                    }
                };
            }
        };
        final AggregateStage stage = stage(flakyOutput, 4, Long.MAX_VALUE, SHORT);
        publish(10, FEED);                           // 4, 4 closed; 2 open
        stage.claimer().run();
        assertThat(stage.merger().run()).as("the first merge fails").isEqualTo(Outcome.FAILED);
        stage.claimer().run();                       // settles: input failed, remainder dropped
        assertThat(stage.merger().run()).as("the second, merged after the failure, skips the failed input")
                .isEqualTo(Outcome.PROCESSED);
        assertThat(inputQueue.getApproximatePendingCount() + inputQueue.getApproximateInFlightCount())
                .as("delivered again whole")
                .isEqualTo(1);
        assertThat(forwardQueue.getApproximatePendingCount()).as("nothing published from the failed round")
                .isZero();

        // The redelivery aggregates all ten.
        final long deadline = System.currentTimeMillis() + 10_000;
        while (inputQueue.getApproximateInFlightCount() + inputQueue.getApproximatePendingCount() > 0
               && System.currentTimeMillis() < deadline) {
            stage.claimer().run();
            while (stage.merger().run() == Outcome.PROCESSED) {
                // drain
            }
            Thread.sleep(5);
        }
        long items = 0;
        for (final FileGroupQueueMessage message : drain(forwardQueue)) {
            items += ZipEntryGroup.read(new FileGroup(flakyOutput.resolve(message.fileStoreLocation())).getEntries(),
                    FeedKeyInterner.create()).size();
        }
        assertThat(items).isEqualTo(10);
    }

    // --------------------------------------------------------------------------------

    private FileStoreLocation publish(final int items, final FeedKey key) throws IOException {
        return publish(items, key, Map.of());
    }

    private FileStoreLocation publish(final int items, final FeedKey key, final Map<String, String> headers)
            throws IOException {
        final FileStoreLocation location = writeInput(items, key, headers);
        inputQueue.publish(FileGroupQueueMessage.create(location, key.feed(), key.type(), "receive", "node", null,
                Map.of()));
        return location;
    }

    private FileStoreLocation writeInput(final int items, final FeedKey key, final Map<String, String> headers)
            throws IOException {
        try (final FileStoreWrite write = inputStore.newWrite()) {
            final FileGroup group = new FileGroup(write.getPath());
            final AttributeMap meta = new AttributeMap();
            meta.putAll(headers);
            AttributeMapUtil.addFeedAndType(meta, key.feed(), key.type());
            AttributeMapUtil.write(meta, group.getMeta());
            TestDataUtil.writeZip(group, items, new AttributeMap(), java.util.Set.of(key), null);
            return write.commit();
        }
    }

    private FileStore recordingStore(final List<String> calls) throws IOException {
        final FilesystemFileStore recordingOutput = new FilesystemFileStore("aggregateStore",
                root.resolve("recording-store")) {
            @Override
            public FileStoreWrite newWrite() throws IOException {
                final FileStoreWrite delegate = super.newWrite();
                return new FileStoreWrite() {
                    @Override
                    public Path getPath() {
                        return delegate.getPath();
                    }

                    @Override
                    public FileStoreLocation commit() throws IOException {
                        calls.add("commit");
                        return delegate.commit();
                    }

                    @Override
                    public void close() throws IOException {
                        delegate.close();
                    }
                };
            }
        };
        inputStore = new FilesystemFileStore("receiveStore", root.resolve("recording-input")) {
            @Override
            public void delete(final FileStoreLocation location) throws IOException {
                calls.add("delete");
                super.delete(location);
            }
        };
        forwardQueue = new LocalFileGroupQueue("forwardingInput",
                Files.createDirectories(root.resolve("recording-forward"))) {
            @Override
            public void publish(final FileGroupQueueMessage message) throws IOException {
                calls.add("publish");
                super.publish(message);
            }
        };
        return recordingOutput;
    }

    private static List<FileGroupQueueMessage> drain(final LocalFileGroupQueue queue) throws IOException {
        final List<FileGroupQueueMessage> messages = new ArrayList<>();
        Optional<FileGroupQueueItem> item;
        while ((item = queue.next()).isPresent()) {
            messages.add(item.get().getMessage());
            item.get().acknowledge();
        }
        return messages;
    }

    static void assertCanonical(final Path groupDir, final int expectedItems, final FeedKey key) throws IOException {
        final FileGroup group = new FileGroup(groupDir);
        final List<String> names = ZipUtil.pathList(group.getZip());
        final List<String> expectedNames = new ArrayList<>();
        for (int i = 1; i <= expectedItems; i++) {
            expectedNames.add(String.format("%010d.meta", i));
            expectedNames.add(String.format("%010d.dat", i));
        }
        assertThat(names).isEqualTo(expectedNames);
        final List<ZipEntryGroup> entries = ZipEntryGroup.read(group.getEntries(), FeedKeyInterner.create());
        assertThat(entries).hasSize(expectedItems);
        assertThat(entries).extracting(ZipEntryGroup::getFeedKey).containsOnly(key);
        assertThat(entries).flatExtracting(e -> List.of(e.getMetaEntry().getName(), e.getDataEntry().getName()))
                .isEqualTo(names);
        final AttributeMap meta = new AttributeMap();
        AttributeMapUtil.read(group.getMeta(), meta);
        assertThat(meta.get(StandardHeaderArguments.FEED)).isEqualTo(key.feed());
        assertThat(meta.get(StandardHeaderArguments.TYPE)).isEqualTo(key.type());
    }
}
