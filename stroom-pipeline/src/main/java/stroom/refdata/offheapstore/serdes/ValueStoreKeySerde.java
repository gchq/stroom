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

package stroom.refdata.offheapstore.serdes;

import org.apache.hadoop.hbase.util.ByteBufferUtils;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.ValueStoreKey;
import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;

public class ValueStoreKeySerde implements Serde<ValueStoreKey> {

    public static final int VALUE_HASH_CODE_OFFSET = 0;
    public static final int VALUE_HASH_CODE_BYTES = Integer.BYTES;
    private static final int SIZE_IN_BYTES = Integer.BYTES + Short.BYTES;
    public static final int ID_OFFSET = Integer.BYTES;

    @Override
    public int getBufferCapacity() {
        return SIZE_IN_BYTES;
    }

    @Override
    public ValueStoreKey deserialize(final ByteBuffer byteBuffer) {
        int hashCode = byteBuffer.getInt();
        short uniqueId = byteBuffer.getShort();
        byteBuffer.flip();
        return new ValueStoreKey(hashCode, uniqueId);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final ValueStoreKey valueStoreKey) {
        byteBuffer
                .putInt(valueStoreKey.getValueHashCode())
                .putShort(valueStoreKey.getUniqueId());
        byteBuffer.flip();
    }

    /**
     * Creates a new directly allocated {@link ByteBuffer} from the contents of
     * source, but with an ID value one greater than the ID value in source.
     * This method allows for the mutation of the ID part of the {@link ByteBuffer}
     * without having to fully de-serialise/serialise it.
     */
    public static ByteBuffer nextId(final ByteBuffer source) {
        short currId = source.getShort(ID_OFFSET);

        ByteBuffer output = ByteBuffer.allocateDirect(SIZE_IN_BYTES);

        ByteBuffer sourceDuplicate = source.duplicate();
        sourceDuplicate.limit(Integer.BYTES);
        output.put(sourceDuplicate);
        output.putShort((short) (currId + 1));
        output.flip();
        return output;
    }

    /**
     * Increments the ID part of the {@link ByteBuffer} by one. Does not
     * alter the offset/limit
     */
    public static void incrementId(final ByteBuffer byteBuffer) {
        short currId = byteBuffer.getShort(ID_OFFSET);
        byteBuffer.putShort(ID_OFFSET, (short) (currId + 1));
    }

    /**
     * Updates the ID part of the {@link ByteBuffer} by one. Does not
     * alter the offset/limit
     */
    public static void updateId(final ByteBuffer byteBuffer, final short newId) {
        byteBuffer.putShort(ID_OFFSET, newId);
    }

    /**
     * Extracts the ID part of the {@link ByteBuffer} by one. Does not
     * alter the offset/limit
     */
    public static short extractId(final ByteBuffer byteBuffer) {
        return byteBuffer.getShort(ID_OFFSET);
    }

    /**
     * Compare the valueHashCode part of both byte buffers, comparing in byte form
     */
    public static int compareValueHashCode(final ByteBuffer thisBuffer, final ByteBuffer thatBuffer) {
        try {
            return ByteBufferUtils.compareTo(
                    thisBuffer, VALUE_HASH_CODE_OFFSET, VALUE_HASH_CODE_BYTES,
                    thatBuffer, VALUE_HASH_CODE_OFFSET, VALUE_HASH_CODE_BYTES);
        } catch (Exception e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error comparing [{}] & [{}]",
                    stroom.refdata.offheapstore.ByteBufferUtils.byteBufferInfo(thisBuffer),
                    stroom.refdata.offheapstore.ByteBufferUtils.byteBufferInfo(thatBuffer)), e);
        }
    }
}
