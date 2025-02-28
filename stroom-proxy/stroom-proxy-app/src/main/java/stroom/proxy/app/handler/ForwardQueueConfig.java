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
import jakarta.validation.constraints.Pattern;

import java.util.Objects;

@NotInjectableConfig // Used by multiple other config classes
@JsonPropertyOrder(alphabetic = true)
public class ForwardQueueConfig extends AbstractConfig implements IsProxyConfig {

    public static final String PROP_NAME_ERROR_SUB_PATH_TEMPLATE = "errorSubPathTemplate";
    public static final TemplatingMode DEFAULT_TEMPLATING_MODE = TemplatingMode.REPLACE_UNKNOWN;
    private static final StroomDuration DEFAULT_FORWARD_DELAY = StroomDuration.ZERO;
    private static final StroomDuration DEFAULT_LIVENESS_CHECK_INTERVAL = StroomDuration.ofMinutes(1);
    /**
     * Zero means no retries
     */
    private static final StroomDuration DEFAULT_MAX_RETRY_AGE = StroomDuration.ofDays(7);
    private static final double DEFAULT_RETRY_GROWTH_FACTOR = 1;
    private static final StroomDuration DEFAULT_RETRY_DELAY = StroomDuration.ofMinutes(10);
    private static final StroomDuration DEFAULT_MAX_RETRY_DELAY = StroomDuration.ofDays(1);
    private static final String DEFAULT_ERROR_PATH_TEMPLATE = "${year}${month}${day}/${feed}";
    public static final int DEFAULT_FORWARD_RETRY_THREAD_COUNT = 1;
    public static final int DEFAULT_FORWARD_THREAD_COUNT = 5;

    private final StroomDuration retryDelay;
    private final double retryDelayGrowthFactor;
    private final StroomDuration maxRetryDelay;
    private final StroomDuration maxRetryAge;
    private final String errorSubPathTemplate;
    private final TemplatingMode templatingMode;
    private final int forwardThreadCount;
    private final int forwardRetryThreadCount;
    private final StroomDuration livenessCheckInterval;

