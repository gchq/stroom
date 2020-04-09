package stroom.authentication.oauth2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.authentication.api.OIDC;

@JsonInclude(Include.NON_NULL)
class TokenRequest {
    @JsonProperty(OIDC.GRANT_TYPE)
    private final String grantType;

    @JsonProperty(OIDC.CLIENT_ID)
    private final String clientId;
    @JsonProperty(OIDC.CLIENT_SECRET)
    private final String clientSecret;
    @JsonProperty(OIDC.REDIRECT_URI)
    private final String redirectUri;
    @JsonProperty(OIDC.CODE)
    private final String code;

    @JsonCreator
    TokenRequest(@JsonProperty(OIDC.GRANT_TYPE) final String grantType,
                        @JsonProperty(OIDC.CLIENT_ID) final String clientId,
                        @JsonProperty(OIDC.CLIENT_SECRET) final String clientSecret,
                        @JsonProperty(OIDC.REDIRECT_URI) final String redirectUri,
                        @JsonProperty(OIDC.CODE) final String code) {
        this.grantType = grantType;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.code = code;
    }

    public String getGrantType() {
        return grantType;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getCode() {
        return code;
    }
}
