package stroom.security.impl;

import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.AbstractUserIdentityFactory;
import stroom.security.common.impl.JwtContextFactory;
import stroom.security.common.impl.JwtUtil;
import stroom.security.common.impl.UpdatableToken;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenResponse;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.cert.CertificateExtractor;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.apache.http.impl.client.CloseableHttpClient;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Singleton
public class StroomUserIdentityFactory extends AbstractUserIdentityFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomUserIdentityFactory.class);

    private final ProcessingUserIdentityProvider processingUserIdentityProvider;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final UserCache userCache;
    private final Provider<OpenIdConfiguration> openIdConfigProvider;

    @Inject
    public StroomUserIdentityFactory(final JwtContextFactory jwtContextFactory,
                                     final Provider<OpenIdConfiguration> openIdConfigProvider,
                                     final Provider<CloseableHttpClient> httpClientProvider,
                                     final DefaultOpenIdCredentials defaultOpenIdCredentials,
                                     final CertificateExtractor certificateExtractor,
                                     final UserCache userCache,
                                     final ProcessingUserIdentityProvider processingUserIdentityProvider) {
        super(jwtContextFactory,
                openIdConfigProvider,
                httpClientProvider,
                defaultOpenIdCredentials,
                certificateExtractor,
                processingUserIdentityProvider);
        this.processingUserIdentityProvider = processingUserIdentityProvider;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.userCache = userCache;
        this.openIdConfigProvider = openIdConfigProvider;
    }

    @Override
    public Optional<UserIdentity> mapApiIdentity(final JwtContext jwtContext,
                                                 final HttpServletRequest request) {

        // Always try to get the proc user identity as it is a bit of a special case
        return getProcessingUser(jwtContext)
                .or(() -> getApiUserIdentity(jwtContext, request));
    }

    @Override
    public Optional<UserIdentity> mapAuthFlowIdentity(final JwtContext jwtContext,
                                                      final HttpServletRequest request,
                                                      final TokenResponse tokenResponse) {
        final JwtClaims jwtClaims = jwtContext.getJwtClaims();
        final String uniqueId = getUniqueIdentity(jwtClaims);
        final Optional<User> optUser = userCache.get(uniqueId);

        return optUser
                .flatMap(user -> {
                    final UserIdentity userIdentity = createAuthFlowUserIdentity(
                            jwtClaims, request, tokenResponse, user);
                    return Optional.of(userIdentity);
                })
                .or(() -> {
                    throw new AuthenticationException("Unable to find user: " + uniqueId);
                });
    }

    private UserIdentity createAuthFlowUserIdentity(final JwtClaims jwtClaims,
                                                    final HttpServletRequest request,
                                                    final TokenResponse tokenResponse,
                                                    final User user) {
        Objects.requireNonNull(user);
        final HttpSession session = request.getSession(false);

        final UpdatableToken updatableToken = new UpdatableToken(
                tokenResponse,
                jwtClaims,
                super::refreshUsingRefreshToken);

        final UserIdentity userIdentity = new UserIdentityImpl(
                user.getUuid(),
                user.getName(),
                session,
                updatableToken);

        addTokenToRefreshQueue(updatableToken);

        LOGGER.info(() -> "User " + userIdentity
                + " is authenticated for sessionId " + NullSafe.get(session, HttpSession::getId));
        return userIdentity;
    }

    /**
     * Extract a unique identifier from the JWT claims that can be used to map to a local user.
     */
    private String getUserId(final JwtClaims jwtClaims) {
        Objects.requireNonNull(jwtClaims);
        // TODO: 29/11/2022 Think we should use the sub first ???
        String userId = JwtUtil.getEmail(jwtClaims);
        if (userId == null) {
            userId = JwtUtil.getUserIdFromIdentities(jwtClaims);
        }
        if (userId == null) {
            userId = JwtUtil.getUserName(jwtClaims);
        }
        if (userId == null) {
            userId = JwtUtil.getSubject(jwtClaims);
        }

        return userId;
    }

    private Optional<UserIdentity> getApiUserIdentity(final JwtContext jwtContext,
                                                      final HttpServletRequest request) {
        LOGGER.debug(() -> "Getting API user identity for uri: " + request.getRequestURI());

        try {
            final String userId = getUserId(jwtContext.getJwtClaims());
            LOGGER.debug(() -> LogUtil.message("Getting API user identity for user id: {} uri: {}",
                    userId, request.getRequestURI()));

            final String userUuid;

            if (IdpType.TEST_CREDENTIALS.equals(openIdConfigProvider.get().getIdentityProviderType())
                    && jwtContext.getJwtClaims().getAudience().contains(defaultOpenIdCredentials.getOauth2ClientId())
                    && userId.equals(defaultOpenIdCredentials.getApiKeyUserEmail())) {
                LOGGER.debug("Authenticating using default API key. DO NOT USE IN PRODUCTION!");
                // Using default creds so just fake a user
                userUuid = UUID.randomUUID().toString();
            } else {
                final User user = userCache.get(userId).orElseThrow(() ->
                        new AuthenticationException("Unable to find user: " + userId));
                userUuid = user.getUuid();
            }

            return Optional.of(createApiUserIdentity(jwtContext, userId, userUuid, request));
        } catch (final MalformedClaimException e) {
            LOGGER.error(() -> "Error extracting claims from token in request " + request.getRequestURI());
            return Optional.empty();
        }
    }

    private static ApiUserIdentity createApiUserIdentity(final JwtContext jwtContext,
                                                         final String userId,
                                                         final String userUuid,
                                                         final HttpServletRequest request) {
        Objects.requireNonNull(userId);

        final HttpSession session = request.getSession(false);

        return new ApiUserIdentity(
                userUuid,
                userId,
                NullSafe.get(session, HttpSession::getId),
                jwtContext);
    }

    private Optional<UserIdentity> getProcessingUser(final JwtContext jwtContext) {
        try {
            final JwtClaims jwtClaims = jwtContext.getJwtClaims();
            if (processingUserIdentityProvider.isProcessingUser(jwtClaims.getSubject(), jwtClaims.getIssuer())) {
                return Optional.of(processingUserIdentityProvider.get());
            }
        } catch (final MalformedClaimException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return Optional.empty();
    }
}
