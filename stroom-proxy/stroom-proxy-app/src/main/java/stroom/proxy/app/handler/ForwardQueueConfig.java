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

import stroom.util.config.annotations.RequiresProxyRestart;
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
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@NotInjectableConfig // Used by multiple other config classes
@JsonPropertyOrder(alphabetic = true)
public abstract class ForwardQueueConfig extends AbstractConfig implements IsProxyConfig {

    public static final String PROP_NAME_ERROR_SUB_PATH_TEMPLATE = "errorSubPathTemplate";
    protected static final boolean DEFAULT_QUEUE_AND_RETRY_ENABLED = true;
    protected static final StroomDuration DEFAULT_FORWARD_DELAY = StroomDuration.ZERO;
    protected static final StroomDuration DEFAULT_LIVENESS_CHECK_INTERVAL = StroomDuration.ofMinutes(1);
    /**
     * Zero means no retries
     */
    protected static final StroomDuration DEFAULT_MAX_RETRY_AGE = StroomDuration.ofDays(7);
    protected static final double DEFAULT_RETRY_GROWTH_FACTOR = 1;
    protected static final StroomDuration DEFAULT_RETRY_DELAY = StroomDuration.ofMinutes(10);
    protected static final StroomDuration DEFAULT_MAX_RETRY_DELAY = StroomDuration.ofDays(1);
    protected static final int DEFAULT_FORWARD_RETRY_THREAD_COUNT = 1;
    protected static final int DEFAULT_FORWARD_THREAD_COUNT = 5;

    @JsonProperty
    private final boolean queueAndRetryEnabled;
    @JsonProperty
    private final StroomDuration forwardDelay;
    @JsonProperty
    private final StroomDuration retryDelay;
    @JsonProperty
    private final double retryDelayGrowthFactor;
    @JsonProperty
    private final StroomDuration maxRetryDelay;
    @JsonProperty
    private final StroomDuration maxRetryAge;
    @JsonProperty
    private final PathTemplateConfig errorSubPathTemplate;
    @JsonProperty
    private final int forwardThreadCount;
    @JsonProperty
    private final int forwardRetryThreadCount;
    @JsonProperty
    private final StroomDuration livenessCheckInterval;

