package stroom.security.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class OpenIdConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_CLIENT_ID = "clientId";
    public static final String PROP_NAME_CLIENT_SECRET = "clientSecret";

    private final boolean useInternal;

    /**
     * e.g. https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/openid-configuration
     * e.g. https://accounts.google.com/.well-known/openid-configuration
     */
    private final String openIdConfigurationEndpoint;

    /**
     * Don't set if using configuration endpoint
     * e.g. stroom
     * e.g. accounts.google.com
     */
    private final String issuer;

    /**
     * Don't set if using configuration endpoint
     * e.g. https://mydomain.auth.us-east-1.amazoncognito.com/oauth2/authorize
     * e.g. https://accounts.google.com/o/oauth2/v2/auth
     */
    private final String authEndpoint;

    /**
     * Don't set if using configuration endpoint
     * e.g. https://mydomain.auth.us-east-1.amazoncognito.com/oauth2/token
     * e.g. https://accounts.google.com/o/oauth2/token
     */
    private final String tokenEndpoint;

    /**
     * Don't set if using configuration endpoint
     * e.g. https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json
     * e.g. https://www.googleapis.com/oauth2/v3/certs
     */
    private final String jwksUri;

    /**
     * Not provided by the configuration endpoint, must be configured manually.
     * <p>
     * e.g. https://mydomain.auth.us-east-1.amazoncognito.com/logout
     * e.g. https://www.google.com/accounts/Logout?
     * continue=https://appengine.google.com/_ah/logout?continue=http://www.example.com"
     */
    private final String logoutEndpoint;

    /**
     * Some OpenId providers, e.g. AWS Cognito, require a form to be used for token requests.
     */
    private final boolean formTokenRequest;

    /**
     * The client ID used in OpenId authentication.
     */
    private final String clientId;

    /**
     * The client secret used in OpenId authentication.
     */
    private final String clientSecret;

    /**
     * If a custom auth flow request scope is required then this should be set.
     */
    private final String requestScope;

    /**
     * Redirect URI
     */
    private final String redirectUri;

    public OpenIdConfig() {
        useInternal = true;
        openIdConfigurationEndpoint = null;
        issuer = null;
        authEndpoint = null;
        tokenEndpoint = null;
        jwksUri = null;
        logoutEndpoint = null;
        formTokenRequest = false;
        clientSecret = null;
        clientId = null;
        requestScope = null;
        redirectUri = null;
    }

    @JsonCreator
    public OpenIdConfig(@JsonProperty("useInternal") final boolean useInternal,
                        @JsonProperty("openIdConfigurationEndpoint") final String openIdConfigurationEndpoint,
                        @JsonProperty("issuer") final String issuer,
                        @JsonProperty("authEndpoint") final String authEndpoint,
                        @JsonProperty("tokenEndpoint") final String tokenEndpoint,
                        @JsonProperty("jwksUri") final String jwksUri,
                        @JsonProperty("logoutEndpoint") final String logoutEndpoint,
                        @JsonProperty("formTokenRequest") final boolean formTokenRequest,
                        @JsonProperty("clientId") final String clientId,
                        @JsonProperty("clientSecret") final String clientSecret,
                        @JsonProperty("requestScope") final String requestScope,
                        @JsonProperty("redirectUri") final String redirectUri) {
        this.useInternal = useInternal;
        this.openIdConfigurationEndpoint = openIdConfigurationEndpoint;
        this.issuer = issuer;
        this.authEndpoint = authEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.jwksUri = jwksUri;
        this.logoutEndpoint = logoutEndpoint;
        this.formTokenRequest = formTokenRequest;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.requestScope = requestScope;
        this.redirectUri = redirectUri;
    }

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

    @JsonProperty
    @JsonPropertyDescription("You can set an openid-configuration URL to automatically configure much of the openid " +
            "settings. Without this the other endpoints etc must be set manually.")
    public String getOpenIdConfigurationEndpoint() {
        return openIdConfigurationEndpoint;
    }

    @JsonProperty
    @JsonPropertyDescription("The issuer used in OpenId authentication." +
            "Should only be set if not using a configuration endpoint.")
    public String getIssuer() {
        return issuer;
    }

    @JsonProperty
    @JsonPropertyDescription("The authentication endpoint used in OpenId authentication." +
            "Should only be set if not using a configuration endpoint.")
    public String getAuthEndpoint() {
        return authEndpoint;
    }

    @JsonProperty
    @JsonPropertyDescription("The token endpoint used in OpenId authentication." +
            "Should only be set if not using a configuration endpoint.")
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    @JsonProperty
    @JsonPropertyDescription("The URI to obtain the JSON Web Key Set from in OpenId authentication." +
            "Should only be set if not using a configuration endpoint.")
    public String getJwksUri() {
        return jwksUri;
    }

    @JsonProperty
    @JsonPropertyDescription("The logout endpoint for the identity provider." +
            "This is not typically provided by the configuration endpoint.")
    public String getLogoutEndpoint() {
        return logoutEndpoint;
    }

    @JsonProperty(PROP_NAME_CLIENT_ID)
    @JsonPropertyDescription("The client ID used in OpenId authentication.")
    public String getClientId() {
        return clientId;
    }

    // TODO Not sure we can add NotNull to this as it has no default and if useInternal is true
    //  it doesn't need a value
    @JsonProperty(PROP_NAME_CLIENT_SECRET)
    @JsonPropertyDescription("The client secret used in OpenId authentication.")
    public String getClientSecret() {
        return clientSecret;
    }

    @JsonProperty
    @JsonPropertyDescription("Some OpenId providers, e.g. AWS Cognito, require a form to be used for token requests.")
    public boolean isFormTokenRequest() {
        return formTokenRequest;
    }

    @JsonProperty
    @JsonPropertyDescription("If a custom auth flow request scope is required then this should be set.")
    public String getRequestScope() {
        return requestScope;
    }

    @JsonProperty
    @JsonPropertyDescription("Redirect URI used to receive the authorisation code from the remote server. " +
            "If not specified, the user is redirected to the original request URL.")
    public String getRedirectUri() {
        return redirectUri;
    }

    @Override
    public String toString() {
        return "OpenIdConfig{" +
                "useInternal=" + useInternal +
                ", openIdConfigurationEndpoint='" + openIdConfigurationEndpoint + '\'' +
                ", issuer='" + issuer + '\'' +
                ", authEndpoint='" + authEndpoint + '\'' +
                ", tokenEndpoint='" + tokenEndpoint + '\'' +
                ", jwksUri='" + jwksUri + '\'' +
                ", logoutEndpoint='" + logoutEndpoint + '\'' +
                ", formTokenRequest=" + formTokenRequest +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='" + clientSecret + '\'' +
                ", requestScope='" + requestScope + '\'' +
                ", redirectUri='" + redirectUri + '\'' +
                '}';
    }
}
