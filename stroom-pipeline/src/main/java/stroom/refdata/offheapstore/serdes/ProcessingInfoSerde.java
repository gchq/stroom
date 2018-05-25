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

class ProcessingInfoSerde implements
        Serde<RefDataProcessingInfo>,
        Serializer<RefDataProcessingInfo>,
        Deserializer<RefDataProcessingInfo> {

    @Override
    public RefDataProcessingInfo deserialize(final ByteBuffer byteBuffer) {
        // the read order here must match the write order in serialize()
        final long createTimeEpochMs = byteBuffer.getLong();
        final long lastAccessedTimeEpochMs = byteBuffer.getLong();
        final long effectiveTimeEpochMs = byteBuffer.getLong();
        final byte processingStateId = byteBuffer.get();
        final RefDataProcessingInfo.ProcessingState processingState =
                RefDataProcessingInfo.ProcessingState.fromByte(processingStateId);

        return new RefDataProcessingInfo(
                createTimeEpochMs, lastAccessedTimeEpochMs, effectiveTimeEpochMs, processingState);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefDataProcessingInfo refDataProcessingInfo) {
        Objects.requireNonNull(refDataProcessingInfo);
        Objects.requireNonNull(byteBuffer);
        byteBuffer.putLong(refDataProcessingInfo.getCreateTimeEpochMs());
        byteBuffer.putLong(refDataProcessingInfo.getLastAccessedTimeEpochMs());
        byteBuffer.putLong(refDataProcessingInfo.getEffectiveTimeEpochMs());
        byteBuffer.putLong(refDataProcessingInfo.getProcessingState().getId());
        byteBuffer.flip();
    }
}
