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

package stroom.proxy.app.handler;

import stroom.proxy.app.pipeline.stage.forward.ForwardBounds;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import java.util.Objects;

/**
 * How a forward destination retries and when it gives up. A group that could not be delivered goes
 * back on the queue; the destination waits before its next attempt on any group; a group older
 * than {@code maxRetryAge} is given up on ({@code designs/stages/forward.md} F4, F6).
 */
@NotInjectableConfig // One per forward destination
@JsonPropertyOrder(alphabetic = true)
public class ForwardRetryConfig extends AbstractConfig implements IsProxyConfig {

    public static final StroomDuration DEFAULT_MAX_RETRY_AGE = StroomDuration.ofDays(7);
    public static final StroomDuration DEFAULT_RETRY_DELAY = StroomDuration.ofMinutes(10);
    public static final double DEFAULT_RETRY_DELAY_GROWTH_FACTOR = 1;
    public static final StroomDuration DEFAULT_MAX_RETRY_DELAY = StroomDuration.ofHours(1);
    public static final StroomDuration DEFAULT_LIVENESS_CHECK_INTERVAL = StroomDuration.ofMinutes(1);

    @JsonProperty
    private final StroomDuration maxRetryAge;
    @JsonProperty
    private final StroomDuration retryDelay;
    @JsonProperty
    private final double retryDelayGrowthFactor;
    @JsonProperty
    private final StroomDuration maxRetryDelay;
    @JsonProperty
    private final StroomDuration livenessCheckInterval;

    public ForwardRetryConfig() {
        this(null, null, null, null, null);
    }

    @JsonCreator
    public ForwardRetryConfig(
            @JsonProperty("maxRetryAge") final StroomDuration maxRetryAge,
            @JsonProperty("retryDelay") final StroomDuration retryDelay,
            @JsonProperty("retryDelayGrowthFactor") final Double retryDelayGrowthFactor,
            @JsonProperty("maxRetryDelay") final StroomDuration maxRetryDelay,
            @JsonProperty("livenessCheckInterval") final StroomDuration livenessCheckInterval) {
        this.maxRetryAge = Objects.requireNonNullElse(maxRetryAge, DEFAULT_MAX_RETRY_AGE);
        this.retryDelay = Objects.requireNonNullElse(retryDelay, DEFAULT_RETRY_DELAY);
        this.retryDelayGrowthFactor = Objects.requireNonNullElse(
                retryDelayGrowthFactor, DEFAULT_RETRY_DELAY_GROWTH_FACTOR);
        this.maxRetryDelay = Objects.requireNonNullElse(maxRetryDelay, DEFAULT_MAX_RETRY_DELAY);
        this.livenessCheckInterval = Objects.requireNonNullElse(
                livenessCheckInterval, DEFAULT_LIVENESS_CHECK_INTERVAL);
    }

    @JsonPropertyDescription("How long a group is retried for, measured from when its message was published " +
                             "to the forward queue. A group older than this is given up on at its next failure " +
                             "and written to the failure destination. Zero means no retry at all; something " +
                             "very large means retry for ever. Size it to the outages the destination is " +
                             "expected to have. Default is 7 days.")
    public StroomDuration getMaxRetryAge() {
        return maxRetryAge;
    }

    @JsonPropertyDescription("How long this destination waits after a failed delivery before its next attempt " +
                             "on any group. The wait is served by whichever thread next attempts a delivery, " +
                             "with the group claimed throughout.")
    public StroomDuration getRetryDelay() {
        return retryDelay;
    }

    @Min(1)
    @JsonPropertyDescription("How much the wait grows by after each consecutive round of failures, e.g. 2 " +
                             "doubles it. The default of 1 keeps it flat. A success resets it.")
    public double getRetryDelayGrowthFactor() {
        return retryDelayGrowthFactor;
    }

    @JsonPropertyDescription("The longest the wait grows to when retryDelayGrowthFactor is above 1. " +
                             "Default is 1 hour. On a Kafka forward queue this must be shorter than the " +
                             "consumer's max.poll.interval.ms, since the wait holds the claim.")
    public StroomDuration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    @JsonPropertyDescription("How often the destination's liveness check runs, where it has one. The " +
                             "destination's forwarding is paused while the check fails.")
    public StroomDuration getLivenessCheckInterval() {
        return livenessCheckInterval;
    }

    @SuppressWarnings("unused") // Used by jakarta.validation
    @JsonIgnore
    @ValidationMethod(message = "maxRetryDelay must be greater than or equal to retryDelay")
    @Valid
    public boolean isMaxRetryDelayValid() {
        return retryDelayGrowthFactor == 1
               || maxRetryDelay.toMillis() >= retryDelay.toMillis();
    }

    @SuppressWarnings("unused") // Used by jakarta.validation
    @JsonIgnore
    @ValidationMethod(message = "livenessCheckInterval must be greater than zero")
    @Valid
    public boolean isLivenessCheckIntervalValid() {
        return livenessCheckInterval.toMillis() > 0;
    }

    @JsonIgnore
    public ForwardBounds toBounds() {
        return new ForwardBounds(
                maxRetryAge.getDuration(),
                retryDelay.getDuration(),
                retryDelayGrowthFactor,
                maxRetryDelay.getDuration());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ForwardRetryConfig that = (ForwardRetryConfig) o;
        return Double.compare(retryDelayGrowthFactor, that.retryDelayGrowthFactor) == 0
               && Objects.equals(maxRetryAge, that.maxRetryAge)
               && Objects.equals(retryDelay, that.retryDelay)
               && Objects.equals(maxRetryDelay, that.maxRetryDelay)
               && Objects.equals(livenessCheckInterval, that.livenessCheckInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxRetryAge, retryDelay, retryDelayGrowthFactor, maxRetryDelay, livenessCheckInterval);
    }

    @Override
    public String toString() {
        return "ForwardRetryConfig{" +
               "maxRetryAge=" + maxRetryAge +
               ", retryDelay=" + retryDelay +
               ", retryDelayGrowthFactor=" + retryDelayGrowthFactor +
               ", maxRetryDelay=" + maxRetryDelay +
               ", livenessCheckInterval=" + livenessCheckInterval +
               '}';
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private StroomDuration maxRetryAge;
        private StroomDuration retryDelay;
        private Double retryDelayGrowthFactor;
        private StroomDuration maxRetryDelay;
        private StroomDuration livenessCheckInterval;

        private Builder() {
        }

        public Builder maxRetryAge(final StroomDuration maxRetryAge) {
            this.maxRetryAge = maxRetryAge;
            return this;
        }

        public Builder retryDelay(final StroomDuration retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }

        public Builder retryDelayGrowthFactor(final double retryDelayGrowthFactor) {
            this.retryDelayGrowthFactor = retryDelayGrowthFactor;
            return this;
        }

        public Builder maxRetryDelay(final StroomDuration maxRetryDelay) {
            this.maxRetryDelay = maxRetryDelay;
            return this;
        }

        public Builder livenessCheckInterval(final StroomDuration livenessCheckInterval) {
            this.livenessCheckInterval = livenessCheckInterval;
            return this;
        }

        public ForwardRetryConfig build() {
            return new ForwardRetryConfig(
                    maxRetryAge, retryDelay, retryDelayGrowthFactor, maxRetryDelay, livenessCheckInterval);
        }
    }
}
