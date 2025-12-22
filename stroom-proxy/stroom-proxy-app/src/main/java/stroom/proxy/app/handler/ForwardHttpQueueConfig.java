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

package stroom.proxy.app.handler;

import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Subclass it so we can have different defaults between file/http
 */
@JsonPropertyOrder(alphabetic = true)
public class ForwardHttpQueueConfig extends ForwardQueueConfig {

    private static final boolean DEFAULT_QUEUE_AND_RETRY_ENABLED = true;

    public ForwardHttpQueueConfig() {
        super(DEFAULT_QUEUE_AND_RETRY_ENABLED,
                DEFAULT_FORWARD_DELAY,
                DEFAULT_RETRY_DELAY,
                DEFAULT_RETRY_GROWTH_FACTOR,
                DEFAULT_MAX_RETRY_DELAY,
                DEFAULT_MAX_RETRY_AGE,
                PathTemplateConfig.DEFAULT,
                DEFAULT_FORWARD_THREAD_COUNT,
                DEFAULT_FORWARD_RETRY_THREAD_COUNT,
                DEFAULT_LIVENESS_CHECK_INTERVAL);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardHttpQueueConfig(
            @JsonProperty("queueAndRetryEnabled") final Boolean queueAndRetryEnabled,
            @JsonProperty("forwardDelay") final StroomDuration forwardDelay,
            @JsonProperty("retryDelay") final StroomDuration retryDelay,
            @JsonProperty("retryDelayGrowthFactor") final Double retryDelayGrowthFactor,
            @JsonProperty("maxRetryDelay") final StroomDuration maxRetryDelay,
            @JsonProperty("maxRetryAge") final StroomDuration maxRetryAge,
            @JsonProperty(PROP_NAME_ERROR_SUB_PATH_TEMPLATE) final PathTemplateConfig errorSubPathTemplate,
            @JsonProperty("forwardThreadCount") final Integer forwardThreadCount,
            @JsonProperty("forwardRetryThreadCount") final Integer forwardRetryThreadCount,
            @JsonProperty("livenessCheckInterval") final StroomDuration livenessCheckInterval) {

        super(Objects.requireNonNullElse(queueAndRetryEnabled, DEFAULT_QUEUE_AND_RETRY_ENABLED),
                forwardDelay,
                retryDelay,
                retryDelayGrowthFactor,
                maxRetryDelay,
                maxRetryAge,
                errorSubPathTemplate,
                forwardThreadCount,
                forwardRetryThreadCount,
                livenessCheckInterval);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private boolean queueAndRetryEnabled = DEFAULT_QUEUE_AND_RETRY_ENABLED;
        private StroomDuration forwardDelay = DEFAULT_FORWARD_DELAY;
        private StroomDuration retryDelay = DEFAULT_RETRY_DELAY;
        private StroomDuration maxRetryDelay = DEFAULT_MAX_RETRY_DELAY;
        private double retryDelayGrowthFactor = DEFAULT_RETRY_GROWTH_FACTOR;
        private StroomDuration maxRetryAge = DEFAULT_MAX_RETRY_AGE;
        private PathTemplateConfig errorSubPathTemplate;
        private TemplatingMode templatingMode;
        private int forwardThreadCount = DEFAULT_FORWARD_THREAD_COUNT;
        private int forwardRetryThreadCount = DEFAULT_FORWARD_RETRY_THREAD_COUNT;
        private StroomDuration livenessCheckInterval;

        private Builder() {
            this(new ForwardHttpQueueConfig());
        }

        private Builder(final ForwardHttpQueueConfig forwardQueueConfig) {
            Objects.requireNonNull(forwardQueueConfig);
            this.queueAndRetryEnabled = forwardQueueConfig.isQueueAndRetryEnabled();
            this.forwardDelay = forwardQueueConfig.getForwardDelay();
            this.retryDelay = forwardQueueConfig.getRetryDelay();
            this.maxRetryDelay = forwardQueueConfig.getMaxRetryDelay();
            this.retryDelayGrowthFactor = forwardQueueConfig.getRetryDelayGrowthFactor();
            this.maxRetryAge = forwardQueueConfig.getMaxRetryAge();
            this.errorSubPathTemplate = forwardQueueConfig.getErrorSubPathTemplate();
            this.forwardThreadCount = forwardQueueConfig.getForwardThreadCount();
            this.forwardRetryThreadCount = forwardQueueConfig.getForwardRetryThreadCount();
            this.livenessCheckInterval = forwardQueueConfig.getLivenessCheckInterval();
        }

        public Builder forwardDelay(final boolean queueAndRetryEnabled) {
            this.queueAndRetryEnabled = queueAndRetryEnabled;
            return this;
        }

        public Builder forwardDelay(final StroomDuration forwardDelay) {
            this.forwardDelay = forwardDelay;
            return this;
        }

        public Builder retryDelay(final StroomDuration retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }

        public Builder maxRetryDelay(final StroomDuration maxRetryDelay) {
            this.maxRetryDelay = maxRetryDelay;
            return this;
        }

        public Builder retryDelayGrowthFactor(final double retryDelayGrowthFactor) {
            this.retryDelayGrowthFactor = retryDelayGrowthFactor;
            return this;
        }

        public Builder maxRetryAge(final StroomDuration maxRetryAge) {
            this.maxRetryAge = maxRetryAge;
            return this;
        }

        public Builder withTemplatingMode(final TemplatingMode templatingMode) {
            this.templatingMode = templatingMode;
            return this;
        }

        public Builder withForwardThreadCount(final int forwardThreadCount) {
            this.forwardThreadCount = forwardThreadCount;
            return this;
        }

        public Builder withForwardRetryThreadCount(final int forwardRetryThreadCount) {
            this.forwardRetryThreadCount = forwardRetryThreadCount;
            return this;
        }

        public Builder livenessCheckInterval(final StroomDuration livenessCheckInterval) {
            this.livenessCheckInterval = livenessCheckInterval;
            return this;
        }

        public ForwardHttpQueueConfig build() {
            return new ForwardHttpQueueConfig(
                    queueAndRetryEnabled,
                    forwardDelay,
                    retryDelay,
                    retryDelayGrowthFactor,
                    maxRetryDelay,
                    maxRetryAge,
                    errorSubPathTemplate,
                    forwardThreadCount,
                    forwardRetryThreadCount,
                    livenessCheckInterval);
        }
    }
}
