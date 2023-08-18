package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StreamingAnalyticProcessTrackerData.class, name = "streaming"),
        @JsonSubTypes.Type(value = TableBuilderAnalyticProcessTrackerData.class, name = "table_builder"),
        @JsonSubTypes.Type(value = ScheduledQueryAnalyticProcessTrackerData.class, name = "scheduled_query"),
})
public abstract class AnalyticProcessTrackerData {

    @JsonProperty
    private String message;

    public AnalyticProcessTrackerData() {
    }

    public AnalyticProcessTrackerData(final String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticProcessTrackerData that = (AnalyticProcessTrackerData) o;
        return Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message);
    }

    @Override
    public String toString() {
        return "AnalyticProcessorTrackerData{" +
                "message='" + message + '\'' +
                '}';
    }
}
