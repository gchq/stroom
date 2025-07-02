package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class SqsConnectorConfig extends AbstractConfig implements IsProxyConfig {

    public static final StroomDuration DEFAULT_POLL_FREQUENCY = StroomDuration.ofSeconds(10);
    @JsonProperty
    private final String awsRegionName;
    @JsonProperty
    private final String awsProfileName;
    @JsonProperty
    private final String queueName;
    @JsonProperty
    private final String queueUrl;
    @JsonProperty
    private final StroomDuration pollFrequency;

    public SqsConnectorConfig() {
        awsRegionName = null;
        awsProfileName = null;
        queueName = null;
        queueUrl = null;
        pollFrequency = DEFAULT_POLL_FREQUENCY;
    }

    @SuppressWarnings({"unused", "checkstyle:LineLength"})
    @JsonCreator
    public SqsConnectorConfig(@JsonProperty("awsRegionName") final String awsRegionName,
                              @JsonProperty("awsProfileName") final String awsProfileName,
                              @JsonProperty("queueName") final String queueName,
                              @JsonProperty("queueUrl") final String queueUrl,
                              @JsonProperty("pollFrequency") final StroomDuration pollFrequency) {
        this.awsRegionName = awsRegionName;
        this.awsProfileName = awsProfileName;
        this.queueName = queueName;
        this.queueUrl = queueUrl;
        this.pollFrequency = Objects.requireNonNullElse(pollFrequency, DEFAULT_POLL_FREQUENCY);
    }

    @NotBlank
    @JsonProperty
    public String getAwsRegionName() {
        return awsRegionName;
    }

    @JsonProperty
    public String getAwsProfileName() {
        return awsProfileName;
    }

    @JsonProperty
    public String getQueueName() {
        return queueName;
    }

    @NotBlank
    @JsonProperty
    public String getQueueUrl() {
        return queueUrl;
    }

    @JsonProperty
    public StroomDuration getPollFrequency() {
        return pollFrequency;
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private String awsRegionName;
        private String awsProfileName;
        private String queueName;
        private String queueUrl;
        private StroomDuration pollFrequency = StroomDuration.ofSeconds(10);

        public Builder awsRegionName(final String awsRegionName) {
            this.awsRegionName = awsRegionName;
            return this;
        }

        public Builder awsProfileName(final String awsProfileName) {
            this.queueName = awsProfileName;
            return this;
        }

        public Builder queueName(final String queueName) {
            this.queueName = queueName;
            return this;
        }

        public Builder queueUrl(final String queueUrl) {
            this.queueUrl = queueUrl;
            return this;
        }

        public Builder pollFrequency(final StroomDuration pollFrequency) {
            this.pollFrequency = pollFrequency;
            return this;
        }

        public SqsConnectorConfig build() {
            return new SqsConnectorConfig(
                    awsRegionName,
                    awsProfileName,
                    queueName,
                    queueUrl,
                    pollFrequency);
        }
    }
}
