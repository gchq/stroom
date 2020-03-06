package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class CreateEntryRequest {
    @JsonProperty
    private Annotation annotation;
    @JsonProperty
    private String type;
    @JsonProperty
    private String data;
    @JsonProperty
    private List<EventId> linkedEvents;

    public CreateEntryRequest(final Annotation annotation,
                              final String type,
                              final String data) {
        this.annotation = annotation;
        this.type = type;
        this.data = data;
    }

    @JsonCreator
    public CreateEntryRequest(@JsonProperty("annotation") final Annotation annotation,
                              @JsonProperty("type") final String type,
                              @JsonProperty("data") final String data,
                              @JsonProperty("linkedEvents") final List<EventId> linkedEvents) {
        this.annotation = annotation;
        this.type = type;
        this.data = data;
        this.linkedEvents = linkedEvents;
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

    public List<EventId> getLinkedEvents() {
        return linkedEvents;
    }
}
