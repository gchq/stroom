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

package stroom.refdata.offheapstore;

import stroom.refdata.lmdb.serde.Deserializer;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.lmdb.serde.Serializer;

import java.nio.ByteBuffer;
import java.util.Objects;

class ProcessingInfoSerde implements
        Serde<ProcessingInfo>,
        Serializer<ProcessingInfo>,
        Deserializer<ProcessingInfo> {

    @Override
    public ProcessingInfo deserialize(final ByteBuffer byteBuffer) {
        final long createTimeEpochMs = byteBuffer.getLong();
        final long effectiveTimeEpochMs = byteBuffer.getLong();
        final byte processtingStateId = byteBuffer.get();
        final ProcessingInfo.ProcessingState processingState = ProcessingInfo.ProcessingState.valueOf(processtingStateId);
        return new ProcessingInfo(createTimeEpochMs, effectiveTimeEpochMs, processingState);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final ProcessingInfo processingInfo) {
        Objects.requireNonNull(processingInfo);
        Objects.requireNonNull(byteBuffer);
        byteBuffer.putLong(processingInfo.getCreateTimeEpochMs());
        byteBuffer.putLong(processingInfo.getEffectiveTimeEpochMs());
        byteBuffer.putLong(processingInfo.getProcessingState().getId());
        byteBuffer.flip();
    }
}
