package stroom.security.impl;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class OpenIdConfig extends AbstractConfig {

    public static final String PROP_NAME_CLIENT_ID = "clientId";
    public static final String PROP_NAME_CLIENT_SECRET = "clientSecret";

    private boolean useInternal = true;

    /**
     * e.g. https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/openid-configuration
     * e.g. https://accounts.google.com/.well-known/openid-configuration
     */
    private String openIdConfigurationEndpoint;

    /**
     * Don't set if using configuration endpoint
     * e.g. stroom
     * e.g. accounts.google.com
     */
    private String issuer;

    /**
     * Don't set if using configuration endpoint
     * e.g. https://mydomain.auth.us-east-1.amazoncognito.com/oauth2/authorize
     * e.g. https://accounts.google.com/o/oauth2/v2/auth
     */
    private String authEndpoint;

    /**
     * Don't set if using configuration endpoint
     * e.g. https://mydomain.auth.us-east-1.amazoncognito.com/oauth2/token
     * e.g. https://accounts.google.com/o/oauth2/token
     */
    private String tokenEndpoint;

    /**
     * Don't set if using configuration endpoint
     * e.g. https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json
     * e.g. https://www.googleapis.com/oauth2/v3/certs
     */
    private String jwksUri;

    /**
     * Not provided by the configuration endpoint, must be configured manually.
     * <p>
     * e.g. https://mydomain.auth.us-east-1.amazoncognito.com/logout
     * e.g. https://www.google.com/accounts/Logout?
     * continue=https://appengine.google.com/_ah/logout?continue=http://www.example.com"
     */
    private String logoutEndpoint;

    /**
     * Some OpenId providers, e.g. AWS Cognito, require a form to be used for token requests.
     */
    private boolean formTokenRequest = true;

    /**
     * Optionally choose a class to resolve JWT claims
     */
    private String jwtClaimsResolver;

    /**
     * The client ID used in OpenId authentication.
     */
    private String clientId;
    /**
     * The client secret used in OpenId authentication.
     */
    private String clientSecret;

    /**
     * @return true if Stroom will handle the OpenId authentication, false if an external
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

    @JsonPropertyDescription("You can set an openid-configuration URL to automatically configure much of the openid " +
            "settings. Without this the other endpoints etc must be set manually.")
    @JsonProperty
    public String getOpenIdConfigurationEndpoint() {
        return openIdConfigurationEndpoint;
    }

    public void setOpenIdConfigurationEndpoint(final String openIdConfigurationEndpoint) {
        this.openIdConfigurationEndpoint = openIdConfigurationEndpoint;
    }

    @JsonProperty
    @JsonPropertyDescription("The issuer used in OpenId authentication." +
            "Should only be set if not using a configuration endpoint.")
    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(final String issuer) {
        this.issuer = issuer;
    }

    @JsonProperty
    @JsonPropertyDescription("The authentication endpoint used in OpenId authentication." +
            "Should only be set if not using a configuration endpoint.")
    public String getAuthEndpoint() {
        return authEndpoint;
    }

    public void setAuthEndpoint(final String authEndpoint) {
        this.authEndpoint = authEndpoint;
    }

    @JsonProperty
    @JsonPropertyDescription("The token endpoint used in OpenId authentication." +
            "Should only be set if not using a configuration endpoint.")
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(final String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    @JsonProperty
    @JsonPropertyDescription("The URI to obtain the JSON Web Key Set from in OpenId authentication." +
            "Should only be set if not using a configuration endpoint.")
    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(final String jwksUri) {
        this.jwksUri = jwksUri;
    }

    @JsonProperty
    @JsonPropertyDescription("The logout endpoint for the identity provider." +
            "This is not typically provided by the configuration endpoint.")
    public String getLogoutEndpoint() {
        return logoutEndpoint;
    }

    public void setLogoutEndpoint(final String logoutEndpoint) {
        this.logoutEndpoint = logoutEndpoint;
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
    @JsonPropertyDescription("The client ID used in OpenId authentication.")
    public String getClientId() {
        return clientId;
    }

    public void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    // TODO Not sure we can add NotNull to this as it has no default and if useInternal is true
    //  it doesn't need a value
    @JsonProperty(PROP_NAME_CLIENT_SECRET)
    @JsonPropertyDescription("The client secret used in OpenId authentication.")
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
                "useInternal=" + useInternal +
                ", openIdConfigurationEndpoint='" + openIdConfigurationEndpoint + '\'' +
                ", issuer='" + issuer + '\'' +
                ", authEndpoint='" + authEndpoint + '\'' +
                ", tokenEndpoint='" + tokenEndpoint + '\'' +
                ", logoutEndpoint='" + logoutEndpoint + '\'' +
                ", jwksUri='" + jwksUri + '\'' +
                ", formTokenRequest=" + formTokenRequest +
                ", jwtClaimsResolver='" + jwtClaimsResolver + '\'' +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='" + clientSecret + '\'' +
                '}';
    }
}
