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
        "duration",
        "useStateTime"
})
@JsonInclude(Include.NON_NULL)
public class RetentionSettings extends DurationSetting {

    private static final SimpleDuration DEFAULT_DURATION = SimpleDuration.builder()
            .time(1)
            .timeUnit(TimeUnit.YEARS)
            .build();

    @JsonProperty
    private final Boolean useStateTime;

    @JsonCreator
    public RetentionSettings(@JsonProperty("enabled") final boolean enabled,
                             @JsonProperty("duration") final SimpleDuration duration,
                             @JsonProperty("useStateTime") final Boolean useStateTime) {
        super(enabled, NullSafe.requireNonNullElse(duration, DEFAULT_DURATION));
        this.useStateTime = NullSafe.requireNonNullElse(useStateTime, false);
    }

    public boolean useStateTime() {
        return useStateTime != null && useStateTime;
    }

    public Boolean getUseStateTime() {
        return useStateTime;
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
        final RetentionSettings that = (RetentionSettings) o;
        return Objects.equals(useStateTime, that.useStateTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), useStateTime);
    }

    @Override
    public String toString() {
        return "RetentionSettings{" +
               "enabled=" + enabled +
               ", duration=" + duration +
               ", useStateTime=" + useStateTime +
               '}';
    }

    public static class Builder {

        private boolean enabled;
        private SimpleDuration duration;
        private Boolean useStateTime;

        public Builder() {
        }

        public Builder(final RetentionSettings retentionSettings) {
            if (retentionSettings != null) {
                this.enabled = retentionSettings.enabled;
                this.duration = retentionSettings.duration;
                this.useStateTime = retentionSettings.useStateTime;
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

        public Builder useStateTime(final Boolean useStateTime) {
            this.useStateTime = useStateTime;
            return this;
        }

        public RetentionSettings build() {
            return new RetentionSettings(enabled, duration, useStateTime);
        }
    }
}
