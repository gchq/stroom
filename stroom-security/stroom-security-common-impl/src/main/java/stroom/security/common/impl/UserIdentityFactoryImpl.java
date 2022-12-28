package stroom.security.common.impl;

import stroom.security.api.HasJwt;
import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfig;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenResponse;
import stroom.util.NullSafe;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.cert.CertificateExtractor;
import stroom.util.exception.ThrowingFunction;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.lifecycle.Managed;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.JwtContext;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class UserIdentityFactoryImpl implements UserIdentityFactory, Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserIdentityFactoryImpl.class);

    private final JwtContextFactory jwtContextFactory;
    private final Provider<OpenIdConfiguration> openIdConfigProvider;
    private final Provider<CloseableHttpClient> httpClientProvider;
    private final IdpIdentityMapper idpIdentityMapper;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final CertificateExtractor certificateExtractor;
    private final ProcessingUserIdentityProvider processingUserIdentityProvider;

    // A service account/user for communicating with other apps in the same OIDC realm,
    // e.g. proxy => stroom. Created lazily.
    // This is tied to stroom/proxy's clientId, and we have only one of them
    private volatile UserIdentity serviceUserIdentity;

    private final BlockingQueue<AbstractTokenUserIdentity> refreshTokensDelayQueue = new DelayQueue<>();
    private ExecutorService refreshExecutorService = null;
    private final AtomicBoolean isShutdownInProgress = new AtomicBoolean(false);

    @Inject
    public UserIdentityFactoryImpl(final JwtContextFactory jwtContextFactory,
                                   final Provider<OpenIdConfiguration> openIdConfigProvider,
                                   final Provider<CloseableHttpClient> httpClientProvider,
                                   final IdpIdentityMapper idpIdentityMapper,
                                   final DefaultOpenIdCredentials defaultOpenIdCredentials,
                                   final CertificateExtractor certificateExtractor,
                                   final ProcessingUserIdentityProvider processingUserIdentityProvider) {
        this.jwtContextFactory = jwtContextFactory;
        this.openIdConfigProvider = openIdConfigProvider;
        this.httpClientProvider = httpClientProvider;
        this.idpIdentityMapper = idpIdentityMapper;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.certificateExtractor = certificateExtractor;
        this.processingUserIdentityProvider = processingUserIdentityProvider;
    }

    @Override
    public Optional<UserIdentity> getApiUserIdentity(final HttpServletRequest request) {
        Optional<UserIdentity> optUserIdentity = Optional.empty();

        final IdpType idpType = openIdConfigProvider.get().getIdentityProviderType();
        if (IdpType.NO_IDP.equals(idpType)) {
            throw new IllegalStateException(
                    "Attempting to get user identity from tokens in request when " +
                            "identityProviderType set to NONE.");
//        } else if (IdpType.TEST.equals(idpType)) {
//
//            return
//
        } else {
            // See if we can log in with a token if one is supplied. It is valid for it to not be present.
            // e.g. the front end calling API methods, as the user is held in session.
            try {
                final Optional<JwtContext> optJwtContext = jwtContextFactory.getJwtContext(request);

                optUserIdentity = optJwtContext.flatMap(jwtContext ->
                                idpIdentityMapper.mapApiIdentity(jwtContext, request))
                        .or(() -> {
                            LOGGER.trace(() ->
                                    "No JWS found in headers in request to " + request.getRequestURI());
                            return Optional.empty();
                        });
            } catch (final RuntimeException e) {
                throw new AuthenticationException("Error authenticating request to "
                        + request.getRequestURI() + " - " + e.getMessage(), e);
            }

            if (optUserIdentity.isEmpty()) {
                LOGGER.trace(() -> "Cannot get a valid JWS for API request to " + request.getRequestURI());
            } else {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Got API user identity "
                            + optUserIdentity.map(Objects::toString).orElse("EMPTY"));
                }
            }

        }
        return optUserIdentity;
    }

    @Override
    public boolean hasAuthenticationToken(final HttpServletRequest request) {
        return jwtContextFactory.hasToken(request);
    }

    @Override
    public boolean hasAuthenticationCertificate(final HttpServletRequest request) {
        return certificateExtractor.extractCertificate(request).isPresent();
    }

    @Override
    public void removeAuthEntries(final Map<String, String> headers) {
        jwtContextFactory.removeAuthorisationEntries(headers);
    }

    @Override
    public Map<String, String> getServiceUserAuthHeaders() {
        final IdpType idpType = openIdConfigProvider.get().getIdentityProviderType();
        if (IdpType.NO_IDP.equals(idpType)) {
            LOGGER.debug("IdpType is {}", idpType);
            return Collections.emptyMap();
        } else {
            final UserIdentity serviceUserIdentity = getServiceUserIdentity();
            return getAuthHeaders(serviceUserIdentity);
        }
    }

    @Override
    public Map<String, String> getAuthHeaders(final UserIdentity userIdentity) {

        if (userIdentity == null) {
            LOGGER.debug("Null user supplied");
            return Collections.emptyMap();

        } else {
            final IdpType idpType = openIdConfigProvider.get().getIdentityProviderType();
            LOGGER.debug(() -> LogUtil.message("IdpType: {}, userIdentity type: {}",
                    idpType, userIdentity.getClass().getSimpleName()));

            if (IdpType.NO_IDP.equals(idpType)) {
                return Collections.emptyMap();

            } else if (IdpType.TEST_CREDENTIALS.equals(idpType)
                    && !processingUserIdentityProvider.isProcessingUser(userIdentity)) {
                // The processing user is a bit special so even when using hard-coded default open id
                // creds the proc user uses tokens created by the internal IDP.
                LOGGER.debug("Using default token");
                return jwtContextFactory.createAuthorisationEntries(defaultOpenIdCredentials.getApiKey());

            } else if (userIdentity instanceof final AbstractTokenUserIdentity tokenUserIdentity) {
                // Ensure the token hasn't gone off, just in case the refresh queue (which refreshes ahead of the
                // expiry time) is busy, so the call to refresh is unlikely.
                if (tokenUserIdentity.hasTokenExpired()) {
                    refresh(userIdentity);
                }
                final String accessToken = Objects.requireNonNull(tokenUserIdentity.getAccessToken(),
                        () -> "Null access token for userIdentity " + userIdentity);
                return jwtContextFactory.createAuthorisationEntries(accessToken);

            } else if (userIdentity instanceof final HasJwt hasJwt) {
                // This is for stroom's processing user identity which we don't need to refresh as
                // ProcessingUserIdentityProviderImpl handles that
                final String accessToken = Objects.requireNonNull(hasJwt.getJwt());
                return jwtContextFactory.createAuthorisationEntries(accessToken);

            } else {
                LOGGER.debug(() -> "Wrong type of userIdentity " + userIdentity.getClass());
                return Collections.emptyMap();
            }
        }
    }

    @Override
    public Map<String, String> getAuthHeaders(final String jwt) {
        return jwtContextFactory.createAuthorisationEntries(jwt);
    }

    /**
     * Extracts the authenticated user's identity from http request when that
     * request is part of a UI based authentication flow with the IDP
     */
    public Optional<UserIdentity> getAuthFlowUserIdentity(final HttpServletRequest request,
                                                          final String code,
                                                          final AuthenticationState state) {
        final OpenIdConfiguration openIdConfiguration = openIdConfigProvider.get();

        if (IdpType.NO_IDP.equals(openIdConfigProvider.get().getIdentityProviderType())) {
            throw new IllegalStateException(
                    "Attempting to do OIDC auth flow with identityProviderType set to NONE.");
        }

        final ObjectMapper mapper = getObjectMapper();
        final String tokenEndpoint = openIdConfiguration.getTokenEndpoint();

        final HttpPost httpPost = new OpenIdPostBuilder(tokenEndpoint, openIdConfiguration, mapper)
                .withCode(code)
                .withGrantType(OpenId.GRANT_TYPE__AUTHORIZATION_CODE)
                .withClientId(openIdConfiguration.getClientId())
                .withClientSecret(openIdConfiguration.getClientSecret())
                .withRedirectUri(state.getUri())
                .build();

        final TokenResponse tokenResponse = getTokenResponse(mapper, httpPost, tokenEndpoint);

        final Optional<UserIdentity> optUserIdentity = jwtContextFactory.getJwtContext(tokenResponse.getIdToken())
                .flatMap(jwtContext ->
                        createUserIdentity(request, state, tokenResponse, jwtContext))
                .or(() -> {
                    throw new RuntimeException("Unable to extract JWT claims");
                });

        LOGGER.debug(() -> "Got auth flow user identity "
                + optUserIdentity.map(Objects::toString).orElse("EMPTY"));

        return optUserIdentity;
    }

    private boolean isNewServiceAccountTokenRequired() {
        return serviceUserIdentity == null
                || (serviceUserIdentity instanceof final ServiceUserIdentity serviceUserIdentity2
                && serviceUserIdentity2.hasTokenExpired());
    }

    @Override
    public UserIdentity getServiceUserIdentity() {

        // Ideally the token will get recreated by the refresh queue just before
        // it expires so callers to this will find a token that is good to use and
        // thus won't be contended.
        if (isNewServiceAccountTokenRequired()) {
            synchronized (this) {
                if (isNewServiceAccountTokenRequired()) {
                    createOrUpdateServiceUserIdentity();
                }
            }
        }
        return serviceUserIdentity;
    }

    /**
     * Refresh the user identity including any tokens associated with that user.
     *
     * @param userIdentity
     */
    public void refresh(final UserIdentity userIdentity) {
        Objects.requireNonNull(userIdentity, "Null userIdentity");
        if (userIdentity instanceof final AbstractTokenUserIdentity tokenUserIdentity) {

            // This will try and refresh/recreate the token just before it expires
            // so that there is no delay for users of the token. They can explicitly call
            // refresh after checking AbstractTokenUserIdentity.hasTokenExpired() if they don't trust
            // the refresh queue, but it should always return false.
            final boolean didRefresh;
            if (userIdentity instanceof ServiceUserIdentity) {
                // service users do not have refresh tokens so just create new ones
                didRefresh = tokenUserIdentity.mutateUnderLock(
                        AbstractTokenUserIdentity::isTokenRefreshRequired,
                        userIdentity2 -> createOrUpdateServiceUserIdentity());
            } else {
                // This takes care of calling isRefreshRequired before and after getting a lock
                didRefresh = tokenUserIdentity.mutateUnderLock(
                        this::isRefreshRequired,
                        this::doRefresh);
            }
            if (LOGGER.isTraceEnabled()) {
                if (!didRefresh) {
                    LOGGER.trace("Refresh not done for {}", userIdentity);
                }
            }
        }
    }

    private boolean hasRefreshTokenExpired(final TokenResponse tokenResponse) {
        // At some point the refresh token itself will expire, so then we need to remove
        // the identity from the session if there is one to force the user to re-authenticate
        if (NullSafe.isBlankString(tokenResponse, TokenResponse::getRefreshToken)) {
            return false;
        } else {
            return jwtContextFactory.getJwtContext(tokenResponse.getRefreshToken(), false)
                    .map(JwtContext::getJwtClaims)
                    .map(ThrowingFunction.unchecked(JwtClaims::getExpirationTime))
                    .map(expireTime -> NumericDate.now().isAfter(expireTime))
                    .orElse(false);
        }
    }

    private void doRefresh(final AbstractTokenUserIdentity identity) {
        final TokenResponse currentTokenResponse = identity.getTokenResponse();
        if (hasRefreshTokenExpired(currentTokenResponse)) {
            if (identity instanceof final UserIdentityImpl userIdentityImpl) {
                LOGGER.info("Refresh token has expired, removing user identity from " +
                        "session to force re-authentication. userIdentity: {}", userIdentityImpl);
                userIdentityImpl.removeUserFromSession();
            } else {
                LOGGER.warn("Refresh token has expired, can't refresh token or create new.");
            }
        } else {
            TokenResponse newTokenResponse = null;
            JwtClaims jwtClaims = null;
            try {
                LOGGER.debug("Refreshing token " + identity);

                LOGGER.debug(LogUtil.message(
                        "Current token expiry max age: {}, refresh token expiry max age: {}",
                        NullSafe.toString(identity.getTokenResponse(),
                                TokenResponse::getExpiresIn,
                                Duration::ofSeconds),
                        NullSafe.toString(identity.getTokenResponse(),
                                TokenResponse::getRefreshTokenExpiresIn,
                                Duration::ofSeconds)));

                final FetchTokenResult fetchTokenResult = refreshTokens(currentTokenResponse);
                newTokenResponse = fetchTokenResult.tokenResponse;
                jwtClaims = fetchTokenResult.jwtClaims;
            } catch (final RuntimeException e) {
                LOGGER.error("Error refreshing token for {} - {}", identity, e.getMessage(), e);
                if (identity instanceof final UserIdentityImpl userIdentityImpl) {
                    userIdentityImpl.invalidateSession();
                }
                throw e;
            } finally {
                // Some IDPs don't seem to send updated refresh tokens so keep the existing refresh token.
                if (newTokenResponse != null
                        && newTokenResponse.getRefreshToken() == null
                        && currentTokenResponse.getRefreshToken() != null) {
                    newTokenResponse = newTokenResponse
                            .copy()
                            .refreshToken(currentTokenResponse.getRefreshToken())
                            .refreshTokenExpiresIn(Objects.requireNonNullElseGet(
                                    newTokenResponse.getRefreshTokenExpiresIn(),
                                    currentTokenResponse::getRefreshTokenExpiresIn))
                            .build();
                }

                // Update the token in the mutable user identity which is held in session
                identity.updateTokens(newTokenResponse, jwtClaims);

                LOGGER.debug(LogUtil.message(
                        "New token expiry max age: {}, refresh token expiry max age: {}",
                        NullSafe.toString(newTokenResponse, TokenResponse::getExpiresIn, Duration::ofSeconds),
                        NullSafe.toString(newTokenResponse,
                                TokenResponse::getRefreshTokenExpiresIn,
                                Duration::ofSeconds)));
            }

            // Put the updated identity on the queue with its new refresh time
            addUserIdentityToRefreshQueueIfRequired(identity);
        }
    }

    private void addUserIdentityToRefreshQueueIfRequired(final UserIdentity userIdentity) {
        if (userIdentity instanceof final AbstractTokenUserIdentity tokenUserIdentity) {
            LOGGER.debug("Adding identity to the refresh queue: {}", userIdentity);

            if (tokenUserIdentity.hasRefreshToken()
                    || tokenUserIdentity instanceof ServiceUserIdentity) {
                refreshTokensDelayQueue.add(tokenUserIdentity);
            } else {
                LOGGER.warn("Unable to refresh userIdentity due to lack of refresh token {}", tokenUserIdentity);
            }
        }
    }

    private FetchTokenResult refreshTokens(final TokenResponse existingTokenResponse) {

        final String refreshToken = NullSafe.requireNonNull(
                existingTokenResponse,
                TokenResponse::getRefreshToken,
                () -> "Unable to refresh token as no existing refresh token is available");

        final ObjectMapper mapper = getObjectMapper();
        final OpenIdConfiguration openIdConfiguration = openIdConfigProvider.get();
        final String tokenEndpoint = openIdConfiguration.getTokenEndpoint();

        final HttpPost httpPost = new OpenIdPostBuilder(tokenEndpoint, openIdConfiguration, mapper)
                .withGrantType(OpenId.GRANT_TYPE__REFRESH_TOKEN)
                .withRefreshToken(refreshToken)
                .withClientId(openIdConfiguration.getClientId())
                .withClientSecret(openIdConfiguration.getClientSecret())
                .build();

        final TokenResponse newTokenResponse = getTokenResponse(mapper, httpPost, tokenEndpoint);

        final JwtClaims jwtClaims = jwtContextFactory.getJwtContext(newTokenResponse.getIdToken())
                .map(JwtContext::getJwtClaims)
                .orElseThrow(() -> new RuntimeException("Unable to extract JWT claims"));

        return new FetchTokenResult(newTokenResponse, jwtClaims);
    }

    private boolean isRefreshRequired(final AbstractTokenUserIdentity userIdentity) {
        final boolean isRefreshRequired;

        // No point refreshing if the user no longer has a session, i.e. has been logged out
        if (userIdentity instanceof final UserIdentityImpl userIdentityImpl) {
            isRefreshRequired = userIdentityImpl.isInSession();
            LOGGER.trace("User has session: {}", isRefreshRequired);
        } else {
            isRefreshRequired = true;
        }

        LOGGER.trace(() -> LogUtil.message("Refresh time: {}", userIdentity.getExpireTime()));

        return isRefreshRequired && userIdentity.isTokenRefreshRequired();
    }

    private TokenResponse getTokenResponse(final ObjectMapper mapper,
                                           final HttpPost httpPost,
                                           final String tokenEndpoint) {
        TokenResponse tokenResponse = null;
        try (final CloseableHttpClient httpClient = httpClientProvider.get()) {
            try (final CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (HttpServletResponse.SC_OK == response.getStatusLine().getStatusCode()) {
                    final String msg = getMessage(response);
                    tokenResponse = mapper.readValue(msg, TokenResponse.class);
                } else {
                    throw new AuthenticationException("Received status " +
                            response.getStatusLine() +
                            " from " +
                            tokenEndpoint);
                }
            }
        } catch (final IOException e) {
            throw new AuthenticationException("Error requesting token from " + tokenEndpoint);
        }

        if (tokenResponse == null || tokenResponse.getIdToken() == null) {
            throw new AuthenticationException("'" +
                    OpenId.ID_TOKEN +
                    "' not provided in response");
        }

        return tokenResponse;
    }

    private String getMessage(final CloseableHttpResponse response) {
        String msg = "";
        try {
            final HttpEntity entity = response.getEntity();
            try (final InputStream is = entity.getContent()) {
                msg = StreamUtil.streamToString(is);
            }
        } catch (final RuntimeException | IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return msg;
    }

    private ObjectMapper getObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private Optional<UserIdentity> createUserIdentity(final HttpServletRequest request,
                                                      final AuthenticationState state,
                                                      final TokenResponse tokenResponse,
                                                      final JwtContext jwtContext) {
        Optional<UserIdentity> optUserIdentity = Optional.empty();
        final JwtClaims jwtClaims = jwtContext.getJwtClaims();

        final String nonce = (String) jwtClaims.getClaimsMap()
                .get(OpenId.NONCE);
        final boolean match = nonce != null && nonce.equals(state.getNonce());
        if (match) {
            optUserIdentity = idpIdentityMapper.mapAuthFlowIdentity(jwtContext, request, tokenResponse);
            optUserIdentity.ifPresent(this::addUserIdentityToRefreshQueueIfRequired);
        } else {
            // If the nonces don't match we need to redirect to log in again.
            // Maybe the request uses an out-of-date stroomSessionId?
            LOGGER.info(() -> "Received a bad nonce!");
        }

        return optUserIdentity;
    }

    private boolean createOrUpdateServiceUserIdentity() {
        final OpenIdConfiguration openIdConfiguration = openIdConfigProvider.get();
        final IdpType idpType = openIdConfiguration.getIdentityProviderType();

        switch (idpType) {
            case NO_IDP:
                serviceUserIdentity = new UserIdentity() {
                    @Override
                    public String getId() {
                        return "NO_IDP SERVICE USER";
                    }
                };
                return true;
            case TEST_CREDENTIALS:
                createOrUpdateTestServiceUser();
                return true;
            case EXTERNAL_IDP:
                createOrUpdateExternalServiceUser(openIdConfiguration);
                return true;
            default:
                throw new RuntimeException(LogUtil.message("{} is not supported for property {}.",
                        openIdConfiguration.getIdentityProviderType(),
                        OpenIdConfig.PROP_NAME_IDP_TYPE));
        }
    }

    private void createOrUpdateTestServiceUser() {
        if (serviceUserIdentity == null) {
            LOGGER.info("Created service user identity {}", serviceUserIdentity);
            serviceUserIdentity = new DefaultOpenIdCredsUserIdentity(
                    defaultOpenIdCredentials.getApiKeyUserEmail(),
                    defaultOpenIdCredentials.getApiKey());
        }
    }

    private void createOrUpdateExternalServiceUser(final OpenIdConfiguration openIdConfiguration) {
        final ObjectMapper mapper = getObjectMapper();
        final String tokenEndpoint = openIdConfiguration.getTokenEndpoint();
        final HttpPost httpPost = new OpenIdPostBuilder(tokenEndpoint, openIdConfiguration, mapper)
                .withGrantType(OpenId.GRANT_TYPE__CLIENT_CREDENTIALS)
                .withClientId(openIdConfiguration.getClientId())
                .withClientSecret(openIdConfiguration.getClientSecret())
                .addScope(OpenId.SCOPE__OPENID)
                .build();

        final TokenResponse tokenResponse = getTokenResponse(mapper, httpPost, tokenEndpoint);

        final FetchTokenResult fetchTokenResult = jwtContextFactory.getJwtContext(tokenResponse.getAccessToken())
                .map(jwtContext ->
                        new FetchTokenResult(tokenResponse, jwtContext.getJwtClaims()))
                .orElseThrow(() -> {
                    throw new RuntimeException("Unable to extract JWT claims for service user");
                });

        if (serviceUserIdentity == null) {
            LOGGER.info("Created service user identity {}", serviceUserIdentity);
            serviceUserIdentity = new ServiceUserIdentity(fetchTokenResult.tokenResponse, fetchTokenResult.jwtClaims);
        } else {
            if (!(serviceUserIdentity instanceof final ServiceUserIdentity serviceUserIdentity2)) {
                throw new RuntimeException(LogUtil.message("Unexpected type {}",
                        serviceUserIdentity.getClass().getSimpleName()));
            }
            LOGGER.info("Updated service user identity {}", serviceUserIdentity);
            serviceUserIdentity2.updateTokens(fetchTokenResult.tokenResponse, fetchTokenResult.jwtClaims);
        }

        // Add the identity onto the queue so the tokens get refreshed
        addUserIdentityToRefreshQueueIfRequired(serviceUserIdentity);
    }

    private void consumeFromRefreshQueue() {
        try {
            final AbstractTokenUserIdentity userIdentity = refreshTokensDelayQueue.take();
            // It is possible that something else has refreshed the token
            LOGGER.debug("Consuming userIdentity {} from refresh queue (size after: {})",
                    userIdentity, refreshTokensDelayQueue.size());
            refresh(userIdentity);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Refresh delay queue interrupted, assume shutdown is happening so do no more");
        }
    }

    @Override
    public void start() throws Exception {
        if (refreshExecutorService == null) {
            LOGGER.info("Initialising OIDC token refresh executor");
            refreshExecutorService = Executors.newSingleThreadExecutor();
            refreshExecutorService.submit(() -> {
                while (!Thread.currentThread().isInterrupted()
                        && !isShutdownInProgress.get()) {
                    consumeFromRefreshQueue();
                }
            });
        }
    }

    @Override
    public void stop() throws Exception {
        isShutdownInProgress.set(true);
        if (refreshExecutorService != null) {
            LOGGER.info("Shutting down OIDC token refresh executor");
            refreshExecutorService.shutdownNow();
            // No need to wait for termination the stuff on the queue has no value once
            // we are shutting down
            LOGGER.info("Successfully shut down OIDC token refresh executor");
        }
    }


    // --------------------------------------------------------------------------------


    private record FetchTokenResult(TokenResponse tokenResponse, JwtClaims jwtClaims) {

    }


    // --------------------------------------------------------------------------------


}
