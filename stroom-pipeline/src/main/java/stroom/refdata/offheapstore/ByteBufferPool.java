/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import stroom.entity.shared.Clearable;
import stroom.util.logging.LambdaLogger;

import javax.inject.Singleton;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;


/*
 * This class is derived/copied from org.apache.hadoop.io.ElasticByteBufferPool
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
 */
@Singleton
public class ByteBufferPool implements Clearable {

    private final TreeMap<Key, ByteBuffer> bufferMap = new TreeMap<>();

    public synchronized ByteBuffer getBuffer(final int minCapacity) {
        // get a buffer at least as big as minCapacity with the smallest insertionTime
        final Map.Entry<Key, ByteBuffer> entry = bufferMap.ceilingEntry(new Key(minCapacity, 0));

        final ByteBuffer buffer;
        if (entry == null) {
            buffer = ByteBuffer.allocateDirect(minCapacity);
        } else {
            bufferMap.remove(entry.getKey());
            buffer = entry.getValue();
        }
        return buffer;
    }

    public synchronized void release(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        if (!buffer.isDirect()) {
            throw new RuntimeException(LambdaLogger.buildMessage("Expecting a direct ByteBuffer"));
        }
        buffer.clear();

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
    }

    public BufferPair getBufferPair(final int minKeyCapacity, final int minValueCapacity) {
        ByteBuffer keyBuffer = getBuffer(minKeyCapacity);
        ByteBuffer valueBuffer = getBuffer(minValueCapacity);
        return BufferPair.of(keyBuffer, valueBuffer);
    }

    public void release(BufferPair bufferPair) {
        Objects.requireNonNull(bufferPair);
        release(bufferPair.getKeyBuffer());
        release(bufferPair.getValueBuffer());
    }

    /**
     * Perform work with a {@link ByteBuffer} obtained from the pool. The {@link ByteBuffer}
     * must not be used outside of the work lambda.
     */
    public <T> T getWithBuffer(final int minCapacity, Function<ByteBuffer, T> work) {
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
    public void doWithBuffer(final int minCapacity, Consumer<ByteBuffer> work) {
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

    /**
     * Perform work with a {@link BufferPair} obtained from the pool. The {@link BufferPair}
     * must not be used outside of the work lambda.
     */
    public <T> T getWithBufferPair(final int minKeyCapacity, final int minValueCapacity, Function<BufferPair, T> work) {
        BufferPair bufferPair = null;
        try {
            bufferPair = getBufferPair(minKeyCapacity, minValueCapacity);
            return work.apply(bufferPair);
        } finally {
            if (bufferPair != null) {
                release(bufferPair);
            }
        }
    }

    /**
     * Perform work with a {@link BufferPair} obtained from the pool. The {@link BufferPair}
     * must not be used outside of the work lambda.
     */
    public void doWithBufferPair(final int minKeyCapacity, final int minValueCapacity, Consumer<BufferPair> work) {
        BufferPair bufferPair = null;
        try {
            bufferPair = getBufferPair(minKeyCapacity, minValueCapacity);
            work.accept(bufferPair);
        } finally {
            if (bufferPair != null) {
                release(bufferPair);
            }
        }
    }

    public synchronized int getCurrentPoolSize() {
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
    }

    private static final class Key implements Comparable<Key> {
        private final int capacity;
        private final long insertionTime;

        private final Comparator<Key> comparator = Comparator
                .comparingInt(Key::getCapacity)
                .thenComparingLong(Key::getInsertionTime);

        private final int hashCode;

        Key(int capacity, long insertionTime) {
            this.capacity = capacity;
            this.insertionTime = insertionTime;
            this.hashCode = Objects.hash(capacity, insertionTime);
        }

        int getCapacity() {
            return capacity;
        }

        long getInsertionTime() {
            return insertionTime;
        }

        @Override
        public int compareTo(Key other) {
            return comparator.compare(this, other);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Key key = (Key) o;
            return capacity == key.capacity &&
                    insertionTime == key.insertionTime;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}

