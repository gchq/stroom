package stroom.proxy.app.security;

import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.AbstractUserIdentityFactory;
import stroom.security.common.impl.JwtContextFactory;
import stroom.security.common.impl.JwtUtil;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenResponse;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.cert.CertificateExtractor;
import stroom.util.jersey.JerseyClientFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;

@Singleton
public class ProxyUserIdentityFactory extends AbstractUserIdentityFactory {

    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;

    @Inject
    ProxyUserIdentityFactory(final JwtContextFactory jwtContextFactory,
                             final Provider<OpenIdConfiguration> openIdConfigProvider,
                             final DefaultOpenIdCredentials defaultOpenIdCredentials,
                             final CertificateExtractor certificateExtractor,
//                             final ProcessingUserIdentityProvider processingUserIdentityProvider,
                             final ServiceUserFactory serviceUserFactory,
                             final JerseyClientFactory jerseyClientFactory) {
        super(jwtContextFactory,
                openIdConfigProvider,
                defaultOpenIdCredentials,
                certificateExtractor,
//                processingUserIdentityProvider,
                serviceUserFactory,
                jerseyClientFactory);
        this.openIdConfigurationProvider = openIdConfigProvider;
    }

    @Override
    protected Optional<UserIdentity> mapApiIdentity(final JwtContext jwtContext,
                                                    final HttpServletRequest request) {
        Objects.requireNonNull(jwtContext);
        // No notion of a local user identity so just wrap the claims in the jwt context

        final JwtClaims jwtClaims = jwtContext.getJwtClaims();

        final String uniqueIdentity = JwtUtil.getUniqueIdentity(openIdConfigurationProvider.get(), jwtClaims);
        final String displayName = JwtUtil.getUserDisplayName(openIdConfigurationProvider.get(), jwtClaims)
                .orElse(null);
        final String fullName = JwtUtil.getClaimValue(jwtClaims, OpenId.CLAIM__NAME)
                .orElse(null);

        return Optional.of(new ProxyClientUserIdentity(
                uniqueIdentity, displayName, fullName, jwtContext));
    }

    @Override
    protected Optional<UserIdentity> mapAuthFlowIdentity(final JwtContext jwtContext,
                                                         final HttpServletRequest request,
                                                         final TokenResponse tokenResponse) {
        throw new UnsupportedOperationException("UI Auth flow not applicable to stroom-proxy");
    }
}
