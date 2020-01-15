package stroom.annotation.shared;

public class EventLink {
    private long annotationId;
    private EventId eventId;

    public EventLink() {
    }

    public EventLink(final long annotationId, final EventId eventId) {
        this.annotationId = annotationId;
        this.eventId = eventId;
    }

    public long getAnnotationId() {
        return annotationId;
    }

    public EventId getEventId() {
        return eventId;
    }
}
