package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class SetTag extends AbstractAnnotationChange {

    @JsonProperty
    private final AnnotationTag tag;

    @JsonCreator
    public SetTag(@JsonProperty("tag") final AnnotationTag tag) {
        this.tag = tag;
    }

    public AnnotationTag getTag() {
        return tag;
    }
}
