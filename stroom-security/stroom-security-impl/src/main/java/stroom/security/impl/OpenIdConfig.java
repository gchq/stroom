package stroom.security.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
public class OpenIdConfig extends AbstractConfig {
    public static final String PROP_NAME_CLIENT_ID = "clientId";
    public static final String PROP_NAME_CLIENT_SECRET = "clientSecret";

    private static final String ISSUER = "accounts.google.com";
    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://accounts.google.com/o/oauth2/token";
    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private boolean useInternal = true;

    private String issuer = ISSUER;
    private String authEndpoint = AUTH_ENDPOINT;
    private String tokenEndpoint = TOKEN_ENDPOINT;
    private String jwksUri = JWKS_URI;

    private String clientId;
    private String clientSecret;

    /**
     * @return true if Stroom will handle the OpenId authenication, false if an external
     * OpenId provider is used.
     */
    public boolean isUseInternal() {
        return useInternal;
    }

    public void setUseInternal(final boolean useInternal) {
        this.useInternal = useInternal;
    }

    @NotNull
    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(final String issuer) {
        this.issuer = issuer;
    }

    @NotNull
    public String getAuthEndpoint() {
        return authEndpoint;
    }

    public void setAuthEndpoint(final String authEndpoint) {
        this.authEndpoint = authEndpoint;
    }

    @NotNull
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(final String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    @NotNull
    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(final String jwksUri) {
        this.jwksUri = jwksUri;
    }

    // TODO Not sure we can add NotNull to this as it has no default and if useInternal is true
    //  it doesn't need a value
    @JsonProperty(PROP_NAME_CLIENT_ID)
    public String getClientId() {
        return clientId;
    }

    public void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    // TODO Not sure we can add NotNull to this as it has no default and if useInternal is true
    //  it doesn't need a value
    @JsonProperty(PROP_NAME_CLIENT_SECRET)
    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(final String clientSecret) {
        this.clientSecret = clientSecret;
    }

    @Override
    public String toString() {
        return "OpenIdConfig{" +
                "issuer='" + issuer + '\'' +
                ", authEndpoint='" + authEndpoint + '\'' +
                ", tokenEndpoint='" + tokenEndpoint + '\'' +
                ", jwksUri='" + jwksUri + '\'' +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='" + clientSecret + '\'' +
                '}';
    }
}