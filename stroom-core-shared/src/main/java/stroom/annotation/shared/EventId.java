package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_DEFAULT)
public class EventId {
    @JsonProperty
    private final long streamId;
    @JsonProperty
    private final long eventId;

    @JsonCreator
    public EventId(@JsonProperty("streamId") final long streamId,
                   @JsonProperty("eventId") final long eventId) {
        this.streamId = streamId;
        this.eventId = eventId;
    }

    public long getStreamId() {
        return streamId;
    }

    public long getEventId() {
        return eventId;
    }

    public static EventId parse(final String string) {
        if (string != null && !string.isEmpty()) {
            final String[] parts = string.split(":");
            if (parts.length == 2) {
                try {
                    return new EventId(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
                } catch (final NumberFormatException e) {
                    // Ignore.
                }
            }
        }
        return null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final EventId eventId1 = (EventId) o;
        return streamId == eventId1.streamId &&
                eventId == eventId1.eventId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamId, eventId);
    }

    @Override
    public String toString() {
        return streamId + ":" + eventId;
    }
}
