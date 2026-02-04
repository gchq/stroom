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

package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.serde.Deserializer;
import stroom.lmdb.serde.EnumSetSerde;
import stroom.lmdb.serde.Serde;
import stroom.lmdb.serde.Serializer;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;
import stroom.pipeline.refdata.store.RefDataProcessingInfo.RefMapFeature;
import stroom.pipeline.refdata.store.RefDataProcessingInfo.RefMapInfo;
import stroom.pipeline.refdata.store.RefDataProcessingInfo.RefStreamFeature;
import stroom.pipeline.refdata.store.offheapstore.UID;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * <pre>
 * < createMs >< lastAccessMs >< effMs   >< structureVer >< strm features >< < mapUid  >< mapFeatures > >[repeated]
 * < 8 bytes  >< 8 bytes      >< 8 bytes >< 1 byte       >< 2 bytes       >< < 4 bytes >< 2 bytes     > >[repeated]
 * </pre>
 */
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
    private static final UnsignedBytes STRUCTURE_VERSION_UNSIGNED_BYTES = UnsignedBytesInstances.ONE;

    private final EnumSetSerde<RefStreamFeature> refStreamFeaturesSerde;
    private final EnumSetSerde<RefMapFeature> refMapFeaturesSerde;
    private final UIDSerde uidSerde;

    public RefDataProcessingInfoSerde() {
        // Future-proof both with 2 bytes to give us up to 16 features each
        refStreamFeaturesSerde = new EnumSetSerde<>(RefStreamFeature.class, 2);
        refMapFeaturesSerde = new EnumSetSerde<>(RefMapFeature.class, 2);
        uidSerde = new UIDSerde();
    }

    @Override
    public RefDataProcessingInfo deserialize(final ByteBuffer byteBuffer) {
        // the read order here must match the write order in serialize()
        final long createTimeEpochMs = byteBuffer.getLong();
        final long lastAccessedTimeEpochMs = byteBuffer.getLong();
        final long effectiveTimeEpochMs = byteBuffer.getLong();
        final byte processingStateId = byteBuffer.get();

        Integer structureVersion = null;
        Set<RefStreamFeature> refStreamFeatures = null;
        List<RefMapInfo> mapInfoList = new ArrayList<>();
        // The following parts were added 20250414, so may not be there in older streams.
        if (byteBuffer.hasRemaining()) {
            structureVersion = (int) STRUCTURE_VERSION_UNSIGNED_BYTES.get(byteBuffer);
            refStreamFeatures = refStreamFeaturesSerde.get(byteBuffer);

            // There may be 0-many RefMapInfo instances
            while (byteBuffer.hasRemaining()) {
                ByteBufferUtils.sliceAndAdvance(byteBuffer, uidSerde.getBufferCapacity());
                final UID uid = uidSerde.deserialize(ByteBufferUtils.sliceAndAdvance(byteBuffer,
                        uidSerde.getBufferCapacity()));
                final Set<RefMapFeature> refMapFeatures = refMapFeaturesSerde.get(byteBuffer);
                mapInfoList.add(new RefMapInfo(uid, refMapFeatures));
            }
        }

        byteBuffer.flip();
        final ProcessingState processingState = ProcessingState.fromByte(processingStateId);

        // Ctor will default null values as needed
        return new RefDataProcessingInfo(
                createTimeEpochMs,
                lastAccessedTimeEpochMs,
                effectiveTimeEpochMs,
                processingState,
                structureVersion,
                refStreamFeatures,
                mapInfoList);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer,
                          final RefDataProcessingInfo refDataProcessingInfo) {
        Objects.requireNonNull(refDataProcessingInfo);
        Objects.requireNonNull(byteBuffer);
        // Fixed widths allow us to (de-)serialise only the bit of the object we are interested in,
        // e.g. just the lastAccessedTime
        byteBuffer.putLong(refDataProcessingInfo.getCreateTimeEpochMs());
        byteBuffer.putLong(refDataProcessingInfo.getLastAccessedTimeEpochMs());
        byteBuffer.putLong(refDataProcessingInfo.getEffectiveTimeEpochMs());
        byteBuffer.put(refDataProcessingInfo.getProcessingState().getId());
        STRUCTURE_VERSION_UNSIGNED_BYTES.put(byteBuffer, refDataProcessingInfo.getStructureVersion());
        refStreamFeaturesSerde.put(byteBuffer, refDataProcessingInfo.getRefStreamFeatures());
        for (final RefMapInfo refMapInfo : refDataProcessingInfo.getMapInfoList()) {
            uidSerde.serialize(byteBuffer, refMapInfo.getMapUid());
            refMapFeaturesSerde.put(byteBuffer, refMapInfo.getMapFeatures());
        }
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
        try {
            byte bState = byteBuffer.get(PROCESSING_STATE_OFFSET);
            return ProcessingState.fromByte(bState);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error getting byte at offset {}, byteBuffer: {}",
                    PROCESSING_STATE_OFFSET, ByteBufferUtils.byteBufferInfo(byteBuffer)), e);
        }
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
                byte bState = extractProcessingStateAsByte(byteBuffer);
                for (final byte processingStateId : processingStateIds) {
                    if (processingStateId == bState) {
                        return true;
                    }
                }
                return false;
            };
        }
    }

    private static byte extractProcessingStateAsByte(final ByteBuffer byteBuffer) {
        try {
            return byteBuffer.get(PROCESSING_STATE_OFFSET);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error getting byte at offset {}, byteBuffer: {}",
                    PROCESSING_STATE_OFFSET, ByteBufferUtils.byteBufferInfo(byteBuffer)), e);
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
        int compareResult = ByteBufferUtils.compareAsLong(
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
        // We have a variable number of RefMapInfo blocks, so we can't know how many bytes it
        // will take up. 1000 gives us room for 162 maps.
        return 1000;
    }
}
