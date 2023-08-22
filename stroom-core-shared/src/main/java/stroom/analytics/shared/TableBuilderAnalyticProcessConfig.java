package stroom.analytics.shared;

import stroom.analytics.shared.TableBuilderAnalyticProcessConfig.AnalyticProcessConfigBuilder;
import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class TableBuilderAnalyticProcessConfig extends AnalyticProcessConfig<AnalyticProcessConfigBuilder> {

    @JsonProperty
    private final Long minMetaCreateTimeMs;
    @JsonProperty
    private final Long maxMetaCreateTimeMs;
    @JsonProperty
    private final SimpleDuration timeToWaitForData;
    @JsonProperty
    private final SimpleDuration dataRetention;

    @JsonCreator
    public TableBuilderAnalyticProcessConfig(@JsonProperty("enabled") final boolean enabled,
                                             @JsonProperty("node") final String node,
                                             @JsonProperty("minMetaCreateTimeMs") final Long minMetaCreateTimeMs,
                                             @JsonProperty("maxMetaCreateTimeMs") final Long maxMetaCreateTimeMs,
                                             @JsonProperty("timeToWaitForData") final SimpleDuration timeToWaitForData,
                                             @JsonProperty("dataRetention") final SimpleDuration dataRetention) {
        super(enabled, node);
        this.minMetaCreateTimeMs = minMetaCreateTimeMs;
        this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
        this.dataRetention = dataRetention;
        this.timeToWaitForData = timeToWaitForData;
    }

    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs;
    }

    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs;
    }

    public SimpleDuration getTimeToWaitForData() {
        return timeToWaitForData;
    }

    public SimpleDuration getDataRetention() {
        return dataRetention;
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
        final TableBuilderAnalyticProcessConfig that = (TableBuilderAnalyticProcessConfig) o;
        return Objects.equals(minMetaCreateTimeMs, that.minMetaCreateTimeMs) &&
                Objects.equals(maxMetaCreateTimeMs, that.maxMetaCreateTimeMs) &&
                Objects.equals(timeToWaitForData, that.timeToWaitForData) &&
                Objects.equals(dataRetention, that.dataRetention);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                minMetaCreateTimeMs,
                maxMetaCreateTimeMs,
                timeToWaitForData,
                dataRetention);
    }

    @Override
    public String toString() {
        return "TableBuilderAnalyticProcessConfig{" +
                "enabled=" + enabled +
                ", node=" + node +
                ", minMetaCreateTimeMs=" + minMetaCreateTimeMs +
                ", maxMetaCreateTimeMs=" + maxMetaCreateTimeMs +
                ", timeToWaitForData=" + timeToWaitForData +
                ", dataRetention=" + dataRetention +
                '}';
    }

    @Override
    public AnalyticProcessConfigBuilder copy() {
        return new AnalyticProcessConfigBuilder(this);
    }

    public static AnalyticProcessConfigBuilder builder() {
        return new AnalyticProcessConfigBuilder();
    }

    public static class AnalyticProcessConfigBuilder extends
            AbstractAnalyticProcessConfigBuilder<TableBuilderAnalyticProcessConfig, AnalyticProcessConfigBuilder> {

        private Long minMetaCreateTimeMs;
        private Long maxMetaCreateTimeMs;
        private SimpleDuration timeToWaitForData;
        private SimpleDuration dataRetention;

        private AnalyticProcessConfigBuilder() {
        }

        private AnalyticProcessConfigBuilder(
                final TableBuilderAnalyticProcessConfig tableBuilderAnalyticProcessConfig) {
            super(tableBuilderAnalyticProcessConfig);
            this.minMetaCreateTimeMs = tableBuilderAnalyticProcessConfig.minMetaCreateTimeMs;
            this.maxMetaCreateTimeMs = tableBuilderAnalyticProcessConfig.maxMetaCreateTimeMs;
            this.timeToWaitForData = tableBuilderAnalyticProcessConfig.timeToWaitForData;
            this.dataRetention = tableBuilderAnalyticProcessConfig.dataRetention;
        }

        public AnalyticProcessConfigBuilder minMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
            this.minMetaCreateTimeMs = minMetaCreateTimeMs;
            return self();
        }

        public AnalyticProcessConfigBuilder maxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
            this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
            return self();
        }

        public AnalyticProcessConfigBuilder timeToWaitForData(final SimpleDuration timeToWaitForData) {
            this.timeToWaitForData = timeToWaitForData;
            return self();
        }

        public AnalyticProcessConfigBuilder dataRetention(final SimpleDuration dataRetention) {
            this.dataRetention = dataRetention;
            return self();
        }

        protected AnalyticProcessConfigBuilder self() {
            return this;
        }

        public TableBuilderAnalyticProcessConfig build() {
            return new TableBuilderAnalyticProcessConfig(
                    enabled,
                    node,
                    minMetaCreateTimeMs,
                    maxMetaCreateTimeMs,
                    timeToWaitForData,
                    dataRetention);
        }
    }
}
