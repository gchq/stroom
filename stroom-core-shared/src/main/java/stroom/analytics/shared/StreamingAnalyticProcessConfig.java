package stroom.analytics.shared;

import stroom.docref.DocRef;

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
    public StreamingAnalyticProcessConfig(@JsonProperty("enabled") final boolean enabled,
                                          @JsonProperty("node") final String node,
                                          @JsonProperty("errorFeed") final DocRef errorFeed,
                                          @JsonProperty("minMetaCreateTimeMs") final Long minMetaCreateTimeMs,
                                          @JsonProperty("maxMetaCreateTimeMs") final Long maxMetaCreateTimeMs) {
        super(enabled, node, errorFeed);
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
        if (!super.equals(o)) {
            return false;
        }
        final StreamingAnalyticProcessConfig that = (StreamingAnalyticProcessConfig) o;
        return Objects.equals(minMetaCreateTimeMs, that.minMetaCreateTimeMs) &&
                Objects.equals(maxMetaCreateTimeMs, that.maxMetaCreateTimeMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), minMetaCreateTimeMs, maxMetaCreateTimeMs);
    }

    @Override
    public String toString() {
        return "StreamingAnalyticProcessConfig{" +
                "enabled=" + enabled +
                ", node=" + node +
                ", minMetaCreateTimeMs=" + minMetaCreateTimeMs +
                ", maxMetaCreateTimeMs=" + maxMetaCreateTimeMs +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder
            extends AbstractAnalyticProcessConfigBuilder<StreamingAnalyticProcessConfig, Builder> {

        private Long minMetaCreateTimeMs;
        private Long maxMetaCreateTimeMs;

        private Builder() {
            super();
        }

        private Builder(final StreamingAnalyticProcessConfig streamingAnalyticProcessConfig) {
            super(streamingAnalyticProcessConfig);
            this.minMetaCreateTimeMs = streamingAnalyticProcessConfig.minMetaCreateTimeMs;
            this.maxMetaCreateTimeMs = streamingAnalyticProcessConfig.maxMetaCreateTimeMs;
        }

        public Builder minMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
            this.minMetaCreateTimeMs = minMetaCreateTimeMs;
            return self();
        }

        public Builder maxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
            this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public StreamingAnalyticProcessConfig build() {
            return new StreamingAnalyticProcessConfig(
                    enabled,
                    node,
                    errorFeed,
                    minMetaCreateTimeMs,
                    maxMetaCreateTimeMs);
        }
    }
}
