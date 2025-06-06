package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class ChangeDescription extends AbstractAnnotationChange {

    @JsonProperty
    private final String description;

    @JsonCreator
    public ChangeDescription(@JsonProperty("description") final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
