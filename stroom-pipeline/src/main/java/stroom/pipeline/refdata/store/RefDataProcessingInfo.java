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

package stroom.pipeline.refdata.store;

import stroom.lmdb.serde.EnumSetSerde.HasBitPosition;
import stroom.pipeline.refdata.store.offheapstore.UID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class RefDataProcessingInfo {

    public static final int LEGACY_STRUCTURE_VERSION = 1;
    public static final int STRUCTURE_VERSION_2 = 2;

    public static final TemporalUnit PROCESSING_INFO_TRUNCATION_UNIT = ChronoUnit.HOURS;
    private static final long PROCESSING_INFO_TRUNCATION_UNIT_MS = ChronoUnit.HOURS.getDuration().toMillis();

    @JsonProperty
    private final long createTimeEpochMs;
    @JsonProperty
    private final long lastAccessedTimeEpochMs;
    @JsonProperty
    private final long effectiveTimeEpochMs;
    @JsonProperty
    private final ProcessingState processingState;
    /**
     * Indicates the version of the code and db structure that this stream was originally loaded using.
     */
    @JsonProperty
    private final int structureVersion;
    /**
     * Any features that this stream has
     */
    @JsonProperty
    private final Set<RefStreamFeature> refStreamFeatures;
    /**
     * Lists the maps present in this stream and any features that each map has.
     */
    @JsonProperty
    private final List<RefMapInfo> mapInfoList;

    public RefDataProcessingInfo(final long createTimeEpochMs,
                                 final long lastAccessedTimeEpochMs,
                                 final long effectiveTimeEpochMs,
                                 final ProcessingState processingState,
                                 final int structureVersion) {
        this(createTimeEpochMs,
                lastAccessedTimeEpochMs,
                effectiveTimeEpochMs,
                processingState,
                structureVersion,
                null,
                null);
    }

    @JsonCreator
    public RefDataProcessingInfo(@JsonProperty("createTimeEpochMs") final long createTimeEpochMs,
                                 @JsonProperty("lastAccessedTimeEpochMs") final long lastAccessedTimeEpochMs,
                                 @JsonProperty("effectiveTimeEpochMs") final long effectiveTimeEpochMs,
                                 @JsonProperty("processingState") final ProcessingState processingState,
                                 @JsonProperty("structureVersion") final Integer structureVersion,
                                 @JsonProperty("refStreamFeatures") final Set<RefStreamFeature> refStreamFeatures,
                                 @JsonProperty("mapInfoList") final List<RefMapInfo> mapInfoList) {
        this.createTimeEpochMs = createTimeEpochMs;
        // To make it clear that we only update the last access time at intervals to avoid
        // frequent writes, truncate the value.
        this.lastAccessedTimeEpochMs = truncateLastAccessTime(lastAccessedTimeEpochMs);
        this.effectiveTimeEpochMs = effectiveTimeEpochMs;
        this.processingState = processingState;
        this.structureVersion = Objects.requireNonNullElse(structureVersion, LEGACY_STRUCTURE_VERSION);
        this.refStreamFeatures = Collections.unmodifiableSet(Objects.requireNonNullElseGet(
                refStreamFeatures,
                () -> EnumSet.noneOf(RefStreamFeature.class)));
        this.mapInfoList = Collections.unmodifiableList(Objects.requireNonNullElseGet(
                mapInfoList,
                List::of));
    }

    public RefDataProcessingInfo cloneWithNewAccessTime(final long accessTimeMs) {
        return new RefDataProcessingInfo(
                createTimeEpochMs,
                accessTimeMs,
                effectiveTimeEpochMs,
                processingState,
                structureVersion,
                refStreamFeatures,
                mapInfoList);
    }

    public RefDataProcessingInfo cloneWithNewState(final ProcessingState newProcessingState,
                                                   final boolean touchLastAccessedTime) {

        long newLastAccessedTime;
        if (touchLastAccessedTime) {
            newLastAccessedTime = truncateLastAccessTime(System.currentTimeMillis());
        } else {
            newLastAccessedTime = lastAccessedTimeEpochMs;
        }
        return new RefDataProcessingInfo(
                createTimeEpochMs,
                newLastAccessedTime,
                effectiveTimeEpochMs,
                newProcessingState,
                structureVersion,
                refStreamFeatures,
                mapInfoList);
    }

    public RefDataProcessingInfo updateLastAccessedTime() {
        return new RefDataProcessingInfo(
                createTimeEpochMs,
                truncateLastAccessTime(System.currentTimeMillis()),
                effectiveTimeEpochMs,
                processingState,
                structureVersion, refStreamFeatures, mapInfoList);
    }

    public long getCreateTimeEpochMs() {
        return createTimeEpochMs;
    }

    /**
     * @return The time an entry for the associated ref stream definition was accessed,
     * truncated to the nearest PROCESSING_INFO_TRUNCATION_UNIT.
     */
    public long getLastAccessedTimeEpochMs() {
        return lastAccessedTimeEpochMs;
    }

    public long getEffectiveTimeEpochMs() {
        return effectiveTimeEpochMs;
    }

    public ProcessingState getProcessingState() {
        return processingState;
    }

    public int getStructureVersion() {
        return structureVersion;
    }

    public Set<RefStreamFeature> getRefStreamFeatures() {
        return refStreamFeatures;
    }

    public List<RefMapInfo> getMapInfoList() {
        return mapInfoList;
    }

    @JsonIgnore
    public Instant getCreateTime() {
        return Instant.ofEpochMilli(createTimeEpochMs);
    }

    @JsonIgnore
    public Instant getLastAccessedTime() {
        return Instant.ofEpochMilli(lastAccessedTimeEpochMs);
    }

    @JsonIgnore
    public Instant getEffectiveTime() {
        return Instant.ofEpochMilli(effectiveTimeEpochMs);
    }

    public static Instant truncateLastAccessTime(final Instant instant) {
        return instant.truncatedTo(PROCESSING_INFO_TRUNCATION_UNIT);
    }

    public static long truncateLastAccessTime(final long timeMs) {
        final long buckets = timeMs / PROCESSING_INFO_TRUNCATION_UNIT_MS;
        return buckets * PROCESSING_INFO_TRUNCATION_UNIT_MS;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RefDataProcessingInfo that = (RefDataProcessingInfo) o;
        return createTimeEpochMs == that.createTimeEpochMs &&
               effectiveTimeEpochMs == that.effectiveTimeEpochMs &&
               processingState == that.processingState;
    }

    @Override
    public int hashCode() {

        return Objects.hash(createTimeEpochMs, effectiveTimeEpochMs, processingState);
    }

    @Override
    public String toString() {
        return "RefDataProcessingInfo{" +
               "createTimeEpochMs=" + Instant.ofEpochMilli(createTimeEpochMs).toString() +
               ", lastAccessedTimeEpochMs=" + Instant.ofEpochMilli(lastAccessedTimeEpochMs).toString() +
               ", effectiveTimeEpochMs=" + Instant.ofEpochMilli(effectiveTimeEpochMs).toString() +
               ", processingState=" + processingState +
               '}';
    }


    // --------------------------------------------------------------------------------


    public enum RefStreamFeature implements HasBitPosition {
        /**
         * This ref stream supports directly stored values rather than always
         * storing a pointer to a value. Whether a value is directly stored or has a pointer
         * will be indicated by an additional byte prefix on the value
         */
        SUPPORTS_DIRECT_VALUES(0);

        private final int bitPosition;

        RefStreamFeature(final int bitPosition) {
            this.bitPosition = bitPosition;
        }

        @Override
        public int getBitPosition() {
            return bitPosition;
        }
    }


    // --------------------------------------------------------------------------------


    public enum RefMapFeature implements HasBitPosition {
        ALL_KEYS(0),
        ALL_RANGES(1),
        KEYS_AND_RANGES(2),
        ;

        private final int bitPosition;

        RefMapFeature(final int bitPosition) {
            this.bitPosition = bitPosition;
        }

        @Override
        public int getBitPosition() {
            return bitPosition;
        }
    }


    // --------------------------------------------------------------------------------


    public static class RefMapInfo {

        private final UID mapUid;
        private final Set<RefMapFeature> mapFeatures;

        public RefMapInfo(final UID mapUid,
                          final Set<RefMapFeature> mapFeatures) {
            this.mapUid = Objects.requireNonNull(mapUid);
            this.mapFeatures = Collections.unmodifiableSet(Objects.requireNonNullElseGet(
                    mapFeatures,
                    () -> EnumSet.noneOf(RefMapFeature.class)));
        }

        public UID getMapUid() {
            return mapUid;
        }

        public Set<RefMapFeature> getMapFeatures() {
            return mapFeatures;
        }
    }
}
