package stroom.authentication.oauth2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.authentication.api.OIDC;

@JsonInclude(Include.NON_NULL)
class TokenResponse {
    @JsonProperty(OIDC.ID_TOKEN)
    private final String idToken;

    @JsonCreator
    TokenResponse(@JsonProperty(OIDC.ID_TOKEN) final String idToken) {
        this.idToken = idToken;
    }

    public String getIdToken() {
        return idToken;
    }
}