    public ForwardQueueConfig() {
        retryDelay = DEFAULT_RETRY_DELAY;
        retryDelayGrowthFactor = DEFAULT_RETRY_GROWTH_FACTOR;
        maxRetryDelay = DEFAULT_RETRY_DELAY;
        maxRetryAge = DEFAULT_MAX_RETRY_AGE;
        errorSubPathTemplate = DEFAULT_ERROR_PATH_TEMPLATE;
        templatingMode = DEFAULT_TEMPLATING_MODE;
        forwardRetryThreadCount = DEFAULT_FORWARD_RETRY_THREAD_COUNT;
        forwardThreadCount = DEFAULT_FORWARD_THREAD_COUNT;
        livenessCheckInterval = DEFAULT_LIVENESS_CHECK_INTERVAL;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardQueueConfig(
            @JsonProperty("retryDelay") final StroomDuration retryDelay,
            @JsonProperty("retryDelayGrowthFactor") final Double retryDelayGrowthFactor,
            @JsonProperty("maxRetryDelay") final StroomDuration maxRetryDelay,
            @JsonProperty("maxRetryAge") final StroomDuration maxRetryAge,
            @JsonProperty(PROP_NAME_ERROR_SUB_PATH_TEMPLATE) final String errorSubPathTemplate,
            @JsonProperty("templatingMode") final TemplatingMode templatingMode,
            @JsonProperty("forwardThreadCount") final int forwardThreadCount,
            @JsonProperty("forwardRetryThreadCount") final int forwardRetryThreadCount,
            @JsonProperty("livenessCheckInterval") final StroomDuration livenessCheckInterval) {

        this.retryDelay = Objects.requireNonNullElse(retryDelay, DEFAULT_RETRY_DELAY);
        this.retryDelayGrowthFactor = Objects.requireNonNullElse(retryDelayGrowthFactor, DEFAULT_RETRY_GROWTH_FACTOR);
        this.maxRetryDelay = Objects.requireNonNullElse(maxRetryDelay, DEFAULT_MAX_RETRY_DELAY);
        this.maxRetryAge = Objects.requireNonNullElse(maxRetryAge, DEFAULT_MAX_RETRY_AGE);
        this.errorSubPathTemplate = errorSubPathTemplate;
        this.templatingMode = templatingMode;
        this.forwardThreadCount = forwardThreadCount;
        this.forwardRetryThreadCount = forwardRetryThreadCount;
        this.livenessCheckInterval = Objects.requireNonNullElse(
                livenessCheckInterval, DEFAULT_LIVENESS_CHECK_INTERVAL);
    }

    @JsonProperty
    @JsonPropertyDescription("If we fail to send, how long should we wait until we try again?")
    public StroomDuration getRetryDelay() {
        return retryDelay;
    }

    @JsonProperty
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
    @JsonProperty
    public double getRetryDelayGrowthFactor() {
        return retryDelayGrowthFactor;
    }

    @JsonProperty
    @JsonPropertyDescription("The maximum duration between the initial attempt and the last retry. Set to zero " +
                             "for no retires at all. Set to something large like 'PT99999999999999D' " +
                             "to 'always' retry.")
    public StroomDuration getMaxRetryAge() {
        return maxRetryAge;
    }

    /**
     * The template to use to create subdirectories of the error destination directory.
     * The error directory is used when the retry limit is reached
     * or the data is explicitly rejected by the downstream proxy/stroom.
     * Must be a relative path.
     * Supported template parameters (must be lower-case) are:
     * <ul>
     *     <li><code>${feed}</code></li>
     *     <li><code>${type}</code></li>
     *     <li><code>${year}</code></li>
     *     <li><code>${month}</code></li>
     *     <li><code>${day}</code></li>
     *     <li><code>${hour}</code></li>
     *     <li><code>${minute}</code></li>
     *     <li><code>${second}</code></li>
     *     <li><code>${millis}</code></li>
     *     <li><code>${ms}</code></li>
     * </ul>
     */
    @Pattern(regexp = "^[^/].*$") // Relative paths only
    @JsonProperty
    public String getErrorSubPathTemplate() {
        return errorSubPathTemplate;
    }

    @JsonProperty
    @JsonPropertyDescription("How to handle unknown parameters in the subPathTemplate. " +
                             "Default value is 'REPLACE_UNKNOWN'.")
    public TemplatingMode getTemplatingMode() {
        return templatingMode;
    }

    @RequiresProxyRestart
    @Min(0)
    @JsonProperty
    @JsonPropertyDescription("The number of threads to consume from the forward queue.")
    public int getForwardThreadCount() {
        return forwardThreadCount;
    }

    @RequiresProxyRestart
    @Min(0)
    @JsonProperty
    @JsonPropertyDescription("The number of threads to consume from the forward retry queue that contains " +
                             "items that failed to forward on the previous attempt.")
    public int getForwardRetryThreadCount() {
        return forwardRetryThreadCount;
    }

    @JsonProperty
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
        final ForwardQueueConfig that = (ForwardQueueConfig) o;
        return Double.compare(retryDelayGrowthFactor, that.retryDelayGrowthFactor) == 0
               && forwardThreadCount == that.forwardThreadCount
               && forwardRetryThreadCount == that.forwardRetryThreadCount
               && Objects.equals(retryDelay, that.retryDelay)
               && Objects.equals(maxRetryDelay, that.maxRetryDelay)
               && Objects.equals(maxRetryAge, that.maxRetryAge)
               && Objects.equals(errorSubPathTemplate, that.errorSubPathTemplate)
               && templatingMode == that.templatingMode
               && Objects.equals(livenessCheckInterval, that.livenessCheckInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(retryDelay,
                retryDelayGrowthFactor,
                maxRetryDelay,
                maxRetryAge,
                errorSubPathTemplate,
                templatingMode,
                forwardThreadCount,
                forwardRetryThreadCount,
                livenessCheckInterval);
    }

    @Override
    public String toString() {
        return "ForwardQueueConfig{" +
               "retryDelay=" + retryDelay +
               ", retryDelayGrowthFactor=" + retryDelayGrowthFactor +
               ", maxRetryDelay=" + maxRetryDelay +
               ", maxRetryAge=" + maxRetryAge +
               ", errorSubPathTemplate='" + errorSubPathTemplate + '\'' +
               ", templatingMode=" + templatingMode +
               ", forwardThreadCount=" + forwardThreadCount +
               ", forwardRetryThreadCount=" + forwardRetryThreadCount +
               ", livenessCheckInterval=" + livenessCheckInterval +
               '}';
    }

    // --------------------------------------------------------------------------------


    public static class Builder {

        private StroomDuration retryDelay = DEFAULT_RETRY_DELAY;
        private StroomDuration maxRetryDelay = DEFAULT_MAX_RETRY_DELAY;
        private double retryDelayGrowthFactor = DEFAULT_RETRY_GROWTH_FACTOR;
        private StroomDuration maxRetryAge = DEFAULT_MAX_RETRY_AGE;
        private String errorSubPathTemplate;
        private TemplatingMode templatingMode;
        private int forwardThreadCount;
        private int forwardRetryThreadCount;
        private StroomDuration livenessCheckInterval;

        private Builder() {
            this(new ForwardQueueConfig());
        }

        private Builder(final ForwardQueueConfig forwardQueueConfig) {
            Objects.requireNonNull(forwardQueueConfig);
            this.retryDelay = forwardQueueConfig.retryDelay;
            this.maxRetryDelay = forwardQueueConfig.maxRetryDelay;
            this.retryDelayGrowthFactor = forwardQueueConfig.retryDelayGrowthFactor;
            this.maxRetryAge = forwardQueueConfig.maxRetryAge;
            this.errorSubPathTemplate = forwardQueueConfig.errorSubPathTemplate;
            this.templatingMode = forwardQueueConfig.templatingMode;
            this.forwardThreadCount = forwardQueueConfig.forwardThreadCount;
            this.forwardRetryThreadCount = forwardQueueConfig.forwardRetryThreadCount;
            this.livenessCheckInterval = forwardQueueConfig.livenessCheckInterval;
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

        public Builder errorSubPathTemplate(final String errorSubPathTemplate) {
            this.errorSubPathTemplate = errorSubPathTemplate;
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

        public ForwardQueueConfig build() {
            return new ForwardQueueConfig(
                    retryDelay,
                    retryDelayGrowthFactor,
                    maxRetryDelay,
                    maxRetryAge,
                    errorSubPathTemplate,
                    templatingMode,
                    forwardThreadCount,
                    forwardRetryThreadCount,
                    livenessCheckInterval);
        }
    }
}
