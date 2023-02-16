package stroom.util.concurrent;

import stroom.test.common.TestUtil;
import stroom.util.thread.CustomThreadFactory;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestBatchingBoundedQueue {

    private final ThreadFactory threadFactory = new CustomThreadFactory("BatchingQueue");
    private final WaitStrategy waitStrategy = new BlockingWaitStrategy();

    @Test
    void doNothing() {
        // Spin it up then shut down
        final BatchingBoundedQueue<AtomicLong> batchingQueue = new BatchingBoundedQueue<>(
                threadFactory,
                waitStrategy,
                10,
                Duration.ofSeconds(5),
                iterable -> {
                });

        batchingQueue.shutdown();
    }

    @Test
    void put() {
        final List<List<AtomicInteger>> batches = new ArrayList<>();

        final BatchingBoundedQueue<AtomicInteger> batchingQueue = new BatchingBoundedQueue<>(
                threadFactory,
                waitStrategy,
                10,
                Duration.ofSeconds(5),
                iterable -> {
                    final List<AtomicInteger> items = new ArrayList<>();
                    iterable.iterator().forEachRemaining(items::add);
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
    void putOneThenWait() {
        final List<List<AtomicInteger>> batches = new ArrayList<>();

        final BatchingBoundedQueue<AtomicInteger> batchingQueue = new BatchingBoundedQueue<>(
                threadFactory,
                waitStrategy,
                10,
                Duration.ofSeconds(1),
                iterable -> {
                    final List<AtomicInteger> items = new ArrayList<>();
                    iterable.iterator().forEachRemaining(items::add);
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
    void putOneThenShutdown() {
        final List<List<AtomicInteger>> batches = new ArrayList<>();

        final BatchingBoundedQueue<AtomicInteger> batchingQueue = new BatchingBoundedQueue<>(
                threadFactory,
                waitStrategy,
                10,
                Duration.ofSeconds(1),
                iterable -> {
                    final List<AtomicInteger> items = new ArrayList<>();
                    iterable.iterator().forEachRemaining(items::add);
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

    @Test
    void putManyWithSlowConsumer() {
        final List<List<AtomicInteger>> batches = new ArrayList<>();

        final BatchingBoundedQueue<AtomicInteger> batchingQueue = new BatchingBoundedQueue<>(
                threadFactory,
                waitStrategy,
                10,
                Duration.ofSeconds(1),
                iterable -> {
                    ThreadUtil.sleepIgnoringInterrupts(2_000);
                    final List<AtomicInteger> items = new ArrayList<>();
                    iterable.iterator().forEachRemaining(items::add);
                    batches.add(items);
                });

        for (int i = 0; i < 25; i++) {
            batchingQueue.put(new AtomicInteger(i));
        }

        batchingQueue.shutdown();

        // batch has expired so we get a partial batch
        assertThat(batches)
                .hasSize(3);
        assertThat(batches)
                .extracting(List::size)
                .contains(10, 10, 5);
    }

    @Test
    void offer() {
    }

    @Test
    void nearestPowerOfTwo() {
    }

    @TestFactory
    Stream<DynamicTest> testNearestPowerOfTwo() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(int.class)
                .withOutputType(int.class)
                .withTestFunction(testCase ->
                        BatchingBoundedQueue.nearestPowerOfTwo(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(1, 1)
                .addCase(2, 2)
                .addCase(3, 4)
                .addCase(4, 4)
                .addCase(5, 4)
                .addCase(6, 8)
                .addCase(7, 8)
                .addCase(8, 8)
                .addCase(1000, 1024)
                .addCase(1025, 1024)
                .addCase(100_000, 131_072)
                .addCase(1_000_000, 1_048_576)
                .addCase(10_000_000, 8_388_608)
                .build();
    }

}
