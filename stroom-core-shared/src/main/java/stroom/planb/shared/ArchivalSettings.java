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
 * Configuration for Stage 3 archival. Old data is moved from the main shard
 * into date-labelled archive shards on the shared file store.
 *
 * duration is the archival lead time: how long data stays in the main shard
 * before being moved to an archive shard. The retention duration must be
 * greater than this value.
 */
@JsonPropertyOrder({"enabled", "duration", "granularity"})
@JsonInclude(Include.NON_NULL)
public class ArchivalSettings extends DurationSetting {

    private static final SimpleDuration DEFAULT_LEAD_TIME = SimpleDuration.builder()
            .time(7)
            .timeUnit(TimeUnit.DAYS)
            .build();

    @JsonProperty
    private final ArchivalGranularity granularity;

    @JsonCreator
    public ArchivalSettings(
            @JsonProperty("enabled") final boolean enabled,
            @JsonProperty("duration") final SimpleDuration duration,
            @JsonProperty("granularity") final ArchivalGranularity granularity) {
        super(enabled, Objects.requireNonNullElse(duration, DEFAULT_LEAD_TIME));
        if (getDuration().getTime() <= 0) {
            throw new IllegalArgumentException(
                    "ArchivalSettings duration must be positive, got: " + getDuration());
        }
        this.granularity = Objects.requireNonNullElse(granularity, ArchivalGranularity.DAY);
    }

    public ArchivalGranularity getGranularity() {
        return granularity;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ArchivalSettings that = (ArchivalSettings) o;
        return Objects.equals(granularity, that.granularity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), granularity);
    }

    @Override
    public String toString() {
        return "ArchivalSettings{" +
               super.toString() +
               ", granularity=" + granularity +
               '}';
    }

    public static class Builder {

        private boolean enabled;
        private SimpleDuration duration;
        private ArchivalGranularity granularity;

        public Builder() {
        }

        public Builder(final ArchivalSettings settings) {
            if (settings != null) {
                this.enabled = settings.isEnabled();
                this.duration = settings.getDuration();
                this.granularity = settings.granularity;
            }
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder duration(final SimpleDuration duration) {
            this.duration = duration;
            return this;
        }

        public Builder granularity(final ArchivalGranularity granularity) {
            this.granularity = granularity;
            return this;
        }

        public ArchivalSettings build() {
            return new ArchivalSettings(enabled, duration, granularity);
        }
    }
}
