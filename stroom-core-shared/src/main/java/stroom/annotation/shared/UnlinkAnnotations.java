package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public final class UnlinkAnnotations extends AbstractAnnotationChange {

    @JsonProperty
    private final List<Long> annotations;

    @JsonCreator
    public UnlinkAnnotations(@JsonProperty("annotations") final List<Long> annotations) {
        this.annotations = annotations;
    }

    public List<Long> getAnnotations() {
        return annotations;
    }
}
