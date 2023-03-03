package stroom.proxy.app.security;

import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.AbstractUserIdentityFactory;
import stroom.security.common.impl.JwtContextFactory;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenResponse;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.cert.CertificateExtractor;

import org.apache.http.impl.client.CloseableHttpClient;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

@Singleton
public class ProxyUserIdentityFactory extends AbstractUserIdentityFactory {

    @Inject
    ProxyUserIdentityFactory(final JwtContextFactory jwtContextFactory,
                             final Provider<OpenIdConfiguration> openIdConfigProvider,
                             final Provider<CloseableHttpClient> httpClientProvider,
                             final DefaultOpenIdCredentials defaultOpenIdCredentials,
                             final CertificateExtractor certificateExtractor,
                             final ProcessingUserIdentityProvider processingUserIdentityProvider) {
        super(jwtContextFactory,
                openIdConfigProvider,
                httpClientProvider,
                defaultOpenIdCredentials,
                certificateExtractor,
                processingUserIdentityProvider);
    }

    @Override
    protected Optional<UserIdentity> mapApiIdentity(final JwtContext jwtContext,
                                                 final HttpServletRequest request) {
        Objects.requireNonNull(jwtContext);
        // No notion of a local user identity so just wrap the claims in the jwt context
        return Optional.of(new ProxyClientUserIdentity(jwtContext));
    }

    @Override
    protected Optional<UserIdentity> mapAuthFlowIdentity(final JwtContext jwtContext,
                                                      final HttpServletRequest request,
                                                      final TokenResponse tokenResponse) {
        throw new UnsupportedOperationException("UI Auth flow not applicable to stroom-proxy");
    }
}
