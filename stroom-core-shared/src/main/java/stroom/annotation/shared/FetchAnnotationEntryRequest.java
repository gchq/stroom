package stroom.annotation.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class FetchAnnotationEntryRequest {

    @JsonProperty
    private final DocRef annotationRef;
    @JsonProperty
    private final long annotationEntryId;

    @JsonCreator
    public FetchAnnotationEntryRequest(@JsonProperty("annotationRef") final DocRef annotationRef,
                                       @JsonProperty("annotationEntryId") final long annotationEntryId) {
        this.annotationRef = annotationRef;
        this.annotationEntryId = annotationEntryId;
    }

    public DocRef getAnnotationRef() {
        return annotationRef;
    }

    public long getAnnotationEntryId() {
        return annotationEntryId;
    }
}
