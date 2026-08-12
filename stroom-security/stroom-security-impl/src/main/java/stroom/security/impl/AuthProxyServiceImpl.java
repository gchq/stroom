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

import stroom.security.common.impl.ClientCredentials;
import stroom.security.common.impl.OpenIdTokenRequestHelper;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenResponse;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;

public class AuthProxyServiceImpl implements AuthProxyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AuthProxyServiceImpl.class);

    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;
    private final JerseyClientFactory jerseyClientFactory;

    @Inject
    public AuthProxyServiceImpl(final Provider<OpenIdConfiguration> openIdConfigurationProvider,
                                final JerseyClientFactory jerseyClientFactory) {
        this.openIdConfigurationProvider = openIdConfigurationProvider;
        this.jerseyClientFactory = jerseyClientFactory;
    }

    @Override
    public String fetchToken(final ClientCredentials clientCredentials) {
        final OpenIdConfiguration openIdConfiguration = openIdConfigurationProvider.get();
        final IdpType idpType = Objects.requireNonNull(
                openIdConfiguration.getIdentityProviderType(),
                "identityProviderType is not configured");

        final String token = switch (idpType) {
            case EXTERNAL_IDP -> fetchTokenFromExternalIdp(clientCredentials, openIdConfiguration);
            // This endpoint brokers a client-credentials token request to a separate identity provider. When
            // Stroom is its own identity provider there is no separate provider to broker to, and no scoped
            // service account to mint a token for, so the request is not supported here. Programmatic callers
            // authenticate to an internal-IdP Stroom with an API key instead.
            case INTERNAL_IDP -> throw new IllegalArgumentException(
                    "Fetching a client-credentials token is not supported when identityProviderType is "
                    + IdpType.INTERNAL_IDP + ". Authenticate with an API key instead.");
            case NO_IDP -> throw new IllegalArgumentException(
                    "Stroom is not configured to use an identity provider");
        };

        LOGGER.debug(() -> LogUtil.message("Fetched access token for clientId '{}' (idpType {})",
                clientCredentials.getClientId(), idpType));
        return token;
    }

    private String fetchTokenFromExternalIdp(final ClientCredentials clientCredentials,
                                             final OpenIdConfiguration openIdConfiguration) {

        final String tokenEndpoint = Objects.requireNonNull(openIdConfiguration.getTokenEndpoint(),
                "tokenEndpoint is not configured");
        try {
            LOGGER.debug(() -> LogUtil.message("Fetching access token for clientId '{}' from '{}'",
                    clientCredentials.getClientId(), tokenEndpoint));
            final TokenResponse tokenResponse = new OpenIdTokenRequestHelper(
                    tokenEndpoint, openIdConfiguration, JsonUtil.getNoIndentMapper(), jerseyClientFactory)
                    .withClientCredentials(clientCredentials)
                    .withGrantType(OpenId.GRANT_TYPE__CLIENT_CREDENTIALS)
                    .addScopes(openIdConfiguration.getClientCredentialsScopes())
                    .sendRequest(false);

            return Objects.requireNonNull(tokenResponse.getAccessToken(),
                    "No getAccessToken in response");
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error fetching client credentials flow access token for " +
                                                       "clientId '{}' at endpoint '{}': {}",
                    openIdConfiguration.getClientId(),
                    tokenEndpoint,
                    e.getMessage()), e);
        }
    }

}
