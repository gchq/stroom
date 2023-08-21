package stroom.analytics.shared;

import stroom.analytics.shared.StreamingAnalyticProcessConfig.AnalyticProcessConfigBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class StreamingAnalyticProcessConfig extends AnalyticProcessConfig<AnalyticProcessConfigBuilder> {

    @JsonProperty
    private final Long minMetaCreateTimeMs;
    @JsonProperty
    private final Long maxMetaCreateTimeMs;

    @JsonCreator
    public StreamingAnalyticProcessConfig(@JsonProperty("enabled") final boolean enabled,
                                          @JsonProperty("node") final String node,
                                          @JsonProperty("minMetaCreateTimeMs") final Long minMetaCreateTimeMs,
                                          @JsonProperty("maxMetaCreateTimeMs") final Long maxMetaCreateTimeMs) {
        super(enabled, node);
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

    @Override
    public AnalyticProcessConfigBuilder copy() {
        return new AnalyticProcessConfigBuilder(this);
    }

    public static AnalyticProcessConfigBuilder builder() {
        return new AnalyticProcessConfigBuilder();
    }

    public static class AnalyticProcessConfigBuilder
            extends AbstractAnalyticProcessConfigBuilder<StreamingAnalyticProcessConfig, AnalyticProcessConfigBuilder> {

        private Long minMetaCreateTimeMs;
        private Long maxMetaCreateTimeMs;

        private AnalyticProcessConfigBuilder() {
            super();
        }

        private AnalyticProcessConfigBuilder(final StreamingAnalyticProcessConfig streamingAnalyticProcessConfig) {
            super(streamingAnalyticProcessConfig);
            this.minMetaCreateTimeMs = streamingAnalyticProcessConfig.minMetaCreateTimeMs;
            this.maxMetaCreateTimeMs = streamingAnalyticProcessConfig.maxMetaCreateTimeMs;
        }

        public AnalyticProcessConfigBuilder minMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
            this.minMetaCreateTimeMs = minMetaCreateTimeMs;
            return self();
        }

        public AnalyticProcessConfigBuilder maxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
            this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
            return self();
        }

        @Override
        protected AnalyticProcessConfigBuilder self() {
            return this;
        }

        @Override
        public StreamingAnalyticProcessConfig build() {
            return new StreamingAnalyticProcessConfig(
                    enabled,
                    node,
                    minMetaCreateTimeMs,
                    maxMetaCreateTimeMs);
        }
    }
}
