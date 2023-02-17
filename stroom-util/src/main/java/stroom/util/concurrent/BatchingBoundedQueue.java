package stroom.util.concurrent;

import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;

import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceReportingEventHandler;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.Util;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

/**
 * A lock free blocking bounded queue supporting multiple producer threads and a single background consumer
 * thread.
 *
 * @param <T> The type of item in the queue
 */
public class BatchingBoundedQueue<T> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BatchingBoundedQueue.class);

    private final ThreadFactory threadFactory;
    private final Disruptor<ItemHolder<T>> disruptor;
    private final RingBuffer<ItemHolder<T>> ringBuffer;
    private final WaitStrategy waitStrategy;
    private final ItemHandler<T> itemHandler;
    private final WorkHandler<ItemHolder<T>>[] workHandlers;
    private final int maxBatchSize;
    private final int consumerCount;
    private final Duration maxItemAge;

    /**
     * @param threadFactory Factory for the consumer thread
     * @param waitStrategy  Strategy for waiting producers/consumer
     * @param consumerCount
     * @param maxBatchSize  Max items to pass to the batchConsumer on each call
     * @param maxItemAge    Approximate max age of an item passed to batchConsumer, where age is
     *                      taken from the time the item is added to the batch
     *                      (not when put is called, which may block).
     * @param batchConsumer Called for a batch of >0 items. May be less than maxBatchSize if an item in
     *                      the queue is too old or shutdown has been called. {@code batchConsumer} runs in a single
     *                      thread provided by {@code threadFactory}
     */
    public BatchingBoundedQueue(final ThreadFactory threadFactory,
                                final WaitStrategy waitStrategy,
                                final int consumerCount,
                                final int maxBatchSize,
                                final Duration maxItemAge,
                                final BatchConsumer<T> batchConsumer) {
        this.threadFactory = threadFactory;
        this.waitStrategy = waitStrategy;
        this.maxBatchSize = maxBatchSize;
        this.maxItemAge = maxItemAge;
        this.consumerCount = consumerCount;
        this.itemHandler = ItemHandler.buildSingle(maxBatchSize, maxItemAge);
        this.workHandlers = BatchHandler.buildMultiple(consumerCount, batchConsumer);
        this.disruptor = buildDisruptor();
        this.ringBuffer = disruptor.start();
        setupHeartBeatTimer();
    }

    /**
     * Place an item on the queue. This may block if the queue is full.
     *
     * @param item The item to place on the queue.
     */
    public void put(final T item) {
        LOGGER.debug("put called for item: {}", item);
        Objects.requireNonNull(item);
        ringBuffer.publishEvent(this::setItemOnHolder, item);
    }

    /**
     * Place an item on the queue if there is space.
     *
     * @param item The item to place on the queue.
     * @return true if the item was placed on the queue.
     */
    public boolean offer(final T item) {
        LOGGER.debug("offer called for item: {}", item);
        Objects.requireNonNull(item);
        return ringBuffer.tryPublishEvent(this::setItemOnHolder, item);
    }

    int getRingSize() {
        return ringBuffer.getBufferSize();
    }

    private void putHeartBeat() {
        LOGGER.debug("putHeartBeat called");
        // Have
        forEachConsumer(() ->
                ringBuffer.publishEvent(this::markHolderAsHeartBeat));
    }

    /**
     * Shuts the queue and associated threads down. Only call this after
     * all calls to {@link BatchingBoundedQueue#put(Object)} have finished.
     */
    public void shutdown() {
        LOGGER.debug("Shutting down disruptor");

        // Send a shutdown item as the last item to ensure everything gets flushed
        forEachConsumer(() ->
                ringBuffer.publishEvent(this::markHolderAsShuttingDown));

        // Now block till all items have been consumed
        disruptor.shutdown();
    }

    private void forEachConsumer(final Runnable runnable) {
        for (int i = 0; i < consumerCount; i++) {
            runnable.run();
        }
    }

    private void setItemOnHolder(final ItemHolder<T> itemHolder, final long sequence, T item) {
        // Place our item into this mutable holder in the ring buffer
        itemHolder.setItem(item);
    }

    private void markHolderAsHeartBeat(final ItemHolder<T> itemHolder, final long sequence) {
        // Place our item into this mutable holder in the ring buffer
        itemHolder.setAsHeartBeat();
    }

    private void markHolderAsShuttingDown(final ItemHolder<T> itemHolder, final long sequence) {
        // Place our item into this mutable holder in the ring buffer
        itemHolder.setAsShuttingDown();
    }

    private Disruptor<ItemHolder<T>> buildDisruptor() {
        final int ringSize = Util.ceilingNextPowerOfTwo(maxBatchSize * 2);

        LOGGER.debug(() -> LogUtil.message(
                "Building disruptor with consumerCount: {}, maxBatchSize: {}, maxItemAge: {}, " +
                        "ringSize: {}, waitStrategy: {}, threadFactory: {}",
                consumerCount,
                maxBatchSize,
                maxItemAge,
                ringSize,
                waitStrategy.getClass().getSimpleName(),
                threadFactory.getClass().getSimpleName()));

        final Disruptor<ItemHolder<T>> disruptor = new Disruptor<>(
                ItemHolder::new,
                ringSize,
                threadFactory,
                ProducerType.MULTI,
                waitStrategy);

        // Register the handler(s)
        disruptor.handleEventsWith(itemHandler);
        //
        disruptor.after(itemHandler)
                .handleEventsWithWorkerPool(workHandlers);

        return disruptor;
    }

    private Timer setupHeartBeatTimer() {
        // Set it up as a daemon thread
        final Timer timer = new Timer(true);

        long delayMs = Math.max(1_000, maxItemAge.toMillis() / 2);
        LOGGER.debug(() -> LogUtil.message("Setting up timer with delayMs: {}", Duration.ofMillis(delayMs)));

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                putHeartBeat();
            }
        }, delayMs);

        return timer;
    }

    /**
     * Get the nearest power of two to {@code n}.
     */
    // Pkg private for testing
    static int nearestPowerOfTwo(int n) {
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


    // --------------------------------------------------------------------------------


    private static final class BatchHandler<T> implements WorkHandler<ItemHolder<T>> {

        private final BatchConsumer<T> batchConsumer;

        private BatchHandler(final BatchConsumer<T> batchConsumer) {
            this.batchConsumer = batchConsumer;
        }

        private static <T> WorkHandler<ItemHolder<T>>[] buildMultiple(
                final int consumerCount,
                final BatchConsumer<T> batchConsumer) {

            //noinspection unchecked
            return IntStream.range(0, consumerCount)
                    .boxed()
                    .map(i -> new BatchHandler<>(batchConsumer))
                    .toArray(WorkHandler[]::new);
        }

        @Override
        public void onEvent(final ItemHolder<T> event) throws Exception {
            final EventType eventType = event.eventType;

            // HEART_BEAT and SHUTDOWN are only used by the ItemHandler.
            // All we care about is BATCH ones
            if (EventType.BATCH.equals(eventType)) {
                NullSafe.consume(event.getBatch(), batch ->
                        batchConsumer.accept(batch, event.batchSize));
            } else {
                LOGGER.trace("Ignoring event of type {}", eventType);
            }
        }
    }


    // --------------------------------------------------------------------------------


    private static final class ItemHandler<T>
            implements SequenceReportingEventHandler<ItemHolder<T>>, LifecycleAware {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ItemHandler.class);

        private final int consumerId;
        private final int consumerCount;
        private final int maxBatchSize;
        private final Duration maxItemAge;

        private Instant batchExpiryTime = null;
        private Queue<T> batchQueue = new LinkedList<>();
        private int batchCounter = 0; // Getting size of linked list is expensive
        private Sequence sequenceCallback = null;
        private int disruptorBatchSize = 0;

        private ItemHandler(final int consumerId,
                            final int consumerCount,
                            final int maxBatchSize,
                            final Duration maxItemAge) {
            this.consumerId = consumerId;
            this.consumerCount = consumerCount;
            this.maxBatchSize = maxBatchSize;
            this.maxItemAge = maxItemAge;
        }

        public static <T> ItemHandler<T> buildSingle(final int maxBatchSize,
                                                     final Duration maxItemAge) {
            return new ItemHandler<>(0, 1, maxBatchSize, maxItemAge);
        }

        public static <T> ItemHandler<T>[] buildMultiple(final int consumerCount,
                                                         final int maxBatchSize,
                                                         final Duration maxItemAge) {
            ItemHandler<T>[] consumers = new ItemHandler[consumerCount];
            for (int i = 0; i < consumerCount; i++) {
                consumers[i] = new ItemHandler<>(i, consumerCount, maxBatchSize, maxItemAge);
            }
            return consumers;
        }

        @Override
        public void onEvent(final ItemHolder<T> itemHolder,
                            final long sequence,
                            final boolean endOfBatch) {

            LOGGER.debug("consumerId: {}, sequence: {}, endOfBatch: {}, itemHolder: {}",
                    consumerId, sequence, endOfBatch, itemHolder);

            if (LOGGER.isDebugEnabled()) {
                // Track the batch size used by the disruptor
                disruptorBatchSize++;
                if (endOfBatch) {
                    LOGGER.debug("endOfBatch signalled by disruptor, disruptorBatchSize: {}", disruptorBatchSize);
                    disruptorBatchSize = 0;
                }
            }

            Objects.requireNonNull(itemHolder);
            final EventType eventType = itemHolder.getItemType();
            final boolean isBatchComplete;
            switch (eventType) {
                case ITEM -> {
                    if (itemHolder.hasItem()) {
                        isBatchComplete = addItemToBatchQueue(itemHolder, endOfBatch);
                    } else {
                        isBatchComplete = false;
                    }
                }
                case HEART_BEAT -> {
                    // See if the queue is too old
                    LOGGER.debug("Received heart beat event");
                    isBatchComplete = flushBatchQueueIfRequired(itemHolder, false, endOfBatch);
                }
                case SHUTDOWN -> {
                    // See if the queue is too old
                    LOGGER.debug("Received shutdown event");
                    isBatchComplete = flushBatchQueueIfRequired(itemHolder, true, endOfBatch);
                }
                default -> throw new IllegalArgumentException("Unknown type " + eventType);
            }

//                if (isBatchComplete) {
//                    LOGGER.debug("Setting sequence");
//                    sequenceCallback.set(sequence);
//                }

            // Let the ring know
            sequenceCallback.set(sequence);
        }

        @Override
        public void onStart() {
            LOGGER.debug("Consumer onStart called");
        }

        @Override
        public void onShutdown() {
            LOGGER.debug("Consumer onShutdown called");

            // Not sure if we need this
//            flushBatchQueue(true);
        }

        @Override
        public void setSequenceCallback(final Sequence sequenceCallback) {
            this.sequenceCallback = sequenceCallback;
        }

        private boolean flushBatchQueueIfRequired(final ItemHolder<T> itemHolder, final boolean isForced,
                                                  final boolean endOfBatch) {
            final boolean isBatchComplete;
            if (NullSafe.hasItems(batchQueue)) {

//                if (endOfBatch) {
//                    LOGGER.debug("processing endOfBatch batch size: {}, ringBuffer capacity: {}",
//                            ids.size(), ringBuffer.remainingCapacity());
//                    processBatch();
//                    isBatchComplete = false;

                if (isForced) {
                    LOGGER.debug("Forced flush       - batch size: {}", batchCounter);
                    swapQueue(itemHolder);
                    isBatchComplete = true;
                } else if (batchCounter >= maxBatchSize) {
                    LOGGER.debug("Batch full         - batch size: {}", batchCounter);
                    swapQueue(itemHolder);
                    isBatchComplete = true;
                } else if (batchExpiryTime != null && Instant.now().isAfter(batchExpiryTime)) {
                    LOGGER.debug("Batch expired      - batch size: {}", batchCounter);
                    swapQueue(itemHolder);
                    isBatchComplete = true;
                } else {
                    LOGGER.debug("Flush not required - batch size: {}", batchCounter);
                    isBatchComplete = false;
                }
            } else {
                LOGGER.debug("Flush not required - batch size: {}", batchCounter);
                isBatchComplete = false;
            }
            return isBatchComplete;
        }

        private void swapQueue(final ItemHolder<T> itemHolder) {
            LOGGER.debug("Swapping queue");
            if (!batchQueue.isEmpty()) {
                final Queue<T> queueCopy = batchQueue;
                batchQueue = new LinkedList<>();
                final int batchSize = batchCounter;
                batchCounter = 0;
                // Null this so we can re-set it when we get the next event
                batchExpiryTime = null;

                LOGGER.debug(() -> LogUtil.message("Passing {} items to consumer", queueCopy.size()));
//                batchConsumer.accept(queueCopy);

                // Put the batch back on the ring so the downstream processors can pick it up
                itemHolder.setBatch(queueCopy, batchSize);
            } else {
                LOGGER.debug("Not swapping empty queue");
            }
        }

        private boolean addItemToBatchQueue(final ItemHolder<T> itemHolder,
                                            final boolean endOfBatch) {
            // Base the batchExpiryTime on the oldest item in the queue
            if (batchExpiryTime == null) {
                batchExpiryTime = Instant.now().plus(maxItemAge);
            }


            batchQueue.add(itemHolder.getItem());
            batchCounter++;
            // Clear the valueHolder so our value doesn't sit in the buffer for ages if the buffer
            // is quiet
            itemHolder.clear();

            return flushBatchQueueIfRequired(itemHolder, false, endOfBatch);
        }
    }


    // --------------------------------------------------------------------------------


    private static final class ItemHolder<T> implements Clearable {

        private EventType eventType = EventType.HEART_BEAT;
        private T item = null;
        private Iterable<T> batch = null;
        private int batchSize = -1;

        public ItemHolder() {
        }

        public void setItem(final T item) {
            this.eventType = EventType.ITEM;
            this.item = Objects.requireNonNull(item);
            this.batch = null;
            batchSize = -1;
        }

        public void setBatch(final Iterable<T> batch, final int batchSize) {
            this.eventType = EventType.BATCH;
            this.item = null;
            this.batch = Objects.requireNonNull(batch);
            this.batchSize = batchSize;
        }

        public void setAsHeartBeat() {
            this.eventType = EventType.HEART_BEAT;
            this.item = null;
            this.batch = null;
            batchSize = -1;
        }

        public void setAsShuttingDown() {
            this.eventType = EventType.SHUTDOWN;
            this.item = null;
            this.batch = null;
            batchSize = -1;
        }

        public void clear() {
            eventType = EventType.EMPTY;
            item = null;
            batch = null;
            batchSize = -1;
        }

        public EventType getItemType() {
            return eventType;
        }

        public boolean hasItem() {
            return item != null;
        }

        public boolean hasBatch() {
            return batch != null;
        }

        public T getItem() {
            return item;
        }

        public Iterable<T> getBatch() {
            return batch;
        }

        @Override
        public String toString() {
            return "ItemHolder{" +
                    "eventType=" + eventType +
                    ", item=" + item +
                    ", hasBatch=" + hasBatch() +
                    '}';
        }
    }


    // --------------------------------------------------------------------------------


    public interface BatchConsumer<T> extends BiConsumer<Iterable<T>, Integer> {

    }


    // --------------------------------------------------------------------------------


    private enum EventType {
        /**
         * A shutdown event to indicate the queue has been shut down so batches can be completed.
         */
        SHUTDOWN,
        /**
         * A heart beat event to allow the queue to check for expired batches
         */
        HEART_BEAT,
        /**
         * A single item
         */
        ITEM,
        /**
         * A batch of items
         */
        BATCH,
        /**
         * An empty event
         */
        EMPTY
    }
}
