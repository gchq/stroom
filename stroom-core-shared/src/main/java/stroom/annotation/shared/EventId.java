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

    public long getEventId() {
        return eventId;
    }
}
