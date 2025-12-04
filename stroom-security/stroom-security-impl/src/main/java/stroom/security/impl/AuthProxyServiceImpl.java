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

import stroom.security.api.HasJwt;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.security.common.impl.ClientCredentials;
import stroom.security.common.impl.OpenIdTokenRequestHelper;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenResponse;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.authentication.HasRefreshable;
import stroom.util.authentication.Refreshable;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class AuthProxyServiceImpl implements AuthProxyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AuthProxyServiceImpl.class);

    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final JerseyClientFactory jerseyClientFactory;
    private final UserIdentityFactory userIdentityFactory;

    @Inject
    public AuthProxyServiceImpl(final Provider<OpenIdConfiguration> openIdConfigurationProvider,
                                final DefaultOpenIdCredentials defaultOpenIdCredentials,
                                final JerseyClientFactory jerseyClientFactory,
                                final UserIdentityFactory userIdentityFactory) {
        this.openIdConfigurationProvider = openIdConfigurationProvider;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.jerseyClientFactory = jerseyClientFactory;
        this.userIdentityFactory = userIdentityFactory;
    }

    @Override
    public String fetchToken(final ClientCredentials clientCredentials) {
        final OpenIdConfiguration openIdConfiguration = openIdConfigurationProvider.get();
        final IdpType idpType = Objects.requireNonNull(
                openIdConfiguration.getIdentityProviderType(),
                "identityProviderType is not configured");

        final String token = switch (idpType) {
            case EXTERNAL_IDP -> fetchTokenFromExternalIdp(clientCredentials, openIdConfiguration);
            case INTERNAL_IDP -> fetchTokenFromInternalIdp(clientCredentials, openIdConfiguration);
            case NO_IDP -> throw new IllegalArgumentException(
                    "Stroom is not configured to use an identity provide");
            case TEST_CREDENTIALS -> defaultOpenIdCredentials.getApiKey();
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

    private String fetchTokenFromInternalIdp(final ClientCredentials clientCredentials,
                                             final OpenIdConfiguration openIdConfiguration) {
        validateClientCredentials(clientCredentials, openIdConfiguration);

        final UserIdentity userIdentity = userIdentityFactory.getServiceUserIdentity();
        return extractToken(userIdentity);
    }

    private void validateClientCredentials(final ClientCredentials clientCredentials,
                                           final OpenIdConfiguration openIdConfiguration) {
        Objects.requireNonNull(clientCredentials);
        if (!Objects.equals(clientCredentials.getClientId(), openIdConfiguration.getClientId())
            || !Objects.equals(clientCredentials.getClientSecret(), openIdConfiguration.getClientSecret())) {
            throw new IllegalArgumentException(LogUtil.message(
                    "When identityProviderType is {}, the provided clientId and clientSecret must match " +
                    "those in Stroom's config", IdpType.INTERNAL_IDP));
        }
    }

    private String extractToken(final UserIdentity serviceUserIdentity) {

        if (serviceUserIdentity instanceof final HasJwt hasJwt) {
            final String jwt = hasJwt.getJwt();
            Objects.requireNonNull(jwt, "JWT is missing");

            Instant expiry = null;
            Duration expiryDuration = null;

            if (serviceUserIdentity instanceof final HasRefreshable hasRefreshable) {
                final Refreshable refreshable = hasRefreshable.getRefreshable();

                expiry = NullSafe.get(refreshable,
                        Refreshable::getExpireTimeEpochMs,
                        Instant::ofEpochMilli);
                expiryDuration = NullSafe.get(expiry,
                        expiry2 -> Duration.between(Instant.now(), expiry2));
            }

            LOGGER.debug("Access token successfully obtained. Expire time: {}, expires in: {}",
                    Objects.requireNonNullElse(expiry, "?"),
                    Objects.requireNonNullElse(expiryDuration, "?"));

            return jwt;
        } else if (serviceUserIdentity == null) {
            throw new RuntimeException("Null service user identity");
        } else {
            throw new RuntimeException(LogUtil.message(
                    "User identity type {} does not have a token.", serviceUserIdentity.getClass().getSimpleName()));
        }
    }
}
