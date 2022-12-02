package stroom.security.impl;

import stroom.config.common.UriFactory;
import stroom.security.common.impl.ExternalIdpConfigurationProvider;
import stroom.security.common.impl.IdpConfigurationProvider;
import stroom.security.openid.api.OpenIdConfig;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.util.NullSafe;

import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * A front for the internal and external OIDC config providers. The useInternal prop in local
 * config controls which delegat is used.
 */
public class CombinedIdpConfigurationProvider implements IdpConfigurationProvider {

    private final InternalIdpConfigurationProvider internalIdpConfigurationResponseProvider;
    private final ExternalIdpConfigurationProvider externalIdpConfigurationResponseProvider;
    private final Provider<OpenIdConfig> openIdConfigProvider;
    private final UriFactory uriFactory;

    @Inject
    public CombinedIdpConfigurationProvider(
            final InternalIdpConfigurationProvider internalIdpConfigurationResponseProvider,
            final ExternalIdpConfigurationProvider externalIdpConfigurationResponseProvider,
            final Provider<OpenIdConfig> openIdConfigProvider,
            final UriFactory uriFactory) {

        this.internalIdpConfigurationResponseProvider = internalIdpConfigurationResponseProvider;
        this.externalIdpConfigurationResponseProvider = externalIdpConfigurationResponseProvider;
        this.openIdConfigProvider = openIdConfigProvider;
        this.uriFactory = uriFactory;
    }

    @Override
    public OpenIdConfigurationResponse getConfigurationResponse() {
        return getValueFromDelegate(IdpConfigurationProvider::getConfigurationResponse);
    }

    @Override
    public String getOpenIdConfigurationEndpoint() {
        return getValueFromDelegate(IdpConfigurationProvider::getOpenIdConfigurationEndpoint);
    }

    @Override
    public String getIssuer() {
        return getValueFromDelegate(IdpConfigurationProvider::getIssuer);
    }

    @Override
    public String getAuthEndpoint() {
        return getValueFromDelegate(IdpConfigurationProvider::getAuthEndpoint);
    }

    @Override
    public String getTokenEndpoint() {
        return getValueFromDelegate(IdpConfigurationProvider::getTokenEndpoint);
    }

    @Override
    public String getJwksUri() {
        return getValueFromDelegate(IdpConfigurationProvider::getJwksUri);
    }

    @Override
    public String getLogoutEndpoint() {
        final String logoutEndpoint = getValueFromDelegate(IdpConfigurationProvider::getLogoutEndpoint);
        // If the IdP doesn't provide a logout endpoint then use the internal one to invalidate
        // the session and redirect to perform a a new auth flow.

        return NullSafe.isBlankString(logoutEndpoint)
                ? uriFactory.publicUri(InternalIdpConfigurationProvider.INTERNAL_AUTH_ENDPOINT).toString()
                : logoutEndpoint;
    }

    @Override
    public String getClientId() {
        return getValueFromDelegate(IdpConfigurationProvider::getClientId);
    }

    @Override
    public String getClientSecret() {
        return getValueFromDelegate(IdpConfigurationProvider::getClientSecret);
    }

    @Override
    public boolean isFormTokenRequest() {
        return getValueFromDelegate(IdpConfigurationProvider::isFormTokenRequest);
    }

    @Override
    public String getRequestScope() {
        return getValueFromDelegate(IdpConfigurationProvider::getRequestScope);
    }

    @Override
    public boolean isValidateAudience() {
        return getValueFromDelegate(IdpConfigurationProvider::isValidateAudience);
    }

    @Override
    public String getLogoutRedirectParamName() {
        return getValueFromDelegate(IdpConfigurationProvider::getLogoutRedirectParamName);
    }

    private <T> T getValueFromDelegate(final Function<IdpConfigurationProvider, T> function) {
        if (openIdConfigProvider.get().isUseInternal()) {
            return function.apply(internalIdpConfigurationResponseProvider);
        } else {
            return function.apply(externalIdpConfigurationResponseProvider);
        }
    }
}
