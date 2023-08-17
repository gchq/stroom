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
public class ScheduledQueryAnalyticConfig extends AnalyticConfig {

    @JsonProperty
    private final SimpleDuration timeToWaitForData;
    @JsonProperty
    private final SimpleDuration queryFrequency;

    @JsonCreator
    public ScheduledQueryAnalyticConfig(@JsonProperty("timeToWaitForData") final SimpleDuration timeToWaitForData,
                                        @JsonProperty("queryFrequency") final SimpleDuration queryFrequency) {
        this.timeToWaitForData = timeToWaitForData;
        this.queryFrequency = queryFrequency;
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
        final ScheduledQueryAnalyticConfig that = (ScheduledQueryAnalyticConfig) o;
        return Objects.equals(queryFrequency, that.queryFrequency) &&
                Objects.equals(timeToWaitForData, that.timeToWaitForData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryFrequency, timeToWaitForData);
    }

    @Override
    public String toString() {
        return "ScheduledQueryConfig{" +
                "queryFrequency=" + queryFrequency +
                ", timeToWaitForData=" + timeToWaitForData +
                '}';
    }
}
