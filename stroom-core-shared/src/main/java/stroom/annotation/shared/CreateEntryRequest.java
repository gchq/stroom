package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.UserRef;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class CreateEntryRequest {

    @JsonProperty
    private final Annotation annotation;
    @JsonProperty
    private final String type;
    @JsonProperty
    private final EntryValue entryValue;
    @JsonProperty
    private final List<EventId> linkedEvents;

    @JsonCreator
    public CreateEntryRequest(@JsonProperty("annotation") final Annotation annotation,
                              @JsonProperty("type") final String type,
                              @JsonProperty("entryValue") final EntryValue entryValue,
                              @JsonProperty("linkedEvents") final List<EventId> linkedEvents) {
        this.annotation = annotation;
        this.type = type;
        this.entryValue = entryValue;
        this.linkedEvents = linkedEvents;
    }

    public CreateEntryRequest(final Annotation annotation,
                              final String type,
                              final EntryValue entryValue) {
        this(annotation, type, entryValue, Collections.emptyList());
    }

    public CreateEntryRequest(final Annotation annotation,
                              final String type,
                              final String value) {
        this(annotation, type, StringEntryValue.of(value), Collections.emptyList());
    }

    public CreateEntryRequest(final Annotation annotation,
                              final String type,
                              final String value,
                              final List<EventId> linkedEvents) {
        this(annotation, type, StringEntryValue.of(value), linkedEvents);
    }

    public static CreateEntryRequest assignmentRequest(
            final Annotation annotation,
            final UserRef assignedTo) {

        return new CreateEntryRequest(
                annotation,
                Annotation.ASSIGNED_TO,
                UserRefEntryValue.of(assignedTo),
                Collections.emptyList());
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public String getType() {
        return type;
    }

    public EntryValue getEntryValue() {
        return entryValue;
    }

    public List<EventId> getLinkedEvents() {
        return linkedEvents;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CreateEntryRequest that = (CreateEntryRequest) o;
        return Objects.equals(annotation, that.annotation) && Objects.equals(type,
                that.type) && Objects.equals(entryValue,
                that.entryValue) && Objects.equals(linkedEvents, that.linkedEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotation, type, entryValue, linkedEvents);
    }

    @Override
    public String toString() {
        return "CreateEntryRequest{" +
                "annotation=" + annotation +
                ", type='" + type + '\'' +
                ", entryValue='" + entryValue + '\'' +
                ", linkedEvents=" + linkedEvents +
                '}';
    }
}
