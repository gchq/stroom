package stroom.security.impl;

import stroom.config.common.UriFactory;
import stroom.security.common.impl.ExternalIdpConfigurationProvider;
import stroom.security.common.impl.IdpConfigurationProvider;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfig;
import stroom.security.openid.api.OpenIdConfigurationResponse;
import stroom.util.NullSafe;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * A front for the internal and external OIDC config providers. The useInternal prop in local
 * config controls which delegate is used.
 */
public class DelegatingIdpConfigurationProvider implements IdpConfigurationProvider {

    private final InternalIdpConfigurationProvider internalIdpConfigurationProvider;
    private final ExternalIdpConfigurationProvider externalIdpConfigurationProvider;
    private final StroomTestIdpConfigurationProvider stroomTestIdpConfigurationProvider;
    private final Provider<OpenIdConfig> openIdConfigProvider;
    private final UriFactory uriFactory;

    @Inject
    public DelegatingIdpConfigurationProvider(
            final InternalIdpConfigurationProvider internalIdpConfigurationProvider,
            final ExternalIdpConfigurationProvider externalIdpConfigurationProvider,
            final StroomTestIdpConfigurationProvider stroomTestIdpConfigurationProvider,
            final Provider<OpenIdConfig> openIdConfigProvider,
            final UriFactory uriFactory) {

        this.internalIdpConfigurationProvider = internalIdpConfigurationProvider;
        this.externalIdpConfigurationProvider = externalIdpConfigurationProvider;
        this.stroomTestIdpConfigurationProvider = stroomTestIdpConfigurationProvider;
        this.openIdConfigProvider = openIdConfigProvider;
        this.uriFactory = uriFactory;
    }

    @Override
    public OpenIdConfigurationResponse getConfigurationResponse() {
        return getDelegate().getConfigurationResponse();
    }

    @Override
    public IdpType getIdentityProviderType() {
        return openIdConfigProvider.get().getIdentityProviderType();
    }

    @Override
    public String getOpenIdConfigurationEndpoint() {
        return getDelegate().getOpenIdConfigurationEndpoint();
    }

    @Override
    public String getIssuer() {
        return getDelegate().getIssuer();
    }

    @Override
    public String getAuthEndpoint() {
        return getDelegate().getAuthEndpoint();
    }

    @Override
    public String getTokenEndpoint() {
        return getDelegate().getTokenEndpoint();
    }

    @Override
    public String getJwksUri() {
        return getDelegate().getJwksUri();
    }

    @Override
    public String getLogoutEndpoint() {
        final String logoutEndpoint = getDelegate().getLogoutEndpoint();
        // If the IdP doesn't provide a logout endpoint then use the internal one to invalidate
        // the session and redirect to perform a new auth flow.

        return NullSafe.isBlankString(logoutEndpoint)
                ? uriFactory.publicUri(InternalIdpConfigurationProvider.INTERNAL_AUTH_ENDPOINT).toString()
                : logoutEndpoint;
    }

    @Override
    public String getClientId() {
        return getDelegate().getClientId();
    }

    @Override
    public String getClientSecret() {
        return getDelegate().getClientSecret();
    }

    @Override
    public boolean isFormTokenRequest() {
        return getDelegate().isFormTokenRequest();
    }

    @Override
    public String getRequestScope() {
        return getDelegate().getRequestScope();
    }

    @Override
    public boolean isValidateAudience() {
        return getDelegate().isValidateAudience();
    }

    @Override
    public String getLogoutRedirectParamName() {
        return getDelegate().getLogoutRedirectParamName();
    }

    private IdpConfigurationProvider getDelegate() {
        return switch (openIdConfigProvider.get().getIdentityProviderType()) {

            case INTERNAL_IDP -> internalIdpConfigurationProvider;
            case EXTERNAL_IDP -> externalIdpConfigurationProvider;
            case TEST_CREDENTIALS -> stroomTestIdpConfigurationProvider;
            case NO_IDP -> throw new UnsupportedOperationException(
                    "No delegate when IDP type is " + IdpType.NO_IDP);
        };
    }
}
