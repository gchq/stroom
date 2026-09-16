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
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * A {@link FileGroupQueue} on a Kafka topic. Shared mode only; the broker owns the claim.
 * <p>
 * Each consumer thread has its own {@link Consumer} in one group; Kafka assigns partitions across
 * them, and a rebalance is how another node takes over. Auto-commit is off. The partition key is the
 * message's aggregation key when it has one, so every part of a feed reaches one consuming node, and
 * its id otherwise.
 * </p>
 * <p>
 * A consumer may hold many records at once - the aggregate stage holds every input of an open
 * aggregate - and they complete in any order, so a completed record is not committed past on its
 * own. Each partition keeps a ledger of the records this consumer holds; completing one commits the
 * offset up to the first record still held, or past the highest completed when none is. A record is
 * therefore never committed past while an earlier one is still open, and a crash redelivers from the
 * oldest open record.
 * </p>
 * <p>
 * A partition is a sequence, so one record that cannot be processed must not block the ones behind
 * it. {@code fail()} re-publishes the record to the tail with the attempt counted in a header, then
 * completes the original. After {@code maxDeliveryAttempts}, and for a record that cannot be
 * decoded, the record goes to the dead-letter topic {@code <topic>.failed} instead, which is where a
 * human acts. {@code close()} without completing seeks back to the record, which also releases
 * every later record this consumer holds on that partition: a seek is positional. A record may only
 * be completed or released on the thread that polled it, which the Kafka consumer enforces itself.
 * </p>
 */
