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
import stroom.lmdb.serde.Deserializer;
import stroom.lmdb.serde.Serde;
import stroom.lmdb.serde.Serializer;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;

public class RefDataProcessingInfoSerde implements
        Serde<RefDataProcessingInfo>,
        Serializer<RefDataProcessingInfo>,
        Deserializer<RefDataProcessingInfo> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataProcessingInfoSerde.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(RefDataProcessingInfoSerde.class);

    public static final int CREATE_TIME_OFFSET = 0;
    public static final int LAST_ACCESSED_TIME_OFFSET = CREATE_TIME_OFFSET + Long.BYTES;
    public static final int EFFECTIVE_TIME_OFFSET = LAST_ACCESSED_TIME_OFFSET + Long.BYTES;
    public static final int PROCESSING_STATE_OFFSET = EFFECTIVE_TIME_OFFSET + Long.BYTES;
    private static final int BUFFER_CAPACITY = (Long.BYTES * 3) + 1;

    @Override
    public RefDataProcessingInfo deserialize(final ByteBuffer byteBuffer) {
        // the read order here must match the write order in serialize()
        final long createTimeEpochMs = byteBuffer.getLong();
        final long lastAccessedTimeEpochMs = byteBuffer.getLong();
        final long effectiveTimeEpochMs = byteBuffer.getLong();
        final byte processingStateId = byteBuffer.get();
        byteBuffer.flip();
        final ProcessingState processingState = ProcessingState.fromByte(processingStateId);

        return new RefDataProcessingInfo(
                createTimeEpochMs, lastAccessedTimeEpochMs, effectiveTimeEpochMs, processingState);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefDataProcessingInfo refDataProcessingInfo) {
        Objects.requireNonNull(refDataProcessingInfo);
        Objects.requireNonNull(byteBuffer);
        // Fixed widths allow us to (de-)serialise only the bit of the object we are interested in,
        // e.g. just the lastAccessedTime
        byteBuffer.putLong(refDataProcessingInfo.getCreateTimeEpochMs());
        byteBuffer.putLong(refDataProcessingInfo.getLastAccessedTimeEpochMs());
        byteBuffer.putLong(refDataProcessingInfo.getEffectiveTimeEpochMs());
        byteBuffer.put(refDataProcessingInfo.getProcessingState().getId());
        byteBuffer.flip();
    }

    public void updateState(final ByteBuffer byteBuffer,
                            final ProcessingState newProcessingState) {

        // absolute put so no need to change the buffer position
        byteBuffer.put(PROCESSING_STATE_OFFSET, newProcessingState.getId());
    }

    public void updateLastAccessedTime(final ByteBuffer byteBuffer, final long newLastAccessedTimeEpochMs) {
        // absolute put so no need to change the buffer position
        byteBuffer.putLong(
                LAST_ACCESSED_TIME_OFFSET,
                RefDataProcessingInfo.truncateLastAccessTime(newLastAccessedTimeEpochMs));
    }

    public void updateLastAccessedTime(final ByteBuffer byteBuffer) {
        updateLastAccessedTime(
                byteBuffer,
                RefDataProcessingInfo.truncateLastAccessTime(System.currentTimeMillis()));
    }

    public static ProcessingState extractProcessingState(final ByteBuffer byteBuffer) {
        final byte bState = byteBuffer.get(PROCESSING_STATE_OFFSET);
        return ProcessingState.fromByte(bState);
    }

    /**
     * Creates a predicate that is an OR of the passed states.
     */
    public static Predicate<ByteBuffer> createProcessingStatePredicate(final ProcessingState... processingStates) {
        if (processingStates == null || processingStates.length == 0) {
            return byteBuffer -> false;
        } else {
            final byte[] processingStateIds = new byte[processingStates.length];
            int i = 0;
            for (final ProcessingState processingState : processingStates) {
                processingStateIds[i++] = processingState.getId();
            }
            return byteBuffer -> {
                final byte bState = byteBuffer.get(PROCESSING_STATE_OFFSET);
                for (final byte processingStateId : processingStateIds) {
                    if (processingStateId == bState) {
                        return true;
                    }
                }
                return false;
            };
        }
    }

    public static long extractLastAccessedTimeMs(final ByteBuffer byteBuffer) {
        return byteBuffer.getLong(LAST_ACCESSED_TIME_OFFSET);
    }

    /**
     * Return true if the {@link RefDataProcessingInfo} object represent by valueBuffer has a last accessed
     * time after the epoch millis time represented by timeMsBuffer.
     *
     * @param processingInfoBuffer {@link ByteBuffer} containing a serialised {@link RefDataProcessingInfo}
     * @param timeMsBuffer         a {@link ByteBuffer} containing a long representing an epoch millis time
     */
    public static boolean wasAccessedAfter(final ByteBuffer processingInfoBuffer, final ByteBuffer timeMsBuffer) {
        final int compareResult = ByteBufferUtils.compareAsLong(
                timeMsBuffer, timeMsBuffer.position(),
                processingInfoBuffer, LAST_ACCESSED_TIME_OFFSET);

        LAMBDA_LOGGER.trace(() -> LogUtil.message("wasAccessedAfter returns {} for test time {} lastAccessed time {}",
                compareResult,
                Instant.ofEpochMilli(timeMsBuffer.getLong(0)),
                Instant.ofEpochMilli(processingInfoBuffer.getLong(LAST_ACCESSED_TIME_OFFSET))));

        return compareResult < 0;
    }

    @Override
    public int getBufferCapacity() {
        return BUFFER_CAPACITY;
    }
}
