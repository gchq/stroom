package stroom.proxy.app.security;

import stroom.receive.common.ReceiveDataConfig;
import stroom.security.api.CommonSecurityContext;
import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.AbstractUserIdentityFactory;
import stroom.security.common.impl.JwtContextFactory;
import stroom.security.common.impl.JwtUtil;
import stroom.security.common.impl.RefreshManager;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenResponse;
import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.cert.CertificateExtractor;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserDesc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;

public class ProxyUserIdentityFactory extends AbstractUserIdentityFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyUserIdentityFactory.class);

    private final Provider<ProxyApiKeyService> proxyApiKeyServiceProvider;
    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Provider<CommonSecurityContext> proxySecurityContextProvider;

    @Inject
    ProxyUserIdentityFactory(final JwtContextFactory jwtContextFactory,
                             final Provider<OpenIdConfiguration> openIdConfigProvider,
                             final DefaultOpenIdCredentials defaultOpenIdCredentials,
                             final CertificateExtractor certificateExtractor,
                             final ServiceUserFactory serviceUserFactory,
                             final JerseyClientFactory jerseyClientFactory,
                             final RefreshManager refreshManager,
                             final Provider<ProxyApiKeyService> proxyApiKeyServiceProvider,
                             final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                             final Provider<CommonSecurityContext> proxySecurityContextProvider) {
        super(jwtContextFactory,
                openIdConfigProvider,
                defaultOpenIdCredentials,
                certificateExtractor,
                serviceUserFactory,
                jerseyClientFactory,
                refreshManager);
        this.openIdConfigurationProvider = openIdConfigProvider;
        this.proxyApiKeyServiceProvider = proxyApiKeyServiceProvider;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.proxySecurityContextProvider = proxySecurityContextProvider;
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

    @Override
    public Optional<UserIdentity> getApiUserIdentity(final HttpServletRequest request) {
        // First see if we have a Stroom API key to authenticate with, else
        // let the super try and get the identity
        final Optional<UserIdentity> optIdentity;
        if (IdpType.NO_IDP.equals(openIdConfigurationProvider.get().getIdentityProviderType())) {
            optIdentity = fetchApiKeyUserIdentity(request);
        } else {
            optIdentity = fetchApiKeyUserIdentity(request)
                    .or(() -> super.getApiUserIdentity(request));
        }
        LOGGER.debug("getApiUserIdentity() - optIdentity: {}", optIdentity);
        return optIdentity;
    }

    private Optional<UserIdentity> fetchApiKeyUserIdentity(final HttpServletRequest request) {
        final String apiKey = extractApiKey(request);
        if (NullSafe.isNonBlankString(apiKey)) {
            // At this point we are just getting the identity so don't need to check if the user
            // has any perms or not.
            final VerifyApiKeyRequest verifyApiKeyRequest = new VerifyApiKeyRequest(apiKey);
            final Optional<UserDesc> optUserDesc = proxySecurityContextProvider.get()
                    .insecureResult(() ->
                            proxyApiKeyServiceProvider.get()
                                    .verifyApiKey(verifyApiKeyRequest));
            final Optional<UserIdentity> optIdentity = optUserDesc.map(userDesc ->
                    new ApiKeyUserIdentity(apiKey, userDesc));

            LOGGER.debug("fetchApiKeyUserIdentity() - Returning {} for apiKey: {}", optIdentity, apiKey);
            return optIdentity;
        } else {
            LOGGER.debug("fetchApiKeyUserIdentity() - No API key in request");
            return Optional.empty();
        }
    }

    private String extractApiKey(final HttpServletRequest request) {
        return NullSafe.get(
                request.getHeader(HttpHeaders.AUTHORIZATION),
                header -> header.replace(JwtUtil.BEARER_PREFIX, ""));
    }
}
