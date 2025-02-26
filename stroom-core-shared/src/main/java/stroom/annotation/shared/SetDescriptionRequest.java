package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class SetDescriptionRequest {

    @JsonProperty
    private final long annotationId;
    @JsonProperty
    private final String description;

    @JsonCreator
    public SetDescriptionRequest(@JsonProperty("annotationId") final long annotationId,
                                 @JsonProperty("description") final String description) {
        this.annotationId = annotationId;
        this.description = description;
    }

    public long getAnnotationId() {
        return annotationId;
    }

    public String getDescription() {
        return description;
    }
}
