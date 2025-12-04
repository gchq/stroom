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

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.KryoBufferOverflowException;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;
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
public class ByteBufferPoolOutput extends UnsafeByteBufferOutput {

    private final ByteBufferFactory byteBufferFactory;

    public ByteBufferPoolOutput(final ByteBufferFactory byteBufferFactory,
                                final int bufferSize,
                                final int maxBufferSize) {
        this.byteBufferFactory = byteBufferFactory;
        if (maxBufferSize < -1) {
            throw new IllegalArgumentException("maxBufferSize cannot be < -1: " + maxBufferSize);
        }
        this.capacity = bufferSize;
        this.maxCapacity = maxBufferSize == -1
                ? Util.maxArraySize
                : maxBufferSize;
        setBuffer(byteBufferFactory.acquire(bufferSize), -1);
    }

    protected boolean require(final int required) throws KryoException {
        if (this.capacity - this.position >= required) {
            return false;

        } else {
            this.flush();
            if (this.capacity - this.position >= required) {
                return true;
            } else if (required > this.maxCapacity - this.position) {
                if (required > this.maxCapacity) {
                    throw new KryoBufferOverflowException("Buffer overflow. Max capacity: " + this.maxCapacity + ", required: " + required);
                } else {
                    throw new KryoBufferOverflowException("Buffer overflow. Available: " + (this.maxCapacity - this.position) + ", required: " + required);
                }
            } else {
                if (this.capacity == 0) {
                    this.capacity = 16;
                }
                do {
                    this.capacity = Math.min(this.capacity * 2, this.maxCapacity);
                } while (this.capacity - this.position < required);

                final ByteBuffer oldBuffer = this.byteBuffer;
                final ByteBuffer newBuffer = this.byteBufferFactory.acquire(this.capacity);
                oldBuffer.position(0);
                oldBuffer.limit(position);
                newBuffer.put(oldBuffer);
                newBuffer.order(oldBuffer.order());
                setBuffer(newBuffer, -1);

                this.byteBuffer = newBuffer;
                this.byteBufferFactory.release(oldBuffer);

                return true;
            }
        }
    }

    public void writeByteBuffer(final ByteBuffer byteBuffer) {
        final int length = byteBuffer.remaining();
        require(length);
        this.byteBuffer.put(byteBuffer);
        position += length;
    }
}
