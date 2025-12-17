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

package stroom.lmdb.serde;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.util.logging.LogUtil;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;

public interface Serializer<T> {

    int DEFAULT_CAPACITY = 1_000;

    /**
     * @param byteBufferSupplier A supplier of a {@link ByteBuffer} in a ready state for writing. The
     *                           supplier may or may not be called depending on the impl so only use
     *                           the return value to get the serialised form.
     *                           May throw a {@link RuntimeException} if the supplied buffer
     *                           is not big enough to hold the serialised form.
     * @param object             The object to be serialised.
     * @return The serialised form of the object, flipped and ready for reading.
     */
    default ByteBuffer serialize(final Supplier<ByteBuffer> byteBufferSupplier,
                                 final T object) {
        final ByteBuffer byteBuffer = Objects.requireNonNull(byteBufferSupplier.get());
        try {
            serialize(byteBuffer, object);
        } catch (final BufferOverflowException boe) {
            throw new RuntimeException(LogUtil.message("Buffer {} too small for value {}",
                    ByteBufferUtils.byteBufferInfo(byteBuffer),
                    object));
        }
        return byteBuffer;
    }

    /**
     * Serialize object into the passed {@link ByteBuffer}. Assumes there is sufficient capacity.
     * This method will flip the buffer after writing to it.
     * May throw a {@link java.nio.BufferOverflowException} if the supplied buffer
     * is not big enough to hold the serialised form.
     */
    void serialize(final ByteBuffer byteBuffer, final T object);

    /**
     * Method for serialising the value when the length of the serialized form is unknown.
     *
     * @param pooledByteBufferOutputStream The {@link PooledByteBufferOutputStream} to serialize the object to.
     * @param object                       The object to be serialised.
     * @return The serialised form of the object, flipped and ready for reading. This buffer should
     * be used instead of calling {@link PooledByteBufferOutputStream#getByteBuffer()} in case
     * the implementation chooses not to use the passed {@link PooledByteBufferOutputStream}.
     * The returned {@link ByteBuffer} may be a pooled one so ensure that the close method
     * of pooledByteBufferOutputStream is called once byteBuffer is no longer needed.
     */
    default ByteBuffer serialize(final PooledByteBufferOutputStream pooledByteBufferOutputStream,
                                 final T object) {
        throw new UnsupportedOperationException("Not implemented for this serialiser");
    }

    /**
     * Allows the sub-class to specify the capacity of the buffer to be used in serialisation.
     */
    default int getBufferCapacity() {
        return DEFAULT_CAPACITY;
    }
}
