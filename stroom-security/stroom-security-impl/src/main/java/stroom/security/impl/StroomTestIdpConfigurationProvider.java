package stroom.security.impl;

import stroom.config.common.UriFactory;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.openid.api.OpenIdConfig;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

// Overrides some internal IDP config
@Singleton
public class StroomTestIdpConfigurationProvider extends InternalIdpConfigurationProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomTestIdpConfigurationProvider.class);
    private static final Duration TIME_BETWEEN_WARNINGS = Duration.ofMinutes(5);

    private final DefaultOpenIdCredentials defaultOpenIdCredentials;

    private volatile Instant nextWarningTime = Instant.EPOCH;

    @Inject
    public StroomTestIdpConfigurationProvider(final DefaultOpenIdCredentials defaultOpenIdCredentials,
                                              final UriFactory uriFactory,
                                              final Provider<OpenIdConfig> openIdConfigProvider,
                                              final OpenIdClientFactory openIdClientDetailsFactory) {
        super(uriFactory, openIdConfigProvider, openIdClientDetailsFactory);
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
    }

    @Override
    public IdpType getIdentityProviderType() {
        showWarning();
        return IdpType.TEST;
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
    public boolean isValidateAudience() {
        showWarning();
        return false;
    }

    @Override
    public String getIssuer() {
        showWarning();
        return defaultOpenIdCredentials.getOauth2Issuer();
    }

    private void showWarning() {
        // Show a warning every 5mins or so to remind people it is totally insecure
        final Instant now = Instant.now();
        if (now.isAfter(nextWarningTime)) {
            synchronized (this) {
                if (now.isAfter(nextWarningTime)) {
                    LOGGER.warn("Using default and publicly available Open ID authentication credentials. " +
                            "This is totally insecure! Set property " + OpenIdConfig.PROP_NAME_IDP_TYPE +
                            " to " + IdpType.EXTERNAL + "/" + IdpType.INTERNAL + ".");
                    nextWarningTime = Instant.now().plus(TIME_BETWEEN_WARNINGS);
                }
            }
        }
    }
}
