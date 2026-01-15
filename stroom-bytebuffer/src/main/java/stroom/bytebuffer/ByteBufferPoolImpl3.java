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

package stroom.bytebuffer;

import stroom.util.HasHealthCheck;
import stroom.util.sysinfo.SystemInfoResult;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;


/*
 * This class is derived/copied from org.apache.hadoop.hbase.io.ByteBufferOutputStream
 * which has the following licence.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * An unbounded self-populating pool of directly allocated ByteBuffers.
 * When using one of the methods to get/use a buffer from the pool the
 * smallest available buffer that is greater than or equal to the required
 * capacity will be provided. All buffers will be cleared on return to
 * the pool.
 * <p>
 * As this pool is un-bounded it could grow very large under high contention
 */
@Singleton
public class ByteBufferPoolImpl3 implements ByteBufferPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(ByteBufferPoolImpl3.class);

    private static final int MAX_BYTES_IN_POOL = 500 * 1024;

    private final TreeMap<Key, ByteBuffer> bufferMap = new TreeMap<>();

//    private final ConcurrentMap<Integer, AtomicInteger> requestsMap = new ConcurrentHashMap<>();
//    private final AtomicInteger getCount = new AtomicInteger();
//    private final AtomicInteger releaseCount = new AtomicInteger();

//    private final AtomicInteger totalPooledCapacity = new AtomicInteger();
//    private final AtomicInteger largestBufferInPool = new AtomicInteger();

    @Override
    public PooledByteBuffer getPooledByteBuffer(final int minCapacity) {
        return new PooledByteBufferImpl(() -> getBuffer(minCapacity), this::release);
    }

//    private ByteBuffer getBuffer(final int minCapacity) {
//        // This method is called a LOT so needs to be performant
//
//        ByteBuffer buffer = null;
//        int count = 0;
//        boolean isNewBuffer = false;
//        while (buffer == null) {
//            count++;
//            // Loop over all queues with capacity >= minCapacity, starting with smallest
//            for (final Queue<ByteBuffer> bufferQueue : bufferQueueMap.tailMap(minCapacity).values()) {
//                buffer = bufferQueue.poll();
//                if (buffer != null) {
//                    break; // out of for loop
//                }
//            }
//
//            if (buffer == null) {
//                // Looked in all queues and found nothing
//                if (minCapacity > largestBufferInPool.get() || totalPooledCapacity.get() < MAX_BYTES_IN_POOL) {
//                    // if we are trying to get a buffer bigger than anything in the pool then
//                    // there is no point in going round again.
//                    buffer = createNewBuffer(minCapacity);
//                    isNewBuffer = true;
//                }
//            }
//            // if buffer is still null then our pool is at capacity so we just loop round again
//            // as another thread should have released one.
//        }
//
//        if (LOGGER.isDebugEnabled()) {
//            if (count > 1) {
//                LOGGER.debug("Obtained {} buffer after {} iterations", (isNewBuffer ? "new" : "pooled"), count);
//            }
//            getCount.incrementAndGet();
//            requestsMap.computeIfAbsent(minCapacity, k -> new AtomicInteger(0))
//                    .incrementAndGet();
//        }
//        return buffer;
//    }

    private synchronized ByteBuffer getBuffer(final int minCapacity) {
        // get a buffer at least as big as minCapacity with the smallest insertionTime
        final Map.Entry<Key, ByteBuffer> entry = bufferMap.ceilingEntry(new Key(minCapacity, 0));

        final ByteBuffer buffer;
        if (entry == null) {
            buffer = createNewBuffer(minCapacity);
        } else {
            bufferMap.remove(entry.getKey());
            buffer = entry.getValue();
        }
//        if (LOGGER.isDebugEnabled()) {
//            getCount.incrementAndGet();
//            requestsMap.computeIfAbsent(minCapacity, k -> new AtomicInteger(0))
//                    .incrementAndGet();
//        }
        return buffer;
    }

    private ByteBuffer createNewBuffer(final int capacity) {
//        totalPooledCapacity.addAndGet(capacity);
//        largestBufferInPool.updateAndGet(currVal ->
//                Math.max(currVal, capacity));
        return ByteBuffer.allocateDirect(capacity);
    }

    //    void release(final ByteBuffer buffer) {
