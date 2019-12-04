package stroom.annotation.shared;

import java.util.Objects;

public class EventId {
    private long streamId;
    private long eventId;

    public EventId() {
    }

    public EventId(final long streamId, final long eventId) {
        this.streamId = streamId;
        this.eventId = eventId;
    }

    public long getStreamId() {
        return streamId;
    }

    public void setStreamId(final long streamId) {
        this.streamId = streamId;
    }

    public long getEventId() {
        return eventId;
    }

    public void setEventId(final long eventId) {
        this.eventId = eventId;
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
