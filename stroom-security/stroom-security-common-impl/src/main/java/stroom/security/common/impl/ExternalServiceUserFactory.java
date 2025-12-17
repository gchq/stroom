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

package stroom.security.common.impl;

import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenResponse;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jose4j.jwt.JwtClaims;

public class ExternalServiceUserFactory implements ServiceUserFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExternalServiceUserFactory.class);

    private final JwtContextFactory jwtContextFactory;
    private final Provider<OpenIdConfiguration> openIdConfigProvider;
    private final JerseyClientFactory jerseyClientFactory;
    private final ObjectMapper objectMapper;

    @Inject
    public ExternalServiceUserFactory(final JwtContextFactory jwtContextFactory,
                                      final Provider<OpenIdConfiguration> openIdConfigProvider,
                                      final JerseyClientFactory jerseyClientFactory) {
        this.jwtContextFactory = jwtContextFactory;
        this.openIdConfigProvider = openIdConfigProvider;
        this.jerseyClientFactory = jerseyClientFactory;
        objectMapper = createObjectMapper();
    }

    @Override
    public UserIdentity createServiceUserIdentity() {
        // Get the initial token
        final FetchTokenResult fetchTokenResult = fetchExternalServiceUserToken();

        final JwtClaims jwtClaims = fetchTokenResult.jwtClaims();
        final UpdatableToken updatableToken = new UpdatableToken(
                fetchTokenResult.tokenResponse(),
                jwtClaims,
                updatableToken2 -> fetchExternalServiceUserToken());

        final OpenIdConfiguration openIdConfiguration = openIdConfigProvider.get();
        final UserIdentity serviceUserIdentity = new ServiceUserIdentity(
                JwtUtil.getUniqueIdentity(openIdConfiguration, jwtClaims),
                JwtUtil.getUserDisplayName(openIdConfiguration, jwtClaims)
                        .orElse(null),
                updatableToken);

        // Associate the token with the user it is for
        updatableToken.setUserIdentity(serviceUserIdentity);

        LOGGER.info("Created external IDP service user identity {} {}",
                serviceUserIdentity.getClass().getSimpleName(), serviceUserIdentity);

        return serviceUserIdentity;
    }

    @Override
    public boolean isServiceUser(final UserIdentity userIdentity,
                                 final UserIdentity serviceUserIdentity) {
        // Use instance equality check as there should only ever be one ServiceUserIdentity
        // in this JVM
        final boolean isServiceUserIdentity = userIdentity instanceof ServiceUserIdentity
                                              && userIdentity == serviceUserIdentity;
        LOGGER.debug("isServiceUserIdentity: {}, userIdentity: {}, serviceUserIdentity: {}",
                isServiceUserIdentity, userIdentity, serviceUserIdentity);
        return isServiceUserIdentity;
    }

    private FetchTokenResult fetchExternalServiceUserToken() {
        final OpenIdConfiguration openIdConfiguration = openIdConfigProvider.get();
        final String tokenEndpoint = openIdConfiguration.getTokenEndpoint();

        // Only need the access token for a client_credentials flow
        final TokenResponse tokenResponse = new OpenIdTokenRequestHelper(
                tokenEndpoint, openIdConfiguration, objectMapper, jerseyClientFactory)
                .withGrantType(OpenId.GRANT_TYPE__CLIENT_CREDENTIALS)
                .addScopes(openIdConfiguration.getClientCredentialsScopes())
                .sendRequest(false);

        return jwtContextFactory.getJwtContext(tokenResponse.getAccessToken())
                .map(jwtContext ->
                        new FetchTokenResult(tokenResponse, jwtContext.getJwtClaims()))
                .orElseThrow(() ->
                        new RuntimeException("Unable to extract JWT claims for service user"));
    }

    private ObjectMapper createObjectMapper() {
        return JsonUtil.getNoIndentMapper();
    }
}
