package stroom.security.impl;

import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.IdpIdentityMapper;
import stroom.security.common.impl.JwtUtil;
import stroom.security.common.impl.UserIdentityImpl;
import stroom.security.openid.api.OpenIdConfig;
import stroom.security.openid.api.OpenIdConfiguration.IdpType;
import stroom.security.openid.api.TokenResponse;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class IdpIdentityToStroomUserMapper implements IdpIdentityMapper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IdpIdentityToStroomUserMapper.class);

    private final ProcessingUserIdentityProvider processingUserIdentityProvider;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final UserCache userCache;
    private final Provider<OpenIdConfig> openIdConfigProvider;

    @Inject
    public IdpIdentityToStroomUserMapper(final ProcessingUserIdentityProvider processingUserIdentityProvider,
                                         final DefaultOpenIdCredentials defaultOpenIdCredentials,
                                         final UserCache userCache,
                                         final Provider<OpenIdConfig> openIdConfigProvider) {
        this.processingUserIdentityProvider = processingUserIdentityProvider;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.userCache = userCache;
        this.openIdConfigProvider = openIdConfigProvider;
    }

    @Override
    public Optional<UserIdentity> mapApiIdentity(final JwtContext jwtContext,
                                                 final HttpServletRequest request) {

        // Always try to get the proc user identity as even if we are using an external IDP
        // the proc user is on the internal one
        return getProcessingUser(jwtContext)
                .or(() -> getApiUserIdentity(jwtContext, request));
    }

    @Override
    public Optional<UserIdentity> mapAuthFlowIdentity(final JwtContext jwtContext,
                                                      final HttpServletRequest request,
                                                      final TokenResponse tokenResponse) {
        final JwtClaims jwtClaims = jwtContext.getJwtClaims();
        final String userId = getUserId(jwtClaims);
        final Optional<User> optUser = userCache.get(userId);

        return optUser
                .flatMap(user -> {
                    final UserIdentity userIdentity = createUserIdentity(jwtClaims, request, tokenResponse, user);
                    return Optional.of(userIdentity);
                })
                .or(() -> {
                    throw new AuthenticationException("Unable to find user: " + userId);
                });
    }

    private UserIdentity createUserIdentity(final JwtClaims jwtClaims,
                                            final HttpServletRequest request,
                                            final TokenResponse tokenResponse,
                                            final User user) {
        Objects.requireNonNull(user);

        final HttpSession session = request.getSession(false);

        UserIdentity userIdentity = new UserIdentityImpl(
                user.getUuid(),
                user.getName(),
                session,
                tokenResponse,
                jwtClaims);

        LOGGER.info(() -> "User " + userIdentity
                + " is authenticated for sessionId " + NullSafe.get(session, HttpSession::getId));
        return userIdentity;
    }

    /**
     * Extract a unique identifier from the JWT claims that can be used to map to a local user.
     */
    public String getUserId(final JwtClaims jwtClaims) {
        LOGGER.trace("getUserId");
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
        LOGGER.debug(() -> "Getting user identity from jwtContext=" + jwtContext);

        String sessionId = null;
        final HttpSession session = request.getSession(false);
        if (session != null) {
            sessionId = session.getId();
        }

        try {
            final String userId = getUserId(jwtContext.getJwtClaims());
            final String userUuid;
            if (IdpType.TEST.equals(openIdConfigProvider.get().getIdentityProviderType())
                    && jwtContext.getJwtClaims().getAudience().contains(defaultOpenIdCredentials.getOauth2ClientId())
                    && userId.equals(defaultOpenIdCredentials.getApiKeyUserEmail())) {
                LOGGER.warn(() ->
                        "Authenticating using default API key. DO NOT USE IN PRODUCTION!");
                // Using default creds so just fake a user
                userUuid = UUID.randomUUID().toString();
            } else {
                final User user = userCache.get(userId).orElseThrow(() ->
                        new AuthenticationException("Unable to find user: " + userId));
                userUuid = user.getUuid();
            }

            return Optional.of(new ApiUserIdentity(
                    userUuid,
                    userId,
                    sessionId,
                    jwtContext));
        } catch (final MalformedClaimException e) {
            LOGGER.error(() -> "Error extracting claims from token in request " + request.getRequestURI());
            return Optional.empty();
        }
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
