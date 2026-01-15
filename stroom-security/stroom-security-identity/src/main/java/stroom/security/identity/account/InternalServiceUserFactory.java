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

package stroom.security.identity.account;

import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.security.identity.token.TokenBuilder;
import stroom.security.identity.token.TokenBuilderFactory;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.util.authentication.PerishableItem;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Singleton
public class InternalServiceUserFactory implements ServiceUserFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InternalServiceUserFactory.class);

    private static final Duration EXPIRY_DURATION = Duration.ofMinutes(10);
    // This gives us a bit of head-room to refresh it before it is too old to stop internode comms failing
    private static final Duration REFRESH_BUFFER = Duration.ofMillis((long) (EXPIRY_DURATION.toMillis() * 0.15));

    private final TokenBuilderFactory tokenBuilderFactory;
    private final OpenIdClientFactory openIdClientDetailsFactory;

    @Inject
    public InternalServiceUserFactory(final TokenBuilderFactory tokenBuilderFactory,
                                      final OpenIdClientFactory openIdClientDetailsFactory) {
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
    }

    @Override
    public UserIdentity createServiceUserIdentity() {
        final UserIdentity userIdentity = new InternalIdpProcessingUserIdentity(
                REFRESH_BUFFER, this::createServiceUserToken);
        LOGGER.info("Created internal processing user identity '{}' " +
                    "(token expiry duration: {}, refresh buffer: {})",
                userIdentity, EXPIRY_DURATION, REFRESH_BUFFER);
        return userIdentity;
    }

    @Override
    public boolean isServiceUser(final UserIdentity userIdentity,
                                 final UserIdentity serviceUserIdentity) {
        if (userIdentity == null || serviceUserIdentity == null) {
            return false;
        } else {
            return userIdentity instanceof InternalIdpProcessingUserIdentity
                   && Objects.equals(userIdentity.subjectId(), serviceUserIdentity.subjectId());
        }
    }

    private PerishableItem<String> createServiceUserToken() {
        final Instant expiryTime = LocalDateTime.now()
                .plus(EXPIRY_DURATION)
                .toInstant(ZoneOffset.UTC);

        LOGGER.debug("Creating service user token with expiryTime: {} ({}), refresh buffer: {}",
                expiryTime, EXPIRY_DURATION, REFRESH_BUFFER);

        final TokenBuilder tokenBuilder = tokenBuilderFactory
                .builder()
                .expirationTime(expiryTime)
                .clientId(openIdClientDetailsFactory.getClient().getClientId())
                .subject(InternalIdpProcessingUserIdentity.INTERNAL_PROCESSING_USER);
        final String token = tokenBuilder.build();
        return new PerishableItem<>(expiryTime, token);
    }
}
