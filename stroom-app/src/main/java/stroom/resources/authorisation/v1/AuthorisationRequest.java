package stroom.resources.authorisation.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import stroom.query.api.v2.DocRef;

@ApiModel(description = "A request to determine if the user has the requested permission on a 'document'")
public class AuthorisationRequest {

    @JsonProperty
    @ApiModelProperty(required = true)
    private DocRef docRef;

    @JsonProperty
    @ApiModelProperty(
            value = "The permission (e.g. UPDATE) that the user is requesting to use with the document",
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
