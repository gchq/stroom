package stroom.proxy.app;

import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;

import javax.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
public class ProxyOpenIdConfig extends OpenIdConfig implements IsProxyConfig {

    public ProxyOpenIdConfig() {
        super();
    }

    @JsonCreator
    public ProxyOpenIdConfig(@JsonProperty(PROP_NAME_IDP_TYPE) final IdpType identityProviderType,
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
        super(identityProviderType,
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

    @JsonIgnore
    public IdpType getDefaultIdpType() {
        return IdpType.NONE;
    }

    /**
     * @return The type of Open ID Connnect identity provider in use.
     */
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The type of Open ID Connect identity provider that stroom/proxy" +
            "will use for authentication. Valid values are: " +
            "EXTERNAL - An external IDP such as KeyCloak/Cognito, " +
            "TEST - Use hard-coded authentication credentials for test/demo only and " +
            "NONE - No IDP is used. API keys are set in config for feed status checks.")
    @Override
    public IdpType getIdentityProviderType() {
        return super.getIdentityProviderType();
    }

    @SuppressWarnings("unused")
    @JsonIgnore
    @ValidationMethod(message = "INTERNAL is not a valid value for identityProviderType in stroom-proxy.")
    public boolean isIdentityProviderTypeValid() {
        return !IdpType.INTERNAL.equals(getIdentityProviderType());
    }

    public ProxyOpenIdConfig withIdentityProviderType(final IdpType identityProviderType) {
        return new ProxyOpenIdConfig(
                identityProviderType,
                getOpenIdConfigurationEndpoint(),
                getIssuer(),
                getAuthEndpoint(),
                getTokenEndpoint(),
                getJwksUri(),
                getLogoutEndpoint(),
                getLogoutRedirectParamName(),
                isFormTokenRequest(),
                getClientSecret(),
                getClientId(),
                getRequestScope(),
                isValidateAudience());
    }
}
