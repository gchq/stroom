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
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreMeta;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * < typeId >< referenceCount >
 * < 1 byte >< 3 bytes >
 * <p>
 * referenceCount stored as a 3 byte unsigned integer so a max
 * of ~16 million.
 */
public class ValueStoreMetaSerde implements Serde<ValueStoreMeta> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueStoreMetaSerde.class);

    private static final UnsignedBytes REF_COUNT_UNSIGNED_BYTES = UnsignedBytesInstances.THREE;
    private static final int REFERENCE_COUNT_BYTES = REF_COUNT_UNSIGNED_BYTES.length();

    private static final int TYPE_ID_OFFSET = 0;
    private static final int TYPE_ID_BYTES = 1;
    /**
     * The offset of the first byte of the reference count
     */
    private static final int REFERENCE_COUNT_OFFSET = TYPE_ID_OFFSET + TYPE_ID_BYTES;
    /**
     * The offset of the last byte of the reference count
     */
    public static final int REFERENCE_COUNT_END_OFFSET = REFERENCE_COUNT_OFFSET + REFERENCE_COUNT_BYTES - 1;

    private static final int BUFFER_CAPACITY = TYPE_ID_BYTES + REFERENCE_COUNT_BYTES;

    @Override
    public int getBufferCapacity() {
        return BUFFER_CAPACITY;
    }

    @Override
    public ValueStoreMeta deserialize(final ByteBuffer byteBuffer) {

        final ValueStoreMeta valueStoreMeta = new ValueStoreMeta(
                byteBuffer.get(),
                (int) REF_COUNT_UNSIGNED_BYTES.get(byteBuffer));
        byteBuffer.flip();

        return valueStoreMeta;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final ValueStoreMeta valueStoreMeta) {
        byteBuffer.put(valueStoreMeta.getTypeId());
        REF_COUNT_UNSIGNED_BYTES.put(byteBuffer, valueStoreMeta.getReferenceCount());
        byteBuffer.flip();
    }

    /**
     * Leaves byteBuffer unchanged
     */
    public byte extractTypeId(final ByteBuffer byteBuffer) {
        return byteBuffer.get(TYPE_ID_OFFSET);
    }

    /**
     * Leaves byteBuffer unchanged
     */
    public int extractReferenceCount(final ByteBuffer byteBuffer) {
        return (int) REF_COUNT_UNSIGNED_BYTES.get(byteBuffer, REFERENCE_COUNT_OFFSET);
    }

    /**
     * @return True if the reference count is one or zero.
     */
    public boolean isLastReference(final ByteBuffer byteBuffer) {
        // This relies on UnsignedBytes serialising 1 to 001 and 0 to 000.

        // Check the last byte first as low numbers are more likely than high numbers.
        final byte lastByte = byteBuffer.get(REFERENCE_COUNT_END_OFFSET);
        if (lastByte != 1 && lastByte != 0) {
            return false;
        }

        // Everything else should be zero
        for (int j = REFERENCE_COUNT_END_OFFSET - 1; j >= REFERENCE_COUNT_OFFSET; j--) {
            if (byteBuffer.get(j) != 0) {
                return false;
            }
        }
        return true;
    }

    public void cloneAndDecrementRefCount(final ByteBuffer sourceBuffer, final ByteBuffer destBuffer) {
        ByteBufferUtils.copy(sourceBuffer, destBuffer);
        try {
            REF_COUNT_UNSIGNED_BYTES.decrement(destBuffer, REFERENCE_COUNT_OFFSET);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error decrementing reference count. Current value {}. {}",
                    REF_COUNT_UNSIGNED_BYTES.get(sourceBuffer, sourceBuffer.position()),
                    e.getMessage()), e);
        }
    }

    public void cloneAndIncrementRefCount(final ByteBuffer sourceBuffer, final ByteBuffer destBuffer) {
        ByteBufferUtils.copy(sourceBuffer, destBuffer);
        try {
            REF_COUNT_UNSIGNED_BYTES.increment(destBuffer, REFERENCE_COUNT_OFFSET);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error incrementing reference count. Current value {}. {}",
                    ModelStringUtil.formatCsv(REF_COUNT_UNSIGNED_BYTES.get(sourceBuffer, REFERENCE_COUNT_OFFSET)),
                    e.getMessage()), e);
        }
    }

    public static long getMaxReferenceCount() {
        return REF_COUNT_UNSIGNED_BYTES.maxValue();
    }
}
