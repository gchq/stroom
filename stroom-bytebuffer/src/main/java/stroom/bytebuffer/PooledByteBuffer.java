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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;

/**
 * A lazy wrapper for a direct {@link ByteBuffer} obtained from a {@link ByteBufferPool} that can be used
 * with a try with resources block as it implements {@link AutoCloseable}. If not used
 * with a try with resources block then {@link PooledByteBuffer#close()} or
 * {@link PooledByteBuffer#close()} must be called when the underlying {@link ByteBuffer}
 * is no longer needed.
 * <p>
 * The wrapper is empty on creation and when getByteBuffer is called, it will be populated
 * with a {@link ByteBuffer} from the pool. Depending on the implementation of the pool this may block.
 */
public interface PooledByteBuffer extends AutoCloseable {

    /**
     * @return The underlying {@link ByteBuffer} that was obtained from the pool.
     * Depending on the implementation of the pool this method may block if the pool has no buffers when called.
     * The returned {@link ByteBuffer} must not be used once release/close are called.
     */
    ByteBuffer getByteBuffer();

    /**
     * A buffer will be obtained from the pool and passed to the byteBufferConsumer to use.
     * On completion of byteBufferConsumer the buffer will be released and will not be available
     * for any further use.
     */
    void doWithByteBuffer(final Consumer<ByteBuffer> byteBufferConsumer);


    /**
     * Release the underlying {@link ByteBuffer} back to the pool. Once released,
     * the {@link ByteBuffer} cannot be used any more and you should not retain any
     * references to it.
     */
    void close();


    // --------------------------------------------------------------------------------
    // The following methods all delegate to the underlying ByteBuffer to allow
    // operations on the byte buffer without having to call getByteBuffer().
    // --------------------------------------------------------------------------------

    /**
     * Clears the underlying buffer if there is one.
     */
    default void clear() {
        getByteBuffer().clear();
    }

    /**
     * The capacity of the underlying buffer if there is one.
     */
    default Integer capacity() {
        return getByteBuffer().capacity();
    }

    /**
     * Delegates to {@link ByteBuffer#order()}
     */
    default ByteOrder order() {
        return getByteBuffer().order();
    }

