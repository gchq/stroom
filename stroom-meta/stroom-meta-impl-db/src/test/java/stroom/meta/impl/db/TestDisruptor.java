package stroom.meta.impl.db;

import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceReportingEventHandler;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestDisruptor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDisruptor.class);

    //    private static final int RING_SIZE = 2_048;
    private static final int BATCH_SIZE = 1_000;
    private static final int RING_SIZE = nearestPowerOfTwo(BATCH_SIZE * 2);
    private static final int EVENTS_PER_THREAD = 5_000;
    private static final int PRODUCER_THREADS = 10;
    private static final Duration MAX_BATCH_AGE = Duration.ofSeconds(1);
    private static final String WAIT_STRATEGY_NAME = "com.lmax.disruptor.BusySpinWaitStrategy";

    final LongAdder longAdder = new LongAdder();
    final List<LongAdder> producerCounts = new ArrayList<>();

    private RingBuffer<ValueHolder> ringBuffer;

    @Disabled // manual demo test
    @Test
    void test() throws ExecutionException, InterruptedException {

        LOGGER.info("Ring size: {}", RING_SIZE);

        final ThreadFactory threadFactory = DaemonThreadFactory.INSTANCE;


        final WaitStrategy waitStrategy = getWaitStrategy(SleepingWaitStrategy::new);

        final Disruptor<ValueHolder> disruptor = new Disruptor<>(
                ValueHolder::new,
                RING_SIZE,
                threadFactory,
                ProducerType.MULTI,
                waitStrategy);

        Consumer consumer = new Consumer();

        disruptor.handleEventsWith(consumer);

        ringBuffer = disruptor.start();

        final ExecutorService executorService = Executors.newFixedThreadPool(PRODUCER_THREADS);

        final List<CompletableFuture<Void>> futures = IntStream.rangeClosed(1, PRODUCER_THREADS)
                .boxed()
                .map(i -> {
                    final int iCopy = i;
                    return CompletableFuture.runAsync(() -> {
                        final LongAdder producerCount = new LongAdder();
                        producerCounts.add(producerCount);
                        for (int eventCount = 0; eventCount < EVENTS_PER_THREAD; eventCount++) {
                            final DurationTimer durationTimer = DurationTimer.start();
//                            long sequenceId = ringBuffer.next();
                            EventTranslatorOneArg<ValueHolder, Integer> translator = (event, sequence, cnt) -> {
                                event.setValue((iCopy * 1_000_000) + cnt);
                            };
                            ringBuffer.publishEvent(translator, eventCount);
//                            ValueHolder valueHolder = ringBuffer.get(sequenceId);
//                            valueHolder.setValue((iCopy * 1_000_000) + eventCount);
//                            ringBuffer.publishEven;publish(sequenceId);

                            producerCount.increment();
//                            longAdder.add(1);
//                            LOGGER.info("Published sequenceId: {}, capacity: {}, longAdder: {}, time: {}",
//                                    sequenceId,
//                                    ringBuffer.remainingCapacity(),
//                                    longAdder.sum(),
//                                    durationTimer);

//                            ThreadUtil.sleepIgnoringInterrupts(2_000);
                        }
                        LOGGER.info("Thread {} finished", i);
                    }, executorService);
                })
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[]{}))
                .get();

        LOGGER.info("produced: {}, consumed: {}",
                producerCounts.stream()
                        .mapToLong(LongAdder::sum)
                        .sum(),
                consumer.getConsumedCount());
    }


    private static int nearestPowerOfTwo(int n) {
        int v = n;

        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v++; // next power of 2

        int x = v >> 1; // previous power of 2

        return (v - n) > (n - x)
                ? x
                : v;
    }

    private WaitStrategy getWaitStrategy(final Supplier<WaitStrategy> defaultWaitStrategySupplier) {
        final String strategyName = WAIT_STRATEGY_NAME;
        try {
            final Class<?> clazz = Class.forName(strategyName);
            return (WaitStrategy) clazz.getConstructor().newInstance();
        } catch (Exception e) {
            LOGGER.error("Error finding", e);
            return Objects.requireNonNull(
                    Objects.requireNonNull(defaultWaitStrategySupplier)
                            .get());
        }
    }


    // --------------------------------------------------------------------------------


    public static class ValueHolder implements Clearable {

        private Integer value;
//        public static final EventFactory<ValueEvent> EVENT_FACTORY = ValueEvent::new;

        public void setValue(final int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        @Override
        public void clear() {
            this.value = null;
        }
    }


    // --------------------------------------------------------------------------------


    public class Consumer implements SequenceReportingEventHandler<ValueHolder>, LifecycleAware {

        private Sequence sequenceCallback;
        private final List<Integer> ids = new ArrayList<>();
        private Instant batchExpiryTime = null;
        private final LongAdder consumedCount = new LongAdder();
        private final Random random = new Random();
        private int disruptorBatchSize = 0;

//        public EventHandler<ValueEvent>[] getEventHandler() {
//
//            final EventHandler<ValueEvent> eventHandler = (event, sequence, endOfBatch) -> {
//            };
//
//            return new EventHandler[]{eventHandler};
//        }

        @Override
        public void setSequenceCallback(final Sequence sequenceCallback) {
            this.sequenceCallback = sequenceCallback;
        }

        @Override
        public void onEvent(final ValueHolder valueHolder,
                            final long sequence,
                            final boolean endOfBatch) throws Exception {

            consumedCount.increment();

            final boolean isBatchComplete = processEvent(valueHolder, sequence, endOfBatch);

            if (isBatchComplete) {
                // Finished the last batch
                sequenceCallback.set(sequence);
//                LOGGER.info("capacity: {}", ringBuffer.remainingCapacity());
            }
        }

        private boolean processEvent(ValueHolder valueHolder,
                                     long sequenceId,
                                     boolean endOfBatch) {
//            LOGGER.info("Consumed id: {}, sequenceId: {}, capacity: {}, " +
//                            "longAdder: {}, endOfBatch: {}",
//                    id,
//                    sequenceId,
//                    ringBuffer.remainingCapacity(),
//                    longAdder.sum(),
//                    endOfBatch);
            if (batchExpiryTime == null) {
                batchExpiryTime = Instant.now().plus(MAX_BATCH_AGE);

                final int randNo = random.nextInt(10);
                if (randNo == 3) {
//                    LOGGER.info("sleeping");
                    ThreadUtil.sleepIgnoringInterrupts(950 + random.nextInt(100));
                }
            }
            if (endOfBatch) {
                LOGGER.info("batch size: {}", disruptorBatchSize);
                disruptorBatchSize = 0;
            } else {
                disruptorBatchSize++;
            }

            ids.add(valueHolder.getValue());
            // Clear the valueHolder so our value doesn't sit in the buffer for ages if the buffer
            // is quiet
            valueHolder.clear();

            final boolean isBatchComplete;
            if (endOfBatch) {
                LOGGER.info("processing endOfBatch batch size: {}, ringBuffer capacity: {}",
                        ids.size(), ringBuffer.remainingCapacity());
                processBatch();
                isBatchComplete = true;
            } else if (ids.size() >= BATCH_SIZE) {
                LOGGER.info("processing full       batch size: {}", ids.size());
                processBatch();
                isBatchComplete = true;
            } else if (batchExpiryTime != null && Instant.now().isAfter(batchExpiryTime)) {
                LOGGER.info("processing expired    batch size: {}", ids.size());
                processBatch();
                isBatchComplete = true;
            } else {
                isBatchComplete = false;
            }

//            longAdder.add(-1);
            return isBatchComplete;
        }

        private void processBatch() {
//            consumedCount.add(ids.size());
            ids.clear();
            batchExpiryTime = null;
        }

        public LongAdder getConsumedCount() {
            return consumedCount;
        }

        @Override
        public void onStart() {
            LOGGER.info("Consumer started");

        }

        @Override
        public void onShutdown() {
            LOGGER.info("Consumer shutdown");
        }
    }


    // --------------------------------------------------------------------------------


    private static class BackPressureQueue<T> implements BlockingQueue<T> {

        private final Queue<T> queue;
        private final Semaphore semaphore;
        private final AtomicInteger count = new AtomicInteger();

        private BackPressureQueue(final int size) {
            this.queue = new ConcurrentLinkedQueue<>();
            this.semaphore = new Semaphore(size);
        }

        @Override
        public void put(final T t) throws InterruptedException {
            semaphore.acquire();
            try {
                queue.add(t);

            } catch (Exception e) {
                semaphore.release();
                throw e;
            }
        }

        @Override
        public boolean add(final T t) {
            final boolean didAcquire = semaphore.tryAcquire();
            if (didAcquire) {
                try {
                    queue.add(t);
                } catch (Exception e) {
                    throw e;
                }
            }
            return didAcquire;
        }

        @Override
        public boolean offer(final T t, final long timeout, final TimeUnit unit) throws InterruptedException {
            semaphore.tryAcquire(timeout, unit);
            return false;
        }

        @Override
        public T take() throws InterruptedException {
            return null;
        }

        @Override
        public T poll(final long timeout, final TimeUnit unit) throws InterruptedException {
            return null;
        }

        @Override
        public int remainingCapacity() {
            return 0;
        }

        @Override
        public int drainTo(final Collection<? super T> c) {
            return 0;
        }

        @Override
        public int drainTo(final Collection<? super T> c, final int maxElements) {
            return 0;
        }

        @Override
        public boolean offer(final T t) {
            return queue.offer(t);
        }

        @Override
        public T remove() {
            return queue.remove();
        }

        @Override
        public T poll() {
            return queue.poll();
        }

        @Override
        public T element() {
            return queue.element();
        }

        @Override
        public T peek() {
            return queue.peek();
        }

        @Override
        public int size() {
            return queue.size();
        }

        @Override
        public boolean isEmpty() {
            return queue.isEmpty();
        }

        @Override
        public boolean contains(final Object o) {
            return queue.contains(o);
        }

        @Override
        public Iterator<T> iterator() {
            return queue.iterator();
        }

        @Override
        public Object[] toArray() {
            return queue.toArray();
        }

        @Override
        public <T1> T1[] toArray(final T1[] a) {
            return queue.toArray(a);
        }

        @Override
        public boolean remove(final Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(final Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(final Collection<? extends T> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(final Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(final Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            queue.clear();
//            semaphore.

        }
    }
}
