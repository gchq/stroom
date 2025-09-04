package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"id", "name"})
@JsonInclude(Include.NON_NULL)
public final class ElementId {

    @JsonProperty
    private final String id;
    @JsonProperty
    private final String name;

    public ElementId(final String id) {
        this(id, id);
    }

    @JsonCreator
    public ElementId(@JsonProperty("id") final String id,
                     @JsonProperty("name") final String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ElementId elementId = (ElementId) o;
        return Objects.equals(id, elementId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        if (name == null || Objects.equals(id, name)) {
            return id;
        }
        return name + " {" + id + "}";
    }
}
