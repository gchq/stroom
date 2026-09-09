/*
 * Copyright 2025 Crown Copyright
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

package stroom.util;


import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.UUID;

/**
 * Util methods for reading/writing {@link UUID}s in binary form.
 */
public class UuidUtil {

    public static final MemoryLayout UNALIGNED_UUID_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG_UNALIGNED
                    .withOrder(ByteOrder.BIG_ENDIAN)
                    .withName("uuidMostSignificant"),
            ValueLayout.JAVA_LONG_UNALIGNED
                    .withOrder(ByteOrder.BIG_ENDIAN)
                    .withName("uuidLeastSignificant"));

    public static final VarHandle UUID_MOST_SIGNIFICANT_HANDLE =
            UNALIGNED_UUID_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("uuidMostSignificant"));

    public static final VarHandle UUID_LEAST_SIGNIFICANT_HANDLE =
            UNALIGNED_UUID_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("uuidLeastSignificant"));

    public static final int UUID_BYTES = 16;

    private UuidUtil() {
    }

    public static byte[] toByteArray(final String uuid) {
        Objects.requireNonNull(uuid);
        return toByteArray(UUID.fromString(uuid));
    }

    public static byte[] toByteArray(final UUID uuid) {
        Objects.requireNonNull(uuid);
        final ByteBuffer buffer = ByteBuffer.allocate(UUID_BYTES);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        buffer.flip();
        return buffer.array();
    }

    public static UUID fromByteArray(final byte[] bytes, final int offset) {
        Objects.requireNonNull(bytes);
        Objects.checkFromIndexSize(offset, UUID_BYTES, bytes.length);
        final ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, UUID_BYTES);
        return readUuid(buffer);
    }

    public static void writeUuid(final String uuid, final ByteBuffer buffer) {
        Objects.requireNonNull(uuid);
        writeUuid(UUID.fromString(uuid), buffer);
    }

    /**
     * Writes the supplied uuid to the buffer, advancing the position of the buffer.
     * Does not flip the buffer. The uuid is written in BIG ENDIAN order.
     */
    public static void writeUuid(final UUID uuid, final ByteBuffer buffer) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(buffer);
        if (buffer.remaining() < UUID_BYTES) {
            throw new IllegalArgumentException("Buffer too small, expecting >=16 bytes remaining.");
        }
        final ByteOrder order = buffer.order();
        try {
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.putLong(uuid.getMostSignificantBits());
            buffer.putLong(uuid.getLeastSignificantBits());
        } finally {
            buffer.order(order);
        }
    }

    /**
     * Reads a {@link UUID} from a {@link ByteBuffer}. The {@link UUID} is read in BIG ENDIAN
     * order. Advances the buffer position.
     */
    public static UUID readUuid(final ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        if (buffer.remaining() < UUID_BYTES) {
            throw new IllegalArgumentException("Buffer too small, expecting >=16 bytes remaining.");
        }
        final ByteOrder order = buffer.order();
        try {
            final long mostSignificantBits = buffer.getLong();
            final long leastSignificantBits = buffer.getLong();
            return new UUID(mostSignificantBits, leastSignificantBits);
        } finally {
            buffer.order(order);
        }
    }

    /**
     * Writes a UUID into a MemorySegment at offset 0.
     */
    public static void writeUuid(final MemorySegment segment, final UUID uuid) {
        writeUuid(segment, uuid, 0L);
    }

    /**
     * Writes a UUID into a MemorySegment at the specified byte offset.
     */
    public static void writeUuid(final MemorySegment segment, final UUID uuid, final long offset) {
        UUID_MOST_SIGNIFICANT_HANDLE.set(segment, offset, uuid.getMostSignificantBits());
        UUID_LEAST_SIGNIFICANT_HANDLE.set(segment, offset, uuid.getLeastSignificantBits());
    }

    /**
     * Reads a UUID from a MemorySegment at the offset 0.
     */
    public static UUID readUuid(final MemorySegment segment) {
        return readUuid(segment, 0L);
    }

    /**
     * Reads a UUID from a MemorySegment at the specified byte offset.
     */
    public static UUID readUuid(final MemorySegment segment, final long offset) {
        final long mostSig = (long) UUID_MOST_SIGNIFICANT_HANDLE.get(segment, offset);
        final long leastSig = (long) UUID_LEAST_SIGNIFICANT_HANDLE.get(segment, offset);
        return new UUID(mostSig, leastSig);
    }

    /**
     * Reads a {@link UUID} from a {@link ByteBuffer}. The {@link UUID} is read in BIG ENDIAN
     * order. Does not advance the buffer position.
     */
    public static UUID readUuid(final ByteBuffer buffer, final int offset) {
        Objects.requireNonNull(buffer);
        Objects.checkFromIndexSize(offset, UUID_BYTES, buffer.limit());
        final ByteOrder order = buffer.order();
        try {
            final long mostSignificantBits = buffer.getLong(offset);
            final long leastSignificantBits = buffer.getLong(offset + Long.BYTES);
            return new UUID(mostSignificantBits, leastSignificantBits);
        } finally {
            buffer.order(order);
        }
    }
}
