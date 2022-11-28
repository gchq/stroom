package stroom.proxy.app.security;

import stroom.security.api.RequestAuthenticator;
import stroom.security.openid.api.OpenIdConfig;
import stroom.security.openid.api.OpenIdConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;

public class ProxySecurityModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ApiUserIdentityFactory.class).to(ApiUserIdentityFactoryImpl.class);
        bind(RequestAuthenticator.class).to(OpenIdTokenAuthenticator.class);
    }

    @SuppressWarnings("unused")
    @Provides
    public OpenIdConfiguration getOpenIdConfiguration(final Provider<OpenIdConfig> openIdConfigProvider) {
        return openIdConfigProvider.get();
    }
}
