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

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Kafka-backed implementation of {@link FileGroupQueue}.
 * <p>
 * Each logical queue maps to a Kafka topic. Messages are serialised as JSON
 * bytes using the shared {@link FileGroupQueueMessageCodec}. The Kafka record
 * key is set to {@link FileGroupQueueMessage#fileGroupId()} to provide
 * partition affinity for related file groups.
 * </p>
 * <p>
 * This implementation uses manual offset commit ({@code enable.auto.commit=false})
 * so that acknowledgement is explicit. {@link KafkaFileGroupQueueItem#acknowledge()}
 * commits the offset for the consumed partition. {@link KafkaFileGroupQueueItem#fail(Throwable)}
 * does not commit, causing the message to be redelivered on the next poll
 * (at-least-once semantics).
 * </p>
 */
public class KafkaFileGroupQueue implements FileGroupQueue {

    static final String DEFAULT_CONSUMER_GROUP_PREFIX = "stroom-proxy-";
    static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofMillis(100);

    private final String name;
    private final String topic;
    private final Producer<String, byte[]> producer;
    private final Consumer<String, byte[]> consumer;
    private final FileGroupQueueMessageCodec codec;

    /**
     * Create a Kafka queue from a {@link QueueDefinition}.
     *
     * @param name       The logical queue name.
     * @param definition The queue definition containing Kafka config.
     * @param codec      The message codec for JSON serialisation.
     */
    public KafkaFileGroupQueue(final String name,
                               final QueueDefinition definition,
                               final FileGroupQueueMessageCodec codec) {
        this(
                name,
                requireNonBlank(definition.getTopic(), "definition.topic"),
                createProducer(definition),
                createConsumer(name, definition),
                codec);
    }

    /**
     * Test-friendly constructor that accepts pre-built producer and consumer.
     */
    KafkaFileGroupQueue(final String name,
                        final String topic,
                        final Producer<String, byte[]> producer,
                        final Consumer<String, byte[]> consumer,
                        final FileGroupQueueMessageCodec codec) {
        this.name = requireNonBlank(name, "name");
        this.topic = requireNonBlank(topic, "topic");
        this.producer = Objects.requireNonNull(producer, "producer");
        this.consumer = Objects.requireNonNull(consumer, "consumer");
        this.codec = Objects.requireNonNull(codec, "codec");

        consumer.subscribe(Collections.singletonList(this.topic));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public QueueType getType() {
        return QueueType.KAFKA;
    }

    @Override
    public void publish(final FileGroupQueueMessage message) throws IOException {
        Objects.requireNonNull(message, "message");

        if (!name.equals(message.queueName())) {
            throw new IllegalArgumentException("Message queueName '" + message.queueName()
                                               + "' does not match queue '" + name + "'");
        }

        final byte[] value = codec.toBytes(message);
        final ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                topic,
                message.fileGroupId(),
                value);

        try {
            producer.send(record).get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while publishing to Kafka topic " + topic, e);
        } catch (final ExecutionException e) {
            throw new IOException("Failed to publish to Kafka topic " + topic, e.getCause());
        }
    }

    @Override
    public Optional<FileGroupQueueItem> next() throws IOException {
        final ConsumerRecords<String, byte[]> records = consumer.poll(DEFAULT_POLL_TIMEOUT);

        if (records.isEmpty()) {
            return Optional.empty();
        }

        // Process one record at a time to match the FileGroupQueue contract.
        final ConsumerRecord<String, byte[]> record = records.iterator().next();
        final FileGroupQueueMessage message = codec.fromBytes(record.value());

        return Optional.of(new KafkaFileGroupQueueItem(
                record,
                message));
    }

    @Override
    public void close() throws IOException {
        try {
            producer.close();
        } finally {
            consumer.close();
        }
    }

    private static KafkaProducer<String, byte[]> createProducer(final QueueDefinition definition) {
        final Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                requireNonBlank(definition.getBootstrapServers(), "definition.bootstrapServers"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // Apply user-supplied overrides.
        if (definition.getProducerConfig() != null) {
            props.putAll(definition.getProducerConfig());
        }

        return new KafkaProducer<>(props);
    }

    private static KafkaConsumer<String, byte[]> createConsumer(final String queueName,
                                                                final QueueDefinition definition) {
        final Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                requireNonBlank(definition.getBootstrapServers(), "definition.bootstrapServers"));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, DEFAULT_CONSUMER_GROUP_PREFIX + queueName);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Apply user-supplied overrides (may override group.id etc.).
        if (definition.getConsumerConfig() != null) {
            props.putAll(definition.getConsumerConfig());
        }

        return new KafkaConsumer<>(props);
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    /**
     * A leased queue item backed by a Kafka {@link ConsumerRecord}.
     */
    private final class KafkaFileGroupQueueItem implements FileGroupQueueItem {

        private final ConsumerRecord<String, byte[]> record;
        private final FileGroupQueueMessage message;
        private boolean completed;

        private KafkaFileGroupQueueItem(final ConsumerRecord<String, byte[]> record,
                                        final FileGroupQueueMessage message) {
            this.record = Objects.requireNonNull(record, "record");
            this.message = Objects.requireNonNull(message, "message");
        }

        @Override
        public String getId() {
            return record.topic() + "-" + record.partition() + "-" + record.offset();
        }

        @Override
        public FileGroupQueueMessage getMessage() {
            return message;
        }

        @Override
        public Map<String, String> getMetadata() {
            final Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("queueName", name);
            metadata.put("queueType", QueueType.KAFKA.name());
            metadata.put("topic", record.topic());
            metadata.put("partition", Integer.toString(record.partition()));
            metadata.put("offset", Long.toString(record.offset()));
            metadata.put("key", record.key());
            metadata.put("state", completed ? "completed" : "in-flight");
            return Map.copyOf(metadata);
        }

        @Override
        public void acknowledge() throws IOException {
            if (completed) {
                return;
            }

            final TopicPartition tp = new TopicPartition(record.topic(), record.partition());
            final OffsetAndMetadata offsetMeta = new OffsetAndMetadata(record.offset() + 1);

            try {
                consumer.commitSync(Map.of(tp, offsetMeta));
            } catch (final Exception e) {
                throw new IOException("Failed to commit Kafka offset for " + getId(), e);
            }

            completed = true;
        }

        @Override
        public void fail(final Throwable error) {
            // Do not commit the offset. The message will be redelivered
            // on the next poll (at-least-once semantics).
            completed = true;
        }

        @Override
        public void close() {
            // No per-item resources to release.
        }
    }
}
