package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@JsonPropertyOrder(alphabetic = true)
public class SqsConnectorConfig extends AbstractConfig implements IsProxyConfig {

    @JsonProperty
    private final String queueName;
    @JsonProperty
    private final StroomDuration pollFrequency;

    public SqsConnectorConfig() {
        queueName = null;
        pollFrequency = StroomDuration.ofSeconds(10);
    }

    @SuppressWarnings({"unused", "checkstyle:LineLength"})
    @JsonCreator
    public SqsConnectorConfig(@JsonProperty("queueName") final String queueName,
                              @JsonProperty("pollFrequency") final StroomDuration pollFrequency) {
        this.queueName = queueName;
        this.pollFrequency = pollFrequency;
    }

    @JsonProperty
    public String getQueueName() {
        return queueName;
    }

    @JsonProperty
    public StroomDuration getPollFrequency() {
        return pollFrequency;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String queueName;
        private StroomDuration pollFrequency = StroomDuration.ofSeconds(10);

        public Builder queueName(final String queueName) {
            this.queueName = queueName;
            return this;
        }

        public Builder pollFrequency(final StroomDuration pollFrequency) {
            this.pollFrequency = pollFrequency;
            return this;
        }

        public SqsConnectorConfig build() {
            return new SqsConnectorConfig(
                    queueName,
                    pollFrequency);
        }
    }
}
