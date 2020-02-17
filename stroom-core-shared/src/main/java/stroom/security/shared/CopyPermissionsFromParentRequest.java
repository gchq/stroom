package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.DocRef;

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