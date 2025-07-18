package stroom.annotation.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ChangeAnnotationEntryRequest {

    @JsonProperty
    private final DocRef annotationRef;
    @JsonProperty
    private final long annotationEntryId;
    @JsonProperty
    private final String data;

    @JsonCreator
    public ChangeAnnotationEntryRequest(@JsonProperty("annotationRef") final DocRef annotationRef,
                                        @JsonProperty("annotationEntryId") final long annotationEntryId,
                                        @JsonProperty("data") final String data) {
        this.annotationRef = annotationRef;
        this.annotationEntryId = annotationEntryId;
        this.data = data;
    }

    public DocRef getAnnotationRef() {
        return annotationRef;
    }

    public long getAnnotationEntryId() {
        return annotationEntryId;
    }

    public String getData() {
        return data;
    }
}
