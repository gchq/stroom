package stroom.planb.impl.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"name", "timeStamp", "attributes"})
@JsonInclude(Include.NON_NULL)
public class TraceEvent {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final Instant timeStamp;
    @JsonProperty
    private final List<TraceAttribute> attributes;

    @JsonCreator
    public TraceEvent(@JsonProperty("name") final String name,
                      @JsonProperty("timeStamp") final Instant timeStamp,
                      @JsonProperty("attributes") final List<TraceAttribute> attributes) {
        this.name = name;
        this.timeStamp = timeStamp;
        this.attributes = attributes;
    }

    public String getName() {
        return name;
    }

    public Instant getTimeStamp() {
        return timeStamp;
    }

    public List<TraceAttribute> getAttributes() {
        return attributes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TraceEvent that = (TraceEvent) o;
        return Objects.equals(name, that.name) && Objects.equals(timeStamp,
                that.timeStamp) && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, timeStamp, attributes);
    }

    @Override
    public String toString() {
        return "TraceEvent{" +
               "name='" + name + '\'' +
               ", timeStamp=" + timeStamp +
               ", attributes=" + attributes +
               '}';
    }
}
