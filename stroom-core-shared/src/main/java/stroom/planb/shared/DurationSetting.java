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

package stroom.planb.shared;

import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "enabled",
        "duration"
})
@JsonInclude(Include.NON_NULL)
public class DurationSetting {

    private static final SimpleDuration DEFAULT_DURATION = SimpleDuration.builder()
            .time(1)
            .timeUnit(TimeUnit.DAYS)
            .build();

    @JsonProperty
    final boolean enabled;
    @JsonProperty
    final SimpleDuration duration;

    @JsonCreator
    public DurationSetting(@JsonProperty("enabled") final boolean enabled,
                           @JsonProperty("duration") final SimpleDuration duration) {
        this.enabled = enabled;
        this.duration = NullSafe.requireNonNullElse(duration, DEFAULT_DURATION);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public SimpleDuration getDuration() {
        return duration;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DurationSetting that = (DurationSetting) o;
        return enabled == that.enabled &&
               Objects.equals(duration, that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, duration);
    }

    @Override
    public String toString() {
        return "DurationSetting{" +
               "enabled=" + enabled +
               ", duration=" + duration +
               '}';
    }

    public static class Builder {

        private boolean enabled;
        private SimpleDuration duration;

        public Builder() {
        }

        public Builder(final DurationSetting durationSetting) {
            if (durationSetting != null) {
                this.enabled = durationSetting.enabled;
                this.duration = durationSetting.duration;
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

        public DurationSetting build() {
            return new DurationSetting(enabled, duration);
        }
    }
}
