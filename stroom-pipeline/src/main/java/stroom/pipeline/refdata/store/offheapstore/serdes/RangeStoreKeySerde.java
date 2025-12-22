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
import stroom.pipeline.refdata.store.offheapstore.RangeStoreKey;
import stroom.pipeline.refdata.store.offheapstore.UID;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * < mapUid >< rangeStartInc >< rangeEndExc >
 * < 4 bytes >< 8 bytes >< 8 bytes >
 */
public class RangeStoreKeySerde implements Serde<RangeStoreKey> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RangeStoreKeySerde.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(RangeStoreKeySerde.class);

    public static final int UID_OFFSET = 0;
    public static final int RANGE_FROM_OFFSET = UID_OFFSET + UID.UID_ARRAY_LENGTH;
    public static final int RANGE_TO_OFFSET = RANGE_FROM_OFFSET + Long.BYTES;

    private static final int BUFFER_CAPACITY = UID.UID_ARRAY_LENGTH + (Long.BYTES * 2);

    @Override
    public RangeStoreKey deserialize(final ByteBuffer byteBuffer) {

        // Create a bytebuffer that is a view onto the existing buffer
        // NOTE: if the passed bytebuffer is owned by LMDB then this deserialize method
        // needs to be used with care
        final ByteBuffer dupBuffer = byteBuffer.duplicate();

        // Set the limit at the end of the UID part
        dupBuffer.limit(byteBuffer.position() + UID.UID_ARRAY_LENGTH);
        final UID mapUid = UID.wrap(dupBuffer);

        // advance the position now we have a dup of the UID portion
        byteBuffer.position(byteBuffer.position() + UID.UID_ARRAY_LENGTH);

        final long rangeFromInc = byteBuffer.getLong();
        final long rangeToExc = byteBuffer.getLong();
        byteBuffer.flip();

        return new RangeStoreKey(mapUid, new Range<>(rangeFromInc, rangeToExc));
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RangeStoreKey rangeStoreKey) {
        UIDSerde.writeUid(byteBuffer, rangeStoreKey.getMapUid());
        final Range<Long> range = rangeStoreKey.getKeyRange();
        byteBuffer.putLong(range.getFrom());
        byteBuffer.putLong(range.getTo());
        byteBuffer.flip();
    }

    public static CompareResult isKeyInRange(final ByteBuffer byteBuffer,
                                             final UID mapDefinitionUid,
                                             final long key) {
        // from = inclusive, to = exclusive

        // Create a view of just the uid part so we can do an equality check on it
//        final ByteBuffer uidPartBuffer = byteBuffer.duplicate();
//        uidPartBuffer.position(UID_OFFSET);
//        uidPartBuffer.limit(UID.UID_ARRAY_LENGTH);

        if (ByteBufferUtils.containsPrefix(byteBuffer, mapDefinitionUid.getBackingBuffer())) {
            final long rangeFromInc = byteBuffer.getLong(RANGE_FROM_OFFSET);

            if (key >= rangeFromInc) {
                final long rangeToExc = byteBuffer.getLong(RANGE_TO_OFFSET);
                if (key < rangeToExc) {
                    return CompareResult.IN_RANGE;
                } else {
                    return CompareResult.ABOVE_RANGE;
                }
            } else {
                return CompareResult.BELOW_RANGE;
            }
        } else {
            return CompareResult.MAP_UID_MISMATCH;
        }
    }

    public void serializeWithoutRangePart(final ByteBuffer byteBuffer, final RangeStoreKey key) {

        serialize(byteBuffer, key);

        // set the limit to just after the UID part
        byteBuffer.limit(UID.UID_ARRAY_LENGTH);
    }

    /**
     * The returned UID is just a wrapper onto the passed {@link ByteBuffer}. If you need to use it outside
     * a txn/cursor then you will need to copy it.
     */
    public UID extractUid(final ByteBuffer byteBuffer) {
        return UIDSerde.extractUid(byteBuffer, UID_OFFSET);
    }

    /**
     * Copy the contents of sourceByteBuffer into destByteBuffer but with the supplied UID.
     */
    public static void copyWithNewUid(final ByteBuffer sourceByteBuffer,
                                      final ByteBuffer destByteBuffer,
                                      final UID newUid) {
        Objects.requireNonNull(sourceByteBuffer);
        Objects.requireNonNull(destByteBuffer);
        Objects.requireNonNull(newUid);

        if (destByteBuffer.remaining() < sourceByteBuffer.remaining()) {
            throw new RuntimeException(LogUtil.message("Insufficient remaining, source: {}, dest: {}",
                    sourceByteBuffer.remaining(),
                    destByteBuffer.remaining()));
        }

        destByteBuffer.put(newUid.getBackingBuffer());
        final ByteBuffer rangePartBuffer = sourceByteBuffer.slice(
                RANGE_FROM_OFFSET,
                Long.BYTES * 2);
        destByteBuffer.put(rangePartBuffer);
        destByteBuffer.flip();
        sourceByteBuffer.rewind();
    }

    @Override
    public int getBufferCapacity() {
        return BUFFER_CAPACITY;
    }


    // --------------------------------------------------------------------------------


    public enum CompareResult {
        MAP_UID_MISMATCH,
        IN_RANGE,
        ABOVE_RANGE,
        BELOW_RANGE
    }
}
