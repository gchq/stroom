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
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

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
    private final ItemHolderConsumer<T> itemHolderConsumer;
    private final int maxBatchSize;
    private final Duration maxItemAge;

    /**
     * @param threadFactory Factory for the consumer thread
     * @param waitStrategy  Strategy for waiting producers/consumer
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
                                final int maxBatchSize,
                                final Duration maxItemAge,
                                final Consumer<Iterable<T>> batchConsumer) {
        this.threadFactory = threadFactory;
        this.waitStrategy = waitStrategy;
        this.maxBatchSize = maxBatchSize;
        this.maxItemAge = maxItemAge;
        this.itemHolderConsumer = new ItemHolderConsumer<>(batchConsumer, maxBatchSize, maxItemAge);
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

    private void putHeartBeat() {
        LOGGER.debug("putHeartBeat called");
        ringBuffer.publishEvent(this::markHolderAsHeartBeat);
    }

    /**
     * Shuts the queue and associated threads down. Only call this after
     * all calls to {@link BatchingBoundedQueue#put(Object)} have finished.
     */
    public void shutdown() {
        LOGGER.debug("Shutting down disruptor");

        // Send a shutdown item as the last item to ensure everything gets flushed
        ringBuffer.publishEvent(this::markHolderAsShuttingDown);

        // Now block till all items have been consumed
        disruptor.shutdown();
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
        final int ringSize = nearestPowerOfTwo(maxBatchSize * 2);

        LOGGER.debug(() -> LogUtil.message(
                "Building disruptor with maxBatchSize: {}, maxItemAge: {}, " +
                        "ringSize: {}, waitStrategy: {}, threadFactory: {}",
                maxBatchSize, maxItemAge, ringSize, waitStrategy.getClass().getSimpleName(),
                threadFactory.getClass().getSimpleName()));

        final Disruptor<ItemHolder<T>> disruptor = new Disruptor<>(
                ItemHolder::new,
                ringSize,
                threadFactory,
                ProducerType.MULTI,
                waitStrategy);

        disruptor.handleEventsWith(this.itemHolderConsumer);

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


    private static final class ItemHolderConsumer<T>
            implements SequenceReportingEventHandler<ItemHolder<T>>, LifecycleAware {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ItemHolderConsumer.class);

        private final Consumer<Iterable<T>> batchConsumer;
        private final int maxBatchSize;
        private final Duration maxItemAge;
        private Instant batchExpiryTime = null;

        private Queue<T> batchQueue = new LinkedList<>();

        private int batchCounter = 0; // Getting size of linked list is expensive
        private Sequence sequenceCallback = null;
        private int disruptorBatchSize = 0;

        private ItemHolderConsumer(final Consumer<Iterable<T>> batchConsumer,
                                   final int maxBatchSize,
                                   final Duration maxItemAge) {
            this.batchConsumer = batchConsumer;
            this.maxBatchSize = maxBatchSize;
            this.maxItemAge = maxItemAge;
        }

        @Override
        public void onEvent(final ItemHolder<T> itemHolder,
                            final long sequence,
                            final boolean endOfBatch) {

            if (LOGGER.isDebugEnabled()) {
                // Track the batch size used by the disruptor
                disruptorBatchSize++;
                if (endOfBatch) {
                    LOGGER.debug("endOfBatch signalled by disruptor, disruptorBatchSize: {}", disruptorBatchSize);
                    disruptorBatchSize = 0;
                }
            }

            if (itemHolder != null) {
                final ItemType itemType = itemHolder.getItemType();
                final boolean isBatchComplete;
                switch (itemType) {
                    case PAYLOAD -> {
                        if (itemHolder.hasItem()) {
                            isBatchComplete = addItemToBatchQueue(itemHolder, endOfBatch);
                        } else {
                            isBatchComplete = false;
                        }
                    }
                    case HEART_BEAT -> {
                        // See if the queue is too old
                        LOGGER.debug("Received heart beat item");
                        isBatchComplete = flushBatchQueueIfRequired(false, endOfBatch);
                    }
                    case SHUTTING_DOWN -> {
                        // See if the queue is too old
                        LOGGER.debug("Received shutting down item");
                        isBatchComplete = flushBatchQueueIfRequired(true, endOfBatch);
                    }
                    default -> throw new IllegalArgumentException("Unknown type " + itemType);
                }

                if (isBatchComplete) {
                    LOGGER.debug("Setting sequence");
                    sequenceCallback.set(sequence);
                }
            }
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

        private boolean flushBatchQueueIfRequired(final boolean isForced, final boolean endOfBatch) {
            final boolean isBatchComplete;
            if (NullSafe.hasItems(batchQueue)) {

//                if (endOfBatch) {
//                    LOGGER.debug("processing endOfBatch batch size: {}, ringBuffer capacity: {}",
//                            ids.size(), ringBuffer.remainingCapacity());
//                    processBatch();
//                    isBatchComplete = false;

                if (isForced) {
                    LOGGER.debug("Forced flush       - batch size: {}", batchCounter);
                    swapQueue();
                    isBatchComplete = true;
                } else if (batchCounter >= maxBatchSize) {
                    LOGGER.debug("Batch full         - batch size: {}", batchCounter);
                    swapQueue();
                    isBatchComplete = true;
                } else if (batchExpiryTime != null && Instant.now().isAfter(batchExpiryTime)) {
                    LOGGER.debug("Batch expired      - batch size: {}", batchCounter);
                    swapQueue();
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

        private void swapQueue() {
            LOGGER.debug("Swapping queue");
            if (!batchQueue.isEmpty()) {
                final Queue<T> queueCopy = batchQueue;
                batchQueue = new LinkedList<>();
                batchCounter = 0;
                // Null this so we can re-set it when we get the next event
                batchExpiryTime = null;

                LOGGER.debug(() -> LogUtil.message("Passing {} items to consumer", queueCopy.size()));
                batchConsumer.accept(queueCopy);
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

            return flushBatchQueueIfRequired(false, endOfBatch);
        }
    }


    // --------------------------------------------------------------------------------


    private static final class ItemHolder<T> implements Clearable {

        private ItemType itemType = ItemType.HEART_BEAT;
        private T item = null;

        public ItemHolder() {
        }

        public void setItem(final T item) {
            this.item = Objects.requireNonNull(item);
            this.itemType = ItemType.PAYLOAD;
        }

        public void setAsHeartBeat() {
            this.item = null;
            this.itemType = ItemType.HEART_BEAT;
        }

        public void setAsShuttingDown() {
            this.item = null;
            this.itemType = ItemType.SHUTTING_DOWN;
        }

        public void clear() {
            setAsHeartBeat();
        }

        public ItemType getItemType() {
            return itemType;
        }

        public boolean hasItem() {
            return item != null;
        }

        public T getItem() {
            return item;
        }
    }


    // --------------------------------------------------------------------------------


    private enum ItemType {
        SHUTTING_DOWN,
        HEART_BEAT,
        PAYLOAD
    }
}
