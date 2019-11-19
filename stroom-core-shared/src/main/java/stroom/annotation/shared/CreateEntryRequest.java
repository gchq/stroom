package stroom.annotation.shared;

public class CreateEntryRequest {
    private Annotation annotation;
    private String type;
    private String data;
    private long streamId;
    private long eventId;

    public CreateEntryRequest() {
    }

    public CreateEntryRequest(final Annotation annotation,
                              final String type,
                              final String data) {
        this.annotation = annotation;
        this.type = type;
        this.data = data;
    }

    public CreateEntryRequest(final Annotation annotation, final String type, final String data, final long streamId, final long eventId) {
        this.annotation = annotation;
        this.type = type;
        this.data = data;
        this.streamId = streamId;
        this.eventId = eventId;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public void setAnnotation(final Annotation annotation) {
        this.annotation = annotation;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
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
}
