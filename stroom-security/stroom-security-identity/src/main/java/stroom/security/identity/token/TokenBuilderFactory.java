/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.security.identity.token;

import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.exceptions.TokenCreationException;
import stroom.security.openid.api.PublicJsonWebKeyProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenBuilderFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenBuilderFactory.class);

    private final IdentityConfig config;
    private final PublicJsonWebKeyProvider publicJsonWebKeyProvider;

    private Instant expiryDateForApiKeys;

    @Inject
    public TokenBuilderFactory(final IdentityConfig config,
                               final PublicJsonWebKeyProvider publicJsonWebKeyProvider) {
        this.config = config;
        this.publicJsonWebKeyProvider = publicJsonWebKeyProvider;
    }

    public TokenBuilderFactory expiryDateForApiKeys(Instant expiryDate) {
        this.expiryDateForApiKeys = expiryDate;
        return this;
    }

    public TokenBuilder newBuilder(TokenType tokenType) {
        TokenBuilder tokenBuilder = new TokenBuilder();
        switch (tokenType) {
            case API:
                if (expiryDateForApiKeys == null) {
                    expiryDateForApiKeys = Instant.now()
                            .plus(config.getTokenConfig().getTimeUntilExpirationForUserToken());
                }
                tokenBuilder.expiryDate(expiryDateForApiKeys);
                break;
            case USER:
                Instant expiryDateForLogin = Instant.now()
                        .plus(config.getTokenConfig().getTimeUntilExpirationForUserToken());
                tokenBuilder.expiryDate(expiryDateForLogin);
                break;
            case EMAIL_RESET:
                Instant expiryDateForReset = Instant.now()
                        .plus(config.getTokenConfig().getTimeUntilExpirationForEmailResetToken());
                tokenBuilder.expiryDate(expiryDateForReset);
                break;
            default:
                String errorMessage = "Unknown token type:" + tokenType.toString();
                LOGGER.error(errorMessage);
                throw new TokenCreationException(tokenType, errorMessage);
        }

        tokenBuilder
                .issuer(config.getTokenConfig().getJwsIssuer())
                .privateVerificationKey(publicJsonWebKeyProvider.getFirst())
                .algorithm(config.getTokenConfig().getAlgorithm());

        return tokenBuilder;
    }
}
