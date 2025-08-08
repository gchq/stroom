package stroom.pathways.shared.otel.trace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Resource {

    @JsonProperty("attributes")
    private final List<KeyValue> attributes;

    @JsonProperty("droppedAttributesCount")
    private final int droppedAttributesCount;

    @JsonCreator
    public Resource(@JsonProperty("attributes") final List<KeyValue> attributes,
                    @JsonProperty("droppedAttributesCount") final int droppedAttributesCount) {
        this.attributes = attributes;
        this.droppedAttributesCount = droppedAttributesCount;
    }

    public List<KeyValue> getAttributes() {
        return attributes;
    }

    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Resource resource = (Resource) o;
        return droppedAttributesCount == resource.droppedAttributesCount &&
               Objects.equals(attributes, resource.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributes, droppedAttributesCount);
    }

    @Override
    public String toString() {
        return "Resource{" +
               "attributes=" + attributes +
               ", droppedAttributesCount=" + droppedAttributesCount +
               '}';
    }
}
