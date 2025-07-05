package stroom.security.common.impl;

import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.util.Objects;

public class TestCredentialsServiceUserFactory implements ServiceUserFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCredentialsServiceUserFactory.class);

    private final DefaultOpenIdCredentials defaultOpenIdCredentials;

    @Inject
    public TestCredentialsServiceUserFactory(final DefaultOpenIdCredentials defaultOpenIdCredentials) {
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
    }

    @Override
    public UserIdentity createServiceUserIdentity() {
        final UserIdentity serviceUserIdentity = new DefaultOpenIdCredsUserIdentity(
                defaultOpenIdCredentials.getApiKeyUserEmail(),
                defaultOpenIdCredentials.getApiKey());
        LOGGER.info("Created test service user identity {} {}",
                serviceUserIdentity.getClass().getSimpleName(), serviceUserIdentity);
        return serviceUserIdentity;
    }

    @Override
    public boolean isServiceUser(final UserIdentity userIdentity,
                                 final UserIdentity serviceUserIdentity) {
        if (userIdentity == null || serviceUserIdentity == null) {
            return false;
        } else {
            return userIdentity instanceof DefaultOpenIdCredsUserIdentity
                   && Objects.equals(
                    userIdentity.subjectId(),
                    serviceUserIdentity.subjectId());
        }
    }
}
