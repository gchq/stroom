package stroom.security.impl;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A request to determine if the user has the requested permission on a 'document'")
public class AuthorisationRequest {

    @JsonProperty
    @Schema(required = true)
    private DocRef docRef;

    @JsonProperty
    @Schema(description = "The permission (e.g. UPDATE) that the user is requesting to use with the document",
            example = "UPDATE",
            required = true)
    private String permission;

    public AuthorisationRequest() {
    }

    public AuthorisationRequest(final DocRef docRef, final String permission) {
        this.docRef = docRef;
        this.permission = permission;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getPermission() {
        return permission;
    }
}
