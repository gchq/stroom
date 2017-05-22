package stroom.resources.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.query.api.v1.DocRef;

public class AuthorisationRequest {
    @JsonProperty
    private DocRef docRef;
    @JsonProperty
    private String permissions;

    public AuthorisationRequest() {}

    public AuthorisationRequest(DocRef docRef, String permissions){
        this.docRef = docRef;
        this.permissions = permissions;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getPermissions() {
        return permissions;
    }
}
