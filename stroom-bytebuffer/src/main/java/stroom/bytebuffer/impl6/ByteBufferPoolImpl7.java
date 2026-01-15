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

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferSupport;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferPair;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl.Pool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.sysinfo.SystemInfoResult;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
public class ByteBufferPoolImpl7 implements ByteBufferPool {

    private final ByteBufferFactoryImpl byteBufferFactory;

    @Inject
    public ByteBufferPoolImpl7(final ByteBufferFactoryImpl byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    @Override
    public PooledByteBuffer getPooledByteBuffer(final int minCapacity) {
        return getPooledBufferByMinCapacity(minCapacity);
    }

    private PooledByteBuffer getPooledBufferByMinCapacity(final int size) {
        if (size <= ByteBufferFactoryImpl.MAX_CACHED_BUFFER_SIZE) {
            final int exponent = byteBufferFactory.getExponent(size);
            final Pool pool = byteBufferFactory.pools[exponent];
            ByteBuffer byteBuffer = pool.poll();
            if (byteBuffer != null) {
                if (byteBuffer.capacity() >= size) {
                    byteBuffer.clear();
                    return new PooledByteBufferImpl(pool, byteBuffer);
                } else {
                    ByteBufferSupport.unmap(byteBuffer);
                }
            }

            final int roundedSize = ByteBufferFactoryImpl.pow2(exponent);
            byteBuffer = ByteBuffer.allocateDirect(roundedSize);
            return new PooledByteBufferImpl(pool, byteBuffer);

        } else {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(size);
            return new NonPooledByteBuffer(byteBuffer);
        }
    }

    @Override
    public PooledByteBufferPair getPooledBufferPair(final int minKeyCapacity, final int minValueCapacity) {
        final PooledByteBuffer keyBuffer = getPooledBufferByMinCapacity(minKeyCapacity);
        final PooledByteBuffer valueBuffer = getPooledBufferByMinCapacity(minValueCapacity);
        return new PooledByteBufferPairImpl(keyBuffer, valueBuffer);
    }

    @Override
    public <T> T getWithBuffer(final int minCapacity, final Function<ByteBuffer, T> work) {
        try (final PooledByteBuffer buffer = getPooledBufferByMinCapacity(minCapacity)) {
            return work.apply(buffer.getByteBuffer());
        }
    }

    @Override
    public void doWithBuffer(final int minCapacity, final Consumer<ByteBuffer> work) {
        try (final PooledByteBuffer buffer = getPooledBufferByMinCapacity(minCapacity)) {
            work.accept(buffer.getByteBuffer());
        }
    }

    @Override
    public void doWithBufferPair(final int minKeyCapacity,
                                 final int minValueCapacity,
                                 final BiConsumer<ByteBuffer, ByteBuffer> work) {
        try (final PooledByteBuffer keyBuffer = getPooledBufferByMinCapacity(minKeyCapacity);
                final PooledByteBuffer valueBuffer = getPooledBufferByMinCapacity(minValueCapacity)) {
            work.accept(keyBuffer.getByteBuffer(), valueBuffer.getByteBuffer());
        }
    }

    @Override
    public int getCurrentPoolSize() {
        return 0;
//        return Arrays.stream(pooledBufferQueues)
//                .filter(Objects::nonNull)
//                .mapToInt(PooledByteBufferQueue::size)
//                .sum();
    }

    @Override
    public void clear() {
//        // Allows the UI to clear buffers sat in the pool. Buffers on loan are unaffected
//        final List<String> msgs = new ArrayList<>();
//        for (final PooledByteBufferQueue pooledBufferQueue : pooledBufferQueues) {
//            if (pooledBufferQueue != null) {
//                pooledBufferQueue.clear(msgs);
//            }
//        }
//
//        LOGGER.info("Cleared the following buffers from the pool (buffer size:number cleared) - " +
//                String.join(", ", msgs));
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        return null;

//        try {
//            SystemInfoResult.Builder builder = SystemInfoResult.builder(this)
//                    .addDetail("Total buffers in pool", getCurrentPoolSize());
//
//            final SortedMap<Integer, Map<String, Integer>> offsetMapOfInfoMaps = new TreeMap<>();
//            int overallTotalSizeBytes = 0;
//
//            try {
//                for (int offset = 0; offset < sizesCount; offset++) {
//                    final SortedMap<String, Integer> infoMap = new TreeMap<>();
//
//                    final PooledByteBufferQueue pooledBufferQueue = pooledBufferQueues[offset];
//                    final int availableBuffersOnQueue = pooledBufferQueue != null
//                            ? pooledBufferQueue.size()
//                            : -1;
//                    final int buffersHighWaterMark = pooledBufferQueue != null
//                            ? pooledBufferQueue.getPooledBufferCount()
//                            : 0;
//                    final int bufferCapacity = pooledBufferQueue != null
//                            ? pooledBufferQueue.getBufferSize()
//                            : 0;
//
//                    final int buffersOnLoan = buffersHighWaterMark - availableBuffersOnQueue;
//                    final int configuredMaximum = Optional.ofNullable(
//                                    byteBufferPoolConfigProvider.get().getPooledByteBufferCounts())
//                            .map(map -> map.get(bufferCapacity))
//                            .orElse(DEFAULT_MAX_BUFFERS_PER_QUEUE);
//
//                    final int totalSizeBytes = buffersHighWaterMark * bufferCapacity;
//                    overallTotalSizeBytes += totalSizeBytes;
//
//                    infoMap.put("Buffers available in pool", availableBuffersOnQueue);
//                    infoMap.put("Buffers available or on loan", buffersHighWaterMark);
//                    infoMap.put("Buffers on loan", buffersOnLoan);
//                    infoMap.put("Total size (bytes)", totalSizeBytes);
//                    infoMap.put("Configured max buffers", configuredMaximum);
//
//                    offsetMapOfInfoMaps.put(bufferCapacity, infoMap);
//                }
//
//                builder
//                        .addDetail("Pooled buffers (grouped by buffer capacity)", offsetMapOfInfoMaps)
//                        .addDetail("Overall total size (bytes)", overallTotalSizeBytes);
//            } catch (Exception e) {
//                LOGGER.error("Error getting capacity counts", e);
//                builder.addDetail("Buffer capacity counts", "Error getting counts");
//            }
//
//            return builder.build();
//        } catch (RuntimeException e) {
//            return SystemInfoResult.builder(this)
//                    .addError(e)
//                    .build();
//        }
    }


    private static class NonPooledByteBuffer implements PooledByteBuffer {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(stroom.bytebuffer.impl6.NonPooledByteBuffer.class);

        private ByteBuffer byteBuffer;

        NonPooledByteBuffer(final ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
        }

        /**
         * @return The underlying {@link ByteBuffer} that was obtained from the pool.
         * Depending on the implementation of the pool this method may block if the pool has no buffers when called.
         * The returned {@link ByteBuffer} must not be used once release/close are called.
         */
        @Override
        public ByteBuffer getByteBuffer() {
            Objects.requireNonNull(byteBuffer, "Already released");
            return byteBuffer;
        }

        /**
         * A buffer will be obtained from the pool and passed to the byteBufferConsumer to use.
         * On completion of byteBufferConsumer the buffer will be released and will not be available
         * for any further use.
         */
        @Override
        public void doWithByteBuffer(final Consumer<ByteBuffer> byteBufferConsumer) {
            try (this) {
                byteBufferConsumer.accept(byteBuffer);
            }
        }

        /**
         * Clears the underlying buffer if there is one.
         */
        @Override
        public void clear() {
            Objects.requireNonNull(byteBuffer, "Already released");
            byteBuffer.clear();
        }

        @Override
        public Integer getCapacity() {
            return byteBuffer.capacity();
        }

        /**
         * Release the underlying {@link ByteBuffer} back to the pool. Once released,
         * the {@link ByteBuffer} cannot be used any more and you should not retain any
         * references to it.
         */
        @Override
        public void close() {
            Objects.requireNonNull(byteBuffer, "Already released");
            if (byteBuffer.isDirect()) {
                try {
                    LOGGER.debug("Unmapping buffer {}", byteBuffer);
                    ByteBufferSupport.unmap(byteBuffer);
                } catch (final Exception e) {
                    LOGGER.error("Error releasing direct byte buffer", e);
                }
            }
            byteBuffer = null;
        }

        @Override
        public String toString() {
            return "NonPooledByteBuffer{" +
                    "byteBuffer=" + ByteBufferUtils.byteBufferInfo(byteBuffer) +
                    '}';
        }
    }


    private static class PooledByteBufferImpl implements PooledByteBuffer {

        private final Pool pooledByteBufferQueue;
        private ByteBuffer byteBuffer;

        PooledByteBufferImpl(final Pool pooledByteBufferQueue,
                             final ByteBuffer byteBuffer) {
            this.pooledByteBufferQueue = pooledByteBufferQueue;
            this.byteBuffer = byteBuffer;
        }

        /**
         * @return The underlying {@link ByteBuffer} that was obtained from the pool.
         * Depending on the implementation of the pool this method may block if the pool has no buffers when called.
         * The returned {@link ByteBuffer} must not be used once release/close are called.
         */
        @Override
        public ByteBuffer getByteBuffer() {
            Objects.requireNonNull(byteBuffer, "Already released");
            return byteBuffer;
        }

        /**
         * A buffer will be obtained from the pool and passed to the byteBufferConsumer to use.
         * On completion of byteBufferConsumer the buffer will be released and will not be available
         * for any further use.
         */
        @Override
        public void doWithByteBuffer(final Consumer<ByteBuffer> byteBufferConsumer) {
            Objects.requireNonNull(byteBuffer, "Already released");
            try (this) {
                byteBufferConsumer.accept(byteBuffer);
            }
        }

        /**
         * Clears the underlying buffer if there is one.
         */
        @Override
        public void clear() {
            Objects.requireNonNull(byteBuffer, "Already released");
            byteBuffer.clear();
        }

        @Override
        public Integer getCapacity() {
            Objects.requireNonNull(byteBuffer, "Already released");
            return byteBuffer.capacity();
        }

        /**
         * Release the underlying {@link ByteBuffer} back to the pool. Once released,
         * the {@link ByteBuffer} cannot be used any more and you should not retain any
         * references to it.
         */
        @Override
        public void close() {
            Objects.requireNonNull(byteBuffer, "Already released");
            if (!pooledByteBufferQueue.offer(byteBuffer)) {
                ByteBufferSupport.unmap(byteBuffer);
                byteBuffer = null;
            }
        }

        @Override
        public String toString() {
            return "PooledByteBuffer{" +
                    "byteBuffer=" + ByteBufferUtils.byteBufferInfo(byteBuffer) +
                    '}';
        }
    }
}
