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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.RefDataValue;

import java.nio.ByteBuffer;

/**
 * A serde that understands how to (de)serialise the type specific sub part of
 * a {@link RefDataValue}. This serde will only handle/see the content it knows about.
 */
public interface RefDatValueSubSerde extends Serde<RefDataValue> {

    Logger LOGGER = LoggerFactory.getLogger(RefDatValueSubSerde.class);

    // we expect to get buffers that cover only the sub part so offset take that into account
    int REFERENCE_COUNT_OFFSET = 0;
    int REFERENCE_COUNT_BYTES = Integer.BYTES;
    int VALUE_OFFSET = REFERENCE_COUNT_OFFSET + REFERENCE_COUNT_BYTES;

    /**
     * Gets the reference count from the buffer, advancing the position of the buffer past
     * the reference count part
     */
    default int getReferenceCount(ByteBuffer byteBuffer) {
        return byteBuffer.getInt();
    }

    /**
     * Extracts the reference count from the buffer, leaving the buffer unchanged
     */
    default int extractReferenceCount(ByteBuffer byteBuffer) {
        return byteBuffer.getInt(REFERENCE_COUNT_OFFSET);
    }

    default void putReferenceCount(final RefDataValue refDataValue, final ByteBuffer byteBuffer) {
        byteBuffer.putInt(refDataValue.getReferenceCount());
    }

    /**
     * Compares the value portion of each of the passed {@link ByteBuffer} instances.
     * @return True if the bytes of the value portion of each buffer are equal
     */
    default boolean areValuesEqual(final ByteBuffer thisValue, final ByteBuffer thatValue) {
        final int ignoredBytes = REFERENCE_COUNT_BYTES;
        int result = ByteBufferUtils.compareTo(
                thisValue, VALUE_OFFSET, thisValue.limit() - ignoredBytes - 1,
                thatValue, VALUE_OFFSET, thatValue.limit() - ignoredBytes - 1);
        return result == 0;
    }

    default int updateReferenceCount(final ByteBuffer valueBuffer, int referenceCountDelta) {
        int currRefCount = valueBuffer.getInt(REFERENCE_COUNT_OFFSET);
        int newRefCount = currRefCount + referenceCountDelta;
        valueBuffer.putInt(REFERENCE_COUNT_OFFSET, newRefCount);
        LOGGER.trace("Changing ref count from {} to {}", currRefCount, newRefCount);
        return newRefCount;
    }

    /**
     * Extracts a new {@link ByteBuffer} from the passed {@link ByteBuffer} that represents just
     * the value part of the original {@link ByteBuffer}. The passed {@link ByteBuffer} is not
     * modified.
     */
    default ByteBuffer extractValueBuffer(final ByteBuffer byteBuffer) {
        // advance the position to the value bit
        byteBuffer.position(VALUE_OFFSET);
        // create a new buffer from just the value part
        final ByteBuffer valueBuffer = byteBuffer.slice();
        byteBuffer.rewind();
        return valueBuffer;
    }
}
