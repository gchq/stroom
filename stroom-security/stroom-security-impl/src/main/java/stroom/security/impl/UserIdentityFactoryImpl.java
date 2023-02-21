package stroom.security.impl;

import stroom.config.common.UriFactory;
import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.UserIdentity;
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.TokenRequest;
import stroom.security.openid.api.TokenResponse;
import stroom.security.shared.User;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.JwtContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

@Singleton
class UserIdentityFactoryImpl implements UserIdentityFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserIdentityFactoryImpl.class);

    private final ProcessingUserIdentityProvider processingUserIdentityProvider;
    private final InternalJwtContextFactory internalJwtContextFactory;
    private final StandardJwtContextFactory standardJwtContextFactory;
    private final OpenIdConfig openIdConfig;
    private final OpenIdManager openIdManager;
    private final ResolvedOpenIdConfig resolvedOpenIdConfig;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final UserCache userCache;
    private final Provider<CloseableHttpClient> httpClientProvider;

    @Inject
    UserIdentityFactoryImpl(final ProcessingUserIdentityProvider processingUserIdentityProvider,
                            final InternalJwtContextFactory internalJwtContextFactory,
                            final StandardJwtContextFactory standardJwtContextFactory,
                            final OpenIdConfig openIdConfig,
                            final OpenIdManager openIdManager,
                            final ResolvedOpenIdConfig resolvedOpenIdConfig,
                            final DefaultOpenIdCredentials defaultOpenIdCredentials,
                            final UserCache userCache,
                            final Provider<CloseableHttpClient> httpClientProvider) {
        this.processingUserIdentityProvider = processingUserIdentityProvider;
        this.internalJwtContextFactory = internalJwtContextFactory;
        this.standardJwtContextFactory = standardJwtContextFactory;
        this.openIdConfig = openIdConfig;
        this.openIdManager = openIdManager;
        this.resolvedOpenIdConfig = resolvedOpenIdConfig;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.userCache = userCache;
        this.httpClientProvider = httpClientProvider;
    }

    private boolean useExternalIdentityProvider() {
        return !openIdConfig.isUseInternal();
    }

    @Override
    public Optional<UserIdentity> getApiUserIdentity(final HttpServletRequest request) {
        Optional<UserIdentity> optionalUserIdentity = Optional.empty();

        // See if we can login with a token if one is supplied.
        try {
            // Always try the internal context factory first.
            Optional<JwtContext> optionalContext = internalJwtContextFactory.getJwtContext(request);
            if (optionalContext.isPresent()) {
                optionalUserIdentity = getProcessingUser(optionalContext.get());
            } else if (useExternalIdentityProvider()) {
                optionalContext = standardJwtContextFactory.getJwtContext(request);
            }

            if (optionalContext.isEmpty()) {
                LOGGER.debug(() -> "No JWS found in headers in request to " + request.getRequestURI());

            } else if (optionalUserIdentity.isEmpty()) {
                optionalUserIdentity = getUserIdentity(request, optionalContext.get());
            }

        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }

        if (optionalUserIdentity.isEmpty()) {
            LOGGER.debug(() -> "Cannot get a valid JWS for API request to " + request.getRequestURI() + ". " +
                    "This may be due to Stroom being left open in a browser after Stroom was restarted.");
        }

        return optionalUserIdentity;
    }

    @Override
    public Optional<UserIdentity> getAuthFlowUserIdentity(final HttpServletRequest request,
                                                          final String code,
                                                          final AuthenticationState state) {
        final HttpSession session = request.getSession(false);

        final ObjectMapper mapper = getMapper();
        final String tokenEndpoint = resolvedOpenIdConfig.getTokenEndpoint();
        final String redirectUri = openIdManager.getRedirectUri(state.getUri());
        final HttpPost httpPost = new HttpPost(tokenEndpoint);

        // AWS requires form content and not a JSON object.
        if (resolvedOpenIdConfig.isFormTokenRequest()) {
            final List<NameValuePair> nvps = new ArrayList<>();
            nvps.add(new BasicNameValuePair(OpenId.CODE, code));
            nvps.add(new BasicNameValuePair(OpenId.GRANT_TYPE, OpenId.GRANT_TYPE__AUTHORIZATION_CODE));
            nvps.add(new BasicNameValuePair(OpenId.CLIENT_ID, resolvedOpenIdConfig.getClientId()));
            nvps.add(new BasicNameValuePair(OpenId.CLIENT_SECRET, resolvedOpenIdConfig.getClientSecret()));
            nvps.add(new BasicNameValuePair(OpenId.REDIRECT_URI, redirectUri));
            setFormParams(httpPost, nvps);

        } else {
            try {
                final TokenRequest tokenRequest = TokenRequest.builder()
                        .code(code)
                        .grantType(OpenId.GRANT_TYPE__AUTHORIZATION_CODE)
                        .clientId(resolvedOpenIdConfig.getClientId())
                        .clientSecret(resolvedOpenIdConfig.getClientSecret())
                        .redirectUri(redirectUri)
                        .build();
                final String json = mapper.writeValueAsString(tokenRequest);

                httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            } catch (final JsonProcessingException e) {
                throw new AuthenticationException(e.getMessage(), e);
            }
        }

        final TokenResponse tokenResponse = getTokenResponse(mapper, httpPost, tokenEndpoint);

        // Always try the internal context factory first.
        Optional<JwtContext> optionalContext = internalJwtContextFactory.getJwtContext(tokenResponse.getIdToken());
        if (optionalContext.isEmpty() && useExternalIdentityProvider()) {
            optionalContext = standardJwtContextFactory.getJwtContext(tokenResponse.getIdToken());
        }

        final JwtClaims jwtClaims = optionalContext
                .map(JwtContext::getJwtClaims)
                .orElseThrow(() -> new RuntimeException("Unable to extract JWT claims"));

        return createUserIdentity(
                session,
                state,
                tokenResponse,
                jwtClaims);
    }

    @Override
    public void refresh(final UserIdentity userIdentity) {
        if (userIdentity instanceof UserIdentityImpl) {
            final UserIdentityImpl identity = (UserIdentityImpl) userIdentity;

            // Check to see if the user needs a token refresh.
            if (hasTokenExpired(identity)) {
                identity.getLock().lock();
                try {
                    if (hasTokenExpired(identity)) {
                        doRefresh(identity);
                    }
                } finally {
                    identity.getLock().unlock();
                }
            }
        }
    }

    private void doRefresh(final UserIdentityImpl identity) {
        TokenResponse tokenResponse = null;
        JwtClaims jwtClaims = null;

        try {
            LOGGER.debug("Refreshing token " + identity);

            if (identity.getTokenResponse() == null ||
                    identity.getTokenResponse().getRefreshToken() == null) {
                throw new NullPointerException("Unable to refresh token as no refresh token is available");
            }

            final ObjectMapper mapper = getMapper();
            final String tokenEndpoint = resolvedOpenIdConfig.getTokenEndpoint();
            final HttpPost httpPost = new HttpPost(tokenEndpoint);

            // AWS requires form content and not a JSON object.
            if (resolvedOpenIdConfig.isFormTokenRequest()) {
                final List<NameValuePair> nvps = new ArrayList<>();
                nvps.add(new BasicNameValuePair(OpenId.GRANT_TYPE, OpenId.REFRESH_TOKEN));
                nvps.add(new BasicNameValuePair(OpenId.REFRESH_TOKEN,
                        identity.getTokenResponse().getRefreshToken()));
                nvps.add(new BasicNameValuePair(OpenId.CLIENT_ID, resolvedOpenIdConfig.getClientId()));
                nvps.add(new BasicNameValuePair(OpenId.CLIENT_SECRET,
                        resolvedOpenIdConfig.getClientSecret()));
                setFormParams(httpPost, nvps);

            } else {
                throw new UnsupportedOperationException("JSON not supported for token refresh");
            }

            tokenResponse = getTokenResponse(mapper, httpPost, tokenEndpoint);

            // Always try the internal context factory first.
            Optional<JwtContext> optionalContext =
                    internalJwtContextFactory.getJwtContext(tokenResponse.getIdToken());
            if (optionalContext.isEmpty() && useExternalIdentityProvider()) {
                optionalContext = standardJwtContextFactory.getJwtContext(tokenResponse.getIdToken());
            }

            jwtClaims = optionalContext
                    .map(JwtContext::getJwtClaims)
                    .orElseThrow(() -> new RuntimeException("Unable to extract JWT claims"));

        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            identity.invalidateSession();
            throw e;

        } finally {
            // Some IDPs don't seem to send updated refresh tokens so keep the existing refresh token.
            if (tokenResponse != null && tokenResponse.getRefreshToken() == null) {
                tokenResponse = tokenResponse
                        .copy()
                        .refreshToken(identity.getTokenResponse().getRefreshToken())
                        .build();
            }

            identity.setTokenResponse(tokenResponse);
            identity.setJwtClaims(jwtClaims);
        }
    }

    private boolean hasTokenExpired(final UserIdentityImpl userIdentity) {
        try {
            final JwtClaims jwtClaims = userIdentity.getJwtClaims();
            if (jwtClaims == null) {
                throw new NullPointerException("User identity has null claims");
            }
            if (jwtClaims.getExpirationTime() == null) {
                throw new NullPointerException("User identity has null expiration time");
            }

            final NumericDate expirationTime = jwtClaims.getExpirationTime();
            expirationTime.addSeconds(10);
            final NumericDate now = NumericDate.now();
            return expirationTime.isBefore(now);
        } catch (final MalformedClaimException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return false;
    }

    private void setFormParams(final HttpPost httpPost,
                               final List<NameValuePair> nvps) {
        try {
            String authorization = resolvedOpenIdConfig.getClientId() + ":" + resolvedOpenIdConfig.getClientSecret();
            authorization = Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8));
            authorization = "Basic " + authorization;

            httpPost.setHeader(HttpHeaders.AUTHORIZATION, authorization);
            httpPost.setHeader(HttpHeaders.ACCEPT, "*/*");
            httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);
            httpPost.setEntity(new UrlEncodedFormEntity(nvps));
        } catch (final UnsupportedEncodingException e) {
            throw new AuthenticationException(e.getMessage(), e);
        }
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
            LOGGER.debug(e::getMessage, e);
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

    private ObjectMapper getMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private Optional<UserIdentity> getUserIdentity(final HttpServletRequest request,
                                                   final JwtContext jwtContext) {
        LOGGER.debug(() -> "Getting user identity from jwtContext=" + jwtContext);

        String sessionId = null;
        final HttpSession session = request.getSession(false);
        if (session != null) {
            sessionId = session.getId();
        }

        try {
            final String userId = getUserId(jwtContext.getJwtClaims());
            final User user;
            if (jwtContext.getJwtClaims().getAudience().contains(defaultOpenIdCredentials.getOauth2ClientId())
                    && userId.equals(defaultOpenIdCredentials.getApiKeyUserEmail())) {
                LOGGER.warn(() ->
                        "Authenticating using default API key. DO NOT USE IN PRODUCTION!");
                // Using default creds so just fake a user
                // TODO Not sure if this is enough info in the user
                user = new User();
                user.setName(userId);
                user.setUuid(UUID.randomUUID().toString());
            } else {
                user = userCache.get(userId).orElseThrow(() ->
                        new AuthenticationException("Unable to find user: " + userId));
            }

            return Optional.of(new ApiUserIdentity(user.getUuid(),
                    userId,
                    sessionId,
                    jwtContext));

        } catch (final MalformedClaimException e) {
            LOGGER.error(() -> "Error extracting claims from token in request " + request.getRequestURI());
            return Optional.empty();
        }
    }

    private Optional<UserIdentity> createUserIdentity(final HttpSession session,
                                                      final AuthenticationState state,
                                                      final TokenResponse tokenResponse,
                                                      final JwtClaims jwtClaims) {
        Optional<UserIdentity> optional = Optional.empty();

        final String nonce = (String) jwtClaims.getClaimsMap().get(OpenId.NONCE);
        final boolean match = nonce != null && nonce.equals(state.getNonce());
        if (match) {
            final String userId = getUserId(jwtClaims);
            final String sessionId = session.getId();
            LOGGER.info(() -> "User " + userId + " is authenticated for sessionId " + sessionId);
            final Optional<User> optionalUser = userCache.get(userId);
            final User user = optionalUser.orElseThrow(() ->
                    new AuthenticationException("Unable to find user: " + userId));
            optional = Optional.of(new UserIdentityImpl(user.getUuid(), userId, session, tokenResponse, jwtClaims));

        } else {
            // If the nonces don't match we need to redirect to log in again.
            // Maybe the request uses an out-of-date stroomSessionId?
            LOGGER.info(() -> "Received a bad nonce!");
        }

        return optional;
    }

    private String getUserId(final JwtClaims jwtClaims) {
        LOGGER.trace("getUserId");
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

    private Optional<UserIdentity> getProcessingUser(final JwtContext jwtContext) {
        try {
            final JwtClaims jwtClaims = jwtContext.getJwtClaims();
            final UserIdentity processingUser = processingUserIdentityProvider.get();
            if (processingUser.getId().equals(jwtClaims.getSubject())) {
                return Optional.of(processingUserIdentityProvider.get());
            }
        } catch (final MalformedClaimException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return Optional.empty();
    }
}
