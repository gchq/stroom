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

import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.codahale.metrics.health.HealthCheck;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

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
 * <h2>Threading</h2>
 * <p>
 * A stage may run several consumer threads, so this queue gives each calling
 * thread <strong>its own</strong> {@link Consumer}, created on that thread's
 * first call to {@link #next()} and joined to the shared consumer group. Kafka
 * then distributes the topic's partitions across those consumers, which is the
 * intended way to consume a topic in parallel.
 * </p>
 * <p>
 * A single shared consumer would be wrong twice over. {@link KafkaConsumer} is
 * not thread-safe and throws {@code ConcurrentModificationException} when two
 * threads enter it; and even with a lock, committing {@code offset + 1} from
 * whichever thread finished first would acknowledge earlier offsets still being
 * processed by other threads, losing those records on restart.
 * </p>
 * <p>
 * The {@link Producer} is shared - {@link KafkaProducer} is thread-safe and is
 * designed to be shared across threads.
 * </p>
 * <p>
 * Parallelism is bounded by the topic's partition count: consumers in a group
 * beyond the number of partitions are assigned nothing and sit idle.
 * </p>
 */
public class KafkaFileGroupQueue implements FileGroupQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KafkaFileGroupQueue.class);

    static final String DEFAULT_CONSUMER_GROUP_PREFIX = "stroom-proxy-";
    static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofMillis(100);

    /**
     * Consumer properties whose values this implementation depends on. Setting them
     * under {@code consumer:} is rejected by validation rather than quietly ignored,
     * because each one breaks the queue in a way that is hard to observe:
     * {@code max.poll.records} above 1 silently skips records, auto-commit
     * acknowledges unprocessed records, and the wrong deserialiser corrupts payloads.
     */
    public static final Set<String> RESERVED_CONSUMER_PROPERTIES = Set.of(
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);

    /**
     * Producer properties whose values this implementation depends on. Weakening
     * {@code acks} would let a publish report success before the record is durably
     * replicated, breaking the pipeline's no-data-loss guarantee at the point where
     * the ownership-transfer contract assumes the message is safe.
     */
    public static final Set<String> RESERVED_PRODUCER_PROPERTIES = Set.of(
            ProducerConfig.ACKS_CONFIG,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);

    private final String name;
    private final String topic;
    private final String bootstrapServers;
    private final Producer<String, byte[]> producer;
    private final FileGroupQueueMessageCodec codec;

    /**
     * Creates a consumer already subscribed to the topic. Invoked once per
     * consuming thread.
     */
    private final Supplier<Consumer<String, byte[]>> consumerFactory;

    /**
     * This thread's consumer. Never shared with another thread.
     */
    private final ThreadLocal<Consumer<String, byte[]>> threadConsumer = new ThreadLocal<>();

    /**
     * Every consumer handed out, so they can all be closed.
     */
    private final List<Consumer<String, byte[]>> consumers = new CopyOnWriteArrayList<>();

    private final AtomicBoolean closed = new AtomicBoolean();

    // Lazy AdminClient for health checks — created on first healthCheck() call.
    private volatile AdminClient adminClient;

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
                requireNonBlank(definition.getBootstrapServers(), "definition.bootstrapServers"),
                createProducer(definition),
                () -> createConsumer(name, definition),
                codec);
    }

    /**
     * Test-friendly constructor accepting a single pre-built consumer.
     * <p>
     * The same instance is handed to every thread, so this is only appropriate
     * for single-threaded tests. Use the {@link Supplier} form to exercise
     * multi-threaded consumption.
     * </p>
     */
    KafkaFileGroupQueue(final String name,
                        final String topic,
                        final String bootstrapServers,
                        final Producer<String, byte[]> producer,
                        final Consumer<String, byte[]> consumer,
                        final FileGroupQueueMessageCodec codec) {
        this(name,
                topic,
                bootstrapServers,
                producer,
                () -> Objects.requireNonNull(consumer, "consumer"),
                codec);
    }

    /**
     * Test-friendly constructor accepting a consumer factory, mirroring
     * production behaviour of one consumer per consuming thread.
     */
    KafkaFileGroupQueue(final String name,
                        final String topic,
                        final String bootstrapServers,
                        final Producer<String, byte[]> producer,
                        final Supplier<Consumer<String, byte[]>> consumerFactory,
                        final FileGroupQueueMessageCodec codec) {
        this.name = requireNonBlank(name, "name");
        this.topic = requireNonBlank(topic, "topic");
        this.bootstrapServers = bootstrapServers != null ? bootstrapServers : "";
        this.producer = Objects.requireNonNull(producer, "producer");
        this.consumerFactory = Objects.requireNonNull(consumerFactory, "consumerFactory");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * @return This thread's consumer, creating and subscribing it on first use.
     */
    private Consumer<String, byte[]> consumerForCurrentThread() throws IOException {
        Consumer<String, byte[]> threadsConsumer = threadConsumer.get();
        if (threadsConsumer == null) {
            if (closed.get()) {
                throw new IOException("Kafka queue '" + name + "' is closed");
            }

            threadsConsumer = Objects.requireNonNull(
                    consumerFactory.get(), "consumerFactory returned null");
            threadsConsumer.subscribe(Collections.singletonList(topic));

            consumers.add(threadsConsumer);
            threadConsumer.set(threadsConsumer);

            LOGGER.debug(() -> LogUtil.message(
                    "Created Kafka consumer for thread '{}' on queue '{}' (topic {})",
                    Thread.currentThread().getName(), name, topic));
        }
        return threadsConsumer;
    }

    /**
     * @return The number of consumers created so far - one per consuming thread.
     */
    int getConsumerCount() {
        return consumers.size();
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
        final Consumer<String, byte[]> threadsConsumer = consumerForCurrentThread();
        final ConsumerRecords<String, byte[]> records = threadsConsumer.poll(DEFAULT_POLL_TIMEOUT);

        if (records.isEmpty()) {
            return Optional.empty();
        }

        // Process one record at a time to match the FileGroupQueue contract.
        final ConsumerRecord<String, byte[]> record = records.iterator().next();
        final FileGroupQueueMessage message = codec.fromBytes(record.value());

        // The item carries the consumer that produced it so that acknowledge()
        // commits on the consumer that actually owns the partition.
        return Optional.of(new KafkaFileGroupQueueItem(
                threadsConsumer,
                record,
                message));
    }

    @Override
    public void close() throws IOException {
        closed.set(true);
        try {
            final AdminClient ac = adminClient;
            if (ac != null) {
                ac.close(Duration.ofSeconds(5));
            }
        } finally {
            try {
                producer.close();
            } finally {
                closeConsumers();
            }
        }
    }

    private void closeConsumers() {
        // wakeup() is the only method on a Kafka consumer that is safe to call
        // from another thread. Closing normally happens after the stage runner
        // has joined its threads, but if a poll is still in flight this unblocks
        // it rather than letting close hang.
        for (final Consumer<String, byte[]> c : consumers) {
            try {
                c.wakeup();
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message(
                        "Error waking Kafka consumer on queue '{}': {}", name, e.getMessage()));
            }
        }

        for (final Consumer<String, byte[]> c : consumers) {
            try {
                c.close();
            } catch (final RuntimeException e) {
                LOGGER.warn(() -> LogUtil.message(
                        "Error closing Kafka consumer on queue '{}': {}", name, e.getMessage()));
            }
        }
        consumers.clear();
    }

    @Override
    public HealthCheck.Result healthCheck() {
        try {
            final AdminClient ac = getOrCreateAdminClient();
            final Map<String, TopicDescription> result = ac.describeTopics(
                            Collections.singletonList(topic))
                    .allTopicNames()
                    .get(5, TimeUnit.SECONDS);

            final TopicDescription desc = result.get(topic);
            final int partitions = desc != null
                    ? desc.partitions().size()
                    : 0;

            return HealthCheck.Result.builder()
                    .healthy()
                    .withDetail("topic", topic)
                    .withDetail("partitions", partitions)
                    .build();

        } catch (final TimeoutException e) {
            return HealthCheck.Result.builder()
                    .unhealthy()
                    .withMessage("Kafka health check timed out for topic '%s'", topic)
                    .build();
        } catch (final ExecutionException e) {
            return HealthCheck.Result.builder()
                    .unhealthy()
                    .withMessage("Kafka health check failed for topic '%s': %s",
                            topic, e.getCause() != null ? e.getCause().getMessage() : e.getMessage())
                    .build();
        } catch (final Exception e) {
            return HealthCheck.Result.unhealthy(e);
        }
    }

    private AdminClient getOrCreateAdminClient() {
        AdminClient ac = adminClient;
        if (ac == null) {
            synchronized (this) {
                ac = adminClient;
                if (ac == null) {
                    final Properties props = new Properties();
                    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
                    ac = AdminClient.create(props);
                    adminClient = ac;
                }
            }
        }
        return ac;
    }

    private static KafkaProducer<String, byte[]> createProducer(final QueueDefinition definition) {
        return new KafkaProducer<>(buildProducerProperties(definition));
    }

    static Properties buildProducerProperties(final QueueDefinition definition) {
        final Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                requireNonBlank(definition.getBootstrapServers(), "definition.bootstrapServers"));

        // User overrides go on first so that the reserved properties below always win.
        // Validation rejects an attempt to set a reserved property, so reaching here
        // with one is not expected - this ordering just means the resulting client is
        // still correct if validation is ever bypassed.
        if (definition.getProducerConfig() != null) {
            props.putAll(definition.getProducerConfig());
        }

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        return props;
    }

    private static KafkaConsumer<String, byte[]> createConsumer(final String queueName,
                                                                final QueueDefinition definition) {
        return new KafkaConsumer<>(buildConsumerProperties(queueName, definition));
    }

    static Properties buildConsumerProperties(final String queueName,
                                              final QueueDefinition definition) {
        final Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                requireNonBlank(definition.getBootstrapServers(), "definition.bootstrapServers"));
        // Defaults that callers may legitimately override.
        props.put(ConsumerConfig.GROUP_ID_CONFIG, DEFAULT_CONSUMER_GROUP_PREFIX + queueName);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // User overrides go on next so that the reserved properties below always win.
        // Validation rejects an attempt to set a reserved property, so reaching here
        // with one is not expected - this ordering just means the resulting client is
        // still correct if validation is ever bypassed.
        if (definition.getConsumerConfig() != null) {
            props.putAll(definition.getConsumerConfig());
        }

        // Reserved: the code depends on these values.
        //   max.poll.records   next() returns a single record and discards the rest of
        //                      the batch, so anything above 1 silently skips records.
        //   enable.auto.commit acknowledgement is explicit; auto-commit would commit
        //                      offsets for records that have not been processed.
        //   deserializers      the codec requires a String key and byte[] value.
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1");

        return props;
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

        /**
         * The consumer this record came from. Offsets must be committed on the
         * consumer that owns the partition, not on some other thread's.
         */
        private final Consumer<String, byte[]> owningConsumer;
        private final ConsumerRecord<String, byte[]> record;
        private final FileGroupQueueMessage message;
        private boolean completed;

        private KafkaFileGroupQueueItem(final Consumer<String, byte[]> owningConsumer,
                                        final ConsumerRecord<String, byte[]> record,
                                        final FileGroupQueueMessage message) {
            this.owningConsumer = Objects.requireNonNull(owningConsumer, "owningConsumer");
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
        public void acknowledge() throws IOException {
            if (completed) {
                return;
            }

            final TopicPartition tp = new TopicPartition(record.topic(), record.partition());
            final OffsetAndMetadata offsetMeta = new OffsetAndMetadata(record.offset() + 1);

            try {
                owningConsumer.commitSync(Map.of(tp, offsetMeta));
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
