package stroom.security.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.AbstractUserIdentityFactory;
import stroom.security.common.impl.HasJwtClaims;
import stroom.security.common.impl.JwtContextFactory;
import stroom.security.common.impl.JwtUtil;
import stroom.security.common.impl.RefreshManager;
import stroom.security.common.impl.UpdatableToken;
import stroom.security.impl.apikey.ApiKeyService;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenResponse;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.authentication.HasRefreshable;
import stroom.util.cert.CertificateExtractor;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.exception.DataChangedException;
import stroom.util.exception.ThrowingFunction;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.UserDocRefUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@Singleton
public class StroomUserIdentityFactory
        extends AbstractUserIdentityFactory
        implements Clearable, PermissionChangeEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomUserIdentityFactory.class);

    private static final String CACHE_NAME_BY_SUBJECT_ID = "User Cache (by Subject Id)";

    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final LoadingStroomCache<String, Optional<User>> cacheBySubjectId;
    private final Provider<OpenIdConfiguration> openIdConfigProvider;
    private final Provider<UserService> userServiceProvider;
    private final UserCache userCache;
    private final SecurityContext securityContext;
    private final EntityEventBus entityEventBus;
    private final ApiKeyService apiKeyService;

    @Inject
    public StroomUserIdentityFactory(final JwtContextFactory jwtContextFactory,
                                     final Provider<OpenIdConfiguration> openIdConfigProvider,
                                     final DefaultOpenIdCredentials defaultOpenIdCredentials,
                                     final CertificateExtractor certificateExtractor,
                                     final UserCache userCache,
                                     final ServiceUserFactory serviceUserFactory,
                                     final Provider<UserService> userServiceProvider,
                                     final SecurityContext securityContext,
                                     final JerseyClientFactory jerseyClientFactory,
                                     final EntityEventBus entityEventBus,
                                     final RefreshManager refreshManager,
                                     final ApiKeyService apiKeyService,
                                     final Provider<AuthorisationConfig> authorisationConfigProvider,
                                     final CacheManager cacheManager) {


        super(jwtContextFactory,
                openIdConfigProvider,
                defaultOpenIdCredentials,
                certificateExtractor,
                serviceUserFactory,
                jerseyClientFactory,
                refreshManager);

        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.openIdConfigProvider = openIdConfigProvider;
        this.userCache = userCache;
        this.userServiceProvider = userServiceProvider;
        this.securityContext = securityContext;
        this.entityEventBus = entityEventBus;
        this.apiKeyService = apiKeyService;

        cacheBySubjectId = cacheManager.createLoadingCache(
                CACHE_NAME_BY_SUBJECT_ID,
                () -> authorisationConfigProvider.get().getUserCache(),
                subjectId -> {
                    LOGGER.debug("Loading user with subjectId '{}' into cache '{}'",
                            subjectId, CACHE_NAME_BY_SUBJECT_ID);
                    //
                    return userServiceProvider.get().getUserBySubjectId(subjectId);
                });
    }

    @Override
    protected Optional<UserIdentity> mapApiIdentity(final JwtContext jwtContext,
                                                    final HttpServletRequest request) {

        final String headerKey = UserIdentityFactory.RUN_AS_USER_HEADER;
        final String runAsUserUuid = request.getHeader(headerKey);
        if (!NullSafe.isBlankString(runAsUserUuid)) {
            // Request is proxying for a user, so it needs to be the processing user that
            // sent the request. Getting the proc user, even though we don't do anything with it will
            // ensure it is authenticated.
            // We have to run as like this because human users may have aws tokens that are not refreshable.
            getProcessingUser(jwtContext)
                    .orElseThrow(() -> new AuthenticationException(
                            "Expecting request to be made by processing user identity. url: "
                                    + request.getRequestURI()));

            final UserIdentity runAsUserIdentity = userCache.getByUuid(runAsUserUuid)
                    .map(User::asRef)
                    .map(BasicUserIdentity::new)
                    .orElseThrow(() -> new AuthenticationException(LogUtil.message("{} {} not found",
                            headerKey, runAsUserUuid)));
            LOGGER.trace("Found '{}' header, running as user {}", headerKey, runAsUserIdentity);
            return Optional.ofNullable(runAsUserIdentity);
        } else {
            // Always try to get the proc user identity as it is a bit of a special case
            final Optional<UserIdentity> optUserIdentity = getProcessingUser(jwtContext)
                    .or(() -> getApiUserIdentity(jwtContext, request));
            LOGGER.debug("Returning optUserIdentity: {}", optUserIdentity);
            return optUserIdentity;
        }
    }

    @Override
    protected Optional<UserIdentity> mapAuthFlowIdentity(final JwtContext jwtContext,
                                                         final HttpServletRequest request,
                                                         final TokenResponse tokenResponse) {
        final JwtClaims jwtClaims = jwtContext.getJwtClaims();
        final String subjectId = JwtUtil.getUniqueIdentity(openIdConfigProvider.get(), jwtClaims);
        final Optional<User> optUser = getOrCreateUserBySubjectId(subjectId);

        return optUser
                .flatMap(user -> {
                    final User effectiveUser = updateUserInfo(user, jwtClaims);
                    final UserIdentity userIdentity = createAuthFlowUserIdentity(
                            jwtClaims, request, tokenResponse, effectiveUser);
                    return Optional.of(userIdentity);
                })
                .or(() -> {
                    throw new AuthenticationException("Unable to find user: " + subjectId);
                });
    }

    @Override
    public Optional<UserIdentity> getApiUserIdentity(final HttpServletRequest request) {
        // First see if we have a Stroom API key to authenticate with, else see if we have
        // a valid JWT. Proxy can't auth using API keys as it doesn't have the back end to hold/manage them.
        return apiKeyService.fetchVerifiedIdentity(request)
                .or(() -> super.getApiUserIdentity(request));
    }

    /**
     * Call this when a user logs out (or is logged out) to stop refreshing tokens for that user.
     */
    public void logoutUser(final UserIdentity userIdentity) {
        LOGGER.debug("Logging out user {}", userIdentity);
        if (userIdentity instanceof final HasRefreshable hasRefreshable) {
            removeTokenFromRefreshManager(hasRefreshable.getRefreshable());
        }
    }

    /**
     * External IDP users are identified by their subject which is a not very helpful UUID.
     * Therefore, we cache the preferred_username and full_name in the stroom user whenever the user
     * logs in, or hits the api. We have no way to request this information from the IDP prior to
     * them logging in though.
     * Each time we map their identity we check the cached info is up-to-date and if so update it.
     */
    private User updateUserInfo(final User user, final JwtClaims jwtClaims) {
        AtomicReference<User> userRef = new AtomicReference<>(user);
        final String displayName = JwtUtil.getUserDisplayName(openIdConfigProvider.get(), jwtClaims)
                .orElse(null);

        // Hopefully this one is enough of a standard to always be there.
        final String fullName = JwtUtil.getClaimValue(jwtClaims, OpenId.CLAIM__NAME)
                .orElse(null);

        final Predicate<User> hasUserInfoChangedPredicate = aUser ->
                !Objects.equals(displayName, aUser.getDisplayName())
                        || !Objects.equals(fullName, aUser.getFullName());

        if (hasUserInfoChangedPredicate.test(user)) {
            synchronized (this) {
                securityContext.asProcessingUser(() -> {

                    int iterationCount = 0;
                    boolean success = false;

                    final UserService userService = userServiceProvider.get();
                    while (!success && iterationCount < 10) {
                        final User persistedUser = userService.loadByUuid(user.getUuid())
                                .orElseThrow(() -> new RuntimeException(
                                        "Expecting to find user with uuid " + user.getUuid()));

                        if (hasUserInfoChangedPredicate.test(user)) {
                            final String currentDisplayName = persistedUser.getDisplayName();
                            final String currentFullName = persistedUser.getFullName();

                            persistedUser.setDisplayName(displayName);
                            persistedUser.setFullName(fullName);
                            try {
                                // It is possible for another node to do this, so OCC would throw
                                // an exception
                                final User updatedUser = userService.update(persistedUser);
                                logNameChange(persistedUser,
                                        currentDisplayName,
                                        currentFullName,
                                        displayName,
                                        fullName);

                                // Caches need to know the user has changed
                                EntityEvent.fire(
                                        entityEventBus,
                                        DocRef.builder()
                                                .uuid(updatedUser.getUuid())
                                                .type(UserDocRefUtil.USER)
                                                .build(),
                                        EntityAction.UPDATE);

                                userRef.set(updatedUser);
                                success = true;
                            } catch (DataChangedException e) {
                                LOGGER.debug(LogUtil.message(
                                        "Another node has updated user {}, going round again. iterationCount: {}",
                                        user, iterationCount));
                            }
                        } else {
                            LOGGER.debug("Another node has updated it to how we want it");
                            success = true;
                        }
                        iterationCount++;
                    }
                    if (!success) {
                        throw new RuntimeException(LogUtil.message(
                                "Unable to update user {} after {} attempts", user, iterationCount));
                    }
                });
            }
        }
        return Objects.requireNonNull(userRef.get());
    }

    private static void logNameChange(final User persistedUser,
                                      final String currentDisplayName,
                                      final String currentFullName,
                                      final String displayName,
                                      final String fullName) {
        final StringBuilder sb = new StringBuilder()
                .append("Updating IDP user info for user with name/subject: ")
                .append(persistedUser.getSubjectId());

        if (!Objects.equals(currentDisplayName, displayName)) {
            sb.append(", displayName: '")
                    .append(currentDisplayName)
                    .append("' => '")
                    .append(displayName)
                    .append("'");
        }

        if (!Objects.equals(currentFullName, fullName)) {
            sb.append(", fullName: '")
                    .append(currentFullName)
                    .append("' => '")
                    .append(fullName)
                    .append("'");
        }

        LOGGER.warn(sb.toString());
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
                super::refreshUsingRefreshToken,
                session);

        final UserIdentity userIdentity = new UserIdentityImpl(
                user.getUuid(),
                user.getSubjectId(),
                user.getDisplayName(),
                user.getFullName(),
                session,
                updatableToken);

        updatableToken.setUserIdentity(userIdentity);
        addTokenToRefreshManager(updatableToken);

        LOGGER.info(() -> "Authenticated user " + userIdentity
                + " for sessionId " + NullSafe.get(session, HttpSession::getId));
        return userIdentity;
    }

    private Optional<UserIdentity> getApiUserIdentity(final JwtContext jwtContext,
                                                      final HttpServletRequest request) {
        LOGGER.debug(() -> "Getting API user identity for uri: " + request.getRequestURI());

        try {
            final JwtClaims jwtClaims = jwtContext.getJwtClaims();
            final String subjectId = JwtUtil.getUniqueIdentity(openIdConfigProvider.get(), jwtClaims);
            final Optional<String> optDisplayName = JwtUtil.getUserDisplayName(openIdConfigProvider.get(), jwtClaims);
            LOGGER.debug(() -> LogUtil.message("Getting API user identity for user id: {}, displayName: {}, uri: {}",
                    subjectId, optDisplayName, request.getRequestURI()));

            final String userUuid;

            if (IdpType.TEST_CREDENTIALS.equals(openIdConfigProvider.get().getIdentityProviderType())
                    && jwtContext.getJwtClaims().getAudience().contains(defaultOpenIdCredentials.getOauth2ClientId())
                    && subjectId.equals(defaultOpenIdCredentials.getApiKeyUserEmail())) {
                LOGGER.debug("Authenticating using default API key. DO NOT USE IN PRODUCTION!");
                // Using default creds so just fake a user
                userUuid = UUID.randomUUID().toString();
            } else {
                User user = getOrCreateUserBySubjectId(subjectId).orElseThrow(() ->
                        new AuthenticationException("Unable to find user with id: " + subjectId
                                + "(displayName: " + optDisplayName + ")"));
                user = updateUserInfo(user, jwtClaims);
                userUuid = user.getUuid();
            }

            return Optional.of(createApiUserIdentity(
                    jwtContext,
                    subjectId,
                    optDisplayName.orElse(null),
                    userUuid,
                    request));
        } catch (final MalformedClaimException e) {
            LOGGER.error(() -> "Error extracting claims from token in request " + request.getRequestURI());
            return Optional.empty();
        }
    }

    private static ApiUserIdentity createApiUserIdentity(final JwtContext jwtContext,
                                                         final String userId,
                                                         final String displayName,
                                                         final String userUuid,
                                                         final HttpServletRequest request) {
        Objects.requireNonNull(userId);

        final HttpSession session = request.getSession(false);

        return new ApiUserIdentity(
                userUuid,
                userId,
                displayName,
                NullSafe.get(session, HttpSession::getId),
                jwtContext);
    }

    private Optional<UserIdentity> getProcessingUser(final JwtContext jwtContext) {
        try {
            final JwtClaims jwtClaims = jwtContext.getJwtClaims();
            final UserIdentity serviceUser = getServiceUserIdentity();
            if (isServiceUser(jwtClaims.getSubject(), jwtClaims.getIssuer(), serviceUser)) {
                return Optional.of(serviceUser);
            }
        } catch (final MalformedClaimException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return Optional.empty();
    }

    public boolean isServiceUser(final String subject,
                                 final String issuer,
                                 final UserIdentity serviceUser) {
        if (serviceUser instanceof final HasJwtClaims hasJwtClaims) {
            return Optional.ofNullable(hasJwtClaims.getJwtClaims())
                    .map(ThrowingFunction.unchecked(jwtClaims -> {
                        final boolean isProcessingUser = Objects.equals(subject, jwtClaims.getSubject())
                                && Objects.equals(issuer, jwtClaims.getIssuer());

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Comparing subject: [{}|{}], issuer[{}|{}], result: {}",
                                    subject,
                                    jwtClaims.getSubject(),
                                    issuer,
                                    jwtClaims.getIssuer(),
                                    isProcessingUser);
                        }
                        return isProcessingUser;
                    }))
                    .orElse(false);
        } else {
            final String requiredIssuer = openIdConfigProvider.get().getIssuer();
            final boolean isProcessingUser = Objects.equals(subject, serviceUser.getSubjectId())
                    && Objects.equals(issuer, requiredIssuer);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Comparing subject: [{}|{}], issuer[{}|{}], result: {}",
                        subject,
                        serviceUser.getSubjectId(),
                        issuer,
                        requiredIssuer,
                        isProcessingUser);
            }
            return isProcessingUser;
        }
    }

    /**
     * Gets a user from the cache and if it doesn't exist creates it in the database.
     *
     * @param subjectId This is the unique identifier for the user that links the stroom user
     *                  to an IDP user, e.g. may be the 'sub' on the IDP depending on stroom config.
     */
    private Optional<User> getOrCreateUserBySubjectId(final String subjectId) {
        if (NullSafe.isBlankString(subjectId)) {
            return Optional.empty();
        } else {
            Optional<User> optUser = cacheBySubjectId.get(subjectId);
            if (optUser.isEmpty()) {
                optUser = securityContext.asProcessingUserResult(() ->
                        Optional.ofNullable(userServiceProvider.get().getOrCreateUser(subjectId)));
                if (optUser.isPresent()) {
                    cacheBySubjectId.put(subjectId, optUser);
                }
            }
            return optUser;
        }
    }

    @Override
    public void clear() {
        cacheBySubjectId.clear();
    }

    @Override
    public void onChange(final PermissionChangeEvent event) {
        if (event.getUserRef() != null) {
            // User is not a Doc so DocRef is being abused to make use of EntityEvent
            // DocRef.name is User.subjectId
            // DocRef.uuid is User.userUuid
            String subjectId = event.getUserRef().getSubjectId();
            if (subjectId != null) {
                // Don't know if it is a user or a group so invalidate both
                cacheBySubjectId.invalidate(subjectId);
            }
        }
    }
}
