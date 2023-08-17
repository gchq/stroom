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
public class TableBuilderAnalyticConfig extends AnalyticConfig {

    @JsonProperty
    private final SimpleDuration timeToWaitForData;
    @JsonProperty
    private final SimpleDuration dataRetention;

    @JsonCreator
    public TableBuilderAnalyticConfig(@JsonProperty("timeToWaitForData") final SimpleDuration timeToWaitForData,
                                      @JsonProperty("dataRetention") final SimpleDuration dataRetention) {
        this.dataRetention = dataRetention;
        this.timeToWaitForData = timeToWaitForData;
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
        final TableBuilderAnalyticConfig that = (TableBuilderAnalyticConfig) o;
        return Objects.equals(dataRetention, that.dataRetention) &&
                Objects.equals(timeToWaitForData, that.timeToWaitForData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataRetention, timeToWaitForData);
    }

    @Override
    public String toString() {
        return "ScheduledQueryConfig{" +
                "dataRetention=" + dataRetention +
                ", timeToWaitForData=" + timeToWaitForData +
                '}';
    }
}
