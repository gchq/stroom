package stroom.planb.impl.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"name", "startTime", "endTime", "attributes", "events", "insertTime"})
@JsonInclude(Include.NON_NULL)
public class TraceValue {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final Instant startTime;
    @JsonProperty
    private final Instant endTime;
    @JsonProperty
    private final List<TraceAttribute> attributes;
    @JsonProperty
    private final List<TraceEvent> events;
    @JsonProperty
    private final Instant insertTime;

    @JsonCreator
    public TraceValue(@JsonProperty("name") final String name,
                      @JsonProperty("startTime") final Instant startTime,
                      @JsonProperty("endTime") final Instant endTime,
                      @JsonProperty("attributes") final List<TraceAttribute> attributes,
                      @JsonProperty("events") final List<TraceEvent> events,
                      @JsonProperty("insertTime") final Instant insertTime) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.attributes = attributes;
        this.events = events;
        this.insertTime = insertTime;
    }

    public String getName() {
        return name;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public List<TraceAttribute> getAttributes() {
        return attributes;
    }

    public List<TraceEvent> getEvents() {
        return events;
    }

    public Instant getInsertTime() {
        return insertTime;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TraceValue that = (TraceValue) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(startTime, that.startTime) &&
               Objects.equals(endTime, that.endTime) &&
               Objects.equals(attributes, that.attributes) &&
               Objects.equals(events, that.events) &&
               Objects.equals(insertTime, that.insertTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, startTime, endTime, attributes, events, insertTime);
    }

    @Override
    public String toString() {
        return "TraceValue{" +
               "name='" + name + '\'' +
               ", startTime=" + startTime +
               ", endTime=" + endTime +
               ", attributes=" + attributes +
               ", events=" + events +
               ", insertTime=" + insertTime +
               '}';
    }
}
