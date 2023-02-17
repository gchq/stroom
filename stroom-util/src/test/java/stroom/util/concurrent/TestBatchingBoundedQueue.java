package stroom.util.concurrent;

import stroom.test.common.TestUtil;
import stroom.util.concurrent.BatchingBoundedQueue.BatchConsumer;
import stroom.util.exception.ThrowingRunnable;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.thread.CustomThreadFactory;

import ch.qos.logback.classic.Level;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.util.Util;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class TestBatchingBoundedQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestBatchingBoundedQueue.class);

    private final ThreadFactory threadFactory = new CustomThreadFactory("BatchingQueue");
    private final WaitStrategy waitStrategy = new BlockingWaitStrategy();

    @Test
    void doNothing() {
        // Spin it up then shut down
        final BatchingBoundedQueue<AtomicLong> batchingQueue = new BatchingBoundedQueue<>(
                threadFactory,
                waitStrategy,
                1,
                10,
                Duration.ofSeconds(5),
                (batch, batchSize) -> {
                });

        batchingQueue.shutdown();
    }

    @Test
    void put() {
        final List<List<AtomicInteger>> batches = new ArrayList<>();

        final BatchingBoundedQueue<AtomicInteger> batchingQueue = new BatchingBoundedQueue<>(
                threadFactory,
                waitStrategy,
                1,
                10,
                Duration.ofSeconds(5),
                (batch, batchSize) -> {
                    final List<AtomicInteger> items = new ArrayList<>();
                    batch.iterator().forEachRemaining(items::add);
                    batches.add(items);
                });

        for (int i = 0; i < 25; i++) {
            batchingQueue.put(new AtomicInteger(i));
        }

        batchingQueue.shutdown();

        assertThat(batches)
                .hasSize(3);

        // two full, one partial
        assertThat(batches.get(0))
                .hasSize(10);
        assertThat(batches.get(1))
                .hasSize(10);
        assertThat(batches.get(2))
                .hasSize(5);

        assertThat(batches.get(0))
                .extracting(AtomicInteger::get)
                .contains(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(batches.get(1))
                .extracting(AtomicInteger::get)
                .contains(10, 11, 12, 13, 14, 15, 16, 17, 18, 19);
        assertThat(batches.get(2))
                .extracting(AtomicInteger::get)
                .contains(20, 21, 22, 23, 24);
    }

    @Test
    void put_threeConsumers() {
        final List<List<AtomicInteger>> batches = new ArrayList<>();

        final int requiredBatchSize = 2;
        final BatchingBoundedQueue<AtomicInteger> batchingQueue = new BatchingBoundedQueue<>(
                threadFactory,
                waitStrategy,
                3,
                requiredBatchSize,
                Duration.ofSeconds(1),
                (batch, batchSize) -> {
                    final List<AtomicInteger> items = new ArrayList<>();
                    batch.iterator().forEachRemaining(items::add);
                    batches.add(items);
                });

        for (int i = 0; i < 6; i++) {
            batchingQueue.put(new AtomicInteger(i));
        }
        batchingQueue.shutdown();

        assertThat(batches)
                .hasSize(3);

        // two full, one partial
        assertThat(batches.get(0))
                .hasSize(requiredBatchSize);
        assertThat(batches.get(1))
                .hasSize(requiredBatchSize);
        assertThat(batches.get(2))
                .hasSize(requiredBatchSize);

        assertThat(batches.stream()
                .map(list -> list.stream()
                        .map(AtomicInteger::get)
                        .collect(Collectors.toList())))
                .containsExactlyInAnyOrder(
                        List.of(0, 1),
                        List.of(2, 3),
                        List.of(4, 5));
    }

    @Test
    void put_highVolume() {
        TestUtil.withTemporaryLogLevel(Level.INFO, BatchingBoundedQueue.class, () -> {
            final List<List<AtomicInteger>> batches = new ArrayList<>();

            final LongAdder itemCounter = new LongAdder();
            final BatchConsumer<AtomicInteger> batchConsumer = (batch, batchSize) -> {
                itemCounter.add(batchSize);
                batches.add(StreamSupport.stream(batch.spliterator(), false).collect(Collectors.toList()));
            };

            final int producersCount = 10;
            final int consumersCount = 10;
            final int itemsPerProducer = 100_000;

            final int batchSize = 500;
            final BatchingBoundedQueue<AtomicInteger> batchingQueue = new BatchingBoundedQueue<>(
                    threadFactory,
                    waitStrategy,
                    consumersCount,
                    batchSize,
                    Duration.ofSeconds(1),
                    batchConsumer);

            final ExecutorService producerExecutor = Executors.newFixedThreadPool(producersCount);

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final CompletableFuture[] futures = IntStream.range(0, producersCount)
                    .boxed()
                    .map(i -> {
                        return CompletableFuture.runAsync(() -> {
                            ThrowingRunnable.unchecked(countDownLatch::await).run();
                            LOGGER.info("Starting producer: {}", i);

                            for (int j = 0; j < itemsPerProducer; j++) {
                                batchingQueue.put(new AtomicInteger((1_000_000 * i) + j));
                            }

                            LOGGER.info("Finished producer: {}", i);
                        }, producerExecutor);
                    })
                    .toArray(CompletableFuture[]::new);

            LOGGER.info("Counting down latch");
            countDownLatch.countDown();

            ThrowingRunnable.unchecked(() ->
                    CompletableFuture.allOf(futures).get()).run();

            LOGGER.info("Shutting down queue");
            batchingQueue.shutdown();

            LOGGER.info("batchCount: {}, itemCounter: {}", batches.size(), itemCounter.sum());

            assertThat(itemCounter.sum())
                    .isEqualTo(itemsPerProducer * producersCount);
        });
    }

    @Test
    void putOneThenWait() {
        final List<List<AtomicInteger>> batches = new ArrayList<>();

        final BatchingBoundedQueue<AtomicInteger> batchingQueue = new BatchingBoundedQueue<>(
                threadFactory,
                waitStrategy,
                1,
                10,
                Duration.ofSeconds(1),
                (batch, batchSize) -> {
                    final List<AtomicInteger> items = new ArrayList<>();
                    batch.iterator().forEachRemaining(items::add);
                    batches.add(items);
                });

        batchingQueue.put(new AtomicInteger(1));

        ThreadUtil.sleepIgnoringInterrupts(3_000);

        // batch has expired so we get a partial batch
        assertThat(batches)
                .hasSize(1);
        assertThat(batches.get(0))
                .hasSize(1);

        batchingQueue.shutdown();
    }

    @Test
    void put_oneThenShutdown() {
        final List<List<AtomicInteger>> batches = new ArrayList<>();

        final BatchingBoundedQueue<AtomicInteger> batchingQueue = new BatchingBoundedQueue<>(
                threadFactory,
                waitStrategy,
                1,
                10,
                Duration.ofSeconds(1),
                (batch, batchSize) -> {
                    final List<AtomicInteger> items = new ArrayList<>();
                    batch.iterator().forEachRemaining(items::add);
                    batches.add(items);
                });

        batchingQueue.put(new AtomicInteger(1));

        batchingQueue.shutdown();

        // shutdown so we get a partial batch
        assertThat(batches)
                .hasSize(1);
        assertThat(batches.get(0))
                .hasSize(1);
    }

    @Disabled // Can't really assert the blocking on the producer side
    @Test
    void putManyWithSlowConsumer() {
        final List<List<AtomicInteger>> batches = new ArrayList<>();

        final BatchingBoundedQueue<AtomicInteger> batchingQueue = new BatchingBoundedQueue<>(
                threadFactory,
                waitStrategy,
                1,
                10,
                Duration.ofSeconds(1),
                (batch, batchSize) -> {
                    ThreadUtil.sleepIgnoringInterrupts(1_000);
                    final List<AtomicInteger> items = new ArrayList<>();
                    batch.iterator().forEachRemaining(items::add);
                    batches.add(items);
                });

        final int itemCount = 55;

        // Puts only blocked if the ring fills up
        assertThat(itemCount)
                .isGreaterThan(batchingQueue.getRingSize());

        LOGGER.info("Producer wait times: {}", IntStream.range(0, itemCount)
                .mapToLong(i -> {
                    return DurationTimer.measure(() -> batchingQueue.put(new AtomicInteger(i))).toMillis();
                })
                .summaryStatistics());

        batchingQueue.shutdown();

        // batch has expired so we get a partial batch
        assertThat(batches)
                .hasSize(6);
        assertThat(batches)
                .extracting(List::size)
                .contains(10, 10, 10, 10, 10, 5);
    }

    @Test
    void offer() {
    }

    @Test
    void nearestPowerOfTwo() {
    }

    @Disabled // Just verifying behaviour of util function
    @TestFactory
    Stream<DynamicTest> testCeilingNextPowerOfTwo() {

        return TestUtil.buildDynamicTestStream()
                .withInputType(int.class)
                .withOutputType(int.class)
                .withTestFunction(testCase ->
                        Util.ceilingNextPowerOfTwo(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(1, 1)
                .addCase(2, 2)
                .addCase(3, 4)
                .addCase(4, 4)
                .addCase(5, 8)
                .addCase(6, 8)
                .addCase(7, 8)
                .addCase(8, 8)
                .addCase(1000, 1_024)
                .addCase(1025, 2_048)
                .addCase(100_000, 131_072)
                .addCase(1_000_000, 1_048_576)
                .addCase(10_000_000, 16_777_216)
                .build();
    }

}
