package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class StreamingAnalyticProcessConfig extends AnalyticProcessConfig {

    @JsonProperty
    private final Long minMetaCreateTimeMs;
    @JsonProperty
    private final Long maxMetaCreateTimeMs;

    @JsonCreator
    public StreamingAnalyticProcessConfig(@JsonProperty("minMetaCreateTimeMs") final Long minMetaCreateTimeMs,
                                          @JsonProperty("maxMetaCreateTimeMs") final Long maxMetaCreateTimeMs) {
        this.minMetaCreateTimeMs = minMetaCreateTimeMs;
        this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
    }

    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs;
    }

    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StreamingAnalyticProcessConfig that = (StreamingAnalyticProcessConfig) o;
        return Objects.equals(minMetaCreateTimeMs, that.minMetaCreateTimeMs) &&
                Objects.equals(maxMetaCreateTimeMs, that.maxMetaCreateTimeMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minMetaCreateTimeMs, maxMetaCreateTimeMs);
    }

    @Override
    public String toString() {
        return "StreamingAnalyticProcessConfig{" +
                "minMetaCreateTimeMs=" + minMetaCreateTimeMs +
                ", maxMetaCreateTimeMs=" + maxMetaCreateTimeMs +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Long minMetaCreateTimeMs;
        private Long maxMetaCreateTimeMs;

        private Builder() {
        }

        private Builder(final StreamingAnalyticProcessConfig streamingAnalyticProcessConfig) {
            this.minMetaCreateTimeMs = streamingAnalyticProcessConfig.minMetaCreateTimeMs;
            this.maxMetaCreateTimeMs = streamingAnalyticProcessConfig.maxMetaCreateTimeMs;
        }

        public Builder minMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
            this.minMetaCreateTimeMs = minMetaCreateTimeMs;
            return this;
        }

        public Builder maxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
            this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
            return this;
        }

        public StreamingAnalyticProcessConfig build() {
            return new StreamingAnalyticProcessConfig(
                    minMetaCreateTimeMs,
                    maxMetaCreateTimeMs);
        }
    }
}
