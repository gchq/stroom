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

package stroom.security.identity.openid;

import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdClient;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.string.StringUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.Optional;

@Singleton
public class OpenIdClientDetailsFactoryImpl implements OpenIdClientFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenIdClientDetailsFactoryImpl.class);

    private static final String INTERNAL_STROOM_CLIENT = "Stroom Client Internal";
    private static final String CLIENT_ID_SUFFIX = ".client-id.apps.stroom-idp";
    private static final String CLIENT_SECRET_SUFFIX = ".client-secret.apps.stroom-idp";
    private static final char[] ALLOWED_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGJKLMNPRSTUVWXYZ0123456789"
            .toCharArray();

    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    // We have to use AbstractOpenIdConfig instead of OpenIdConfiguration, so we get
    // the one that is backed by stroom's config.yml rather than the one that is derived
    // from the config.yml + the IDP's config endpoint (i.e. relies on this class).
    private final Provider<AbstractOpenIdConfig> openIdConfigurationProvider;
    private final OpenIdClientDao openIdClientDao;
    private volatile OpenIdClient oAuth2Client = null;

    @Inject
    public OpenIdClientDetailsFactoryImpl(final OpenIdClientDao openIdClientDao,
                                          final DefaultOpenIdCredentials defaultOpenIdCredentials,
                                          final Provider<AbstractOpenIdConfig> openIdConfigurationProvider) {
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.openIdConfigurationProvider = openIdConfigurationProvider;
        this.openIdClientDao = openIdClientDao;
    }

    public OpenIdClient getClient() {
        // Do the init here rather than in the ctor to avoid guice circular dep issues
        if (oAuth2Client == null) {
            initOauth2Client(INTERNAL_STROOM_CLIENT);
        }
        return oAuth2Client;
    }

    public OpenIdClient getClient(final String clientId) {
        final OpenIdClient client = getClient();
        // Internal IDP only supports one client
        if (!Objects.requireNonNull(clientId).equals(client.getClientId())) {
            throw new RuntimeException(LogUtil.message(
                    "Unexpected client ID: {}, expecting {}", clientId, client.getClientId()));
        } else {
            return client;
        }
    }

    private void initOauth2Client(final String clientName) {
        OpenIdClient client = null;
        final DurationTimer timer1 = DurationTimer.start();
        synchronized (this) {
            LOGGER.debug("Acquired local lock in {}", timer1);
            if (oAuth2Client == null) {
                final OpenIdConfiguration openIdConfiguration = openIdConfigurationProvider.get();
                final IdpType idpType = openIdConfiguration.getIdentityProviderType();

                if (IdpType.TEST_CREDENTIALS.equals(idpType)) {
                    client = createDefaultOAuthClient();
                } else if (IdpType.INTERNAL_IDP.equals(idpType)) {
                    // We are first thread on this node, but other nodes may beat us to it so,
                    // check the DB
                    client = createOAuth2Client(clientName, openIdConfiguration);
                }
            } else {
                LOGGER.debug("Another thread beat us to it");
            }
        }
        oAuth2Client = Objects.requireNonNull(client, "client is still null after init");
        LOGGER.info("Initialised Stroom's Oauth2 clientId ('{}') and clientSecret",
                oAuth2Client.getClientId());
    }

    private OpenIdClient createOAuth2Client(final String clientName,
                                            final OpenIdConfiguration openIdConfiguration) {
        return openIdClientDao.getClientByName(clientName)
                .or(() -> {
                    // Generate new randomised client details and persist them
                    final OpenIdClient newOAuth2Client = createOAuth2ClientCredentials(
                            clientName, openIdConfiguration);

                    try {
                        openIdClientDao.createIfNotExists(newOAuth2Client);
                    } catch (final Exception e) {
                        LOGGER.error("Error writing new oauth2 client to database: {}",
                                LogUtil.exceptionMessage(e), e);
                    }
                    // Another node may have created it before us as there is no cluster lock
                    // so re-query to get the actual db one.
                    final Optional<OpenIdClient> optClient = openIdClientDao.getClientByName(clientName);
                    if (optClient.filter(client -> Objects.equals(newOAuth2Client, client)).isPresent()) {
                        LOGGER.info("Persisted Stroom's Oauth2 clientId ('{}') and clientSecret",
                                newOAuth2Client.getClientId());
                    }

                    return optClient;
                })
                .orElseThrow(() ->
                        new NullPointerException("Unable to get or create internal client details"));
    }

    private OpenIdClient createDefaultOAuthClient() {
        return new OpenIdClient(
                defaultOpenIdCredentials.getOauth2ClientName(),
                defaultOpenIdCredentials.getOauth2ClientId(),
                defaultOpenIdCredentials.getOauth2ClientSecret(),
                defaultOpenIdCredentials.getOauth2ClientUriPattern());
    }

    private static OpenIdClient createOAuth2ClientCredentials(final String name,
                                                              final OpenIdConfiguration openIdConfiguration) {
        // If we have them in config then use them, else fall back to randomised creds
        final String clientId = Objects.requireNonNullElseGet(
                openIdConfiguration.getClientId(),
                () -> createRandomCode(CLIENT_ID_SUFFIX));
        final String clientSecret = Objects.requireNonNullElseGet(
                openIdConfiguration.getClientSecret(),
                () -> createRandomCode(CLIENT_SECRET_SUFFIX));

        LOGGER.debug("");
        return new OpenIdClient(name, clientId, clientSecret, ".*");
    }

    static OpenIdClient createRandomisedOAuth2Client(final String name) {
        return new OpenIdClient(
                name,
                createRandomCode(CLIENT_ID_SUFFIX),
                createRandomCode(CLIENT_SECRET_SUFFIX),
                ".*");
    }

    public static String createRandomCode(final String suffix) {
        return StringUtil.createRandomCode(40) + suffix;
    }

}
