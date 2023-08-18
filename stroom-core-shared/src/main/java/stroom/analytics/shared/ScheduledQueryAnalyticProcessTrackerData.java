package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ScheduledQueryAnalyticProcessTrackerData extends AnalyticProcessTrackerData {

    @JsonProperty
    private Long lastExecutionTimeMs;
    @JsonProperty
    private Long lastWindowStartTimeMs;
    @JsonProperty
    private Long lastWindowEndTimeMs;

    public ScheduledQueryAnalyticProcessTrackerData() {
    }

    @JsonCreator
    public ScheduledQueryAnalyticProcessTrackerData(@JsonProperty("lastExecutionTimeMs") final Long lastExecutionTimeMs,
                                                    @JsonProperty("lastWindowStartTimeMs")
                                                    final Long lastWindowStartTimeMs,
                                                    @JsonProperty("lastWindowEndTimeMs") final Long lastWindowEndTimeMs,
                                                    @JsonProperty("message") final String message) {
        super(message);
        this.lastExecutionTimeMs = lastExecutionTimeMs;
        this.lastWindowStartTimeMs = lastWindowStartTimeMs;
        this.lastWindowEndTimeMs = lastWindowEndTimeMs;
    }

    public Long getLastExecutionTimeMs() {
        return lastExecutionTimeMs;
    }

    public void setLastExecutionTimeMs(final Long lastExecutionTimeMs) {
        this.lastExecutionTimeMs = lastExecutionTimeMs;
    }

    public Long getLastWindowStartTimeMs() {
        return lastWindowStartTimeMs;
    }

    public void setLastWindowStartTimeMs(final Long lastWindowStartTimeMs) {
        this.lastWindowStartTimeMs = lastWindowStartTimeMs;
    }

    public Long getLastWindowEndTimeMs() {
        return lastWindowEndTimeMs;
    }

    public void setLastWindowEndTimeMs(final Long lastWindowEndTimeMs) {
        this.lastWindowEndTimeMs = lastWindowEndTimeMs;
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
        final ScheduledQueryAnalyticProcessTrackerData that = (ScheduledQueryAnalyticProcessTrackerData) o;
        return Objects.equals(lastExecutionTimeMs, that.lastExecutionTimeMs) &&
                Objects.equals(lastWindowStartTimeMs, that.lastWindowStartTimeMs) &&
                Objects.equals(lastWindowEndTimeMs, that.lastWindowEndTimeMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), lastExecutionTimeMs, lastWindowStartTimeMs, lastWindowEndTimeMs);
    }
}
