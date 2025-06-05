package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class ChangeComment extends AbstractAnnotationChange {

    @JsonProperty
    private final String comment;

    @JsonCreator
    public ChangeComment(@JsonProperty("comment") final String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }
}