    /**
     * Delegates to {@link ByteBuffer#slice()}
     */
    default PooledByteBuffer slice() {
        getByteBuffer().slice();
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#slice(int index, int length)}
     */
    default PooledByteBuffer slice(int index, int length) {
        getByteBuffer().slice(index, length);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#asReadOnlyBuffer()}
     */
    default PooledByteBuffer asReadOnlyBuffer() {
        getByteBuffer().asReadOnlyBuffer();
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#get()}
     */
    default byte get() {
        return getByteBuffer().get();
    }

    /**
     * Delegates to {@link ByteBuffer#get(int index)}
     */
    default byte get(int index) {
        return getByteBuffer().get(index);
    }

    /**
     * Delegates to {@link ByteBuffer#get(byte[] dst, int offset, int length)}
     */
    default PooledByteBuffer get(byte[] dst, int offset, int length) {
        getByteBuffer().get(dst, offset, length);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#get(byte[] dst)}
     */
    default PooledByteBuffer get(byte[] dst) {
        getByteBuffer().get(dst);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#get(int index, byte[] dst, int offset, int length)}
     */
    default PooledByteBuffer get(int index, byte[] dst, int offset, int length) {
        getByteBuffer().get(index, dst, offset, length);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#get(int index, byte[] dst)}
     */
    default PooledByteBuffer get(int index, byte[] dst) {
        getByteBuffer().get(index, dst);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#put(byte[] src)}
     */
    default PooledByteBuffer put(byte[] src) {
        getByteBuffer().put(src);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#put(byte b)}
     */
    default PooledByteBuffer put(byte b) {
        getByteBuffer().put(b);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#put(int index, byte b)}
     */
    default PooledByteBuffer put(int index, byte b) {
        getByteBuffer().put(index, b);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#put(ByteBuffer src)}
     */
    default PooledByteBuffer put(ByteBuffer src) {
        getByteBuffer().put(src);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#put(int index, ByteBuffer src, int offset, int length)}
     */
    default PooledByteBuffer put(int index, ByteBuffer src, int offset, int length) {
        getByteBuffer().put(index, src, offset, length);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#put(byte[] src, int offset, int length)}
     */
    default PooledByteBuffer put(byte[] src, int offset, int length) {
        getByteBuffer().put(src, offset, length);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#put(int index, byte[] src, int offset, int length)}
     */
    default PooledByteBuffer put(int index, byte[] src, int offset, int length) {
        getByteBuffer().put(index, src, offset, length);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#put(int index, byte[] src)}
     */
    default PooledByteBuffer put(int index, byte[] src) {
        getByteBuffer().put(index, src);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#getChar()}
     */
    default char getChar() {
        return getByteBuffer().getChar();
    }

    /**
     * Delegates to {@link ByteBuffer#getChar(int index)}
     */
    default char getChar(int index) {
        return getByteBuffer().getChar(index);
    }

    /**
     * Delegates to {@link ByteBuffer#putChar(char value)}
     */
    default PooledByteBuffer putChar(char value) {
        getByteBuffer().putChar(value);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#putChar(int index, char value)}
     */
    default PooledByteBuffer putChar(int index, char value) {
        getByteBuffer().putChar(index, value);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#getShort()}
     */
    default short getShort() {
        return getByteBuffer().getShort();
    }

    /**
     * Delegates to {@link ByteBuffer#putShort(short value)}
     */
    default PooledByteBuffer putShort(short value) {
        getByteBuffer().putShort(value);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#getShort(int index)}
     */
    default short getShort(int index) {
        return getByteBuffer().getShort(index);
    }

    /**
     * Delegates to {@link ByteBuffer#putShort(int index, short value)}
     */
    default PooledByteBuffer putShort(int index, short value) {
        getByteBuffer().putShort(index, value);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#getInt()}
     */
    default int getInt() {
        return getByteBuffer().getInt();
    }

    /**
     * Delegates to {@link ByteBuffer#putInt(int value)}
     */
    default PooledByteBuffer putInt(int value) {
        getByteBuffer().putInt(value);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#getInt(int index)}
     */
    default int getInt(int index) {
        return getByteBuffer().getInt(index);
    }

    /**
     * Delegates to {@link ByteBuffer#putInt(int index, int value)}
     */
    default PooledByteBuffer putInt(int index, int value) {
        getByteBuffer().putInt(index, value);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#getLong()}
     */
    default long getLong() {
        return getByteBuffer().getLong();
    }

    /**
     * Delegates to {@link ByteBuffer#putLong(long value)}
     */
    default PooledByteBuffer putLong(long value) {
        getByteBuffer().putLong(value);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#getLong(int index)}
     */
    default long getLong(int index) {
        return getByteBuffer().getLong(index);
    }

    /**
     * Delegates to {@link ByteBuffer#putLong(int index, long value)}
     */
    default PooledByteBuffer putLong(int index, long value) {
        getByteBuffer().putLong(index, value);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#getFloat()}
     */
    default float getFloat() {
        return getByteBuffer().getFloat();
    }

    /**
     * Delegates to {@link ByteBuffer#putFloat(float value)}
     */
    default PooledByteBuffer putFloat(float value) {
        getByteBuffer().putFloat(value);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#getFloat(int index)}
     */
    default float getFloat(int index) {
        return getByteBuffer().getFloat(index);
    }

    /**
     * Delegates to {@link ByteBuffer#putFloat(int index, float value)}
     */
    default PooledByteBuffer putFloat(int index, float value) {
        getByteBuffer().putFloat(index, value);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#getDouble()}
     */
    default double getDouble() {
        return getByteBuffer().getDouble();
    }

    /**
     * Delegates to {@link ByteBuffer#putDouble(double value)}
     */
    default PooledByteBuffer putDouble(double value) {
        getByteBuffer().putDouble(value);
        return this;
    }

    /**
     * Delegates to {@link ByteBuffer#getDouble(int index)}
     */
    default double getDouble(int index) {
        return getByteBuffer().getDouble(index);
    }

    /**
     * Delegates to {@link ByteBuffer#putDouble(int index, double value)}
     */
    default PooledByteBuffer putDouble(int index, double value) {
        getByteBuffer().putDouble(index, value);
        return this;
    }
}
