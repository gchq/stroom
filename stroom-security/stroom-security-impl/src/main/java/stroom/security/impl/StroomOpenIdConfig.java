package stroom.security.impl;

import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class StroomOpenIdConfig extends OpenIdConfig implements IsStroomConfig {

    public StroomOpenIdConfig() {
        super();
    }

    @JsonCreator
    public StroomOpenIdConfig(@JsonProperty(PROP_NAME_IDP_TYPE) final IdpType identityProviderType,
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
        return IdpType.INTERNAL;
    }

    public StroomOpenIdConfig withIdentityProviderType(final IdpType identityProviderType) {
        return new StroomOpenIdConfig(
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
