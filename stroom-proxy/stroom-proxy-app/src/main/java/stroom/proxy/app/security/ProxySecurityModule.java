package stroom.proxy.app.security;

import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.receive.common.RequestAuthenticator;
import stroom.security.common.impl.ExternalIdpConfigurationProvider;
import stroom.security.common.impl.ExternalProcessingUserIdentityProvider;
import stroom.security.common.impl.HttpClientProvider;
import stroom.security.common.impl.IdpConfigurationProvider;
import stroom.security.common.impl.IdpIdentityMapper;
import stroom.security.common.impl.JwtContextFactory;
import stroom.receive.common.RequestAuthenticatorImpl;
import stroom.security.common.impl.StandardJwtContextFactory;
import stroom.security.api.UserIdentityFactory;
import stroom.security.common.impl.UserIdentityFactoryImpl;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.guice.HasHealthCheckBinder;

import com.google.inject.AbstractModule;
import org.apache.http.impl.client.CloseableHttpClient;

public class ProxySecurityModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CloseableHttpClient.class).toProvider(HttpClientProvider.class);
        bind(IdpIdentityMapper.class).to(IdpIdentityToProxyIdentityMapper.class);
        bind(JwtContextFactory.class).to(StandardJwtContextFactory.class);
        bind(RequestAuthenticator.class).to(RequestAuthenticatorImpl.class);
        bind(UserIdentityFactory.class).to(UserIdentityFactoryImpl.class);
        bind(IdpConfigurationProvider.class).to(ExternalIdpConfigurationProvider.class);
        // Now bind OpenIdConfiguration to the iface from prev bind
        bind(OpenIdConfiguration.class).to(IdpConfigurationProvider.class);

        HasHealthCheckBinder.create(binder())
                .bind(ExternalIdpConfigurationProvider.class);

        bind(ProcessingUserIdentityProvider.class).to(ExternalProcessingUserIdentityProvider.class);
    }
}
