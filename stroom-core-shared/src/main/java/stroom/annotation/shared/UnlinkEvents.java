package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public final class UnlinkEvents extends AbstractAnnotationChange {

    @JsonProperty
    private final List<EventId> events;

    @JsonCreator
    public UnlinkEvents(@JsonProperty("events") final List<EventId> events) {
        this.events = events;
    }

    public List<EventId> getEvents() {
        return events;
    }
}
