package stroom.security.impl;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
public class OpenIdConfig extends AbstractConfig {
    public static final String PROP_NAME_CLIENT_ID = "clientId";
    public static final String PROP_NAME_CLIENT_SECRET = "clientSecret";

    private static final String OPEN_ID_CONFIGURATION__ENDPOINT = "https://accounts.google.com/.well-known/openid-configuration";
    private static final String ISSUER = "accounts.google.com";
    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://accounts.google.com/o/oauth2/token";
    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private boolean useInternal = true;

    private String openIdConfigurationEndpoint = OPEN_ID_CONFIGURATION__ENDPOINT;
    private String issuer = ISSUER;
    private String authEndpoint = AUTH_ENDPOINT;
    private String tokenEndpoint = TOKEN_ENDPOINT;
    private String jwksUri = JWKS_URI;
    private boolean formTokenRequest;
    private String jwtClaimsResolver;

    private String clientId;
    private String clientSecret;

    /**
     * @return true if Stroom will handle the OpenId authenication, false if an external
     * OpenId provider is used.
     */
    @JsonProperty
    @JsonPropertyDescription("True if Stroom will handle OpenId authentication, false if an " +
            "external OpenId provider is to be used.")
    public boolean isUseInternal() {
        return useInternal;
    }

    public void setUseInternal(final boolean useInternal) {
        this.useInternal = useInternal;
    }

    @JsonPropertyDescription("You can set an openid-configuration URL to automatically configure much of the openid settings. Without this the other endpoints etc must be set manually.")
    @JsonProperty
    public String getOpenIdConfigurationEndpoint() {
        return openIdConfigurationEndpoint;
    }

    public void setOpenIdConfigurationEndpoint(final String openIdConfigurationEndpoint) {
        this.openIdConfigurationEndpoint = openIdConfigurationEndpoint;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The issuer used in OpenId authentication." +
            "Should only be set if useInternal is true.")
    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(final String issuer) {
        this.issuer = issuer;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The authentication endpoint used in OpenId authentication." +
            "Should only be set if useInternal is true.")
    public String getAuthEndpoint() {
        return authEndpoint;
    }

    public void setAuthEndpoint(final String authEndpoint) {
        this.authEndpoint = authEndpoint;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The token endpoint used in OpenId authentication." +
            "Should only be set if useInternal is true.")
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(final String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The URI to obtain the JSON Web Key Set from in OpenId authentication." +
            "Should only be set if useInternal is true.")
    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(final String jwksUri) {
        this.jwksUri = jwksUri;
    }

    @JsonProperty
    @JsonPropertyDescription("Optionally choose a class to resolve JWT claims")
    public String getJwtClaimsResolver() {
        return jwtClaimsResolver;
    }

    public void setJwtClaimsResolver(final String jwtClaimsResolver) {
        this.jwtClaimsResolver = jwtClaimsResolver;
    }

    // TODO Not sure we can add NotNull to this as it has no default and if useInternal is true
    //  it doesn't need a value
    @JsonProperty(PROP_NAME_CLIENT_ID)
    @JsonPropertyDescription("The client ID used in OpenId authentication." +
            "Should only be set if useInternal is true.")
    public String getClientId() {
        return clientId;
    }

    public void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    // TODO Not sure we can add NotNull to this as it has no default and if useInternal is true
    //  it doesn't need a value
    @JsonProperty(PROP_NAME_CLIENT_SECRET)
    @JsonPropertyDescription("The client secret used in OpenId authentication." +
            "Should only be set if useInternal is true.")
    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(final String clientSecret) {
        this.clientSecret = clientSecret;
    }

    @JsonProperty
    @JsonPropertyDescription("Some OpenId providers, e.g. AWS Cognito, require a form to be used for token requests.")
    public boolean isFormTokenRequest() {
        return formTokenRequest;
    }

    public void setFormTokenRequest(final boolean formTokenRequest) {
        this.formTokenRequest = formTokenRequest;
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
                ", formTokenRequest='" + formTokenRequest + '\'' +
                '}';
    }
}