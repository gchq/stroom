package stroom.security.identity.account;

import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.security.identity.token.TokenBuilder;
import stroom.security.identity.token.TokenBuilderFactory;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class InternalServiceUserFactory implements ServiceUserFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InternalServiceUserFactory.class);

    private static final Duration MAX_AGE_BEFORE_REFRESH = Duration.ofDays(1);

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
                MAX_AGE_BEFORE_REFRESH, this::createToken);
        LOGGER.info("Created internal processing user identity {}", userIdentity);
        return userIdentity;
    }

    @Override
    public boolean isServiceUser(final UserIdentity userIdentity,
                                 final UserIdentity serviceUserIdentity) {
        if (userIdentity == null || serviceUserIdentity == null) {
            return false;
        } else {
            return userIdentity instanceof InternalIdpProcessingUserIdentity
                    && Objects.equals(userIdentity.getSubjectId(), serviceUserIdentity.getSubjectId());
        }
    }

    private String createToken() {
        final Instant timeToExpiryInSeconds = LocalDateTime.now()
                .plusYears(1)
                .toInstant(ZoneOffset.UTC);
        final TokenBuilder tokenBuilder = tokenBuilderFactory
                .builder()
                .expirationTime(timeToExpiryInSeconds)
                .clientId(openIdClientDetailsFactory.getClient().getClientId())
                .subject(InternalIdpProcessingUserIdentity.INTERNAL_PROCESSING_USER);
        return tokenBuilder.build();
    }
}
