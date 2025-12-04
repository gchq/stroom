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

package stroom.bytebuffer.impl6;

import stroom.bytebuffer.ByteBufferSupport;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class PooledByteBufferQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PooledByteBufferQueue.class);

    private final BlockingQueue<ByteBuffer> queue;

    // The max number of buffers for each buffer size that the pool should manage.
    private final int maxBufferCount;
    // The buffer capacity for each offset/index. Saves computing a Math.pow each time.
    private final int bufferSize;

    public PooledByteBufferQueue(final int maxBufferCount,
                                 final int bufferSize) {
        this.maxBufferCount = maxBufferCount;
        this.bufferSize = bufferSize;
        this.queue = new ArrayBlockingQueue<>(maxBufferCount);
    }

    public PooledByteBuffer get() {
        return new PooledByteBufferImpl(this, getByteBuffer());
    }

    public ByteBuffer getByteBuffer() {
        ByteBuffer buffer = queue.poll();
        if (buffer == null) {
            // Queue empty so if the pool hasn't reached them limit for this buffer size
            // create a new one.
            buffer = ByteBuffer.allocateDirect(bufferSize);
        }
        return buffer;
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
            LOGGER.trace(() -> LogUtil.message("Unable to return buffer to the queue so will destroy it " +
                            "(capacity: {}, queue size: {}, counter value: {}, configured limit: {}",
                    buffer.capacity(),
                    size(),
                    queue.size(),
                    maxBufferCount));
            unmapBuffer(buffer);
        }
    }

    private void unmapBuffer(final ByteBuffer buffer) {
        if (buffer.isDirect()) {
            try {
                LOGGER.trace("Unmapping buffer {}", buffer);
                ByteBufferSupport.unmap(buffer);
            } catch (final Exception e) {
                LOGGER.error("Error releasing direct byte buffer", e);
            }
        }
    }

    public int getPooledBufferCount() {
        return queue.size();
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
        msgs.add(bufferSize + ":" + drainedBuffers.size());

        // Destroy all the cleared buffers
        ByteBuffer byteBuffer = drainedBuffers.poll();
        while (byteBuffer != null) {
            unmapBuffer(byteBuffer);
            byteBuffer = drainedBuffers.poll();
        }
    }
}
