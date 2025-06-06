package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class ChangeTitle extends AbstractAnnotationChange {

    @JsonProperty
    private final String title;

    @JsonCreator
    public ChangeTitle(@JsonProperty("title") final String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
