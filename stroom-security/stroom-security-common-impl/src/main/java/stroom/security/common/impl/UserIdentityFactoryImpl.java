package stroom.security.common.impl;

import stroom.security.api.HasJwt;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.OpenIdConfiguration.IdpType;
import stroom.security.openid.api.TokenRequest;
import stroom.security.openid.api.TokenRequest.Builder;
import stroom.security.openid.api.TokenResponse;
import stroom.util.NullSafe;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.cert.CertificateUtil;
import stroom.util.exception.ThrowingFunction;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.lifecycle.Managed;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.JwtContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

@Singleton
public class UserIdentityFactoryImpl implements UserIdentityFactory, Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserIdentityFactoryImpl.class);

    private final JwtContextFactory jwtContextFactory;
    private final Provider<OpenIdConfiguration> openIdConfigProvider;
    private final Provider<CloseableHttpClient> httpClientProvider;
    private final IdpIdentityMapper idpIdentityMapper;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;

    // A service account/user for communicating with other apps in the same OIDC realm,
    // e.g. proxy => stroom. Created lazily.
    // This is tied to stroom/proxy's clientId, and we have only one of them
    private volatile ServiceUserIdentity serviceUserIdentity;

    private final BlockingQueue<AbstractTokenUserIdentity> refreshTokensDelayQueue = new DelayQueue<>();
    private ExecutorService refreshExecutorService = null;
    private final AtomicBoolean isShutdownInProgress = new AtomicBoolean(false);

    @Inject
    public UserIdentityFactoryImpl(final JwtContextFactory jwtContextFactory,
                                   final Provider<OpenIdConfiguration> openIdConfigProvider,
                                   final Provider<CloseableHttpClient> httpClientProvider,
                                   final IdpIdentityMapper idpIdentityMapper,
                                   final DefaultOpenIdCredentials defaultOpenIdCredentials) {
        this.jwtContextFactory = jwtContextFactory;
        this.openIdConfigProvider = openIdConfigProvider;
        this.httpClientProvider = httpClientProvider;
        this.idpIdentityMapper = idpIdentityMapper;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
    }

    @Override
    public Optional<UserIdentity> getApiUserIdentity(final HttpServletRequest request) {
        Optional<UserIdentity> optUserIdentity = Optional.empty();

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
            LOGGER.debug(e::getMessage, e);
        }

        if (optUserIdentity.isEmpty()) {
            LOGGER.trace(() -> "Cannot get a valid JWS for API request to " + request.getRequestURI());
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Got API user identity "
                        + optUserIdentity.map(Objects::toString).orElse("EMPTY"));
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
        return CertificateUtil.extractCertificate(request).isPresent();
    }

    @Override
    public void removeAuthEntries(final Map<String, String> headers) {
        jwtContextFactory.removeAuthorisationEntries(headers);
    }

    @Override
    public Map<String, String> getAuthHeaders(final UserIdentity userIdentity) {
        if (userIdentity == null) {
            LOGGER.debug("Null user supplied");
            return Collections.emptyMap();
        } else if (IdpType.TEST.equals(openIdConfigProvider.get().getIdentityProviderType())) {
            LOGGER.debug("Using default token");
            return buildHeaders(defaultOpenIdCredentials.getApiKey());
        } else if (userIdentity instanceof final AbstractTokenUserIdentity tokenUserIdentity) {
            // just in case the refresh queue is backed up
            if (tokenUserIdentity.isTokenRefreshRequired()) {
                refresh(userIdentity);
            }
            final String accessToken = Objects.requireNonNull(tokenUserIdentity.getAccessToken(),
                    () -> "Null access token for userIdentity " + userIdentity);
            return buildHeaders(accessToken);
        } else if (userIdentity instanceof final HasJwt hasJwt) {
            // This is for stroom's processing user identity which we don't need to refresh as
            // ProcessingUserIdentityProviderImpl handles that
            final String accessToken = Objects.requireNonNull(hasJwt.getJwt());
            return buildHeaders(accessToken);
        } else {
            LOGGER.debug(() -> "Wrong type of userIdentity " + userIdentity.getClass());
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, String> getAuthHeaders(final String jwt) {
        return buildHeaders(jwt);
    }

    private Map<String, String> buildHeaders(final String accessToken) {
        // Should be common to both internal and external IDPs
        if (NullSafe.isBlankString(accessToken)) {
            return Collections.emptyMap();
        } else {
            return Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
    }

    @Override
    public Optional<UserIdentity> getAuthFlowUserIdentity(final HttpServletRequest request,
                                                          final String code,
                                                          final AuthenticationState state) {
        final OpenIdConfiguration openIdConfiguration = openIdConfigProvider.get();

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
                || serviceUserIdentity.hasTokenExpired();
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

    @Override
    public void refresh(final UserIdentity userIdentity) {
        Objects.requireNonNull(userIdentity, "Null userIdentity");
        if (userIdentity instanceof final AbstractTokenUserIdentity tokenUserIdentity) {

            final boolean didRefresh;
            if (userIdentity instanceof ServiceUserIdentity) {
                // service users do not have refresh tokens so just create new ones
                didRefresh = tokenUserIdentity.mutateUnderLock(
                        userIdentity2 -> createOrUpdateServiceUserIdentity(),
                        this::doRefresh);
            } else {
                // This takes care of calling isRefreshRequired before and after getting a lock
                didRefresh = tokenUserIdentity.mutateUnderLock(
                        this::isRefreshRequired,
                        this::doRefresh);
            }
            if (!didRefresh) {
                LOGGER.debug("Refresh not done for {}", userIdentity);
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
                LOGGER.error(e.getMessage(), e);
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
            LOGGER.debug("User has session: {}", isRefreshRequired);
        } else {
            isRefreshRequired = true;
        }

        LOGGER.debug(() -> LogUtil.message("Refresh time: {}", userIdentity.getExpireTime()));

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
            LOGGER.info("Updated service user identity {}", serviceUserIdentity);
            serviceUserIdentity.updateTokens(fetchTokenResult.tokenResponse, fetchTokenResult.jwtClaims);
        }

        // Add the identity onto the queue so the tokens get refreshed
        addUserIdentityToRefreshQueueIfRequired(serviceUserIdentity);
        return true;
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


    private static final class OpenIdPostBuilder {

        private final String endpointUri;
        private final OpenIdConfiguration openIdConfiguration;
        private final ObjectMapper objectMapper;

        private String clientId = null;
        private String clientSecret = null;
        private String code = null;
        private String grantType = null;
        private String redirectUri = null;
        private String refreshToken = null;
        private List<String> scopes = new ArrayList<>();

        private OpenIdPostBuilder(final String endpointUri,
                                  final OpenIdConfiguration openIdConfiguration,
                                  final ObjectMapper objectMapper) {
            this.endpointUri = endpointUri;
            this.openIdConfiguration = openIdConfiguration;
            this.objectMapper = objectMapper;
        }

        public OpenIdPostBuilder withClientId(final String clientId) {
            this.clientId = clientId;
            return this;
        }

        public OpenIdPostBuilder withClientSecret(final String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public OpenIdPostBuilder withCode(final String code) {
            this.code = code;
            return this;
        }

        public OpenIdPostBuilder withGrantType(final String grantType) {
            this.grantType = grantType;
            return this;
        }

        public OpenIdPostBuilder withRedirectUri(final String redirectUri) {
            this.redirectUri = redirectUri;
            return this;
        }

        public OpenIdPostBuilder withRefreshToken(final String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public OpenIdPostBuilder withScopes(final List<String> scopes) {
            this.scopes = scopes;
            return this;
        }

        public OpenIdPostBuilder addScope(final String scope) {
            if (scopes == null) {
                scopes = new ArrayList<>();
            }
            this.scopes.add(scope);
            return this;
        }

        private void addBasicAuth(final HttpPost httpPost) {
            // Some OIDC providers expect authentication using a basic auth header
            // others expect the client(Id|Secret) to be in the form params and some cope
            // with both. Therefore, put them in both places to cover all bases.
            if (!NullSafe.isBlankString(clientId) && NullSafe.isBlankString(clientSecret)) {
                String authorization = openIdConfiguration.getClientId()
                        + ":"
                        + openIdConfiguration.getClientSecret();
                authorization = Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8));
                authorization = "Basic " + authorization;
                httpPost.setHeader(HttpHeaders.AUTHORIZATION, authorization);
            }
        }

        private void setFormParams(final HttpPost httpPost,
                                   final List<NameValuePair> nvps) {
            try {
                httpPost.setHeader(HttpHeaders.ACCEPT, "*/*");
                httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);
                httpPost.setEntity(new UrlEncodedFormEntity(nvps));
            } catch (final UnsupportedEncodingException e) {
                throw new AuthenticationException(e.getMessage(), e);
            }
        }

        private void buildFormPost(final HttpPost httpPost) {
            final List<NameValuePair> pairs = new ArrayList<>();

            final BiConsumer<String, String> addPair = (name, val) -> {
                if (!NullSafe.isBlankString(val)) {
                    pairs.add(new BasicNameValuePair(name, val));
                }
            };

            final String scopesStr = String.join(" ", scopes);
            addPair.accept(OpenId.CLIENT_ID, clientId);
            addPair.accept(OpenId.CLIENT_SECRET, clientSecret);
            addPair.accept(OpenId.CODE, code);
            addPair.accept(OpenId.GRANT_TYPE, grantType);
            addPair.accept(OpenId.REDIRECT_URI, redirectUri);
            addPair.accept(OpenId.REFRESH_TOKEN, refreshToken);
            addPair.accept(OpenId.SCOPE, scopesStr);

            LOGGER.debug("Form name/value pairs: {}", pairs);

            setFormParams(httpPost, pairs);
        }

        private void buildJsonPost(final HttpPost httpPost) {
            try {
                final Builder builder = TokenRequest.builder();
                final BiConsumer<Consumer<String>, String> addValue = (func, val) -> {
                    if (!NullSafe.isBlankString(val)) {
                        func.accept(val);
                    }
                };
                final String scopesStr = String.join(" ", scopes);
                addValue.accept(builder::clientId, clientId);
                addValue.accept(builder::clientSecret, clientId);
                addValue.accept(builder::code, clientId);
                addValue.accept(builder::grantType, clientId);
                addValue.accept(builder::redirectUri, clientId);
                addValue.accept(builder::refreshToken, refreshToken);
                addValue.accept(builder::scope, scopesStr);

                final TokenRequest tokenRequest = builder.build();
                final String json = objectMapper.writeValueAsString(tokenRequest);

                LOGGER.debug("json: {}", json);

                httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            } catch (final JsonProcessingException e) {
                throw new AuthenticationException(e.getMessage(), e);
            }
        }

        public HttpPost build() {
            final HttpPost httpPost = new HttpPost(endpointUri);
            if (openIdConfiguration.isFormTokenRequest()) {
                buildFormPost(httpPost);
            } else {
                buildJsonPost(httpPost);
            }
            addBasicAuth(httpPost);
            return httpPost;
        }
    }
}
