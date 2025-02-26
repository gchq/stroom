package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class CreateAnnotationRequest {

    @JsonProperty
    private final Annotation annotation;
    @JsonProperty
    private final List<EventId> linkedEvents;

    @JsonCreator
    public CreateAnnotationRequest(@JsonProperty("annotation") final Annotation annotation,
                                   @JsonProperty("linkedEvents") final List<EventId> linkedEvents) {
        this.annotation = annotation;
        this.linkedEvents = linkedEvents;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public List<EventId> getLinkedEvents() {
        return linkedEvents;
    }
}
