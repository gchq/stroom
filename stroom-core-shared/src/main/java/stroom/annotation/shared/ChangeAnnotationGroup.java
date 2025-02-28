package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ChangeAnnotationGroup extends AbstractAnnotationChange {

    @JsonProperty
    private final AnnotationGroup annotationGroup;

    @JsonCreator
    public ChangeAnnotationGroup(@JsonProperty("annotationGroup") final AnnotationGroup annotationGroup) {
        this.annotationGroup = annotationGroup;
    }

    public AnnotationGroup getAnnotationGroup() {
        return annotationGroup;
    }
}
