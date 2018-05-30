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

import stroom.refdata.lmdb.serde.Deserializer;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.lmdb.serde.Serializer;
import stroom.refdata.offheapstore.RefDataProcessingInfo;

import java.nio.ByteBuffer;
import java.util.Objects;

public class RefDataProcessingInfoSerde implements
        Serde<RefDataProcessingInfo>,
        Serializer<RefDataProcessingInfo>,
        Deserializer<RefDataProcessingInfo> {

    public static final int CREATE_TIME_OFFSET = 0;
    public static final int LAST_ACCESSED_TIME_OFFSET = CREATE_TIME_OFFSET + Long.BYTES;
    public static final int EFFECTIVE_TIME_OFFSET = LAST_ACCESSED_TIME_OFFSET + Long.BYTES;
    public static final int PROCESSING_STATE_OFFSET = EFFECTIVE_TIME_OFFSET + Long.BYTES;

    @Override
    public RefDataProcessingInfo deserialize(final ByteBuffer byteBuffer) {
        // the read order here must match the write order in serialize()
        final long createTimeEpochMs = byteBuffer.getLong();
        final long lastAccessedTimeEpochMs = byteBuffer.getLong();
        final long effectiveTimeEpochMs = byteBuffer.getLong();
        final byte processingStateId = byteBuffer.get();
        byteBuffer.flip();
        final RefDataProcessingInfo.ProcessingState processingState =
                RefDataProcessingInfo.ProcessingState.fromByte(processingStateId);

        return new RefDataProcessingInfo(
                createTimeEpochMs, lastAccessedTimeEpochMs, effectiveTimeEpochMs, processingState);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefDataProcessingInfo refDataProcessingInfo) {
        Objects.requireNonNull(refDataProcessingInfo);
        Objects.requireNonNull(byteBuffer);
        // TODO if we don't care about fixed widths we could use a custom kryo serialiser
        // that uses variable width longs for storage efficiency
        // Fixed widths allow us to (de-)serialise only the bit of the object we are interested in,
        // e.g. just the lastAccessedTime
        byteBuffer.putLong(refDataProcessingInfo.getCreateTimeEpochMs());
        byteBuffer.putLong(refDataProcessingInfo.getLastAccessedTimeEpochMs());
        byteBuffer.putLong(refDataProcessingInfo.getEffectiveTimeEpochMs());
        byteBuffer.put(refDataProcessingInfo.getProcessingState().getId());
        byteBuffer.flip();
    }

    public void updateState(final ByteBuffer byteBuffer,
                            final RefDataProcessingInfo.ProcessingState newProcessingState) {

        // absolute put so no need to change the buffer position
        byteBuffer.put(PROCESSING_STATE_OFFSET, newProcessingState.getId());
    }

    public void updateLastAccessedTime(final ByteBuffer byteBuffer, final long newLastAccessedTimeEpochMs) {
        // absolute put so no need to change the buffer position
        byteBuffer.putLong(LAST_ACCESSED_TIME_OFFSET, newLastAccessedTimeEpochMs);
    }

    public void updateLastAccessedTime(final ByteBuffer byteBuffer) {
        updateLastAccessedTime(byteBuffer, System.currentTimeMillis());
    }
}
