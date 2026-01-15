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

package stroom.security.identity.token;

import stroom.security.identity.config.IdentityConfig;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.PublicJsonWebKeyProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class TokenBuilderFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TokenBuilderFactory.class);

    private final Provider<IdentityConfig> configProvider;
    private final PublicJsonWebKeyProvider publicJsonWebKeyProvider;
    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;

    @Inject
    public TokenBuilderFactory(final Provider<IdentityConfig> configProvider,
                               final PublicJsonWebKeyProvider publicJsonWebKeyProvider,
                               final Provider<OpenIdConfiguration> openIdConfigurationProvider) {
        this.configProvider = configProvider;
        this.publicJsonWebKeyProvider = publicJsonWebKeyProvider;
        this.openIdConfigurationProvider = openIdConfigurationProvider;
    }

    public TokenBuilder builder() {
        final String issuer = openIdConfigurationProvider.get().getIssuer();
        LOGGER.debug("Creating token builder with issuer {}", issuer);
        final TokenBuilder tokenBuilder = new TokenBuilder();
        final IdentityConfig identityConfig = configProvider.get();
        // The algorithm assumes that the default algorithm set in the config had that value when the
        // default open id creds were generated.
        tokenBuilder
                .issuer(issuer)
                .privateVerificationKey(publicJsonWebKeyProvider.getFirst())
                .algorithm(identityConfig.getTokenConfig().getAlgorithm());
        return tokenBuilder;
    }
}
