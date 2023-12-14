package stroom.bytebuffer.impl6;

import stroom.bytebuffer.ByteBufferSupport;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

class PooledByteBufferQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PooledByteBufferQueue.class);

    private final BlockingQueue<ByteBuffer> queue;

    // One counter for each size of buffer. Keeps track of the number of buffers known to the pool
    // whether in the pool or currently on loan. Each AtomicInteger will increase until it hits its
    // configured limit then will never go down unless clear() is called.
    private final AtomicInteger pooledBufferCounter;
    // The max number of buffers for each buffer size that the pool should manage.
    private final int maxBufferCount;
    // The buffer capacity for each offset/index. Saves computing a Math.pow each time.
    private final int bufferSize;
    // The threshold for the number of created buffers (for each offset) to warn at.
    private final int warningThreshold;

    /**
     * Whether the thread should be blocked when requesting a buffer from the pool and the
     * limit for that buffer size has been reached. If false new buffers will be created and excess buffers
     * will have to be destroyed when no longer needed which may have a performance/memory penalty
     **/
    private final boolean blockOnExhaustedPool;

    /**
     * When the number of created buffers for any size reaches this threshold a warning will be logged.
     */
    private final int warningThresholdPercentage;

    public PooledByteBufferQueue(final AtomicInteger pooledBufferCounter,
                                 final int maxBufferCount,
                                 final int bufferSize,
                                 final int warningThreshold,
                                 final boolean blockOnExhaustedPool,
                                 final int warningThresholdPercentage) {
        this.pooledBufferCounter = pooledBufferCounter;
        this.maxBufferCount = maxBufferCount;
        this.bufferSize = bufferSize;
        this.warningThreshold = warningThreshold;
        this.blockOnExhaustedPool = blockOnExhaustedPool;
        this.warningThresholdPercentage = warningThresholdPercentage;
        this.queue = new ArrayBlockingQueue<>(maxBufferCount);
    }

    public PooledByteBuffer get() {
        final ByteBuffer buffer = tryGetByteBuffer();
        if (buffer == null) {
            return null;
        }
        return new PooledByteBufferImpl(this, buffer);
    }

    public PooledByteBuffer forceGet() {
        final ByteBuffer buffer = getByteBuffer();
        return new PooledByteBufferImpl(this, buffer);
    }

    private ByteBuffer tryGetByteBuffer() {
        ByteBuffer buffer = queue.poll();
        if (buffer == null) {
            // Queue empty so if the pool hasn't reached them limit for this buffer size
            // create a new one.
            buffer = createNewBufferIfAllowed();
        }
        return buffer;
    }

    private ByteBuffer getByteBuffer() {
        final ByteBuffer buffer;
        // None in the pool and not allowed to create any more pool buffers so we either
        // wait on the queue or just create an excess one that will have to be destroyed on release.
        // Creation of an excess one is a last resort due to the cost of creation/destruction
        if (blockOnExhaustedPool) {
            try {
                // At max pooled buffers so we just have to block and wait for another thread to release
                LOGGER.debug("Taking from queue, may block");
                buffer = queue.take();
            } catch (InterruptedException e) {
                LOGGER.debug("Thread interrupted waiting for a buffer from the pool", e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrupted while waiting for a buffer from the pool");
            }
        } else {
            // Don't want to block so create a new one
            // Excess buffers will have to be unmapped rather than returned to the pool
            final int roundedCapacity = bufferSize;
            LOGGER.debug("Creating new buffer beyond the pool limit (capacity: {})", roundedCapacity);
            buffer = ByteBuffer.allocateDirect(roundedCapacity);
        }

        return buffer;
    }

    private ByteBuffer createNewBufferIfAllowed() {
        final int maxBufferCount = this.maxBufferCount;
        final int warningThreshold = this.warningThreshold;
        final AtomicInteger bufferCounter = pooledBufferCounter;
        final int roundedCapacity = bufferSize;

        ByteBuffer byteBuffer = null;

        while (true) {
            int currBufferCount = bufferCounter.get();


            if (currBufferCount < maxBufferCount) {
                final int newBufferCount = currBufferCount + 1;

                if (bufferCounter.compareAndSet(currBufferCount, newBufferCount)) {
                    // Succeeded in incrementing the count so we can create one
                    LOGGER.debug("Creating new pooled buffer (capacity: {})", roundedCapacity);
                    byteBuffer = ByteBuffer.allocateDirect(roundedCapacity);

                    if (newBufferCount == warningThreshold) {
                        LOGGER.warn("Hit {}% ({}) of the limit of {} for pooled buffers of size {}.",
                                warningThresholdPercentage,
                                warningThreshold,
                                newBufferCount,
                                bufferSize);
                    } else if (newBufferCount == maxBufferCount) {
                        LOGGER.warn("Hit limit of {} for pooled buffers of size {}. " +
                                        "Future calls to the pool will create new buffers but excess buffers " +
                                        "will have to be freed rather than returned to the pool. This may incur a " +
                                        "performance overhead. Consider changing the pool settings.",
                                newBufferCount,
                                roundedCapacity);
                    }

                    break;
                } else {
                    // CAS failed so another thread beat us, go round again.
                }
            } else {
                // At max count so can't add any more to the pool
                break;
            }
        }
        return byteBuffer;
    }

    void release(final ByteBuffer buffer) {
        // Use offer rather than put as that will fail if the thread is interrupted but
        // we want the buffer back on the queue whatever happens, else the pool will be
        // exhausted.
        // As pooledBufferCounters controls the number of queued items we don't need to worry
        // about offer failing.
        final boolean didOfferSucceed = queue.offer(buffer);

        if (!didOfferSucceed) {
            // We must have created more buffers than there are under pool control so we just have
            // to unmap it
            LOGGER.debug(() -> LogUtil.message("Unable to return buffer to the queue so will destroy it " +
                            "(capacity: {}, queue size: {}, counter value: {}, configured limit: {}",
                    buffer.capacity(),
                    size(),
                    pooledBufferCounter.get(),
                    maxBufferCount));
            unmapBuffer(buffer);
        }
    }

    private void unmapBuffer(final ByteBuffer buffer) {
        if (buffer.isDirect()) {
            try {
                LOGGER.debug("Unmapping buffer {}", buffer);
                ByteBufferSupport.unmap((MappedByteBuffer) buffer);
            } catch (Exception e) {
                LOGGER.error("Error releasing direct byte buffer", e);
            }
        }
    }

    public int getPooledBufferCount() {
        return pooledBufferCounter.get();
    }

    public int size() {
        return queue.size();
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getMaxBufferCount() {
        return maxBufferCount;
    }

    public void clear(final List<String> msgs) {
        // The queue of buffers is the source of truth so clear that out
        final Queue<ByteBuffer> drainedBuffers = new ArrayDeque<>(queue.size());
        queue.drainTo(drainedBuffers);

        // As well as removing the buffers we need to reduce the counters to allow new buffers to
        // be created again if needs be. It doesn't matter that this happens sometime later than
        // the draining of the queue.
        pooledBufferCounter.addAndGet(-1 * drainedBuffers.size());
        msgs.add(bufferSize + ":" + drainedBuffers.size());

        // Destroy all the cleared buffers
        ByteBuffer byteBuffer = drainedBuffers.poll();
        while (byteBuffer != null) {
            unmapBuffer(byteBuffer);
            byteBuffer = drainedBuffers.poll();
        }
    }
}
