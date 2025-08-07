package stroom.planb.impl.data;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class TraceEvent {
    private final String name;
    private final Instant timeStamp;
    private final List<TraceAttribute> attributes;

    public TraceEvent(final String name, final Instant timeStamp, final List<TraceAttribute> attributes) {
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
