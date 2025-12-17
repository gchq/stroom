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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Preconditions;
import jakarta.validation.constraints.NotNull;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A wrapper over a {@link ByteBuffer} that contains a UID. A UID is a fixed width
 * set of bytes (see UID_ARRAY_LENGTH) that forms a unique identifier. The underlying
 * {@link ByteBuffer} MUST not be mutated.
 */
public class UID implements Comparable<UID> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UID.class);

    // Changing this value would require any data stored using UIDs to be
    // migrated to the new byte array length
    public static final int UID_ARRAY_LENGTH = 4;

    public static final UnsignedBytes UNSIGNED_BYTES = UnsignedBytesInstances.ofLength(UID_ARRAY_LENGTH);

    private final ByteBuffer byteBuffer;

    private UID(final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    /**
     * Wraps a new UID around the passed {@link ByteBuffer} without copying.
     * The buffer should be ready
     */
    public static UID wrap(final ByteBuffer byteBuffer) {
        LOGGER.trace(() -> "buffer: " + ByteBufferUtils.byteBufferInfo(byteBuffer));

        if (byteBuffer.remaining() != UID_ARRAY_LENGTH) {
            throw new RuntimeException(LogUtil.message(
                    "Bytebuffer should have {} bytes remaining, buffer: {}",
                    UID_ARRAY_LENGTH, ByteBufferUtils.byteBufferInfo(byteBuffer)));
        }
        return new UID(byteBuffer);
    }

    /**
     * For use in testing only, e.g. <pre>UID uid = UID.of(0, 0, 1, 0);</pre>
     */
    public static UID of(final ByteBuffer byteBuffer, final int... byteValues) {
        Preconditions.checkArgument(byteValues.length == UID_ARRAY_LENGTH);
        for (int i = 0; i < UID_ARRAY_LENGTH; i++) {
            final byte b = (byte) byteValues[i];
            byteBuffer.put(b);
        }
        byteBuffer.flip();
        return new UID(byteBuffer);
    }

    /**
     * Writes the value to the passed buffer and wraps it with a UID.
     */
    public static UID of(final long value, final ByteBuffer byteBuffer) {
        UID.writeUid(value, byteBuffer);
        return UID.wrap(byteBuffer);
    }

    /**
     * Write the lowest UID value (i.e. 0) into the passed buffer.
     */
    public static UID minimumValue(final ByteBuffer byteBuffer) {
        writeMinimumValue(byteBuffer);
        return UID.wrap(byteBuffer);
    }

    /**
     * Write the lowest uid value into the buffer and leave it ready for reading
     *
     * @param byteBuffer
     */
    public static void writeMinimumValue(final ByteBuffer byteBuffer) {
        for (int i = 0; i < UID_ARRAY_LENGTH; i++) {
            byteBuffer.put((byte) 0);
        }
        // Get ready for reading
        byteBuffer.flip();
    }

    /**
     * @return A newly allocated byte buffer containing the same UID bytes as this. Useful when this UID
     * wraps an LMDB managed bytebuffer that you want to de-associate from.
     */
    public UID cloneToNewBuffer() {
        final ByteBuffer newBuffer = ByteBuffer.allocateDirect(UID_ARRAY_LENGTH);
        newBuffer.put(byteBuffer);
        byteBuffer.rewind();
        newBuffer.flip();
        return new UID(newBuffer);
    }

    /**
     * Clone the contents of this into the passed buffer. destByteBuffer will be left ready for reading
     */
    public UID cloneToBuffer(final ByteBuffer destByteBuffer) {
        destByteBuffer.put(byteBuffer);
        byteBuffer.rewind();
        destByteBuffer.flip();
        return new UID(destByteBuffer);
    }

    public long getValue() {
        return UNSIGNED_BYTES.get(byteBuffer, byteBuffer.position());
    }

    /**
     * Increments the UID bytes found at offset 0 in byteBuffer to the next UID value.
     * The position/limit of byteBuffer are unchanged.
     */
    public static void incrementUid(final ByteBuffer byteBuffer) {
        try {
            UNSIGNED_BYTES.increment(byteBuffer, 0);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error incrementing UID. Current value {}. {}",
                    UNSIGNED_BYTES.get(byteBuffer, byteBuffer.position()),
                    e.getMessage()), e);
        }
    }

    /**
     * Increments the UID bytes found at offset in byteBuffer to the next UID value.
     * The position/limit of byteBuffer are unchanged.
     */
    public static void incrementUid(final ByteBuffer byteBuffer, final int offset) {
        UNSIGNED_BYTES.increment(byteBuffer, offset);
    }

    /**
     * Writes the next uid value after 'this' to the passed bytebuffer
     */
    public void writeNextUid(final ByteBuffer otherBuffer) {
        ByteBufferUtils.copy(this.byteBuffer, otherBuffer);
        try {
            // Increment the uid in place in the copy
            UNSIGNED_BYTES.increment(otherBuffer);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error writing next UID. Current value {}. {}",
                    UNSIGNED_BYTES.get(this.byteBuffer, this.byteBuffer.position()),
                    e.getMessage()), e);
        }
    }

    /**
     * @return A duplicate view of the backing buffer for the unique ID.
     * The returned buffer should not be mutated.
     */
    public ByteBuffer getBackingBuffer() {
        return byteBuffer.duplicate();
    }

    /**
     * @return The length of the UID itself, rather than the backing array which may be longer
     */
    public static int length() {
        return UID_ARRAY_LENGTH;
    }


    @Override
    public String toString() {
        return ByteBufferUtils.byteBufferInfo(byteBuffer);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UID uid = (UID) o;
        return Objects.equals(byteBuffer, uid.byteBuffer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(byteBuffer);
    }

    /**
     * Write a UID for id into the passed buffer, flips the buffer
     */
    private static ByteBuffer writeUid(final long id, final ByteBuffer byteBuffer) {
        UNSIGNED_BYTES.put(byteBuffer, id);
        byteBuffer.flip();
        return byteBuffer;
    }

    @Override
    public int compareTo(@NotNull final UID other) {
        return UNSIGNED_BYTES.compare(
                this.byteBuffer,
                this.byteBuffer.position(),
                other.byteBuffer,
                other.byteBuffer.position());
    }
}
