/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqlstore.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * The effective-time range of all entries in a given temporal-store map,
 * returned by {@link SqlTemporalStoreResource#getTimeRange(String)}.
 *
 * <p>Both fields are derived from a single aggregation query:
 * {@code SELECT MIN(effective_time), MAX(effective_time) FROM updatable_temporal_store
 * WHERE map_name = ?}.</p>
 *
 * <p>Both fields are {@code null} when the store is empty.</p>
 *
 * <h3>Typical use</h3>
 * <p>Used to initialise the timeline slider in the Floor Map Editor:
 * the slider range is set to {@code [minEffectiveTimeMs, maxEffectiveTimeMs]}
 * and the slider's initial position is set to {@code maxEffectiveTimeMs}.</p>
 */
@JsonInclude(Include.NON_NULL)
public class TemporalStoreTimeRange {

    /**
     * The smallest {@code effective_time} value present in the store for the
     * queried map, in milliseconds since the Unix epoch.
     */
    @JsonProperty
    private final Long minEffectiveTimeMs;

    /**
     * The largest {@code effective_time} value present in the store for the
     * queried map, in milliseconds since the Unix epoch.
     */
    @JsonProperty
    private final Long maxEffectiveTimeMs;

    @JsonCreator
    public TemporalStoreTimeRange(
            @JsonProperty("minEffectiveTimeMs") final Long minEffectiveTimeMs,
            @JsonProperty("maxEffectiveTimeMs") final Long maxEffectiveTimeMs) {
        this.minEffectiveTimeMs = minEffectiveTimeMs;
        this.maxEffectiveTimeMs = maxEffectiveTimeMs;
    }

    /**
     * Returns the earliest effective time across all entries in the map.
     *
     * @return the minimum effective time in milliseconds since the Unix epoch;
     *         {@code null} if the store is empty
     */
    public Long getMinEffectiveTimeMs() {
        return minEffectiveTimeMs;
    }

    /**
     * Returns the latest effective time across all entries in the map.
     *
     * @return the maximum effective time in milliseconds since the Unix epoch;
     *         {@code null} if the store is empty
     */
    public Long getMaxEffectiveTimeMs() {
        return maxEffectiveTimeMs;
    }

    /**
     * Returns {@code true} if the store contained no entries when this range
     * was computed (i.e. both {@link #minEffectiveTimeMs} and
     * {@link #maxEffectiveTimeMs} are {@code null}).
     *
     * @return {@code true} if the store is empty
     */
    @JsonIgnore
    public boolean isEmpty() {
        return minEffectiveTimeMs == null && maxEffectiveTimeMs == null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TemporalStoreTimeRange that = (TemporalStoreTimeRange) o;
        return Objects.equals(minEffectiveTimeMs, that.minEffectiveTimeMs)
                && Objects.equals(maxEffectiveTimeMs, that.maxEffectiveTimeMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minEffectiveTimeMs, maxEffectiveTimeMs);
    }

    @Override
    public String toString() {
        return "TemporalStoreTimeRange{" +
                "minEffectiveTimeMs=" + minEffectiveTimeMs +
                ", maxEffectiveTimeMs=" + maxEffectiveTimeMs +
                '}';
    }
}
