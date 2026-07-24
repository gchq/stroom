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
import stroom.security.openid.api.ClusterToken;
import stroom.security.openid.api.OpenId;
import stroom.util.authentication.PerishableItem;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Singleton
public class InternalServiceUserFactory implements ServiceUserFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InternalServiceUserFactory.class);

    private static final Duration EXPIRY_DURATION = Duration.ofMinutes(10);
    // This gives us a bit of head-room to refresh it before it is too old to stop internode comms failing
    private static final Duration REFRESH_BUFFER = Duration.ofMillis((long) (EXPIRY_DURATION.toMillis() * 0.15));

    private final TokenBuilderFactory tokenBuilderFactory;

    @Inject
    public InternalServiceUserFactory(final TokenBuilderFactory tokenBuilderFactory) {
        this.tokenBuilderFactory = tokenBuilderFactory;
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
        final Instant expiryTime = Instant.now().plus(EXPIRY_DURATION);

        LOGGER.debug("Creating service user token with expiryTime: {} ({}), refresh buffer: {}",
                expiryTime, EXPIRY_DURATION, REFRESH_BUFFER);

        final TokenBuilder tokenBuilder = tokenBuilderFactory
                .builder()
                .expirationTime(expiryTime)
                // The cluster token is a machine credential, independent of any OIDC client registration:
                // a fixed internal issuer and audience, signed with the cluster's internal key (set by the
                // builder factory). This works identically in every IdP mode and does not depend on an
                // internal OIDC client existing (which it does not in external-IdP mode).
                .issuer(ClusterToken.CLUSTER_ISSUER)
                .clientId(ClusterToken.CLUSTER_AUDIENCE)
                .subject(InternalIdpProcessingUserIdentity.INTERNAL_PROCESSING_USER)
                // The inter-node processing-user token is a bearer credential, so it must be marked as
                // an access token to pass the bearer check.
                .type(OpenId.TOKEN_TYPE__ACCESS);
        final String token = tokenBuilder.build();
        return new PerishableItem<>(expiryTime, token);
    }
}
