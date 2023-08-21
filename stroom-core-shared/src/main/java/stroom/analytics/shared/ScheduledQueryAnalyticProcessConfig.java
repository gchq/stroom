package stroom.analytics.shared;

import stroom.analytics.shared.ScheduledQueryAnalyticProcessConfig.AnalyticProcessConfigBuilder;
import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ScheduledQueryAnalyticProcessConfig extends AnalyticProcessConfig<AnalyticProcessConfigBuilder> {

    @JsonProperty
    private final Long minEventTimeMs;
    @JsonProperty
    private final Long maxEventTimeMs;
    @JsonProperty
    private final SimpleDuration timeToWaitForData;
    @JsonProperty
    private final SimpleDuration queryFrequency;

    @JsonCreator
    public ScheduledQueryAnalyticProcessConfig(@JsonProperty("enabled") final boolean enabled,
                                               @JsonProperty("node") final String node,
                                               @JsonProperty("minEventTimeMs") final Long minEventTimeMs,
                                               @JsonProperty("maxEventTimeMs") final Long maxEventTimeMs,
                                               @JsonProperty("timeToWaitForData")
                                                   final SimpleDuration timeToWaitForData,
                                               @JsonProperty("queryFrequency") final SimpleDuration queryFrequency) {
        super(enabled, node);
        this.minEventTimeMs = minEventTimeMs;
        this.maxEventTimeMs = maxEventTimeMs;
        this.timeToWaitForData = timeToWaitForData;
        this.queryFrequency = queryFrequency;
    }

    public Long getMinEventTimeMs() {
        return minEventTimeMs;
    }

    public Long getMaxEventTimeMs() {
        return maxEventTimeMs;
    }

    public SimpleDuration getTimeToWaitForData() {
        return timeToWaitForData;
    }

    public SimpleDuration getQueryFrequency() {
        return queryFrequency;
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
        final ScheduledQueryAnalyticProcessConfig that = (ScheduledQueryAnalyticProcessConfig) o;
        return Objects.equals(minEventTimeMs, that.minEventTimeMs) &&
                Objects.equals(maxEventTimeMs, that.maxEventTimeMs) &&
                Objects.equals(timeToWaitForData, that.timeToWaitForData) &&
                Objects.equals(queryFrequency, that.queryFrequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), minEventTimeMs, maxEventTimeMs, timeToWaitForData, queryFrequency);
    }

    @Override
    public String toString() {
        return "ScheduledQueryAnalyticProcessConfig{" +
                "enabled=" + enabled +
                ", node=" + node +
                ", minEventTimeMs=" + minEventTimeMs +
                ", maxEventTimeMs=" + maxEventTimeMs +
                ", timeToWaitForData=" + timeToWaitForData +
                ", queryFrequency=" + queryFrequency +
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
            AbstractAnalyticProcessConfigBuilder<ScheduledQueryAnalyticProcessConfig, AnalyticProcessConfigBuilder> {

        private Long minEventTimeMs;
        private Long maxEventTimeMs;
        private SimpleDuration timeToWaitForData;
        private SimpleDuration queryFrequency;

        private AnalyticProcessConfigBuilder() {
        }

        private AnalyticProcessConfigBuilder(
                final ScheduledQueryAnalyticProcessConfig scheduledQueryAnalyticProcessConfig) {
            super(scheduledQueryAnalyticProcessConfig);
            this.minEventTimeMs = scheduledQueryAnalyticProcessConfig.minEventTimeMs;
            this.maxEventTimeMs = scheduledQueryAnalyticProcessConfig.maxEventTimeMs;
            this.timeToWaitForData = scheduledQueryAnalyticProcessConfig.timeToWaitForData;
            this.queryFrequency = scheduledQueryAnalyticProcessConfig.queryFrequency;
        }

        public AnalyticProcessConfigBuilder minEventTimeMs(final Long minEventTimeMs) {
            this.minEventTimeMs = minEventTimeMs;
            return self();
        }

        public AnalyticProcessConfigBuilder maxEventTimeMs(final Long maxEventTimeMs) {
            this.maxEventTimeMs = maxEventTimeMs;
            return self();
        }

        public AnalyticProcessConfigBuilder timeToWaitForData(final SimpleDuration timeToWaitForData) {
            this.timeToWaitForData = timeToWaitForData;
            return self();
        }

        public AnalyticProcessConfigBuilder queryFrequency(final SimpleDuration queryFrequency) {
            this.queryFrequency = queryFrequency;
            return self();
        }

        @Override
        protected AnalyticProcessConfigBuilder self() {
            return this;
        }

        public ScheduledQueryAnalyticProcessConfig build() {
            return new ScheduledQueryAnalyticProcessConfig(
                    enabled,
                    node,
                    minEventTimeMs,
                    maxEventTimeMs,
                    timeToWaitForData,
                    queryFrequency);
        }
    }
}
