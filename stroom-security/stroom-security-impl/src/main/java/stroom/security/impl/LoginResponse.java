package stroom.security.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class LoginResponse {
    @JsonProperty
    private final boolean authenticated;
    @JsonProperty
    private final String redirectUri;

    @JsonCreator
    public LoginResponse(@JsonProperty("authenticated") final boolean authenticated,
                         @JsonProperty("redirectUri") final String redirectUri) {
        this.authenticated = authenticated;
        this.redirectUri = redirectUri;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getRedirectUri() {
        return redirectUri;
    }
}
