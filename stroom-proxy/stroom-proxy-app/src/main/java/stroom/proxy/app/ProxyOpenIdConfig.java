package stroom.proxy.app;

import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
public class ProxyOpenIdConfig extends AbstractOpenIdConfig implements IsProxyConfig {

    public ProxyOpenIdConfig() {
        super();
    }

    @JsonCreator
    public ProxyOpenIdConfig(
            @JsonProperty(PROP_NAME_IDP_TYPE) final IdpType identityProviderType,
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
            @JsonProperty("requestScopes") final List<String> requestScopes,
            @JsonProperty("clientCredentialsScopes") final List<String> clientCredentialsScopes,
            @JsonProperty("validateAudience") final boolean validateAudience,
            @JsonProperty("validIssuers") final Set<String> validIssuers,
            @JsonProperty("uniqueIdentityClaim") final String uniqueIdentityClaim,
            @JsonProperty("userDisplayNameClaim") final String userDisplayNameClaim,
            @JsonProperty(PROP_NAME_EXPECTED_SIGNER_PREFIXES) final Set<String> expectedSignerPrefixes) {

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
                requestScopes,
                clientCredentialsScopes,
                validateAudience,
                validIssuers,
                uniqueIdentityClaim,
                userDisplayNameClaim,
                expectedSignerPrefixes);
    }

    @JsonIgnore
    public IdpType getDefaultIdpType() {
        return IdpType.NO_IDP;
    }

    /**
     * @return The type of Open ID Connnect identity provider in use.
     */
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The type of Open ID Connect identity provider that stroom/proxy" +
                             "will use for authentication. Valid values are: " +
                             "EXTERNAL_IDP - An external IDP such as KeyCloak/Cognito, " +
                             "TEST_CREDENTIALS - Use hard-coded authentication credentials for test/demo only and " +
                             "NO_IDP - No IDP is used. API keys are set in config for feed status checks. " +
                             "Changing this property will require a restart of the application.")
    @Override
    public IdpType getIdentityProviderType() {
        return super.getIdentityProviderType();
    }

    @SuppressWarnings("unused")
    @JsonIgnore
    @ValidationMethod(message = "INTERNAL_IDP is not a valid value for identityProviderType in stroom-proxy.")
    public boolean isIdentityProviderTypeValid() {
        return !IdpType.INTERNAL_IDP.equals(getIdentityProviderType());
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
                getRequestScopes(),
                getClientCredentialsScopes(),
                isValidateAudience(),
                getValidIssuers(),
                getUniqueIdentityClaim(),
                getUserDisplayNameClaim(),
                getExpectedSignerPrefixes());
    }
}
