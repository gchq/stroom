package stroom.resources;

import stroom.query.api.DocRef;

public class AuthorisationRequest {
    private DocRef docRef;
    private String permissions;

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
