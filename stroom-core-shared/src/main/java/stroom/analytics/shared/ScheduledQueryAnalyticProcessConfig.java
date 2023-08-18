package stroom.analytics.shared;

import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ScheduledQueryAnalyticProcessConfig extends AnalyticProcessConfig {

    @JsonProperty
    private final Long minEventTimeMs;
    @JsonProperty
    private final Long maxEventTimeMs;
    @JsonProperty
    private final SimpleDuration timeToWaitForData;
    @JsonProperty
    private final SimpleDuration queryFrequency;

    @JsonCreator
    public ScheduledQueryAnalyticProcessConfig(@JsonProperty("minEventTimeMs") final Long minEventTimeMs,
                                               @JsonProperty("maxEventTimeMs") final Long maxEventTimeMs,
                                               @JsonProperty("timeToWaitForData")
                                                   final SimpleDuration timeToWaitForData,
                                               @JsonProperty("queryFrequency") final SimpleDuration queryFrequency) {
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
        final ScheduledQueryAnalyticProcessConfig that = (ScheduledQueryAnalyticProcessConfig) o;
        return Objects.equals(minEventTimeMs, that.minEventTimeMs) &&
                Objects.equals(maxEventTimeMs, that.maxEventTimeMs) &&
                Objects.equals(timeToWaitForData, that.timeToWaitForData) &&
                Objects.equals(queryFrequency, that.queryFrequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minEventTimeMs, maxEventTimeMs, timeToWaitForData, queryFrequency);
    }

    @Override
    public String toString() {
        return "ScheduledQueryAnalyticProcessConfig{" +
                "minEventTimeMs=" + minEventTimeMs +
                ", maxEventTimeMs=" + maxEventTimeMs +
                ", timeToWaitForData=" + timeToWaitForData +
                ", queryFrequency=" + queryFrequency +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Long minEventTimeMs;
        private Long maxEventTimeMs;
        private SimpleDuration timeToWaitForData;
        private SimpleDuration queryFrequency;

        private Builder() {
        }

        private Builder(final ScheduledQueryAnalyticProcessConfig scheduledQueryAnalyticProcessConfig) {
            this.minEventTimeMs = scheduledQueryAnalyticProcessConfig.minEventTimeMs;
            this.maxEventTimeMs = scheduledQueryAnalyticProcessConfig.maxEventTimeMs;
            this.timeToWaitForData = scheduledQueryAnalyticProcessConfig.timeToWaitForData;
            this.queryFrequency = scheduledQueryAnalyticProcessConfig.queryFrequency;
        }

        public Builder minEventTimeMs(final Long minEventTimeMs) {
            this.minEventTimeMs = minEventTimeMs;
            return this;
        }

        public Builder maxEventTimeMs(final Long maxEventTimeMs) {
            this.maxEventTimeMs = maxEventTimeMs;
            return this;
        }

        public Builder timeToWaitForData(final SimpleDuration timeToWaitForData) {
            this.timeToWaitForData = timeToWaitForData;
            return this;
        }

        public Builder queryFrequency(final SimpleDuration queryFrequency) {
            this.queryFrequency = queryFrequency;
            return this;
        }

        public ScheduledQueryAnalyticProcessConfig build() {
            return new ScheduledQueryAnalyticProcessConfig(
                    minEventTimeMs,
                    maxEventTimeMs,
                    timeToWaitForData,
                    queryFrequency);
        }
    }
}
