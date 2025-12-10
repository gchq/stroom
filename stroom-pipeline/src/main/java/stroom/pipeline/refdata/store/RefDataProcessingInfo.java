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

package stroom.pipeline.refdata.store;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class RefDataProcessingInfo {

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

    @JsonCreator
    public RefDataProcessingInfo(@JsonProperty("createTimeEpochMs") final long createTimeEpochMs,
                                 @JsonProperty("lastAccessedTimeEpochMs") final long lastAccessedTimeEpochMs,
                                 @JsonProperty("effectiveTimeEpochMs") final long effectiveTimeEpochMs,
                                 @JsonProperty("processingState") final ProcessingState processingState) {
        this.createTimeEpochMs = createTimeEpochMs;
        // To make it clear that we only update the last access time at intervals to avoid
        // frequent writes, truncate the value.
        this.lastAccessedTimeEpochMs = truncateLastAccessTime(lastAccessedTimeEpochMs);
        this.effectiveTimeEpochMs = effectiveTimeEpochMs;
        this.processingState = processingState;
    }

    public RefDataProcessingInfo cloneWithNewState(final ProcessingState newProcessingState,
                                                   final boolean touchLastAccessedTime) {

        final long newLastAccessedTime;
        if (touchLastAccessedTime) {
            newLastAccessedTime = truncateLastAccessTime(System.currentTimeMillis());
        } else {
            newLastAccessedTime = lastAccessedTimeEpochMs;
        }
        return new RefDataProcessingInfo(
                createTimeEpochMs,
                newLastAccessedTime,
                effectiveTimeEpochMs,
                newProcessingState);
    }

    public RefDataProcessingInfo updateLastAccessedTime() {
        return new RefDataProcessingInfo(
                createTimeEpochMs,
                truncateLastAccessTime(System.currentTimeMillis()),
                effectiveTimeEpochMs,
                processingState);
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

}
