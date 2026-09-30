/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.shared;

import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * The two settings that only mean anything for a store type whose writes pass through a holding
 * shard before reaching the time buckets queries read.
 *
 * <p>A store type declares that it has these by implementing {@link HasHoldingAreaSettings}. One
 * that merges straight into its buckets has no holding shard, so neither setting exists for it.
 */
@JsonPropertyOrder({"maxWaitForData", "compactionFrequency"})
@JsonInclude(Include.NON_NULL)
public final class HoldingAreaSettings {

    public static final SimpleDuration DEFAULT_MAX_WAIT_FOR_DATA = SimpleDuration.builder()
            .time(12)
            .timeUnit(TimeUnit.HOURS)
            .build();

    public static final SimpleDuration DEFAULT_COMPACTION_FREQUENCY = SimpleDuration.builder()
            .time(1)
            .timeUnit(TimeUnit.HOURS)
            .build();

    @JsonProperty
    private final SimpleDuration maxWaitForData;

    @JsonProperty
    private final SimpleDuration compactionFrequency;

    /**
     * A non-positive wait is accepted here and reported by
     * {@link AbstractPlanBSettings#validationError}. Throwing instead would fail the whole document
     * on import rather than telling the user which setting is wrong.
     */
    @JsonCreator
    public HoldingAreaSettings(
            @JsonProperty("maxWaitForData") final SimpleDuration maxWaitForData,
            @JsonProperty("compactionFrequency") final SimpleDuration compactionFrequency) {
        this.maxWaitForData = Objects.requireNonNullElse(maxWaitForData, DEFAULT_MAX_WAIT_FOR_DATA);
        this.compactionFrequency =
                Objects.requireNonNullElse(compactionFrequency, DEFAULT_COMPACTION_FREQUENCY);
    }

    /**
     * The longest the holding shard waits for the rest of a record's data to arrive. This is not
     * latency every record pays: one that is already complete is published on the next merge cycle.
     * Once the wait runs out the record is published with whatever it has, and data arriving after
     * that is published separately.
     */
    public SimpleDuration getMaxWaitForData() {
        return maxWaitForData;
    }

    /**
     * How often to reclaim the pages a drain leaves behind in the holding shard. Purely
     * housekeeping — it has no bearing on when data becomes queryable.
     */
    public SimpleDuration getCompactionFrequency() {
        return compactionFrequency;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final HoldingAreaSettings other)) {
            return false;
        }
        return Objects.equals(maxWaitForData, other.maxWaitForData)
               && Objects.equals(compactionFrequency, other.compactionFrequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxWaitForData, compactionFrequency);
    }

    @Override
    public String toString() {
        return "HoldingAreaSettings{" +
               "maxWaitForData=" + maxWaitForData +
               ", compactionFrequency=" + compactionFrequency +
               '}';
    }

    public static class Builder {

        private SimpleDuration maxWaitForData;
        private SimpleDuration compactionFrequency;

        public Builder() {
        }

        public Builder(final HoldingAreaSettings settings) {
            if (settings != null) {
                this.maxWaitForData = settings.maxWaitForData;
                this.compactionFrequency = settings.compactionFrequency;
            }
        }

        public Builder maxWaitForData(final SimpleDuration maxWaitForData) {
            this.maxWaitForData = maxWaitForData;
            return this;
        }

        public Builder compactionFrequency(final SimpleDuration compactionFrequency) {
            this.compactionFrequency = compactionFrequency;
            return this;
        }

        public HoldingAreaSettings build() {
            return new HoldingAreaSettings(maxWaitForData, compactionFrequency);
        }
    }
}
