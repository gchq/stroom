/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.impl;

import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;

import java.util.List;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
public class StroomOpenIdConfig extends AbstractOpenIdConfig implements IsStroomConfig {

    public StroomOpenIdConfig() {
        super();
    }

    @JsonCreator
    public StroomOpenIdConfig(
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
            @JsonProperty("allowedAudiences") final Set<String> allowedAudiences,
            @JsonProperty("audienceClaimRequired") final Boolean audienceClaimRequired,
            @JsonProperty("validIssuers") final Set<String> validIssuers,
            @JsonProperty("uniqueIdentityClaim") final String uniqueIdentityClaim,
            @JsonProperty("userDisplayNameClaim") final String userDisplayNameClaim,
            @JsonProperty("fullNameClaimTemplate") final String fullNameClaimTemplate,
            @JsonProperty(PROP_NAME_EXPECTED_SIGNER_PREFIXES) final Set<String> expectedSignerPrefixes,
            @JsonProperty("publicKeyUriPattern") final String publicKeyUriPattern) {
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
                allowedAudiences,
                audienceClaimRequired,
                validIssuers,
                uniqueIdentityClaim,
                userDisplayNameClaim,
                fullNameClaimTemplate,
                expectedSignerPrefixes,
                publicKeyUriPattern);
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonIgnore
    public IdpType getDefaultIdpType() {
        return IdpType.INTERNAL_IDP;
    }

    @SuppressWarnings("unused")
    @JsonIgnore
    @ValidationMethod(message = "Invalid value for identityProviderType. Supported values are EXTERNAL_IDP, " +
                                "INTERNAL_IDP and TEST_CREDENTIALS.")
    public boolean isIdentityProviderTypeValid() {
        final IdpType idpType = getIdentityProviderType();
        return IdpType.EXTERNAL_IDP.equals(idpType)
               || IdpType.INTERNAL_IDP.equals(idpType)
               || IdpType.TEST_CREDENTIALS.equals(idpType);
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
                getRequestScopes(),
                getClientCredentialsScopes(),
                getAllowedAudiences(),
                isAudienceClaimRequired(),
                getValidIssuers(),
                getUniqueIdentityClaim(),
                getUserDisplayNameClaim(),
                getFullNameClaimTemplate(),
                getExpectedSignerPrefixes(),
                getPublicKeyUriPattern());
    }
}
