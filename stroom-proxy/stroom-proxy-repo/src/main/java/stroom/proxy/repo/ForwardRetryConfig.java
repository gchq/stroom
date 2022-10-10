package stroom.proxy.repo;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ForwardRetryConfig extends AbstractConfig implements IsProxyConfig {

    private final StroomDuration retryFrequency;
    private final StroomDuration maxRetryDelay;
    private final int maxTries;
    private final String failedForwardDir;


    public ForwardRetryConfig() {
        retryFrequency = StroomDuration.ofMinutes(1);
        maxRetryDelay = StroomDuration.ofHours(1);
        maxTries = 10;
        failedForwardDir = "failures";
    }

    @JsonCreator
    public ForwardRetryConfig(@JsonProperty("retryFrequency") final StroomDuration retryFrequency,
                              @JsonProperty("maxRetryDelay") final StroomDuration maxRetryDelay,
                              @JsonProperty("maxTries") final int maxTries,
                              @JsonProperty("failedForwardDir") final String failedForwardDir) {

        this.retryFrequency = retryFrequency;
        this.maxRetryDelay = maxRetryDelay;
        this.maxTries = maxTries;
        this.failedForwardDir = failedForwardDir;
    }

    @JsonPropertyDescription("How often do we want to retry forwarding data that fails to forward?")
    @JsonProperty
    public StroomDuration getRetryFrequency() {
        return retryFrequency;
    }

    @JsonPropertyDescription("Despite the retry multiplier don't ever wait more than this max delay between retries.")
    @JsonProperty
    public StroomDuration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    @JsonPropertyDescription("The maximum number of retries to attempt before sending to the failed forward data " +
            "directory.")
    @JsonProperty
    public int getMaxTries() {
        return maxTries;
    }

    @JsonPropertyDescription("The directory to put failed forward data in.")
    @JsonProperty
    public String getFailedForwardDir() {
        return failedForwardDir;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private StroomDuration retryFrequency = StroomDuration.ofMinutes(1);
        private StroomDuration maxRetryDelay;
        private int maxTries;
        private String failedForwardDir;

        private Builder() {

        }

        public Builder retryFrequency(final StroomDuration retryFrequency) {
            this.retryFrequency = retryFrequency;
            return this;
        }

        public Builder maxRetryDelay(final StroomDuration maxRetryDelay) {
            this.maxRetryDelay = maxRetryDelay;
            return this;
        }

        public Builder maxTries(final int maxTries) {
            this.maxTries = maxTries;
            return this;
        }

        public Builder failedForwardDir(final String failedForwardDir) {
            this.failedForwardDir = failedForwardDir;
            return this;
        }

        public ForwardRetryConfig build() {
            return new ForwardRetryConfig(retryFrequency, maxRetryDelay, maxTries, failedForwardDir);
        }
    }
}