public class KafkaFileGroupQueue implements FileGroupQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KafkaFileGroupQueue.class);

    static final String DELIVERY_ATTEMPT_HEADER = "queue.deliveryAttempts";
    static final String DEAD_LETTER_TOPIC_SUFFIX = ".failed";
    /**
     * The in-flight lease: how long a consumer may go without polling before Kafka takes its
     * partitions away. The client's own default is five minutes, far below the worst case for a
     * forward or aggregate stage. A claim held across a forward back-off wait must be shorter than
     * this, which the validator checks.
     */
    public static final int DEFAULT_MAX_POLL_INTERVAL_MS = 1_800_000;
    static final String DEFAULT_CONSUMER_GROUP_PREFIX = "stroom-proxy-";

    /**
     * Properties this implementation depends on. Setting them is rejected by validation rather than
     * quietly ignored: more than one record per poll skips records, auto-commit acknowledges
     * unprocessed records, and the wrong deserialiser corrupts payloads.
     */
    public static final Set<String> RESERVED_CONSUMER_PROPERTIES = Set.of(
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
    public static final Set<String> RESERVED_PRODUCER_PROPERTIES = Set.of(
            ProducerConfig.ACKS_CONFIG,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);

    private final String name;
    private final String topic;
    private final String deadLetterTopic;
    private final String bootstrapServers;
    private final Producer<String, byte[]> producer;
    private final FileGroupQueueMessageCodec codec;
    private final int maxDeliveryAttempts;
    private final Supplier<Consumer<String, byte[]>> consumerFactory;
    private final ThreadLocal<ThreadConsumer> threadConsumer = new ThreadLocal<>();
    private final List<Consumer<String, byte[]>> consumers = new CopyOnWriteArrayList<>();
    private final Object consumerLifecycleLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile AdminClient adminClient;

    public KafkaFileGroupQueue(final String name,
                               final QueueDefinition definition,
                               final FileGroupQueueMessageCodec codec) throws IOException {
        this(name,
                requireNonBlank(definition.getTopic(), "definition.topic"),
                requireNonBlank(definition.getBootstrapServers(), "definition.bootstrapServers"),
                createProducer(definition),
                () -> createConsumer(name, definition),
                codec,
                definition.getMaxDeliveryAttempts());
        try {
            ensureDeadLetterTopic();
        } catch (final IOException | RuntimeException e) {
            try {
                close();
            } catch (final IOException | RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    KafkaFileGroupQueue(final String name,
                        final String topic,
                        final String bootstrapServers,
                        final Producer<String, byte[]> producer,
                        final Consumer<String, byte[]> consumer,
                        final FileGroupQueueMessageCodec codec) {
        this(name, topic, bootstrapServers, producer, () -> Objects.requireNonNull(consumer, "consumer"), codec,
                QueueDefinition.DEFAULT_MAX_DELIVERY_ATTEMPTS);
    }

    KafkaFileGroupQueue(final String name,
                        final String topic,
                        final String bootstrapServers,
                        final Producer<String, byte[]> producer,
                        final Consumer<String, byte[]> consumer,
                        final FileGroupQueueMessageCodec codec,
                        final int maxDeliveryAttempts) {
        this(name, topic, bootstrapServers, producer, () -> Objects.requireNonNull(consumer, "consumer"), codec,
                maxDeliveryAttempts);
    }

    KafkaFileGroupQueue(final String name,
                        final String topic,
                        final String bootstrapServers,
                        final Producer<String, byte[]> producer,
                        final Supplier<Consumer<String, byte[]>> consumerFactory,
                        final FileGroupQueueMessageCodec codec) {
        this(name, topic, bootstrapServers, producer, consumerFactory, codec,
                QueueDefinition.DEFAULT_MAX_DELIVERY_ATTEMPTS);
    }

    KafkaFileGroupQueue(final String name,
                        final String topic,
                        final String bootstrapServers,
                        final Producer<String, byte[]> producer,
                        final Supplier<Consumer<String, byte[]>> consumerFactory,
                        final FileGroupQueueMessageCodec codec,
                        final int maxDeliveryAttempts) {
        this.name = requireNonBlank(name, "name");
        this.topic = requireNonBlank(topic, "topic");
        this.deadLetterTopic = this.topic + DEAD_LETTER_TOPIC_SUFFIX;
        this.bootstrapServers = bootstrapServers != null ? bootstrapServers : "";
        this.producer = Objects.requireNonNull(producer, "producer");
        this.consumerFactory = Objects.requireNonNull(consumerFactory, "consumerFactory");
        this.codec = Objects.requireNonNull(codec, "codec");
        if (maxDeliveryAttempts < 1) {
            throw new IllegalArgumentException("maxDeliveryAttempts must be >= 1, got " + maxDeliveryAttempts);
        }
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    /**
     * The dead-letter topic must exist before the first give-up needs it. Created if it is absent;
     * a proxy that can neither create it nor find it refuses to start, because a give-up with nowhere
     * to go would have to either redeliver for ever or drop the record.
     */
    private void ensureDeadLetterTopic() throws IOException {
        final AdminClient ac = getOrCreateAdminClient();
        try {
            ac.createTopics(List.of(new NewTopic(deadLetterTopic, Optional.empty(), Optional.empty())))
                    .all().get(10, TimeUnit.SECONDS);
            LOGGER.info(() -> LogUtil.message("Created dead-letter topic {} for queue '{}'", deadLetterTopic, name));
        } catch (final ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                verifyDeadLetterTopicExists(ac, e.getCause());
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted creating dead-letter topic " + deadLetterTopic, e);
        } catch (final TimeoutException e) {
            verifyDeadLetterTopicExists(ac, e);
        }
    }

    private void verifyDeadLetterTopicExists(final AdminClient ac, final Throwable createFailure) throws IOException {
        try {
            ac.describeTopics(List.of(deadLetterTopic)).allTopicNames().get(10, TimeUnit.SECONDS);
        } catch (final Exception e) {
            e.addSuppressed(createFailure);
            throw new IOException(LogUtil.message(
                    "Queue '{}' needs the dead-letter topic {} and could neither create it nor find it. Create it, "
                    + "or grant the proxy permission to.", name, deadLetterTopic), e);
        }
    }

    private ThreadConsumer consumerForCurrentThread() throws IOException {
        ThreadConsumer threadsConsumer = threadConsumer.get();
        if (threadsConsumer == null) {
            threadsConsumer = createAndRegisterConsumer();
            threadConsumer.set(threadsConsumer);
        }
        return threadsConsumer;
    }

    private ThreadConsumer createAndRegisterConsumer() throws IOException {
        synchronized (consumerLifecycleLock) {
            if (closed.get()) {
                throw new IOException("Kafka queue '" + name + "' is closed");
            }
            final Consumer<String, byte[]> created = Objects.requireNonNull(
                    consumerFactory.get(), "consumerFactory returned null");
            final ThreadConsumer threadsConsumer = new ThreadConsumer(created);
            try {
                created.subscribe(Collections.singletonList(topic), threadsConsumer);
                consumers.add(created);
            } catch (final RuntimeException e) {
                try {
                    created.close();
                } catch (final RuntimeException closeFailure) {
                    e.addSuppressed(closeFailure);
                }
                throw e;
            }
            return threadsConsumer;
        }
    }

    int getConsumerCount() {
        return consumers.size();
    }

    String getDeadLetterTopic() {
        return deadLetterTopic;
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
        send(new ProducerRecord<>(topic, message.partitionKey(), codec.toBytes(message)), topic);
    }

    private void send(final ProducerRecord<String, byte[]> record, final String target) throws IOException {
        try {
            producer.send(record).get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while publishing to Kafka topic " + target, e);
        } catch (final ExecutionException e) {
            throw new IOException("Failed to publish to Kafka topic " + target, e.getCause());
        }
    }

    /**
     * Kafka's poll is the wait: {@code maxWait} is how long the poll blocks for a record before
     * returning empty, so an idle consumer goes round its loop once per {@code maxWait} rather than
     * spinning. Zero returns whatever the client has already fetched.
     */
    @Override
    public Optional<FileGroupQueueItem> next(final Duration maxWait) throws IOException {
        final ThreadConsumer threadsConsumer = consumerForCurrentThread();
        final ConsumerRecords<String, byte[]> records = threadsConsumer.consumer.poll(maxWait);
        if (records.isEmpty()) {
            return Optional.empty();
        }
        final ConsumerRecord<String, byte[]> record = records.iterator().next();
        final Item item = threadsConsumer.claim(record);
        try {
            item.message = codec.fromBytes(record.value());
        } catch (final Exception e) {
            // Undecodable, so it can never be processed: dead-letter it and move on, or the
            // partition stops here.
            LOGGER.error(() -> LogUtil.message(
                    "Undecodable record on queue '{}' at {}-{} offset {}; sending it to {}",
                    name, record.topic(), record.partition(), record.offset(), deadLetterTopic), e);
            try {
                item.deadLetter();
            } catch (final IOException | RuntimeException deadLetterFailure) {
                // The poll has already moved this consumer past the record. Without this it would be
                // skipped until a restart or rebalance rather than tried again on the next poll.
                item.close();
                throw deadLetterFailure;
            }
            return Optional.empty();
        }
        return Optional.of(item);
    }

    /**
     * What one consumer thread holds: its consumer and, per partition, the records polled and not
     * yet completed. Touched only by the thread that owns it, which is also the thread Kafka runs
     * the rebalance callbacks on.
     */
    private final class ThreadConsumer implements ConsumerRebalanceListener {

        private final Consumer<String, byte[]> consumer;
        private final Map<TopicPartition, PartitionLedger> ledgers = new HashMap<>();

        private ThreadConsumer(final Consumer<String, byte[]> consumer) {
            this.consumer = consumer;
        }

        /**
         * A revoked partition's records belong to whoever is assigned it next, from the committed
         * position. This consumer's ledger for it is dropped, so every item still held on it is
         * stale: its completion commits nothing, and its release seeks nothing.
         */
        @Override
        public void onPartitionsRevoked(final Collection<TopicPartition> partitions) {
            for (final TopicPartition tp : partitions) {
                final PartitionLedger ledger = ledgers.remove(tp);
                if (ledger != null) {
                    if (!ledger.held.isEmpty()) {
                        LOGGER.warn(() -> LogUtil.message(
                                "Queue '{}' had {} revoked with {} record(s) still held; they will be delivered "
                                + "again by whichever consumer is assigned it", name, tp, ledger.held.size()));
                    }
                    ledger.drop();
                }
            }
        }

        @Override
        public void onPartitionsAssigned(final Collection<TopicPartition> partitions) {
            // Nothing to do: a ledger is created on the first record polled.
        }

        private Item claim(final ConsumerRecord<String, byte[]> record) {
            final TopicPartition tp = new TopicPartition(record.topic(), record.partition());
            final PartitionLedger ledger = ledgers.computeIfAbsent(tp, PartitionLedger::new);
            final Item item = new Item(ledger, record);
            ledger.claim(item);
            return item;
        }
    }

    /**
     * The records one consumer holds on one partition, in offset order, and what may be committed.
     * <p>
     * Records are polled in offset order, so every offset below the lowest held one is complete.
     * The committable position is therefore the lowest held offset, or one past the highest
     * completed offset when nothing is held. A release seeks the consumer back, which redelivers
     * every record at or after that offset, so those are dropped from the ledger and the highest
     * completed offset is pulled back below the seek: anything past it will be polled again.
     * </p>
     * <p>
     * An item that has been dropped by a release is stale: its record has been, or will be,
     * delivered again as a new item, and the stale one's completion must not touch the ledger.
     * </p>
     */
    private final class PartitionLedger {

        private final TopicPartition tp;
        private final NavigableMap<Long, Item> held = new TreeMap<>();
        private long highestCompleted = -1;
        /** The committable position, as far as this ledger knows; committing it again changes nothing. */
        private long committed = -1;
        /** Set when the partition is revoked: every item on this ledger is stale from then on. */
        private boolean dropped;

        private PartitionLedger(final TopicPartition tp) {
            this.tp = tp;
        }

        private void claim(final Item item) {
            if (held.isEmpty() && committed < 0) {
                // Everything before the first record polled is already committed or was never ours.
                committed = item.record.offset();
            }
            held.put(item.record.offset(), item);
        }

        private boolean holds(final Item item) {
            return !dropped && held.get(item.record.offset()) == item;
        }

        private void drop() {
            dropped = true;
            held.clear();
        }

        /** The item is done, one way or another: commit whatever that makes committable. */
        private void complete(final Item item) throws IOException {
            if (!holds(item)) {
                return;
            }
            held.remove(item.record.offset());
            highestCompleted = Math.max(highestCompleted, item.record.offset());
            final long committable = held.isEmpty()
                    ? highestCompleted + 1
                    : held.firstKey();
            if (committable > committed) {
                try {
                    item.owningConsumer().commitSync(Map.of(tp, new OffsetAndMetadata(committable)));
                } catch (final Exception e) {
                    throw new IOException("Failed to commit Kafka offset " + committable + " on " + tp, e);
                }
                committed = committable;
            }
        }

        /** The item is given back: seek to it, which redelivers it and everything held after it. */
        private void release(final Item item) {
            if (!holds(item)) {
                return;
            }
            final long offset = item.record.offset();
            try {
                item.owningConsumer().seek(tp, offset);
            } catch (final Exception e) {
                // The record stays held, so nothing is committed past it (complete() stops at the
                // first held offset), and it is delivered again from the committed position by
                // whichever consumer next polls the partition. The usual cause is that this consumer
                // no longer has the partition - max.poll.interval.ms went by during a long delivery
                // and the group reassigned it - in which case that has already happened.
                LOGGER.warn(() -> LogUtil.message(
                        "Queue '{}' could not seek back to {} offset {}: {}. The record is uncommitted and will be "
                        + "delivered again by whichever consumer next holds the partition; if this one still "
                        + "does, that is at its next rebalance or restart, and nothing after the record on the "
                        + "partition is committed until then.",
                        name, tp, offset, LogUtil.exceptionMessage(e)), e);
                return;
            }
            held.tailMap(offset, true).clear();
            highestCompleted = Math.min(highestCompleted, offset - 1);
        }
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
            final Map<String, TopicDescription> result = getOrCreateAdminClient()
                    .describeTopics(Collections.singletonList(topic))
                    .allTopicNames()
                    .get(5, TimeUnit.SECONDS);
            final TopicDescription desc = result.get(topic);
            return HealthCheck.Result.builder()
                    .healthy()
                    .withDetail("topic", topic)
                    .withDetail("partitions", desc != null ? desc.partitions().size() : 0)
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
        if (closed.get()) {
            throw new IllegalStateException("Kafka queue '" + name + "' is closed");
        }
        AdminClient ac = adminClient;
        if (ac == null) {
            synchronized (this) {
                if (closed.get()) {
                    throw new IllegalStateException("Kafka queue '" + name + "' is closed");
                }
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

    static Properties buildConsumerProperties(final String queueName, final QueueDefinition definition) {
        final Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                requireNonBlank(definition.getBootstrapServers(), "definition.bootstrapServers"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, DEFAULT_CONSUMER_GROUP_PREFIX + queueName);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, Integer.toString(DEFAULT_MAX_POLL_INTERVAL_MS));
        // Cooperative, not eager: a consumer holds records for minutes - every input of an open
        // aggregate - and a revoked partition drops its ledger, so with the eager protocol every
        // node joining the group redelivers every held record on every node. Cooperative rebalancing
        // revokes only the partitions that actually move. An operator may override it.
        props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, CooperativeStickyAssignor.class.getName());
        if (definition.getConsumerConfig() != null) {
            props.putAll(definition.getConsumerConfig());
        }
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1");
        return props;
    }

    private static String requireNonBlank(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }


    // --------------------------------------------------------------------------------


    private final class Item implements FileGroupQueueItem {

        private final PartitionLedger ledger;
        private final ConsumerRecord<String, byte[]> record;
        private FileGroupQueueMessage message;
        private boolean completed;

        private Item(final PartitionLedger ledger,
                     final ConsumerRecord<String, byte[]> record) {
            this.ledger = ledger;
            this.record = record;
        }

        private Consumer<String, byte[]> owningConsumer() {
            return threadConsumer.get().consumer;
        }

        /**
         * Publish the record to the dead-letter topic, then complete it. A crash between the two
         * delivers it again and dead-letters it twice, which is the accepted direction.
         */
        private void deadLetter() throws IOException {
            final ProducerRecord<String, byte[]> dead = new ProducerRecord<>(
                    deadLetterTopic, null, record.key(), record.value());
            record.headers().forEach(dead.headers()::add);
            send(dead, deadLetterTopic);
            ledger.complete(this);
            completed = true;
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
        public int getDeliveryAttempt() {
            final Header header = record.headers().lastHeader(DELIVERY_ATTEMPT_HEADER);
            if (header == null || header.value() == null) {
                return 1;
            }
            try {
                return Math.max(1, Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8).trim()));
            } catch (final NumberFormatException e) {
                return 1;
            }
        }

        @Override
        public void acknowledge() throws IOException {
            if (completed) {
                return;
            }
            ledger.complete(this);
            completed = true;
        }

        @Override
        public void fail(final Throwable error) throws IOException {
            if (completed) {
                return;
            }
            final int attempt = getDeliveryAttempt();
            if (attempt >= maxDeliveryAttempts) {
                LOGGER.error(() -> LogUtil.message(
                        "Queue '{}' giving up on message {} after {} delivery attempts; sending it to {}",
                        name, message.messageId(), attempt, deadLetterTopic), error);
                deadLetter();
                return;
            } else {
                final ProducerRecord<String, byte[]> retry = new ProducerRecord<>(
                        record.topic(), null, record.key(), record.value());
                record.headers().forEach(h -> {
                    if (!DELIVERY_ATTEMPT_HEADER.equals(h.key())) {
                        retry.headers().add(h);
                    }
                });
                retry.headers().add(DELIVERY_ATTEMPT_HEADER,
                        Integer.toString(attempt + 1).getBytes(StandardCharsets.UTF_8));
                // A republish that fails leaves the record held: releasing is the holder's call,
                // through close(), because on this backend a release is positional and lets go of
                // every later record the holder has too.
                send(retry, record.topic());
                ledger.complete(this);
            }
            completed = true;
        }

        @Override
        public void close() {
            if (!completed) {
                ledger.release(this);
            }
        }
    }
}
