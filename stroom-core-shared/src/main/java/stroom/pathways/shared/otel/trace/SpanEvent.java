package stroom.pathways.shared.otel.trace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class SpanEvent {

    @JsonProperty("timeUnixNano")
    private final String timeUnixNano;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("attributes")
    private final List<KeyValue> attributes;

    @JsonProperty("droppedAttributesCount")
    private final int droppedAttributesCount;

    @JsonCreator
    public SpanEvent(@JsonProperty("timeUnixNano") final String timeUnixNano,
                     @JsonProperty("name") final String name,
                     @JsonProperty("attributes") final List<KeyValue> attributes,
                     @JsonProperty("droppedAttributesCount") final int droppedAttributesCount) {
        this.timeUnixNano = timeUnixNano;
        this.name = name;
        this.attributes = attributes;
        this.droppedAttributesCount = droppedAttributesCount;
    }

    public String getTimeUnixNano() {
        return timeUnixNano;
    }

    public String getName() {
        return name;
    }

    public List<KeyValue> getAttributes() {
        return attributes;
    }

    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SpanEvent spanEvent = (SpanEvent) o;
        return droppedAttributesCount == spanEvent.droppedAttributesCount &&
               Objects.equals(timeUnixNano, spanEvent.timeUnixNano) &&
               Objects.equals(name, spanEvent.name) &&
               Objects.equals(attributes, spanEvent.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeUnixNano, name, attributes, droppedAttributesCount);
    }

    @Override
    public String toString() {
        return "SpanEvent{" +
               "timeUnixNano='" + timeUnixNano + '\'' +
               ", name='" + name + '\'' +
               ", attributes=" + attributes +
               ", droppedAttributesCount=" + droppedAttributesCount +
               '}';
    }
}
