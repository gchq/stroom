package stroom.security.openid.api;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;

import java.util.Objects;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;


@JsonPropertyOrder(alphabetic = true)
public abstract class OpenIdConfig
        extends AbstractConfig
        implements OpenIdConfiguration {

    public static final String PROP_NAME_CLIENT_ID = "clientId";
    public static final String PROP_NAME_CLIENT_SECRET = "clientSecret";
    public static final String PROP_NAME_CONFIGURATION_ENDPOINT = "openIdConfigurationEndpoint";
    public static final String PROP_NAME_IDP_TYPE = "identityProviderType";

    private final IdpType identityProviderType;

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
     * Not provided by the configuration endpoint, must be configured manually.
     * <p>
     * The name of the URI parameter to use when passing the logout redirect URI to the IDP.
     * This is here as the spec seems to have changed from 'redirect_uri' to 'post_logout_redirect_uri'.
     */
    private final String logoutRedirectParamName;

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
     * Whether to validate the audience in JWT token, when the audience is expected to be the clientId.
     */
    private final boolean validateAudience;


    public OpenIdConfig() {
        identityProviderType = getDefaultIdpType();
        openIdConfigurationEndpoint = null;
        issuer = null;
        authEndpoint = null;
        tokenEndpoint = null;
        jwksUri = null;
        logoutEndpoint = null;
        logoutRedirectParamName = OpenId.POST_LOGOUT_REDIRECT_URI;
        formTokenRequest = true;
        clientSecret = null;
        clientId = null;
        requestScope = null;
        validateAudience = true;
    }

    @JsonIgnore
    public abstract IdpType getDefaultIdpType();

    @JsonCreator
    public OpenIdConfig(@JsonProperty(PROP_NAME_IDP_TYPE) final IdpType identityProviderType,
                        @JsonProperty(PROP_NAME_CONFIGURATION_ENDPOINT) final String openIdConfigurationEndpoint,
                        @JsonProperty("issuer") final String issuer,
                        @JsonProperty("authEndpoint") final String authEndpoint,
                        @JsonProperty("tokenEndpoint") final String tokenEndpoint,
                        @JsonProperty("jwksUri") final String jwksUri,
                        @JsonProperty("logoutEndpoint") final String logoutEndpoint,
                        @JsonProperty("logoutRedirectParamName") final String logoutRedirectParamName,
                        @JsonProperty("formTokenRequest") final boolean formTokenRequest,
                        @JsonProperty("clientId") final String clientId,
                        @JsonProperty("clientSecret") final String clientSecret,
                        @JsonProperty("requestScope") final String requestScope,
                        @JsonProperty("validateAudience") final boolean validateAudience) {
        this.identityProviderType = identityProviderType;
        this.openIdConfigurationEndpoint = openIdConfigurationEndpoint;
        this.issuer = issuer;
        this.authEndpoint = authEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.jwksUri = jwksUri;
        this.logoutEndpoint = logoutEndpoint;
        this.logoutRedirectParamName = logoutRedirectParamName;
        this.formTokenRequest = formTokenRequest;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.requestScope = requestScope;
        this.validateAudience = validateAudience;
    }

    /**
     * @return The type of Open ID Connnect identity provider in use.
     */
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The type of Open ID Connect identity provider that stroom/proxy" +
            "will use for authentication. Valid values are: " +
            "INTERNAL - Stroom's own built in IDP (not valid for stroom-proxy)," +
            "EXTERNAL - An external IDP such as KeyCloak/Cognito (stroom's internal IDP can be used as " +
            "stroom-proxy's external IDP) and" +
            "TEST - Use hard-coded authentication credentials for test/demo only.")
    public IdpType getIdentityProviderType() {
        return identityProviderType;
    }

    @Override
    @JsonPropertyDescription("You can set an openid-configuration URL to automatically configure much of the openid " +
            "settings. Without this the other endpoints etc must be set manually.")
    @JsonProperty
    public String getOpenIdConfigurationEndpoint() {
        return openIdConfigurationEndpoint;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The issuer used in OpenId authentication." +
            "Should only be set if not using a configuration endpoint.")
    public String getIssuer() {
        return issuer;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The authentication endpoint used in OpenId authentication." +
            "Should only be set if not using a configuration endpoint.")
    public String getAuthEndpoint() {
        return authEndpoint;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The token endpoint used in OpenId authentication." +
            "Should only be set if not using a configuration endpoint.")
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The URI to obtain the JSON Web Key Set from in OpenId authentication." +
            "Should only be set if not using a configuration endpoint.")
    public String getJwksUri() {
        return jwksUri;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The logout endpoint for the identity provider." +
            "This is not typically provided by the configuration endpoint.")
    public String getLogoutEndpoint() {
        return logoutEndpoint;
    }

    @Override
    @Pattern(regexp = "(" + OpenId.REDIRECT_URI + "|" + OpenId.POST_LOGOUT_REDIRECT_URI + ")")
    @JsonProperty
    @JsonPropertyDescription("The name of the URI parameter to use when passing the logout redirect URI to the IDP. "
            + "This is here as the spec seems to have changed from 'redirect_uri' to 'post_logout_redirect_uri'.")
    public String getLogoutRedirectParamName() {
        return logoutRedirectParamName;
    }

    @Override
    @JsonProperty(PROP_NAME_CLIENT_ID)
    @JsonPropertyDescription("The client ID used in OpenId authentication.")
    public String getClientId() {
        return clientId;
    }

    // TODO Not sure we can add NotNull to this as it has no default and if useInternal is true
    //  it doesn't need a value
    @Override
    @JsonProperty(PROP_NAME_CLIENT_SECRET)
    @JsonPropertyDescription("The client secret used in OpenId authentication.")
    public String getClientSecret() {
        return clientSecret;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("Some OpenId providers, e.g. AWS Cognito, require a form to be used for token requests.")
    public boolean isFormTokenRequest() {
        return formTokenRequest;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("If a custom auth flow request scope is required then this should be set.")
    public String getRequestScope() {
        return requestScope;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("Whether to validate the audience in JWT token, when the audience is expected " +
            "to be the clientId.")
    public boolean isValidateAudience() {
        return validateAudience;
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "If " + PROP_NAME_IDP_TYPE + " is set to 'EXTERNAL', property "
            + PROP_NAME_CONFIGURATION_ENDPOINT + " must be set.")
    public boolean isConfigurationEndpointValid() {
        return !IdpType.EXTERNAL.equals(identityProviderType)
                || (openIdConfigurationEndpoint != null && !openIdConfigurationEndpoint.isBlank());
    }

    @Override
    public String toString() {
        return "OpenIdConfig{" +
                "identityProviderType=" + identityProviderType +
                ", openIdConfigurationEndpoint='" + openIdConfigurationEndpoint + '\'' +
                ", issuer='" + issuer + '\'' +
                ", authEndpoint='" + authEndpoint + '\'' +
                ", tokenEndpoint='" + tokenEndpoint + '\'' +
                ", jwksUri='" + jwksUri + '\'' +
                ", logoutEndpoint='" + logoutEndpoint + '\'' +
                ", logoutRedirectParamName='" + logoutRedirectParamName + '\'' +
                ", formTokenRequest=" + formTokenRequest +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='" + clientSecret + '\'' +
                ", requestScope='" + requestScope + '\'' +
                ", validateAudience=" + validateAudience +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final OpenIdConfig that = (OpenIdConfig) o;
        return formTokenRequest == that.formTokenRequest
                && validateAudience == that.validateAudience
                && identityProviderType == that.identityProviderType
                && Objects.equals(openIdConfigurationEndpoint, that.openIdConfigurationEndpoint)
                && Objects.equals(issuer, that.issuer)
                && Objects.equals(authEndpoint, that.authEndpoint)
                && Objects.equals(tokenEndpoint, that.tokenEndpoint)
                && Objects.equals(jwksUri, that.jwksUri)
                && Objects.equals(logoutEndpoint, that.logoutEndpoint)
                && Objects.equals(logoutRedirectParamName, that.logoutRedirectParamName)
                && Objects.equals(clientId, that.clientId)
                && Objects.equals(clientSecret, that.clientSecret)
                && Objects.equals(requestScope, that.requestScope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identityProviderType,
                openIdConfigurationEndpoint,
                issuer,
                authEndpoint,
                tokenEndpoint,
                jwksUri,
                logoutEndpoint,
                logoutRedirectParamName,
                formTokenRequest,
                clientId,
                clientSecret,
                requestScope,
                validateAudience);
    }

    // --------------------------------------------------------------------------------


}
