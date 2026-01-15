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

import stroom.config.common.UriFactory;
import stroom.security.common.impl.IdpConfigurationProvider;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Singleton
public class InternalIdpConfigurationProvider implements IdpConfigurationProvider {

    // TODO: 29/12/2022 According to the spec:
    //  The issuer value returned MUST be identical to the Issuer URL that was directly used to
    //  retrieve the configuration information.
    //  https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfigurationRequest
    //  Thus it prob ought to be our public URI + '/oauth2/v1/noauth' however if only stroom is using
    //  it then it probably doesn't matter.
    static final String INTERNAL_ISSUER = "stroom";
    // These paths must tally up with those in stroom.security.identity.openid.OpenIdResource
    private static final String OAUTH2_BASE_PATH = "/oauth2/v1/noauth";
    static final String INTERNAL_AUTH_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/auth");
    static final String INTERNAL_TOKEN_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/token");
    static final String INTERNAL_JWKS_URI = ResourcePaths.buildAuthenticatedApiPath(
            OAUTH2_BASE_PATH, "/certs");

    // These paths must tally up with those in
    // stroom.security.identity.authenticate.AuthenticationResource
    static final String AUTHENTICATION_BASE_PATH = "/authentication/v1/noauth";
    static final String INTERNAL_LOGOUT_ENDPOINT = ResourcePaths.buildAuthenticatedApiPath(
            AUTHENTICATION_BASE_PATH, "/logout");


    private final UriFactory uriFactory;
    private final Provider<StroomOpenIdConfig> localOpenIdConfigProvider;
    private final OpenIdClientFactory openIdClientDetailsFactory;

    private volatile String lastConfigurationEndpoint;
    private volatile OpenIdConfigurationResponse openIdConfigurationResp;

    @Inject
    public InternalIdpConfigurationProvider(final UriFactory uriFactory,
                                            final Provider<StroomOpenIdConfig> localOpenIdConfigProvider,
                                            final OpenIdClientFactory openIdClientDetailsFactory) {
        this.uriFactory = uriFactory;
        this.localOpenIdConfigProvider = localOpenIdConfigProvider;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
    }

    @Override
    public OpenIdConfigurationResponse getConfigurationResponse() {
        final StroomOpenIdConfig stroomOpenIdConfig = localOpenIdConfigProvider.get();
        final String configurationEndpoint = stroomOpenIdConfig.getOpenIdConfigurationEndpoint();
        if (isNewResponseRequired(configurationEndpoint)) {
            synchronized (this) {
                if (isNewResponseRequired(configurationEndpoint)) {
                    openIdConfigurationResp = OpenIdConfigurationResponse.builder()
                            .issuer(INTERNAL_ISSUER)
                            .authorizationEndpoint(uriFactory.publicUri(INTERNAL_AUTH_ENDPOINT).toString())
                            .tokenEndpoint(uriFactory.nodeUri(INTERNAL_TOKEN_ENDPOINT).toString())
                            .jwksUri(uriFactory.nodeUri(INTERNAL_JWKS_URI).toString())
                            .logoutEndpoint(uriFactory.publicUri(INTERNAL_LOGOUT_ENDPOINT).toString())
                            .build();
                    lastConfigurationEndpoint = configurationEndpoint;
                }
            }
        }

        return openIdConfigurationResp;
    }

    private boolean isNewResponseRequired(final String configurationEndpoint) {
        return openIdConfigurationResp == null
               || !Objects.equals(lastConfigurationEndpoint, configurationEndpoint);
    }

    @Override
    public IdpType getIdentityProviderType() {
        return localOpenIdConfigProvider.get().getIdentityProviderType();
    }

    @Override
    public String getOpenIdConfigurationEndpoint() {
        return localOpenIdConfigProvider.get().getOpenIdConfigurationEndpoint();
    }

    @Override
    public String getClientId() {
        return openIdClientDetailsFactory.getClient().getClientId();
    }

    @Override
    public String getClientSecret() {
        return openIdClientDetailsFactory.getClient().getClientSecret();
    }

    @Override
    public boolean isFormTokenRequest() {
        // Always true for internal idp
        return true;
    }

    @Override
    public List<String> getRequestScopes() {
        return localOpenIdConfigProvider.get().getRequestScopes();
//        final AbstractOpenIdConfig abstractOpenIdConfig = openIdConfigProvider.get();
//        return NullSafe.isEmptyCollection(abstractOpenIdConfig.getRequestScopes())
//                ? OpenId.DEFAULT_REQUEST_SCOPES
//                : abstractOpenIdConfig.getRequestScopes();
    }

    @Override
    public List<String> getClientCredentialsScopes() {
        return localOpenIdConfigProvider.get().getClientCredentialsScopes();
    }

    @Override
    public Set<String> getAllowedAudiences() {
        return localOpenIdConfigProvider.get().getAllowedAudiences();
    }

    @Override
    public boolean isAudienceClaimRequired() {
        return localOpenIdConfigProvider.get().isAudienceClaimRequired();
    }

    @Override
    public Set<String> getValidIssuers() {
        return localOpenIdConfigProvider.get().getValidIssuers();
    }

    @Override
    public String getUniqueIdentityClaim() {
        return localOpenIdConfigProvider.get().getUniqueIdentityClaim();
    }

    @Override
    public String getUserDisplayNameClaim() {
        return localOpenIdConfigProvider.get().getUserDisplayNameClaim();
    }

    @Override
    public String getFullNameClaimTemplate() {
        return localOpenIdConfigProvider.get().getFullNameClaimTemplate();
    }

    @Override
    public String getLogoutRedirectParamName() {
        return localOpenIdConfigProvider.get().getLogoutRedirectParamName();
    }

    @Override
    public Set<String> getExpectedSignerPrefixes() {
        return localOpenIdConfigProvider.get().getExpectedSignerPrefixes();
    }

    @Override
    public String getPublicKeyUriPattern() {
        return localOpenIdConfigProvider.get().getPublicKeyUriPattern();
    }
}
