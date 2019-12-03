package stroom.annotation.shared;

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
    public String toString() {
        return streamId + ":" + eventId;
    }
}