//        // This method is called a LOT so needs to be performant
//        if (buffer != null) {
//            if (!buffer.isDirect()) {
//                throw new RuntimeException("Expecting a direct ByteBuffer");
//            }
//            // Not certain we need to zero the buffer if clear is called as any users of it
//            // should be immediately writing to part of it and setting the limit/pos
////            for (int i = buffer.position(); i < buffer.limit(); i++) {
////                buffer.put((byte)0);
////            }
//            buffer.clear();
//
//            try {
//                int capacity = buffer.capacity();
//                bufferQueueMap.computeIfAbsent(capacity, k -> new LinkedBlockingQueue<>())
//                        .offer(buffer);
//
//            } catch (Exception e) {
//                // if a buffer is not released back to the pool then it is not the end of the world
//                // is we can just create more as required.
//                throw new RuntimeException("Error releasing buffer back to the pool", e);
//            }
//        }
//        if (LOGGER.isDebugEnabled()) {
//            releaseCount.incrementAndGet();
//        }
//    }

    private synchronized void release(final ByteBuffer buffer) {
        if (buffer != null && buffer.isDirect()) {
            for (int i = buffer.position(); i < buffer.limit(); i++) {
                buffer.put((byte) 0);
            }
            buffer.clear();

            try {
                while (true) {
                    final Key key = new Key(buffer.capacity(), System.nanoTime());
                    if (!bufferMap.containsKey(key)) {
                        bufferMap.put(key, buffer);
                        return;
                    }
                    // Buffers are indexed by (capacity, time).
                    // If our key is not unique on the first try, we try again, since the
                    // time will be different.  Since we use nanoseconds, it's pretty
                    // unlikely that we'll loop even once, unless the system clock has a
                    // poor granularity.
                }
            } catch (final Exception e) {
                // if a buffer is not released back to the pool then it is not the end of the world
                // is we can just create more as required.
                throw new RuntimeException("Error releasing buffer back to the pool", e);
            }
        }
//        if (LOGGER.isDebugEnabled()) {
//            releaseCount.incrementAndGet();
//        }
    }

    @Override
    public PooledByteBufferPair getPooledBufferPair(final int minKeyCapacity, final int minValueCapacity) {
        final ByteBuffer keyBuffer = getBuffer(minKeyCapacity);
        final ByteBuffer valueBuffer = getBuffer(minValueCapacity);
        return new PooledByteBufferPairImpl(this::release, keyBuffer, valueBuffer);
    }

    /**
     * Perform work with a {@link ByteBuffer} obtained from the pool. The {@link ByteBuffer}
     * must not be used outside of the work lambda.
     */
    @Override
    public <T> T getWithBuffer(final int minCapacity, final Function<ByteBuffer, T> work) {
        ByteBuffer buffer = null;
        try {
            buffer = getBuffer(minCapacity);
            return work.apply(buffer);
        } finally {
            if (buffer != null) {
                release(buffer);
            }
        }
    }

    /**
     * Perform work with a {@link ByteBuffer} obtained from the pool. The {@link ByteBuffer}
     * must not be used outside of the work lambda.
     */
    @Override
    public void doWithBuffer(final int minCapacity, final Consumer<ByteBuffer> work) {
        ByteBuffer buffer = null;
        try {
            buffer = getBuffer(minCapacity);
            work.accept(buffer);
        } finally {
            if (buffer != null) {
                release(buffer);
            }
        }
    }

    @Override
    public void doWithBufferPair(final int minKeyCapacity,
                                 final int minValueCapacity,
                                 final BiConsumer<ByteBuffer, ByteBuffer> work) {
        ByteBuffer keyBuffer = null;
        ByteBuffer valueBuffer = null;
        try {
            keyBuffer = getBuffer(minKeyCapacity);
            valueBuffer = getBuffer(minValueCapacity);
            work.accept(keyBuffer, valueBuffer);
        } finally {
            if (keyBuffer != null) {
                release(keyBuffer);
            }
            if (valueBuffer != null) {
                release(valueBuffer);
            }
        }
    }

    @Override
    public int getCurrentPoolSize() {
        return bufferMap.size();
    }

    @Override
    public String toString() {
        return "ByteBufferPool{" +
                "bufferMap=" + bufferMap +
                '}';
    }

    @Override
    public synchronized void clear() {
        bufferMap.clear();
//        getCount.set(0);
//        releaseCount.set(0);
//        requestsMap.clear();
//        totalPooledCapacity.set(0);
//        largestBufferInPool.set(0);
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        try {
            final SystemInfoResult.Builder builder = SystemInfoResult.builder(this)
                    .addDetail("Size", getCurrentPoolSize());
//                    .withDetail("Largest buffer", largestBufferInPool.get());

            SortedMap<Integer, Long> capacityCountsMap = null;
            try {
                capacityCountsMap = bufferMap.entrySet().stream()
                        .collect(Collectors.groupingBy(entry ->
                                        entry.getValue().capacity(),
                                Collectors.counting()))
                        .entrySet()
                        .stream()
                        .collect(HasHealthCheck.buildTreeMapCollector(Map.Entry::getKey, Map.Entry::getValue));

                builder.addDetail("Buffer capacity counts", capacityCountsMap);
            } catch (final Exception e) {
                LOGGER.error("Error getting capacity counts", e);
                builder.addDetail("Buffer capacity counts", "Error getting counts");
            }

//            if (LOGGER.isDebugEnabled()) {
//                builder.withDetail("Requests breakdown", requestsMap);
//                builder.withDetail("Get count", getCount.intValue());
//                builder.withDetail("Release count", releaseCount.intValue());
//                builder.withDetail("Get/Release delta", Math.abs(getCount.intValue() - releaseCount.intValue()));
//            }

            return builder.build();
        } catch (final RuntimeException e) {
            return SystemInfoResult.builder(this)
                    .addError(e)
                    .build();
        }
    }

    private static final class Key implements Comparable<Key> {

        private final int capacity;
        private final long insertionTime;

        Key(final int capacity, final long insertionTime) {
            this.capacity = capacity;
            this.insertionTime = insertionTime;
        }

        @Override
        public int compareTo(final Key other) {
            // This method is called many millions of times, hence no use of (Integer|Long).compareTo
            if (capacity < other.capacity) {
                return -1;
            } else if (capacity > other.capacity) {
                return 1;
            } else {
                if (insertionTime < other.insertionTime) {
                    return -1;
                } else if (insertionTime > other.insertionTime) {
                    return 1;
                } else {
                    return 0;
                }
            }
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Key key = (Key) o;
            return capacity == key.capacity &&
                    insertionTime == key.insertionTime;
        }

        @Override
        public int hashCode() {
            // No need to precompute this as it is not called by the map
            return Objects.hash(capacity, insertionTime);
        }

        @Override
        public String toString() {
            return "Key{" +
                    "capacity=" + capacity +
                    ", insertionTime=" + insertionTime +
                    '}';
        }
    }
}

