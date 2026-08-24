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
import stroom.proxy.app.pipeline.store.FileStoreLocation;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Kafka-backed stage may run several consumer threads, so each must get its own
 * {@link org.apache.kafka.clients.consumer.Consumer}.
 * <p>
 * A single shared consumer was previously used for all threads.
 * {@code KafkaConsumer} is not thread-safe and throws
 * {@code ConcurrentModificationException} when entered concurrently, so any stage
 * configured with {@code consumerThreads > 1} failed. Sharing is also unsafe for
 * offsets: committing {@code offset + 1} from whichever thread finished first
 * acknowledges earlier offsets that other threads are still processing, losing
 * those records on restart.
 * </p>
 */
class TestKafkaFileGroupQueueConcurrency {

    private static final String TOPIC = "test-topic";
    private static final String QUEUE_NAME = "forwardingInput";

    /**
     * Fails if more than one thread is inside a consumer method at once, in the
     * same way the real {@code KafkaConsumer} does.
     */
    private static final class ConcurrencyDetectingConsumer extends MockConsumer<String, byte[]> {

        private final AtomicInteger threadsInside = new AtomicInteger();
        private final Set<String> callingThreads = ConcurrentHashMap.newKeySet();
        private final List<String> violations = new CopyOnWriteArrayList<>();

        private final int recordCount;

        private ConcurrencyDetectingConsumer(final int recordCount) {
            super(OffsetResetStrategy.EARLIEST);
            this.recordCount = recordCount;
        }

        /**
         * The queue subscribes each consumer as it hands it to a thread. MockConsumer
         * only accepts a simulated rebalance once subscribed, so seed the assignment
         * and records here rather than at construction.
         */
        @Override
        public void subscribe(final java.util.Collection<String> topics) {
            super.subscribe(topics);
            final TopicPartition tp = new TopicPartition(TOPIC, 0);
            rebalance(List.of(tp));
            updateBeginningOffsets(Map.of(tp, 0L));
            final FileGroupQueueMessageCodec codec = new FileGroupQueueMessageCodec();
            for (int i = 0; i < recordCount; i++) {
                try {
                    addRecord(new ConsumerRecord<>(
                            TOPIC, 0, i, "fg-" + i, codec.toBytes(message("fg-" + i))));
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        @Override
        public ConsumerRecords<String, byte[]> poll(final Duration timeout) {
            callingThreads.add(Thread.currentThread().getName());
            if (threadsInside.incrementAndGet() > 1) {
                violations.add("concurrent poll() by " + Thread.currentThread().getName());
            }
            try {
                // Widen the window so genuine concurrent entry is detected reliably.
                Thread.sleep(5);
                return super.poll(timeout);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } finally {
                threadsInside.decrementAndGet();
            }
        }
    }

    @Test
    void testEachThreadGetsItsOwnConsumer() throws Exception {
        final MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());

        final List<ConcurrencyDetectingConsumer> created = new CopyOnWriteArrayList<>();
        final FileGroupQueueMessageCodec codec = new FileGroupQueueMessageCodec();

        final KafkaFileGroupQueue queue = new KafkaFileGroupQueue(
                QUEUE_NAME,
                TOPIC,
                "localhost:9092",
                producer,
                () -> {
                    final ConcurrencyDetectingConsumer c = new ConcurrencyDetectingConsumer(20);
                    created.add(c);
                    return c;
                },
                codec);

        final int threads = 4;
        final CountDownLatch start = new CountDownLatch(1);
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < 10; i++) {
                            final Optional<FileGroupQueueItem> item = queue.next();
                            if (item.isPresent()) {
                                item.get().acknowledge();
                            }
                        }
                    } catch (final Throwable e) {
                        errors.add(e);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(errors).isEmpty();

        // One consumer per consuming thread, and no consumer touched by two threads.
        assertThat(created).hasSize(threads);
        assertThat(queue.getConsumerCount()).isEqualTo(threads);
        assertThat(created)
                .allSatisfy(c -> assertThat(c.callingThreads)
                        .as("each consumer is used by exactly one thread")
                        .hasSize(1));
        assertThat(created).allSatisfy(c -> assertThat(c.violations).isEmpty());

        // Distinct threads, i.e. the consumers really were handed out per thread.
        assertThat(created.stream().flatMap(c -> c.callingThreads.stream()).distinct().count())
                .isEqualTo(threads);
    }

    @Test
    void testCloseClosesEveryThreadsConsumer() throws Exception {
        final MockProducer<String, byte[]> producer =
                new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        final List<ConcurrencyDetectingConsumer> created = new CopyOnWriteArrayList<>();
        final FileGroupQueueMessageCodec codec = new FileGroupQueueMessageCodec();

        final KafkaFileGroupQueue queue = new KafkaFileGroupQueue(
                QUEUE_NAME, TOPIC, "localhost:9092", producer,
                () -> {
                    final ConcurrencyDetectingConsumer c = new ConcurrencyDetectingConsumer(0);
                    created.add(c);
                    return c;
                },
                codec);

        final int threads = 3;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        queue.next();
                    } catch (final Exception e) {
                        throw new IllegalStateException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(created).hasSize(threads);

        queue.close();

        assertThat(created).allSatisfy(c -> assertThat(c.closed()).isTrue());
        assertThat(queue.getConsumerCount()).isZero();
    }

    private static FileGroupQueueMessage message(final String fileGroupId) {
        return FileGroupQueueMessage.create(
                QUEUE_NAME,
                fileGroupId,
                FileStoreLocation.localFileSystem("aggregateStore", Path.of("/tmp/store/0000000001")),
                "aggregate",
                "test-node",
                null,
                Map.of());
    }
}
