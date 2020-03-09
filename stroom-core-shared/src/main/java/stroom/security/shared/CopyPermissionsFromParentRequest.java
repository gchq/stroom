package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.DocRef;

@JsonInclude(Include.NON_NULL)
public class CopyPermissionsFromParentRequest {
    @JsonProperty
    private final DocRef docRef;

    @JsonCreator
    public CopyPermissionsFromParentRequest(@JsonProperty("docRef") final DocRef docRef) {
        this.docRef = docRef;
    }

    public DocRef getDocRef() {
        return docRef;
    }
}