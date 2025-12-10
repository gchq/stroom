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
import stroom.security.common.impl.ExternalIdpConfigurationProvider;
import stroom.security.common.impl.IdpConfigurationProvider;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Set;

/**
 * A front for the internal and external OIDC config providers. The useInternal prop in local
 * config controls which delegate is used.
 */
public class DelegatingIdpConfigurationProvider implements IdpConfigurationProvider {

    private final IdpConfigurationProvider delegate;
    // We must inject AbstractOpenIdConfig rather than OpenIdConfiguration as the latter is guice
    // bound to this interface (IdpConfigurationProvider) to provide a derived config based on yaml
    // config + config from the IDP. AbstractOpenIdConfig is bound to the local yaml config only, which is
    // what we need here.
    private final Provider<StroomOpenIdConfig> localOpenIdConfigProvider;
    private final UriFactory uriFactory;

    @Inject
    public DelegatingIdpConfigurationProvider(
            final Provider<InternalIdpConfigurationProvider> internalIdpConfigurationProviderProvider,
            final Provider<ExternalIdpConfigurationProvider> externalIdpConfigurationProviderProvider,
            final Provider<StroomTestIdpConfigurationProvider> stroomTestIdpConfigurationProviderProvider,
            final Provider<StroomOpenIdConfig> localOpenIdConfigProvider,
            final UriFactory uriFactory) {

        delegate = switch (localOpenIdConfigProvider.get().getIdentityProviderType()) {

            case INTERNAL_IDP -> internalIdpConfigurationProviderProvider.get();
            case EXTERNAL_IDP -> externalIdpConfigurationProviderProvider.get();
            case TEST_CREDENTIALS -> stroomTestIdpConfigurationProviderProvider.get();
            // Might need to create a NoIdpConfigurationProvider
            case NO_IDP -> throw new UnsupportedOperationException(
                    "No delegate when IDP type is " + IdpType.NO_IDP);
        };
        this.localOpenIdConfigProvider = localOpenIdConfigProvider;
        this.uriFactory = uriFactory;
    }

    @Override
    public OpenIdConfigurationResponse getConfigurationResponse() {
        return delegate.getConfigurationResponse();
    }

    @Override
    public IdpType getIdentityProviderType() {
        return localOpenIdConfigProvider.get().getIdentityProviderType();
    }

    @Override
    public String getOpenIdConfigurationEndpoint() {
        return delegate.getOpenIdConfigurationEndpoint();
    }

    @Override
    public String getIssuer() {
        return delegate.getIssuer();
    }

    @Override
    public String getAuthEndpoint() {
        return delegate.getAuthEndpoint();
    }

    @Override
    public String getTokenEndpoint() {
        return delegate.getTokenEndpoint();
    }

    @Override
    public String getJwksUri() {
        return delegate.getJwksUri();
    }

    @Override
    public String getLogoutEndpoint() {
        final String logoutEndpoint = delegate.getLogoutEndpoint();
        // If the IdP doesn't provide a logout endpoint then use the internal one to invalidate
        // the session and redirect to perform a new auth flow.

        return NullSafe.isBlankString(logoutEndpoint)
                ? uriFactory.publicUri(InternalIdpConfigurationProvider.INTERNAL_AUTH_ENDPOINT).toString()
                : logoutEndpoint;
    }

    @Override
    public String getClientId() {
        return delegate.getClientId();
    }

    @Override
    public String getClientSecret() {
        return delegate.getClientSecret();
    }

    @Override
    public boolean isFormTokenRequest() {
        return delegate.isFormTokenRequest();
    }

    @Override
    public List<String> getRequestScopes() {
        return delegate.getRequestScopes();
    }

    @Override
    public List<String> getClientCredentialsScopes() {
        return delegate.getClientCredentialsScopes();
    }

    @Override
    public Set<String> getAllowedAudiences() {
        return delegate.getAllowedAudiences();
    }

    @Override
    public boolean isAudienceClaimRequired() {
        return delegate.isAudienceClaimRequired();
    }

    @Override
    public Set<String> getValidIssuers() {
        return delegate.getValidIssuers();
    }

    @Override
    public String getUniqueIdentityClaim() {
        return delegate.getUniqueIdentityClaim();
    }

    @Override
    public String getUserDisplayNameClaim() {
        return delegate.getUserDisplayNameClaim();
    }

    @Override
    public String getFullNameClaimTemplate() {
        return delegate.getFullNameClaimTemplate();
    }

    @Override
    public String getLogoutRedirectParamName() {
        return delegate.getLogoutRedirectParamName();
    }

    @Override
    public Set<String> getExpectedSignerPrefixes() {
        return delegate.getExpectedSignerPrefixes();
    }

    @Override
    public String getPublicKeyUriPattern() {
        return delegate.getPublicKeyUriPattern();
    }
}
