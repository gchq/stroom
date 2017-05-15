package stroom.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.query.api.DocRef;

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
