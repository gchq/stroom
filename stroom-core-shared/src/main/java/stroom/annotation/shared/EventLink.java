package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class EventLink {
    @JsonProperty
    private long annotationId;
    @JsonProperty
    private EventId eventId;

    @JsonCreator
    public EventLink(@JsonProperty("annotationId") final long annotationId,
                     @JsonProperty("eventId") final EventId eventId) {
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
