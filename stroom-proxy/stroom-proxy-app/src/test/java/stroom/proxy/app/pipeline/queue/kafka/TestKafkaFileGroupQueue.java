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

package stroom.proxy.app.pipeline.queue.kafka;

import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.store.FileStoreLocation;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestKafkaFileGroupQueue {

    private static final String QUEUE_NAME = "testQueue";
    private static final String TOPIC = "test-topic";

    private MockProducer<String, byte[]> mockProducer;
    private MockConsumer<String, byte[]> mockConsumer;
    private FileGroupQueueMessageCodec codec;
    private KafkaFileGroupQueue queue;

    @BeforeEach
    void setUp() {
        mockProducer = new MockProducer<>(
                true, // auto-complete
                new StringSerializer(),
                new ByteArraySerializer());
        mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        codec = new FileGroupQueueMessageCodec();

        queue = new KafkaFileGroupQueue(QUEUE_NAME, TOPIC, "localhost:9092", mockProducer, mockConsumer, codec);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (queue != null) {
            queue.close();
        }
    }

    @Test
    void testPublishSendsToCorrectTopic() throws IOException {
        final FileGroupQueueMessage message = createMessage("fg-1");
        queue.publish(message);

        assertThat(mockProducer.history()).hasSize(1);
        final ProducerRecord<String, byte[]> record = mockProducer.history().get(0);
        assertThat(record.topic()).isEqualTo(TOPIC);
        assertThat(record.key()).isEqualTo("fg-1");

        // Verify round-trip through codec.
        final FileGroupQueueMessage decoded = codec.fromBytes(record.value());
        assertThat(decoded.messageId()).isEqualTo("fg-1");
    }


    @Test
    void testNextReturnsEmptyWhenNoRecords() throws IOException {
        // Simulate rebalance so the consumer has an assigned partition but no records.
        simulateRebalance();
        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isEmpty();
    }

    @Test
    void testNextReturnsItemWhenRecordAvailable() throws IOException {
        final FileGroupQueueMessage message = createMessage("fg-2");
        final byte[] value = codec.toBytes(message);

        simulateRebalance();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "fg-2", value));

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();

        final FileGroupQueueItem item = result.get();
        assertThat(item.getMessage().messageId()).isEqualTo("fg-2");
        assertThat(item.getId()).isEqualTo(TOPIC + "-0-0");
    }

    @Test
    void testAcknowledgeCommitsOffset() throws IOException {
        final FileGroupQueueMessage message = createMessage("fg-3");
        final byte[] value = codec.toBytes(message);

        final TopicPartition tp = simulateRebalance();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "fg-3", value));

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();
        result.get().acknowledge();

        // MockConsumer tracks committed offsets.
        final var committed = mockConsumer.committed(Collections.singleton(tp));
        assertThat(committed).containsKey(tp);
        assertThat(committed.get(tp).offset()).isEqualTo(1L); // offset + 1
    }

    @Test
    void testAcknowledgeIsIdempotent() throws IOException {
        final FileGroupQueueMessage message = createMessage("fg-4");
        final byte[] value = codec.toBytes(message);

        simulateRebalance();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "fg-4", value));

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();
        final FileGroupQueueItem item = result.get();
        item.acknowledge();
        // Second acknowledge should be a no-op.
        item.acknowledge();
    }

    @Test
    void testFailRepublishesTheRecordAndCommitsPastTheOriginal() throws IOException {
        final FileGroupQueueMessage message = createMessage("fg-5");
        final byte[] value = codec.toBytes(message);

        final TopicPartition tp = simulateRebalance();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "fg-5", value));

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();
        result.get().fail(new RuntimeException("test error"));

        // This test used to assert the opposite - that fail() commits nothing - because redelivery
        // was achieved by seeking back. That mechanism is what wedged a partition: nothing counted
        // the attempts and nothing ever gave up, so an unprocessable record was re-read forever and
        // every record behind it waited on it. The guarantee it was protecting - that a failed
        // record really is delivered again rather than buried - is now met by republishing a copy
        // and committing past the original, which is also what gives §2.2 a delivery count.
        assertThat(mockProducer.history())
                .as("the record must be republished so it is delivered again")
                .hasSize(1);
        assertThat(new String(
                mockProducer.history().getFirst().headers()
                        .lastHeader(KafkaFileGroupQueue.DELIVERY_ATTEMPT_HEADER).value(),
                java.nio.charset.StandardCharsets.UTF_8))
                .as("the copy carries the incremented attempt count")
                .isEqualTo("2");

        // Committed past the original, so the partition advances rather than stalling on it.
        final var committed = mockConsumer.committed(Collections.singleton(tp));
        assertThat(committed.get(tp).offset()).isEqualTo(1L);
    }

    @Test
    void testCloseClosesProducerAndConsumer() throws IOException {
        queue.close();
        assertThat(mockProducer.closed()).isTrue();
        // MockConsumer.closed() is not directly exposed, but close should not throw.
        queue = null; // Prevent double-close in tearDown.
    }

    @Test
    void testMultiplePublishAndConsume() throws IOException {
        // Publish three messages.
        for (int i = 0; i < 3; i++) {
            queue.publish(createMessage("fg-multi-" + i));
        }
        assertThat(mockProducer.history()).hasSize(3);

        // Simulate rebalance.
        simulateRebalance();

        // Consume and acknowledge each in order.
        // MockConsumer ignores max.poll.records, so we add one record at a time
        // to simulate the real Kafka behaviour.
        for (int i = 0; i < 3; i++) {
            final ProducerRecord<String, byte[]> produced = mockProducer.history().get(i);
            mockConsumer.addRecord(new ConsumerRecord<>(
                    TOPIC, 0, (long) i, produced.key(), produced.value()));

            final Optional<FileGroupQueueItem> result = queue.next();
            assertThat(result).isPresent();
            assertThat(result.get().getMessage().messageId()).isEqualTo("fg-multi-" + i);
            result.get().acknowledge();
        }

        // No more items.
        assertThat(queue.next()).isEmpty();
    }

    /**
     * Simulate a Kafka rebalance to assign a partition to the mock consumer.
     * This must be used instead of {@code assign()} because the queue
     * constructor already calls {@code subscribe()}, and Kafka disallows
     * mixing {@code subscribe()} with {@code assign()}.
     */
    private TopicPartition simulateRebalance() throws IOException {
        // The queue creates and subscribes a consumer per consuming thread, on that
        // thread's first next(). MockConsumer only accepts a simulated rebalance once
        // subscribed, so drive one poll first to trigger subscription.
        queue.next();

        final TopicPartition tp = new TopicPartition(TOPIC, 0);
        mockConsumer.rebalance(Collections.singletonList(tp));
        mockConsumer.updateBeginningOffsets(Map.of(tp, 0L));
        return tp;
    }

    private FileGroupQueueMessage createMessage(final String fileGroupId) {
        return new FileGroupQueueMessage(
                FileGroupQueueMessage.CURRENT_SCHEMA_VERSION,
                fileGroupId,
                testLocation(),
                null,
                null,
                "receive",
                "test-node",
                Instant.now(),
                null,
                Map.of());
    }

    private static FileStoreLocation testLocation() {
        return FileStoreLocation.filesystem("testStore", Path.of("/tmp/test/store/0000000001"));
    }

    /**
     * Consumer creation was create → {@code subscribe} → {@code consumers.add},
     * unguarded and not atomic with the {@code closed} check. A throw from {@code subscribe} left a
     * consumer created but never registered, so {@code closeConsumers()} could not close it and the
     * stage loop made another on its next retry - one leaked group member per attempt. The same
     * gap let a consumer created during {@code close()} join the group and never be closed, which
     * withholds its partitions until Kafka's session timeout expires.
     */
    @Test
    void testAConsumerThatFailsToSubscribeIsClosedRatherThanLeaked() throws IOException {
        final MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        final java.util.concurrent.atomic.AtomicBoolean closedOnFailure =
                new java.util.concurrent.atomic.AtomicBoolean();

        final MockConsumer<String, byte[]> failing =
                new MockConsumer<>(org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST) {
                    @Override
                    public void subscribe(final java.util.Collection<String> topics) {
                        throw new IllegalStateException("subscribe refused");
                    }

                    @Override
                    public void subscribe(final java.util.Collection<String> topics,
                                          final org.apache.kafka.clients.consumer.ConsumerRebalanceListener listener) {
                        throw new IllegalStateException("subscribe refused");
                    }

                    @Override
                    public void close() {
                        closedOnFailure.set(true);
                        super.close();
                    }
                };

        try (final KafkaFileGroupQueue queue = new KafkaFileGroupQueue(
                QUEUE_NAME, TOPIC, "localhost:9092", producer, failing, codec)) {

            assertThatThrownBy(queue::next).isInstanceOf(IllegalStateException.class);

            assertThat(closedOnFailure)
                    .as("a consumer that could not subscribe must be closed, not left in the group")
                    .isTrue();
        }
    }


    /**
     * A failed record must come back. Not committing is not enough on its own: {@code poll()} has
     * already advanced the fetch position past the record, so the next poll returns the following one
     * and the first successful acknowledge would commit an offset beyond the failed one, burying it.
     * {@code fail()} republishes the record to the tail instead, which is what stops it being buried
     * without it blocking the partition; {@code seek()} is confined to the release path.
     */
    @Test
    void testGivingUpPublishesTheRecordToTheDeadLetterTopicAndCommitsPastIt() throws IOException {
        final MockConsumer<String, byte[]> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        final MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        try (final KafkaFileGroupQueue bounded = new KafkaFileGroupQueue(
                QUEUE_NAME, TOPIC, "localhost:9092", producer, consumer, codec, 3)) {
            bounded.next();
            final TopicPartition tp = new TopicPartition(TOPIC, 0);
            consumer.rebalance(java.util.List.of(tp));
            consumer.updateBeginningOffsets(java.util.Map.of(tp, 0L));
            final ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(
                    TOPIC, 0, 0L, "fg-doomed", codec.toBytes(createMessage("fg-doomed")));
            record.headers().add(KafkaFileGroupQueue.DELIVERY_ATTEMPT_HEADER,
                    "3".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            consumer.addRecord(record);

            final FileGroupQueueItem item = bounded.next().orElseThrow();
            assertThat(item.getDeliveryAttempt()).isEqualTo(3);
            item.fail(new RuntimeException("cannot ever succeed"));

            assertThat(producer.history()).hasSize(1);
            assertThat(producer.history().getFirst().topic())
                    .as("a message given up on goes to the dead-letter topic, not back onto its own")
                    .isEqualTo(TOPIC + KafkaFileGroupQueue.DEAD_LETTER_TOPIC_SUFFIX);
            assertThat(consumer.committed(java.util.Set.of(tp)).get(tp).offset())
                    .as("and is committed past so the partition advances")
                    .isEqualTo(1L);
        }
    }

    @Test
    void testAnUndecodableRecordGoesToTheDeadLetterTopic() throws IOException {
        simulateRebalance();
        final TopicPartition tp = new TopicPartition(TOPIC, 0);
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "bad", "{ not a message".getBytes(
                java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(queue.next()).isEmpty();

        assertThat(mockProducer.history()).hasSize(1);
        assertThat(mockProducer.history().getFirst().topic())
                .isEqualTo(TOPIC + KafkaFileGroupQueue.DEAD_LETTER_TOPIC_SUFFIX);
        assertThat(mockConsumer.committed(java.util.Set.of(tp)).get(tp).offset()).isEqualTo(1L);
    }

    @Test
    void testTheAggregationKeyIsThePartitionKey() throws IOException {
        final FileGroupQueueMessage keyed = new FileGroupQueueMessage(
                FileGroupQueueMessage.CURRENT_SCHEMA_VERSION, "m-keyed",
                stroom.proxy.app.pipeline.store.FileStoreLocation.filesystem("s", java.nio.file.Path.of("/tmp/x")),
                "MY_FEED", "Events", "receive", "node", java.time.Instant.now(), null, java.util.Map.of());
        queue.publish(keyed);
        queue.publish(createMessage("m-unkeyed"));

        assertThat(mockProducer.history().get(0).key()).isEqualTo("MY_FEED:Events");
        assertThat(mockProducer.history().get(1).key()).isEqualTo("m-unkeyed");
    }

    @Test
    void testAFailedRecordDoesNotBlockTheOnesBehindIt() throws IOException {
        final FileGroupQueueMessage first = createMessage("fg-rewind-1");
        final FileGroupQueueMessage second = createMessage("fg-rewind-2");

        simulateRebalance();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "fg-rewind-1", codec.toBytes(first)));

        final FileGroupQueueItem failed = queue.next().orElseThrow();
        assertThat(failed.getMessage().messageId()).isEqualTo("fg-rewind-1");
        failed.fail(new RuntimeException("downstream briefly unreachable"));

        // Added only now, so it arrives in a later poll: MockConsumer hands back every record it
        // holds in one batch and ignores max.poll.records, which production relies on to return one
        // at a time, so adding both up front would have the first poll consume and discard this one.
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, "fg-rewind-2", codec.toBytes(second)));

        // The seek this replaced put the fetch position back on the failed record, which is exactly
        // why the partition could stall: with nothing counting attempts, an unprocessable record was
        // re-read ahead of every record behind it, forever. The record behind it must now be
        // reachable, and the failed one comes back as a fresh republished record instead.
        assertThat(queue.next().orElseThrow().getMessage().messageId())
                .as("the record behind a failed one must not be blocked by it")
                .isEqualTo("fg-rewind-2");
    }

    /**
     * The aggregate stage holds many records at once and completes them in any order. Completing
     * one commits only up to the first record still held, so a crash redelivers from the oldest
     * open record and never skips one.
     */
    @Test
    void testCompletingARecordCommitsOnlyPastTheRecordsBeforeTheFirstStillHeld() throws IOException {
        final TopicPartition tp = simulateRebalance();
        // One record per poll, as production's max.poll.records=1 guarantees and MockConsumer ignores.
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "a", codec.toBytes(createMessage("a"))));
        final FileGroupQueueItem first = queue.next().orElseThrow();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, "b", codec.toBytes(createMessage("b"))));
        final FileGroupQueueItem second = queue.next().orElseThrow();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 2L, "c", codec.toBytes(createMessage("c"))));
        final FileGroupQueueItem third = queue.next().orElseThrow();

        third.acknowledge();
        assertThat(mockConsumer.committed(java.util.Set.of(tp)).get(tp))
                .as("the two before it are still held, so nothing is committed")
                .isNull();

        first.acknowledge();
        assertThat(mockConsumer.committed(java.util.Set.of(tp)).get(tp).offset())
                .as("past the first only; the second is still held")
                .isEqualTo(1L);

        second.fail(new RuntimeException("cannot process"));
        assertThat(mockConsumer.committed(java.util.Set.of(tp)).get(tp).offset())
                .as("failing the second completes it, and the third was done already")
                .isEqualTo(3L);
        assertThat(mockProducer.history())
                .as("the failed record was re-published to the tail")
                .hasSize(1);
    }

    @Test
    void testReleasingAHeldRecordSeeksBackToItAndReleasesEveryLaterOne() throws IOException {
        final TopicPartition tp = simulateRebalance();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "a", codec.toBytes(createMessage("a"))));
        final FileGroupQueueItem first = queue.next().orElseThrow();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, "b", codec.toBytes(createMessage("b"))));
        final FileGroupQueueItem second = queue.next().orElseThrow();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 2L, "c", codec.toBytes(createMessage("c"))));
        final FileGroupQueueItem third = queue.next().orElseThrow();
        third.acknowledge();

        second.close();

        assertThat(mockConsumer.position(tp))
                .as("the position is back on the released record; it and the third will be polled again")
                .isEqualTo(1L);
        // The stale third item must not commit past the records that will be redelivered.
        third.acknowledge();
        first.acknowledge();
        assertThat(mockConsumer.committed(java.util.Set.of(tp)).get(tp).offset())
                .as("past the first only: the second and third are to come again")
                .isEqualTo(1L);
    }

    @Test
    void testARevokedPartitionDropsItsLedgerSoStaleItemsCommitNothing() throws IOException {
        final TopicPartition tp = simulateRebalance();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "a", codec.toBytes(createMessage("a"))));
        final FileGroupQueueItem first = queue.next().orElseThrow();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, "b", codec.toBytes(createMessage("b"))));
        final FileGroupQueueItem second = queue.next().orElseThrow();
        second.acknowledge();

        // Revoked and reassigned: whoever held it meanwhile committed further on.
        mockConsumer.rebalance(java.util.List.of());
        mockConsumer.rebalance(java.util.List.of(tp));
        mockConsumer.updateBeginningOffsets(Map.of(tp, 0L));
        mockConsumer.commitSync(Map.of(tp, new OffsetAndMetadata(50L)));

        first.acknowledge();

        assertThat(mockConsumer.committed(java.util.Set.of(tp)).get(tp).offset())
                .as("the stale item must not pull the committed position back")
                .isEqualTo(50L);
    }

    @Test
    void testAFailedRepublishLeavesTheRecordHeldForItsHolderToRelease() throws Exception {
        final MockProducer<String, byte[]> failingProducer = new MockProducer<>(
                false, new StringSerializer(), new ByteArraySerializer());
        final MockConsumer<String, byte[]> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        final KafkaFileGroupQueue failing = new KafkaFileGroupQueue(
                QUEUE_NAME, TOPIC, "localhost:9092", failingProducer, consumer, codec);
        try {
            failing.next();
            final TopicPartition tp = new TopicPartition(TOPIC, 0);
            consumer.rebalance(java.util.List.of(tp));
            consumer.updateBeginningOffsets(Map.of(tp, 0L));
            consumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "a", codec.toBytes(createMessage("a"))));
            final FileGroupQueueItem first = failing.next().orElseThrow();
            consumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, "b", codec.toBytes(createMessage("b"))));
            final FileGroupQueueItem second = failing.next().orElseThrow();

            // send().get() blocks until the mock completes the record; complete it once it is pending.
            final Thread failer = new Thread(() -> {
                while (!failingProducer.errorNext(new RuntimeException("broker gone"))) {
                    Thread.onSpinWait();
                }
            });
            failer.start();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> first.fail(new RuntimeException("cannot")))
                    .isInstanceOf(IOException.class);
            failer.join();

            assertThat(consumer.position(tp)).as("not sought back: still held").isEqualTo(2L);
            second.acknowledge();
            assertThat(consumer.committed(java.util.Set.of(tp)).get(tp))
                    .as("the first is still held, so nothing is committed past it")
                    .isNull();
            first.close();
            assertThat(consumer.position(tp)).as("released by its holder").isEqualTo(0L);
        } finally {
            failing.close();
        }
    }
}
