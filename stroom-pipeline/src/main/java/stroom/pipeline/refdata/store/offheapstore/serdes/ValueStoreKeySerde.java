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

package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.serde.Serde;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;
import stroom.util.logging.LogUtil;

import java.nio.ByteBuffer;
import java.util.Objects;

public class ValueStoreKeySerde implements Serde<ValueStoreKey> {

    public static final int VALUE_HASH_CODE_OFFSET = 0;
    public static final int VALUE_HASH_CODE_BYTES = Long.BYTES;
    public static final int ID_BYTES = Short.BYTES;
    private static final int BUFFER_CAPACITY = VALUE_HASH_CODE_BYTES + ID_BYTES;
    public static final int ID_OFFSET = VALUE_HASH_CODE_OFFSET + VALUE_HASH_CODE_BYTES;

    @Override
    public int getBufferCapacity() {
        return BUFFER_CAPACITY;
    }

    @Override
    public ValueStoreKey deserialize(final ByteBuffer byteBuffer) {
        final long hashCode = byteBuffer.getLong();
        final short uniqueId = byteBuffer.getShort();
        byteBuffer.flip();
        return new ValueStoreKey(hashCode, uniqueId);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final ValueStoreKey valueStoreKey) {
        byteBuffer
                .putLong(valueStoreKey.getValueHashCode())
                .putShort(valueStoreKey.getUniqueId());
        byteBuffer.flip();
    }

    /**
     * Increments the ID part of the {@link ByteBuffer} by one. Does not
     * alter the offset/limit
     */
    public static void incrementId(final ByteBuffer byteBuffer) {
        byteBuffer.putShort(
                ID_OFFSET,
                ((short) (byteBuffer.getShort(ID_OFFSET) + (short) 1)));
    }

    /**
     * Updates the ID part of the {@link ByteBuffer} by one. Does not
     * alter the offset/limit
     */
    public static void updateId(final ByteBuffer byteBuffer, final short newId) {
        byteBuffer.putShort(ID_OFFSET, newId);
    }

    /**
     * Extracts the valueHashCode part of the {@link ByteBuffer} by one. Does not
     * alter the offset/limit
     */
    public static long extractValueHashCode(final ByteBuffer byteBuffer) {
        return byteBuffer.getLong(VALUE_HASH_CODE_OFFSET);
    }

    /**
     * Extracts the ID part of the {@link ByteBuffer} by one. Does not
     * alter the offset/limit
     */
    public static short extractId(final ByteBuffer byteBuffer) {
        return byteBuffer.getShort(ID_OFFSET);
    }

    /**
     * Check equality of valueHashCode part of both byte buffers
     */
    public static boolean valueHashCodeEquals(final ByteBuffer thisBuffer, final ByteBuffer thatBuffer) {
        try {
            return Objects.equals(getValueHashCodeSlice(thisBuffer), getValueHashCodeSlice(thatBuffer));
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error checking equality [{}] & [{}]",
                    ByteBufferUtils.byteBufferInfo(thisBuffer),
                    ByteBufferUtils.byteBufferInfo(thatBuffer)), e);
        }
    }

    private static ByteBuffer getValueHashCodeSlice(final ByteBuffer byteBuffer) {
        return byteBuffer.slice(VALUE_HASH_CODE_OFFSET, VALUE_HASH_CODE_BYTES);
    }
}
