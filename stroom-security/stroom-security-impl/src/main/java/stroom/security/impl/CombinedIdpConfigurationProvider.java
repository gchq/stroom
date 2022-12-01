package stroom.security.impl;

import stroom.security.common.impl.ExternalIdpConfigurationProvider;
import stroom.security.common.impl.IdpConfigurationProvider;
import stroom.security.openid.api.OpenIdConfig;
import stroom.security.openid.api.OpenIdConfigurationResponse;

import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Provider;

public class CombinedIdpConfigurationProvider implements IdpConfigurationProvider {

    private final InternalIdpConfigurationProvider internalIdpConfigurationResponseProvider;
    private final ExternalIdpConfigurationProvider externalIdpConfigurationResponseProvider;
    private final Provider<OpenIdConfig> openIdConfigProvider;

    @Inject
    public CombinedIdpConfigurationProvider(
            final InternalIdpConfigurationProvider internalIdpConfigurationResponseProvider,
            final ExternalIdpConfigurationProvider externalIdpConfigurationResponseProvider,
            final Provider<OpenIdConfig> openIdConfigProvider) {

        this.internalIdpConfigurationResponseProvider = internalIdpConfigurationResponseProvider;
        this.externalIdpConfigurationResponseProvider = externalIdpConfigurationResponseProvider;
        this.openIdConfigProvider = openIdConfigProvider;
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
        return getValueFromDelegate(IdpConfigurationProvider::getLogoutEndpoint);
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
