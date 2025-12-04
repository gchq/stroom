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

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

public class ByteBufferFactoryImpl implements ByteBufferFactory {

    private static final double LOG2 = Math.log(2);
    static final int MAX_CACHED_BUFFER_SIZE = 1024;

    // Cache buffers big enough for single integers and above.
    private static final int MIN_CACHED_BUFFER_SIZE = Integer.BYTES;
    private final int minExponent;

    final Pool[] pools;

    public ByteBufferFactoryImpl() {
        minExponent = getMinExponent(MIN_CACHED_BUFFER_SIZE);
        final int exponent = getExponent(MAX_CACHED_BUFFER_SIZE);
        pools = new Pool[exponent + 1];
        for (int i = minExponent; i < pools.length; i++) {
            pools[i] = new Pool(1000);
        }
    }

    @Override
    public ByteBuffer acquire(final int size) {
        if (size <= MAX_CACHED_BUFFER_SIZE) {
            final int exponent = getExponent(size);
            final Pool pool = pools[exponent];
            final ByteBuffer byteBuffer = pool.poll();
            if (byteBuffer != null) {
                if (byteBuffer.capacity() >= size) {
                    byteBuffer.clear();
                    return byteBuffer;
                } else {
                    unmap(byteBuffer);
                }
            }

            final int roundedSize = pow2(exponent);
            return ByteBuffer.allocateDirect(roundedSize);
        }

        return ByteBuffer.allocateDirect(size);
    }

    @Override
    public void release(final ByteBuffer byteBuffer) {
        if (byteBuffer != null) {
            if (byteBuffer.capacity() > MAX_CACHED_BUFFER_SIZE) {
                unmap(byteBuffer);
            } else {
                final int exponent = getExponent(byteBuffer.capacity());
                final Pool pool = pools[exponent];
                if (!pool.offer(byteBuffer)) {
                    unmap(byteBuffer);
                }
            }
        }
    }

    int getExponent(final int size) {
        return Math.max(minExponent, getMinExponent(size));
    }

    private static int getMinExponent(final int n) {
        return (int) Math.ceil(log2(n));
    }

    public static int pow2(final int exponent) {
        return (int) Math.pow(2, exponent);
    }

    private static double log2(final int n) {
        return Math.log(n) / LOG2;
    }

    private void unmap(final ByteBuffer byteBuffer) {
        ByteBufferSupport.unmap(byteBuffer);
    }


    // --------------------------------------------------------------------------------


    static class Pool {

        private final ArrayBlockingQueue<ByteBuffer> queue;

        public Pool(final int capacity) {
            queue = new ArrayBlockingQueue<>(capacity);
        }

        public ByteBuffer poll() {
            return queue.poll();
        }

        public boolean offer(final ByteBuffer byteBuffer) {
            return queue.offer(byteBuffer);
        }
    }
}