    public ForwardQueueConfig() {
        queueAndRetryEnabled = DEFAULT_QUEUE_AND_RETRY_ENABLED;
        forwardDelay = DEFAULT_FORWARD_DELAY;
        retryDelay = DEFAULT_RETRY_DELAY;
        retryDelayGrowthFactor = DEFAULT_RETRY_GROWTH_FACTOR;
        maxRetryDelay = DEFAULT_RETRY_DELAY;
        maxRetryAge = DEFAULT_MAX_RETRY_AGE;
        errorSubPathTemplate = PathTemplateConfig.DEFAULT;
        forwardRetryThreadCount = DEFAULT_FORWARD_RETRY_THREAD_COUNT;
        forwardThreadCount = DEFAULT_FORWARD_THREAD_COUNT;
        livenessCheckInterval = DEFAULT_LIVENESS_CHECK_INTERVAL;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardQueueConfig(
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

        this.queueAndRetryEnabled = Objects.requireNonNullElse(queueAndRetryEnabled, DEFAULT_QUEUE_AND_RETRY_ENABLED);
        this.forwardDelay = Objects.requireNonNullElse(forwardDelay, DEFAULT_FORWARD_DELAY);
        this.retryDelay = Objects.requireNonNullElse(retryDelay, DEFAULT_RETRY_DELAY);
        this.retryDelayGrowthFactor = Objects.requireNonNullElse(retryDelayGrowthFactor, DEFAULT_RETRY_GROWTH_FACTOR);
        this.maxRetryDelay = Objects.requireNonNullElse(maxRetryDelay, DEFAULT_MAX_RETRY_DELAY);
        this.maxRetryAge = Objects.requireNonNullElse(maxRetryAge, DEFAULT_MAX_RETRY_AGE);
        this.errorSubPathTemplate = Objects.requireNonNullElse(errorSubPathTemplate, PathTemplateConfig.DEFAULT);
        this.forwardThreadCount = Objects.requireNonNullElse(forwardThreadCount, DEFAULT_FORWARD_THREAD_COUNT);
        this.forwardRetryThreadCount = Objects.requireNonNullElse(
                forwardRetryThreadCount, DEFAULT_FORWARD_RETRY_THREAD_COUNT);
        this.livenessCheckInterval = Objects.requireNonNullElse(
                livenessCheckInterval, DEFAULT_LIVENESS_CHECK_INTERVAL);
    }

    @JsonPropertyDescription(
            "Set to true to queue items to be forwarded and to retry when any recoverable errors are " +
            "encountered. Non-recoverable errors or when the configured maxRetryAge is exceeded results " +
            "in directories being moved to the failure destination. Set to false to immediately forward and " +
            "if there is any error to move the directory to the failure destination.")
    public boolean isQueueAndRetryEnabled() {
        return queueAndRetryEnabled;
    }

    @JsonPropertyDescription("Debug/test setting to add a delay before forwarding. Default is zero. Do not set " +
                             "this in production.")
    public StroomDuration getForwardDelay() {
        return forwardDelay;
    }

    @JsonPropertyDescription("If we fail to send, how long should we wait until we try again?")
    public StroomDuration getRetryDelay() {
        return retryDelay;
    }

    @JsonPropertyDescription("If retryDelayGrowthFactor is > 1, " +
                             "this property controls the maximum retry delay interval.")
    public StroomDuration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    /**
     * If we fail to send, how much to increase the retryDelay duration by after each retry failure,
     * e.g. 1.1 means increase by 10% each time,
     * i.e. if retryDelay is 1000 and retryDelayGrowthFactor is 1.1, then the retry delays will be
     * 1000, 1100, 1210, 1331, 1464, etc. Default value of 1 so the times don't increase.
     */
    @Min(1)
    public double getRetryDelayGrowthFactor() {
        return retryDelayGrowthFactor;
    }

    @JsonPropertyDescription("The maximum duration between the initial attempt and the last retry. Set to zero " +
                             "for no retires at all. Set to something large like 'PT99999999999999D' " +
                             "to 'always' retry. Default is 7 days.")
    public StroomDuration getMaxRetryAge() {
        return maxRetryAge;
    }

    @NotNull
    @JsonPropertyDescription("The template to use to create subdirectories of the error destination directory. " +
                             "The error directory is used when the retry limit is reached " +
                             "or the data is explicitly rejected by the downstream proxy/stroom.")
    public PathTemplateConfig getErrorSubPathTemplate() {
        return errorSubPathTemplate;
    }

    @RequiresProxyRestart
    @Min(1)
    @JsonPropertyDescription("The number of threads to consume from the forward queue.")
    public int getForwardThreadCount() {
        return forwardThreadCount;
    }

    @RequiresProxyRestart
    @Min(1)
    @JsonPropertyDescription("The number of threads to consume from the forward retry queue that contains " +
                             "items that failed to forward on the previous attempt.")
    public int getForwardRetryThreadCount() {
        return forwardRetryThreadCount;
    }

    @JsonPropertyDescription("The interval between destination liveness checks, if livenessCheckUrl has a value.")
    public StroomDuration getLivenessCheckInterval() {
        return livenessCheckInterval;
    }

    @SuppressWarnings("unused") // Used by jakarta.validation
    @JsonIgnore
    @ValidationMethod(message = "maxRetryDelay must be greater than or equal to retryDelay")
    @Valid
    // Seems to be ignored if not prefixed with 'is'
    public boolean isMaxRetryDelayValid() {
        if (retryDelayGrowthFactor == 1) {
            return true;
        } else {
            return retryDelay != null
                   && maxRetryDelay != null
                   && maxRetryDelay.toMillis() >= retryDelay.toMillis();
        }
    }

//    public static Builder builder() {
//        return new Builder();
//    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ForwardQueueConfig that = (ForwardQueueConfig) o;
        return Double.compare(retryDelayGrowthFactor, that.retryDelayGrowthFactor) == 0
               && forwardThreadCount == that.forwardThreadCount
               && forwardRetryThreadCount == that.forwardRetryThreadCount
               && Objects.equals(forwardDelay, that.forwardDelay)
               && Objects.equals(retryDelay, that.retryDelay)
               && Objects.equals(maxRetryDelay, that.maxRetryDelay)
               && Objects.equals(maxRetryAge, that.maxRetryAge)
               && Objects.equals(errorSubPathTemplate, that.errorSubPathTemplate)
               && Objects.equals(livenessCheckInterval, that.livenessCheckInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
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

    @Override
    public String toString() {
        return "ForwardQueueConfig{" +
               "forwardDelay=" + forwardDelay +
               ", retryDelay=" + retryDelay +
               ", retryDelayGrowthFactor=" + retryDelayGrowthFactor +
               ", maxRetryDelay=" + maxRetryDelay +
               ", maxRetryAge=" + maxRetryAge +
               ", errorSubPathTemplate='" + errorSubPathTemplate + '\'' +
               ", forwardThreadCount=" + forwardThreadCount +
               ", forwardRetryThreadCount=" + forwardRetryThreadCount +
               ", livenessCheckInterval=" + livenessCheckInterval +
               '}';
    }

    // --------------------------------------------------------------------------------


//    public static class Builder {
//
//        private boolean queueAndRetryEnabled = DEFAULT_QUEUE_AND_RETRY_ENABLED;
//        private StroomDuration forwardDelay = DEFAULT_FORWARD_DELAY;
//        private StroomDuration retryDelay = DEFAULT_RETRY_DELAY;
//        private StroomDuration maxRetryDelay = DEFAULT_MAX_RETRY_DELAY;
//        private double retryDelayGrowthFactor = DEFAULT_RETRY_GROWTH_FACTOR;
//        private StroomDuration maxRetryAge = DEFAULT_MAX_RETRY_AGE;
//        private PathTemplateConfig errorSubPathTemplate;
//        private TemplatingMode templatingMode;
//        private int forwardThreadCount = DEFAULT_FORWARD_THREAD_COUNT;
//        private int forwardRetryThreadCount = DEFAULT_FORWARD_RETRY_THREAD_COUNT;
//        private StroomDuration livenessCheckInterval;
//
//        private Builder() {
//            this(new ForwardQueueConfig());
//        }
//
//        private Builder(final ForwardQueueConfig forwardQueueConfig) {
//            Objects.requireNonNull(forwardQueueConfig);
//            this.queueAndRetryEnabled = forwardQueueConfig.queueAndRetryEnabled;
//            this.forwardDelay = forwardQueueConfig.forwardDelay;
//            this.retryDelay = forwardQueueConfig.retryDelay;
//            this.maxRetryDelay = forwardQueueConfig.maxRetryDelay;
//            this.retryDelayGrowthFactor = forwardQueueConfig.retryDelayGrowthFactor;
//            this.maxRetryAge = forwardQueueConfig.maxRetryAge;
//            this.errorSubPathTemplate = forwardQueueConfig.errorSubPathTemplate;
//            this.forwardThreadCount = forwardQueueConfig.forwardThreadCount;
//            this.forwardRetryThreadCount = forwardQueueConfig.forwardRetryThreadCount;
//            this.livenessCheckInterval = forwardQueueConfig.livenessCheckInterval;
//        }
//
//        public Builder forwardDelay(final boolean queueAndRetryEnabled) {
//            this.queueAndRetryEnabled = queueAndRetryEnabled;
//            return this;
//        }
//
//        public Builder forwardDelay(final StroomDuration forwardDelay) {
//            this.forwardDelay = forwardDelay;
//            return this;
//        }
//
//        public Builder retryDelay(final StroomDuration retryDelay) {
//            this.retryDelay = retryDelay;
//            return this;
//        }
//
//        public Builder maxRetryDelay(final StroomDuration maxRetryDelay) {
//            this.maxRetryDelay = maxRetryDelay;
//            return this;
//        }
//
//        public Builder retryDelayGrowthFactor(final double retryDelayGrowthFactor) {
//            this.retryDelayGrowthFactor = retryDelayGrowthFactor;
//            return this;
//        }
//
//        public Builder maxRetryAge(final StroomDuration maxRetryAge) {
//            this.maxRetryAge = maxRetryAge;
//            return this;
//        }
//
//        public Builder withTemplatingMode(final TemplatingMode templatingMode) {
//            this.templatingMode = templatingMode;
//            return this;
//        }
//
//        public Builder withForwardThreadCount(final int forwardThreadCount) {
//            this.forwardThreadCount = forwardThreadCount;
//            return this;
//        }
//
//        public Builder withForwardRetryThreadCount(final int forwardRetryThreadCount) {
//            this.forwardRetryThreadCount = forwardRetryThreadCount;
//            return this;
//        }
//
//        public Builder livenessCheckInterval(final StroomDuration livenessCheckInterval) {
//            this.livenessCheckInterval = livenessCheckInterval;
//            return this;
//        }
//
//        public ForwardQueueConfig build() {
//            return new ForwardQueueConfig(
//                    queueAndRetryEnabled,
//                    forwardDelay,
//                    retryDelay,
//                    retryDelayGrowthFactor,
//                    maxRetryDelay,
//                    maxRetryAge,
//                    errorSubPathTemplate,
//                    forwardThreadCount,
//                    forwardRetryThreadCount,
//                    livenessCheckInterval);
//        }
//    }
}
