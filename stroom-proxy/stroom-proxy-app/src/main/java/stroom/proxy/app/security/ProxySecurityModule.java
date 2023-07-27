package stroom.proxy.app.security;

import stroom.receive.common.RequestAuthenticator;
import stroom.receive.common.RequestAuthenticatorImpl;
import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentityFactory;
import stroom.security.common.impl.DelegatingServiceUserFactory;
import stroom.security.common.impl.ExternalIdpConfigurationProvider;
import stroom.security.common.impl.ExternalServiceUserFactory;
import stroom.security.common.impl.HttpClientProvider;
import stroom.security.common.impl.IdpConfigurationProvider;
import stroom.security.common.impl.JwtContextFactory;
import stroom.security.common.impl.NoIdpServiceUserFactory;
import stroom.security.common.impl.StandardJwtContextFactory;
import stroom.security.common.impl.TestCredentialsServiceUserFactory;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasHealthCheckBinder;

import com.google.inject.AbstractModule;
import org.apache.http.impl.client.CloseableHttpClient;

public class ProxySecurityModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CloseableHttpClient.class).toProvider(HttpClientProvider.class);
        bind(JwtContextFactory.class).to(StandardJwtContextFactory.class);
        bind(RequestAuthenticator.class).to(RequestAuthenticatorImpl.class);
        bind(UserIdentityFactory.class).to(ProxyUserIdentityFactory.class);
        bind(IdpConfigurationProvider.class).to(ExternalIdpConfigurationProvider.class);
        // Now bind OpenIdConfiguration to the iface from prev bind
        bind(OpenIdConfiguration.class).to(IdpConfigurationProvider.class);

        HasHealthCheckBinder.create(binder())
                .bind(ExternalIdpConfigurationProvider.class);

        // TODO: 26/07/2023 Remove
//        bind(ProcessingUserIdentityProvider.class).to(ExternalProcessingUserIdentityProvider.class);

        bind(ServiceUserFactory.class).to(DelegatingServiceUserFactory.class);
        GuiceUtil.buildMapBinder(binder(), IdpType.class, ServiceUserFactory.class)
                .addBinding(IdpType.EXTERNAL_IDP, ExternalServiceUserFactory.class)
                .addBinding(IdpType.TEST_CREDENTIALS, TestCredentialsServiceUserFactory.class)
                .addBinding(IdpType.NO_IDP, NoIdpServiceUserFactory.class);
    }
}
