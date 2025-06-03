package stroom.annotation.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class SingleAnnotationChangeRequest {

    @JsonProperty
    private final DocRef annotationRef;
    @JsonProperty
    private final AbstractAnnotationChange change;

    @JsonCreator
    public SingleAnnotationChangeRequest(@JsonProperty("annotationRef") final DocRef annotationRef,
                                         @JsonProperty("change") final AbstractAnnotationChange change) {
        this.annotationRef = annotationRef;
        this.change = change;
    }

    public DocRef getAnnotationRef() {
        return annotationRef;
    }

    public AbstractAnnotationChange getChange() {
        return change;
    }
}
