package stroom.security.common.impl;

import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfig;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TestIdpConfigurationProvider implements IdpConfigurationProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestIdpConfigurationProvider.class);
    private static final Duration TIME_BETWEEN_WARNINGS = Duration.ofMinutes(5);

    private final DefaultOpenIdCredentials defaultOpenIdCredentials;

    private volatile Instant nextWarningTime = Instant.EPOCH;

    @Inject
    public TestIdpConfigurationProvider(final DefaultOpenIdCredentials defaultOpenIdCredentials) {
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
    }

    @Override
    public OpenIdConfigurationResponse getConfigurationResponse() {
        throw new UnsupportedOperationException("Not supported for this implementation");
    }

    @Override
    public IdpType getIdentityProviderType() {
        showWarning();
        return IdpType.TEST_CREDENTIALS;
    }

    @Override
    public String getOpenIdConfigurationEndpoint() {
        throw new UnsupportedOperationException("Not supported for this implementation");
    }

    @Override
    public String getClientId() {
        showWarning();
        return defaultOpenIdCredentials.getOauth2ClientId();
    }

    @Override
    public String getClientSecret() {
        showWarning();
        return defaultOpenIdCredentials.getOauth2ClientSecret();
    }

    @Override
    public boolean isFormTokenRequest() {
        throw new UnsupportedOperationException("Not supported for this implementation");
    }

    @Override
    public String getRequestScope() {
        throw new UnsupportedOperationException("Not supported for this implementation");
    }

    @Override
    public boolean isValidateAudience() {
        showWarning();
        return false;
    }

    @Override
    public String getLogoutRedirectParamName() {
        throw new UnsupportedOperationException("Not supported for this implementation");
    }

    @Override
    public String getIssuer() {
        showWarning();
        return defaultOpenIdCredentials.getOauth2Issuer();
    }

    @Override
    public String getAuthEndpoint() {
        throw new UnsupportedOperationException("Not supported for this implementation");
    }

    @Override
    public String getTokenEndpoint() {
        throw new UnsupportedOperationException("Not supported for this implementation");
    }

    @Override
    public String getJwksUri() {
        throw new UnsupportedOperationException("Not supported for this implementation");
    }

    @Override
    public String getLogoutEndpoint() {
        throw new UnsupportedOperationException("Not supported for this implementation");
    }

    private void showWarning() {
        // Show a warning every 5mins or so to remind people it is totally insecure
        final Instant now = Instant.now();
        if (now.isAfter(nextWarningTime)) {
            synchronized (this) {
                if (now.isAfter(nextWarningTime)) {
                    LOGGER.warn("Using default and publicly available Open ID authentication credentials. " +
                            "This is totally insecure! Set property " + OpenIdConfig.PROP_NAME_IDP_TYPE +
                            " to " + IdpType.EXTERNAL_IDP + "/" + IdpType.INTERNAL_IDP + ".");
                    nextWarningTime = Instant.now().plus(TIME_BETWEEN_WARNINGS);
                }
            }
        }
    }
}
