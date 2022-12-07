package stroom.security.common.impl;

import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.UserIdentity;
import stroom.util.exception.ThrowingFunction;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExternalProcessingUserIdentityProvider implements ProcessingUserIdentityProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            ExternalProcessingUserIdentityProvider.class);

    private final UserIdentityFactory userIdentityFactory;

    @Inject
    public ExternalProcessingUserIdentityProvider(final UserIdentityFactory userIdentityFactory) {
        this.userIdentityFactory = userIdentityFactory;
    }

    @Override
    public UserIdentity get() {
        return userIdentityFactory.getServiceUserIdentity();
    }

    @Override
    public boolean isProcessingUser(final UserIdentity userIdentity) {
        return Objects.equals(userIdentity, userIdentityFactory.getServiceUserIdentity());
    }

    @Override
    public boolean isProcessingUser(final String subject, final String issuer) {
        final UserIdentity processingUserIdentity = get();
        if (processingUserIdentity instanceof AbstractTokenUserIdentity tokenUserIdentity) {
            return Optional.ofNullable(tokenUserIdentity.getJwtClaims())
                    .map(ThrowingFunction.unchecked(jwtClaims -> {
                        final boolean isProcessingUser = Objects.equals(subject, jwtClaims.getSubject())
                                && Objects.equals(issuer, jwtClaims.getIssuer());

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Comparing subject: [{}|{}], issuer[{}|{}], result: {}",
                                    subject,
                                    jwtClaims.getSubject(),
                                    issuer,
                                    jwtClaims.getIssuer(),
                                    isProcessingUser);
                        }
                        return isProcessingUser;
                    }))
                    .orElse(false);
        } else {
            throw new RuntimeException("Unexpected UserIdentity type "
                    + processingUserIdentity.getClass().getName());
        }
    }
}
