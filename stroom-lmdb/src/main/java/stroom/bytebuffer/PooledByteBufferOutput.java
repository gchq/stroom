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

package stroom.bytebuffer;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.KryoBufferOverflowException;
import com.esotericsoftware.kryo.util.Util;

import java.nio.ByteBuffer;

/*
 * This class is derived from and copies parts of
 * org.apache.hadoop.hbase.io.ByteBufferOutputStream
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
public class PooledByteBufferOutput extends ByteBufferOutput {

    private final ByteBufferPool byteBufferPool;
    private PooledByteBuffer pooledByteBuffer;

    PooledByteBufferOutput(final ByteBufferPool byteBufferPool,
                                  final int bufferSize,
                                  final int maxBufferSize) {
        this.byteBufferPool = byteBufferPool;
        if (maxBufferSize < -1) {
            throw new IllegalArgumentException("maxBufferSize cannot be < -1: " + maxBufferSize);
        }
        this.capacity = bufferSize;
        this.maxCapacity = maxBufferSize == -1
                ? Util.maxArraySize
                : maxBufferSize;
        pooledByteBuffer = byteBufferPool.getPooledByteBuffer(bufferSize);
        byteBuffer = pooledByteBuffer.getByteBuffer();
    }

    protected boolean require(final int required) throws KryoException {
        if (capacity - position >= required) {
            return false;
        }
        flush();
        if (capacity - position >= required) {
            return true;
        }
        if (required > maxCapacity - position) {
            if (required > maxCapacity) {
                throw new KryoBufferOverflowException("Buffer overflow. Max capacity: " + maxCapacity + ", required: " + required);
            }
            throw new KryoBufferOverflowException(
                    "Buffer overflow. Available: " + (maxCapacity - position) + ", required: " + required);
        }
        if (capacity == 0) {
            capacity = 16;
        }
        do {
            capacity = Math.min(capacity * 2, maxCapacity);
        } while (capacity - position < required);
        final PooledByteBuffer newPooledByteBuffer = byteBufferPool.getPooledByteBuffer(capacity);
        final ByteBuffer newBuffer = newPooledByteBuffer.getByteBuffer();
        byteBuffer.position(0);
        byteBuffer.limit(position);
        newBuffer.put(byteBuffer);
        newBuffer.order(byteBuffer.order());
        byteBuffer = newBuffer;

        pooledByteBuffer.release();
        pooledByteBuffer = newPooledByteBuffer;

        return true;
    }

    public PooledByteBuffer getPooledByteBuffer() {
        return pooledByteBuffer;
    }
}
