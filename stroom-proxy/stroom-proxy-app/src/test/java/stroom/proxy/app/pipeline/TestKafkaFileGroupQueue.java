/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.proxy.app.pipeline;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
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

        queue = new KafkaFileGroupQueue(QUEUE_NAME, TOPIC, mockProducer, mockConsumer, codec);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (queue != null) {
            queue.close();
        }
    }

    @Test
    void testNameAndType() {
        assertThat(queue.getName()).isEqualTo(QUEUE_NAME);
        assertThat(queue.getType()).isEqualTo(QueueType.KAFKA);
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
        assertThat(decoded.fileGroupId()).isEqualTo("fg-1");
        assertThat(decoded.queueName()).isEqualTo(QUEUE_NAME);
    }

    @Test
    void testPublishRejectsWrongQueueName() {
        final FileGroupQueueMessage message = FileGroupQueueMessage.create(
                "wrongQueue",
                "fg-1",
                testLocation(),
                "receive",
                "node-1",
                null,
                Map.of());

        assertThatThrownBy(() -> queue.publish(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wrongQueue");
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
        assertThat(item.getMessage().fileGroupId()).isEqualTo("fg-2");
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
    void testFailDoesNotCommit() throws IOException {
        final FileGroupQueueMessage message = createMessage("fg-5");
        final byte[] value = codec.toBytes(message);

        final TopicPartition tp = simulateRebalance();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "fg-5", value));

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();
        result.get().fail(new RuntimeException("test error"));

        // No offset should have been committed.
        final var committed = mockConsumer.committed(Collections.singleton(tp));
        assertThat(committed.get(tp)).isNull();
    }

    @Test
    void testItemMetadata() throws IOException {
        final FileGroupQueueMessage message = createMessage("fg-6");
        final byte[] value = codec.toBytes(message);

        simulateRebalance();
        mockConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "fg-6", value));

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();

        final Map<String, String> metadata = result.get().getMetadata();
        assertThat(metadata).containsEntry("queueName", QUEUE_NAME);
        assertThat(metadata).containsEntry("queueType", "KAFKA");
        assertThat(metadata).containsEntry("topic", TOPIC);
        assertThat(metadata).containsEntry("partition", "0");
        assertThat(metadata).containsEntry("offset", "0");
        assertThat(metadata).containsEntry("key", "fg-6");
        assertThat(metadata).containsEntry("state", "in-flight");
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
            assertThat(result.get().getMessage().fileGroupId()).isEqualTo("fg-multi-" + i);
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
    private TopicPartition simulateRebalance() {
        final TopicPartition tp = new TopicPartition(TOPIC, 0);
        mockConsumer.rebalance(Collections.singletonList(tp));
        mockConsumer.updateBeginningOffsets(Map.of(tp, 0L));
        return tp;
    }

    private FileGroupQueueMessage createMessage(final String fileGroupId) {
        return FileGroupQueueMessage.create(
                QUEUE_NAME,
                fileGroupId,
                testLocation(),
                "receive",
                "test-node",
                null,
                Map.of());
    }

    private static FileStoreLocation testLocation() {
        return FileStoreLocation.localFileSystem("testStore", Path.of("/tmp/test/store/0000000001"));
    }
}
