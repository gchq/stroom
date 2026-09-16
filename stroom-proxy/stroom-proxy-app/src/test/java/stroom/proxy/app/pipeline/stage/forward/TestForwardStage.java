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

package stroom.proxy.app.pipeline.stage.forward;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.handler.Destination;
import stroom.proxy.app.handler.FileDestination;
import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.handler.Refused;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorkerResult;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.SimplePathCreator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestForwardStage extends StroomUnitTest {

    private static final ForwardBounds LENIENT = new ForwardBounds(
            Duration.ofDays(7), Duration.ZERO, 1, Duration.ZERO);

    private Path root;
    private FilesystemFileStore store;
    private LocalFileGroupQueue queue;
    private FileStoreRegistry registry;
    private ScriptedDestination destination;
    private ScriptedDestination giveUpDestination;

    @BeforeEach
    void setUp() throws IOException {
        root = getCurrentTestDir();
        store = new FilesystemFileStore("aggregateStore", root.resolve("aggregate-store"));
        queue = new LocalFileGroupQueue("forwardingInput", Files.createDirectories(root.resolve("in")));
        registry = new FileStoreRegistry(List.of(store));
        destination = new ScriptedDestination("downstream");
        giveUpDestination = new ScriptedDestination("give-up");
    }

    private ForwardStage stage(final ForwardBounds bounds) {
        return stage(bounds, () -> false);
    }

    private ForwardStage stage(final ForwardBounds bounds, final BooleanSupplier stopping) {
        return new ForwardStage(registry, destination, new GiveUp(giveUpDestination), bounds, stopping);
    }

    private FileGroupQueueWorkerResult run(final ForwardStage stage) throws IOException {
        return new FileGroupQueueWorker(queue, stage).processNext();
    }

    // --------------------------------------------------------------------------------
    // F1: a group is acknowledged only once the destination has accepted it

    @Test
    void testAnAcceptedGroupIsDeletedAndThenAcknowledged() throws Exception {
        final FileStoreLocation location = publish();

        final FileGroupQueueWorkerResult result = run(stage(LENIENT));

        assertThat(result.isProcessed()).isTrue();
        assertThat(destination.deliveries).hasSize(1);
        assertThat(destination.deliveries.getFirst().groupWasComplete()).as("delivered while in its store").isTrue();
        assertThatThrownBy(() -> store.resolve(location))
                .as("the input is deleted once accepted")
                .isInstanceOf(FileGroupNotFoundException.class);
        assertThat(queue.getApproximatePendingCount()).isZero();
        assertThat(queue.getApproximateInFlightCount()).as("acknowledged").isZero();
        assertThat(giveUpDestination.deliveries).isEmpty();
    }

    @Test
    void testATransientFailureFailsTheMessageAndKeepsTheInput() throws Exception {
        final FileStoreLocation location = publish();
        destination.next = new IOException("connection refused");

        final FileGroupQueueWorkerResult result = run(stage(LENIENT));

        assertThat(result.isFailed()).isTrue();
        assertThat(store.resolve(location)).as("the input stays in its store").isDirectory();
        assertThat(queue.getApproximatePendingCount()).as("back on the queue").isEqualTo(1);
        assertThat(queue.getApproximateInFlightCount()).isZero();
        assertThat(queue.getApproximateFailedCount()).isZero();
        try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
            assertThat(item.getDeliveryAttempt()).as("the attempt is counted").isEqualTo(2);
        }
        assertThat(giveUpDestination.deliveries).isEmpty();
    }

    @Test
    void testATransientFailureIsRetriedOnRedeliveryAndThenAccepted() throws Exception {
        final FileStoreLocation location = publish();
        destination.next = new IOException("busy");
        final ForwardStage stage = stage(LENIENT);

        assertThat(run(stage).isFailed()).isTrue();
        assertThat(run(stage).isProcessed()).isTrue();

        assertThat(destination.deliveries).hasSize(2);
        assertThatThrownBy(() -> store.resolve(location)).isInstanceOf(FileGroupNotFoundException.class);
        assertThat(queue.getApproximatePendingCount()).isZero();
        assertThat(stage.consecutiveFailures()).as("a success resets the count").isZero();
    }

    @Test
    void testAGroupThatIsAlreadyGoneIsAcknowledged() throws Exception {
        final FileStoreLocation location = publish();
        store.delete(location);

        final FileGroupQueueWorkerResult result = run(stage(LENIENT));

        assertThat(result.isProcessed()).isTrue();
        assertThat(destination.deliveries).as("nothing to deliver").isEmpty();
        assertThat(queue.getApproximatePendingCount()).isZero();
        assertThat(queue.getApproximateInFlightCount()).isZero();
    }

    // --------------------------------------------------------------------------------
    // F3, F5: a refusal gives up at once, to the give-up destination, with error.log

    @Test
    void testARefusalGivesUpAtOnceWithAnErrorLogAndAcknowledges() throws Exception {
        final FileStoreLocation location = publish();
        destination.next = new Refused(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA, "feed off", null);
        final FileDestination giveUpDir = new FileDestination(
                root.resolve("give-up"), "give-up", new SimplePathCreator(() -> root, () -> root), true);
        final ForwardStage stage = new ForwardStage(registry, destination, new GiveUp(giveUpDir), LENIENT, () -> false);

        final FileGroupQueueWorkerResult result = run(stage);

        assertThat(result.isProcessed()).isTrue();
        assertThat(destination.deliveries).hasSize(1);
        assertThatThrownBy(() -> store.resolve(location)).isInstanceOf(FileGroupNotFoundException.class);
        assertThat(queue.getApproximatePendingCount()).isZero();
        assertThat(queue.getApproximateInFlightCount()).isZero();

        final Path givenUp = onlyGroupUnder(root.resolve("give-up"));
        new FileGroup(givenUp).requireComplete("given up");
        final String errorLog = Files.readString(givenUp.resolve(FileGroup.ERROR_LOG_FILE_NAME));
        assertThat(errorLog)
                .contains("destination: downstream")
                .contains("attempts: 1")
                .contains("age: ")
                .contains("Refused status " + StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA.getCode())
                .contains("feed off");
    }

    @Test
    void testAGiveUpDeliveryThatThrowsFailsTheMessageSoNothingIsAcknowledged() throws Exception {
        final FileStoreLocation location = publish();
        destination.next = new Refused(StroomStatusCode.REJECTED_BY_POLICY_RULES, "policy", null);
        giveUpDestination.next = new IOException("quarantine mount gone");

        final FileGroupQueueWorkerResult result = run(stage(LENIENT));

        assertThat(result.isFailed()).isTrue();
        assertThat(giveUpDestination.deliveries).hasSize(1);
        assertThat(store.resolve(location)).as("still in its store").isDirectory();
        assertThat(queue.getApproximatePendingCount()).isEqualTo(1);
        assertThat(queue.getApproximateInFlightCount()).isZero();
    }

    @Test
    void testTheGiveUpDestinationSeesTheGroupWithItsErrorLog() throws Exception {
        publish();
        destination.next = new Refused(StroomStatusCode.UNEXPECTED_DATA_TYPE, "type", null);

        run(stage(LENIENT));

        assertThat(giveUpDestination.deliveries).hasSize(1);
        assertThat(giveUpDestination.deliveries.getFirst().hadErrorLog()).isTrue();
        assertThat(giveUpDestination.deliveries.getFirst().groupWasComplete()).isTrue();
    }

    // --------------------------------------------------------------------------------
    // F4: the retry bound is an age

    @Test
    void testAGroupOlderThanMaxRetryAgeIsGivenUpOnAtItsNextFailureWhateverTheAttempt() throws Exception {
        final FileStoreLocation location = publish(Instant.now().minus(Duration.ofHours(2)));
        destination.next = new IOException("still down");

        final FileGroupQueueWorkerResult result = run(stage(new ForwardBounds(
                Duration.ofHours(1), Duration.ZERO, 1, Duration.ZERO)));

        assertThat(result.isProcessed()).as("given up on, so acknowledged").isTrue();
        assertThat(giveUpDestination.deliveries).hasSize(1);
        assertThatThrownBy(() -> store.resolve(location)).isInstanceOf(FileGroupNotFoundException.class);
        assertThat(queue.getApproximatePendingCount()).isZero();
    }

    @Test
    void testAGroupYoungerThanMaxRetryAgeIsFailedNotGivenUpOn() throws Exception {
        publish(Instant.now().minus(Duration.ofMinutes(30)));
        destination.next = new IOException("still down");

        final FileGroupQueueWorkerResult result = run(stage(new ForwardBounds(
                Duration.ofHours(1), Duration.ZERO, 1, Duration.ZERO)));

        assertThat(result.isFailed()).isTrue();
        assertThat(giveUpDestination.deliveries).isEmpty();
        assertThat(queue.getApproximatePendingCount()).isEqualTo(1);
    }

    @Test
    void testAnAcceptedGroupIsNeverGivenUpOnHoweverOldItIs() throws Exception {
        final FileStoreLocation location = publish(Instant.now().minus(Duration.ofDays(30)));

        final FileGroupQueueWorkerResult result = run(stage(new ForwardBounds(
                Duration.ofHours(1), Duration.ZERO, 1, Duration.ZERO)));

        assertThat(result.isProcessed()).isTrue();
        assertThat(destination.deliveries).hasSize(1);
        assertThat(giveUpDestination.deliveries).isEmpty();
        assertThatThrownBy(() -> store.resolve(location)).isInstanceOf(FileGroupNotFoundException.class);
    }

    // --------------------------------------------------------------------------------
    // F6: back-off is the destination's, in memory

    @Test
    void testConsecutiveFailuresGrowTheDelayToTheCapAndASuccessResetsIt() throws Exception {
        final ForwardStage stage = stage(new ForwardBounds(
                Duration.ofDays(7), Duration.ofMillis(100), 2, Duration.ofMillis(250)));
        publish();
        destination.next = new IOException("1");
        long before = System.currentTimeMillis();
        run(stage);
        assertThat(stage.consecutiveFailures()).isEqualTo(1);
        assertThat(stage.notBeforeMillis() - before).isGreaterThanOrEqualTo(100);

        destination.next = new IOException("2");
        before = System.currentTimeMillis();
        run(stage);
        assertThat(stage.consecutiveFailures()).isEqualTo(2);
        assertThat(stage.notBeforeMillis() - before).isGreaterThanOrEqualTo(200);

        destination.next = new IOException("3");
        before = System.currentTimeMillis();
        run(stage);
        assertThat(stage.consecutiveFailures()).isEqualTo(3);
        assertThat(stage.notBeforeMillis() - before).as("capped").isBetween(250L, 1_000L);

        run(stage);
        assertThat(stage.consecutiveFailures()).isZero();
        assertThat(stage.notBeforeMillis()).isZero();
    }

    @Test
    void testFailuresInsideOneWaitAreOneRoundSoTheDelayGrowsPerRoundNotPerThread() throws Exception {
        publish();
        publish();
        // Two threads deliver at once, as two of a destination's threads would in an outage, and
        // both fail together: the second failure lands inside the wait the first one set.
        final CountDownLatch bothDelivering = new CountDownLatch(2);
        final Destination failingTogether = new Destination() {
            @Override
            public String getName() {
                return "down";
            }

            @Override
            public String getDescription() {
                return "down";
            }

            @Override
            public void deliver(final Path group) throws IOException {
                bothDelivering.countDown();
                try {
                    if (!bothDelivering.await(10, TimeUnit.SECONDS)) {
                        throw new IOException("the other attempt never arrived");
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new IOException("down");
            }
        };
        final ForwardStage stage = new ForwardStage(registry, failingTogether, new GiveUp(giveUpDestination),
                new ForwardBounds(Duration.ofDays(7), Duration.ofSeconds(30), 2, Duration.ofHours(1)), () -> false);
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final Future<FileGroupQueueWorkerResult> a = pool.submit(() -> run(stage));
            final Future<FileGroupQueueWorkerResult> b = pool.submit(() -> run(stage));
            assertThat(a.get(20, TimeUnit.SECONDS).isFailed()).isTrue();
            assertThat(b.get(20, TimeUnit.SECONDS).isFailed()).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(stage.consecutiveFailures()).as("one round").isEqualTo(1);
        assertThat(queue.getApproximatePendingCount()).isEqualTo(2);
    }

    @Test
    void testAGroupCarryingAnErrorLogResumesItsGiveUpRatherThanTheForward() throws Exception {
        final FileStoreLocation location = publish();
        final Path group = store.resolve(location);
        Files.writeString(group.resolve(FileGroup.ERROR_LOG_FILE_NAME), "failure: earlier\n");

        final FileGroupQueueWorkerResult result = run(stage(LENIENT));

        assertThat(result.isProcessed()).isTrue();
        assertThat(destination.deliveries).as("the forward destination is not tried again").isEmpty();
        assertThat(giveUpDestination.deliveries).hasSize(1);
        assertThat(giveUpDestination.deliveries.getFirst().errorLog())
                .contains("failure: earlier")
                .contains("resumed:");
        assertThatThrownBy(() -> store.resolve(location)).isInstanceOf(FileGroupNotFoundException.class);
        assertThat(queue.getApproximatePendingCount()).isZero();
    }

    @Test
    void testAGiveUpThatThrowsLeavesTheErrorLogSoTheGiveUpIsResumedOnRedelivery() throws Exception {
        publish();
        destination.next = new Refused(StroomStatusCode.REJECTED_BY_POLICY_RULES, "policy", null);
        giveUpDestination.next = new IOException("quarantine mount gone");
        final ForwardStage stage = stage(LENIENT);

        assertThat(run(stage).isFailed()).isTrue();
        assertThat(run(stage).isProcessed()).isTrue();

        assertThat(destination.deliveries).as("the refusal was not re-asked for").hasSize(1);
        assertThat(giveUpDestination.deliveries).hasSize(2);
        assertThat(queue.getApproximatePendingCount()).isZero();
    }

    @Test
    void testAnInputDeleteThatFailsAfterAcceptanceIsLoggedAndTheMessageStillAcknowledged() throws Exception {
        final FileStoreLocation location = publish();
        final AtomicBoolean failDelete = new AtomicBoolean(true);
        final FileStore failingStore = new FileStore() {
            @Override
            public String getName() {
                return store.getName();
            }

            @Override
            public FileStoreWrite newWrite() throws IOException {
                return store.newWrite();
            }

            @Override
            public Path resolve(final FileStoreLocation l) throws IOException {
                return store.resolve(l);
            }

            @Override
            public void delete(final FileStoreLocation l) throws IOException {
                if (failDelete.get()) {
                    throw new IOException("delete denied");
                }
                store.delete(l);
            }
        };
        final ForwardStage stage = new ForwardStage(new FileStoreRegistry(List.of(failingStore)), destination,
                new GiveUp(giveUpDestination), LENIENT, () -> false);

        final FileGroupQueueWorkerResult result = run(stage);

        assertThat(result.isProcessed()).as("acknowledged: the destination has the group").isTrue();
        assertThat(destination.deliveries).as("delivered once, not once per attempt").hasSize(1);
        assertThat(queue.getApproximatePendingCount()).isZero();
        assertThat(queue.getApproximateInFlightCount()).isZero();
        assertThat(store.resolve(location)).as("left for the sweep").isDirectory();
    }

    @Test
    void testTheDelayIsServedBeforeTheNextAttemptOnTheDestination() throws Exception {
        final ForwardStage stage = stage(new ForwardBounds(
                Duration.ofDays(7), Duration.ofMillis(400), 1, Duration.ofMillis(400)));
        publish();
        publish();
        destination.next = new IOException("down");
        run(stage);
        final long failedAt = System.currentTimeMillis();

        run(stage);

        assertThat(destination.deliveries).hasSize(2);
        assertThat(destination.deliveries.get(1).at() - failedAt)
                .as("the second attempt, on another group, waited out the destination's delay")
                .isGreaterThanOrEqualTo(350);
    }

    @Test
    void testAHealthyDestinationIsNotDelayed() throws Exception {
        final ForwardStage stage = stage(new ForwardBounds(
                Duration.ofDays(7), Duration.ofSeconds(30), 1, Duration.ofSeconds(30)));
        publish();
        final long before = System.currentTimeMillis();

        run(stage);

        assertThat(System.currentTimeMillis() - before).isLessThan(5_000);
        assertThat(destination.deliveries).hasSize(1);
    }

    @Test
    void testStoppingEndsTheWaitAndFailsTheMessage() throws Exception {
        final AtomicBoolean stopping = new AtomicBoolean();
        final ForwardStage stage = stage(new ForwardBounds(
                Duration.ofDays(7), Duration.ofSeconds(30), 1, Duration.ofSeconds(30)), stopping::get);
        publish();
        destination.next = new IOException("down");
        run(stage);
        stopping.set(true);
        final long before = System.currentTimeMillis();

        final FileGroupQueueWorkerResult result = run(stage);

        assertThat(System.currentTimeMillis() - before).isLessThan(5_000);
        assertThat(result.isFailed()).isTrue();
        assertThat(destination.deliveries).as("no attempt was made while stopping").hasSize(1);
        assertThat(queue.getApproximatePendingCount()).isEqualTo(1);
        assertThat(queue.getApproximateInFlightCount()).isZero();
    }

    // --------------------------------------------------------------------------------
    // F8: nothing depends on close

    @Test
    void testAClaimHeldWhenTheProcessDiesIsRedeliveredWithTheAttemptCounted() throws Exception {
        publish();
        final FileGroupQueueItem held = queue.next().orElseThrow();
        assertThat(held.getDeliveryAttempt()).isEqualTo(1);
        // The process dies mid-delivery: the claim is never acknowledged, failed or closed.
        queue.simulateProcessDeath();

        final LocalFileGroupQueue reopened = new LocalFileGroupQueue("forwardingInput", root.resolve("in"));
        try (final FileGroupQueueItem again = reopened.next().orElseThrow()) {
            assertThat(again.getMessage().messageId()).isEqualTo(held.getMessage().messageId());
            assertThat(again.getDeliveryAttempt()).isEqualTo(2);
        } finally {
            reopened.close();
        }
    }

    // --------------------------------------------------------------------------------

    private FileStoreLocation publish() throws IOException {
        return publish(Instant.now());
    }

    private FileStoreLocation publish(final Instant createdTime) throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            final FileGroup group = new FileGroup(write.getPath());
            final AttributeMap meta = new AttributeMap();
            AttributeMapUtil.addFeedAndType(meta, "TEST_FEED", "Raw Events");
            AttributeMapUtil.write(meta, group.getMeta());
            Files.writeString(group.getZip(), "zip");
            Files.writeString(group.getEntries(), "");
            location = write.commit();
        }
        queue.publish(new FileGroupQueueMessage(
                FileGroupQueueMessage.CURRENT_SCHEMA_VERSION,
                java.util.UUID.randomUUID().toString(),
                location,
                "TEST_FEED",
                "Raw Events",
                "aggregate",
                "node",
                createdTime,
                null,
                Map.of()));
        return location;
    }

    private static Path onlyGroupUnder(final Path root) throws IOException {
        try (final Stream<Path> stream = Files.walk(root)) {
            final List<Path> zips = stream.filter(p -> p.getFileName().toString().equals("proxy.zip")).toList();
            assertThat(zips).hasSize(1);
            return zips.getFirst().getParent();
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Throws whatever it is told to on its next delivery, once, and records every delivery.
     */
    private static final class ScriptedDestination implements Destination {

        private final String name;
        private final List<Delivery> deliveries = new ArrayList<>();
        private volatile Exception next;

        private ScriptedDestination(final String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "scripted";
        }

        @Override
        public void deliver(final Path group) throws Refused, IOException {
            boolean complete;
            try {
                new FileGroup(group).requireComplete("delivery");
                complete = true;
            } catch (final IOException e) {
                complete = false;
            }
            final Path errorLog = group.resolve(FileGroup.ERROR_LOG_FILE_NAME);
            deliveries.add(new Delivery(group, complete, Files.exists(errorLog),
                    Files.exists(errorLog) ? Files.readString(errorLog) : "", System.currentTimeMillis()));
            final Exception toThrow = next;
            next = null;
            if (toThrow instanceof final Refused refused) {
                throw refused;
            } else if (toThrow instanceof final IOException io) {
                throw io;
            } else if (toThrow instanceof final RuntimeException runtime) {
                throw runtime;
            }
        }

        @Override
        public Optional<stroom.proxy.app.handler.LivenessCheck> livenessCheck() {
            return Optional.empty();
        }
    }

    private record Delivery(Path group, boolean groupWasComplete, boolean hadErrorLog, String errorLog, long at) {

    }
}
