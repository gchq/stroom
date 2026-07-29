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
        "checkInterval",
        "useStateTime"
})
@JsonInclude(Include.NON_NULL)
public class RetentionSettings extends DurationSetting {

    private static final boolean DEFAULT_ENABLED = false;

    private static final SimpleDuration DEFAULT_DURATION = SimpleDuration.builder()
            .time(1)
            .timeUnit(TimeUnit.YEARS)
            .build();

    @JsonProperty
    private final Boolean useStateTime;

    @JsonCreator
    public RetentionSettings(@JsonProperty("enabled") final Boolean enabled,
                             @JsonProperty("duration") final SimpleDuration duration,
                             @JsonProperty("checkInterval") final SimpleDuration checkInterval,
                             @JsonProperty("useStateTime") final Boolean useStateTime) {
        super(Objects.requireNonNullElse(enabled, DEFAULT_ENABLED),
                Objects.requireNonNullElse(duration, DEFAULT_DURATION),
                checkInterval);
        this.useStateTime = Objects.requireNonNullElse(useStateTime, false);
    }

    /**
     * Validates the check frequency against the retention period, for both the client (which blocks
     * the save) and the server (which backstops the import and REST paths).
     *
     * <p>A record is deleted on the first retention run after it passes the retention period, and
     * runs are at least {@code checkInterval} apart, so data can survive
     * {@code retention + checkInterval}. Requiring {@code checkInterval < retention} keeps that
     * under twice the configured period.
     *
     * @return a user-facing message, or null if the settings are valid or retention is off
     */
    public static String checkIntervalError(final RetentionSettings retention) {
        if (retention == null || !retention.isEnabled()) {
            return null;
        }
        final SimpleDuration duration = retention.getDuration();
        final SimpleDuration checkInterval = retention.getCheckInterval();
        if (duration == null || checkInterval == null) {
            return null;
        }
        final long durationMs = duration.getApproxMillis();
        if (durationMs > 0 && checkInterval.getApproxMillis() >= durationMs) {
            return "The retention check frequency (" + checkInterval.toLongString() + ") must be "
                   + "shorter than the retention period (" + duration.toLongString() + "), "
                   + "otherwise data could be kept for far longer than the retention period.";
        }
        return null;
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
               ", checkInterval=" + checkInterval +
               ", useStateTime=" + useStateTime +
               '}';
    }

    public static class Builder {

        private boolean enabled;
        private SimpleDuration duration;
        private SimpleDuration checkInterval;
        private Boolean useStateTime;

        public Builder() {
        }

        public Builder(final RetentionSettings retentionSettings) {
            if (retentionSettings != null) {
                this.enabled = retentionSettings.enabled;
                this.duration = retentionSettings.duration;
                this.checkInterval = retentionSettings.checkInterval;
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

        public Builder checkInterval(final SimpleDuration checkInterval) {
            this.checkInterval = checkInterval;
            return this;
        }

        public Builder useStateTime(final Boolean useStateTime) {
            this.useStateTime = useStateTime;
            return this;
        }

        public RetentionSettings build() {
            return new RetentionSettings(enabled, duration, checkInterval, useStateTime);
        }
    }
}
