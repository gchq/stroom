package stroom.security.impl;

import stroom.security.api.UserIdentity;
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.impl.session.UserIdentitySessionUtil;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.TokenRequest;
import stroom.security.shared.User;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.servlet.UserAgentSessionUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
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
import org.jose4j.jwt.consumer.JwtContext;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

class OpenIdManager {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenIdManager.class);

    private final ResolvedOpenIdConfig openIdConfig;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final JwtContextFactory jwtClaimsResolver;
    private final UserCache userCache;
    private final Provider<CloseableHttpClient> httpClientProvider;

    @Inject
    public OpenIdManager(final ResolvedOpenIdConfig openIdConfig,
                         final DefaultOpenIdCredentials defaultOpenIdCredentials,
                         final JwtContextFactory jwtClaimsResolver,
                         final UserCache userCache,
                         final Provider<CloseableHttpClient> httpClientProvider) {
        this.openIdConfig = openIdConfig;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.jwtClaimsResolver = jwtClaimsResolver;
        this.userCache = userCache;
        this.httpClientProvider = httpClientProvider;
    }

    public String frontChannelOIDC(final HttpServletRequest request, final String postAuthRedirectUri) {
        Objects.requireNonNull(openIdConfig.getAuthEndpoint(),
                "To make an authentication request the OpenId config 'authEndpoint' must not be null");
        Objects.requireNonNull(openIdConfig.getClientId(),
                "To make an authentication request the OpenId config 'clientId' must not be null");

        // Create a state for this authentication request.
        final AuthenticationState state = AuthenticationStateSessionUtil.create(request, postAuthRedirectUri);

        // In some cases we might need to use an external URL as the current incoming one might have been proxied.
        // Use OIDC API.
        UriBuilder authenticationRequest = UriBuilder.fromUri(openIdConfig.getAuthEndpoint())
                .queryParam(OpenId.RESPONSE_TYPE, OpenId.CODE)
                .queryParam(OpenId.CLIENT_ID, openIdConfig.getClientId())
                .queryParam(OpenId.REDIRECT_URI, postAuthRedirectUri)
                .queryParam(OpenId.SCOPE, OpenId.SCOPE__OPENID + " " + OpenId.SCOPE__EMAIL)
                .queryParam(OpenId.STATE, state.getId())
                .queryParam(OpenId.NONCE, state.getNonce());

        // If there's 'prompt' in the request then we'll want to pass that on to the AuthenticationService.
        // In OpenId 'prompt=login' asks the IP to present a login page to the user, and that's the effect
        // this will have. We need this so that we can bypass certificate logins, e.g. for when we need to
        // log in as the 'admin' user but the browser is always presenting a certificate.
        final String prompt = UrlUtils.getLastParam(request, OpenId.PROMPT);
        if (!Strings.isNullOrEmpty(prompt)) {
            authenticationRequest.queryParam(OpenId.PROMPT, prompt);
        }

        final String authenticationRequestUrl = authenticationRequest.build().toString();
        LOGGER.info(() -> "Redirecting with an AuthenticationRequest to: " + authenticationRequestUrl);
        // We want to make sure that the client has the cookie.
        return authenticationRequestUrl;
    }

    public String backChannelOIDC(final HttpServletRequest request,
                                  final String code,
                                  final String stateId,
                                  final String postAuthRedirectUri) {
        Objects.requireNonNull(code, "Null code");
        Objects.requireNonNull(stateId, "Null state Id");

        boolean loggedIn = false;
        String redirectUri = null;

        // If we have a state id then this should be a return from the auth service.
        LOGGER.debug(() -> "We have the following state: " + stateId);

        // Check the state is one we requested.
        final AuthenticationState state = AuthenticationStateSessionUtil.pop(request, stateId);
        if (state == null) {
            LOGGER.warn(() -> "Unexpected state: " + stateId);

        } else {
            // Invalidate the current session.
            HttpSession session = request.getSession(false);
            UserAgentSessionUtil.set(request);

            final String tokenEndpoint = openIdConfig.getTokenEndpoint();
            String idToken = null;

            final ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            String authorization = openIdConfig.getClientId() + ":" + openIdConfig.getClientSecret();
            authorization = Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8));
            authorization = "Basic " + authorization;

            final HttpPost httpPost = new HttpPost(tokenEndpoint);

            // AWS requires form content and not a JSON object.
            if (openIdConfig.isFormTokenRequest()) {
                try {
                    final List<NameValuePair> nvps = new ArrayList<>();
                    nvps.add(new BasicNameValuePair(OpenId.CODE, code));
                    nvps.add(new BasicNameValuePair(OpenId.GRANT_TYPE, OpenId.GRANT_TYPE__AUTHORIZATION_CODE));
                    nvps.add(new BasicNameValuePair(OpenId.CLIENT_ID, openIdConfig.getClientId()));
                    nvps.add(new BasicNameValuePair(OpenId.CLIENT_SECRET, openIdConfig.getClientSecret()));
                    nvps.add(new BasicNameValuePair(OpenId.REDIRECT_URI, postAuthRedirectUri));

                    httpPost.setHeader(HttpHeaders.AUTHORIZATION, authorization);
                    httpPost.setHeader(HttpHeaders.ACCEPT, "*/*");
                    httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);
                    httpPost.setEntity(new UrlEncodedFormEntity(nvps));
                } catch (final UnsupportedEncodingException e) {
                    throw new AuthenticationException(e.getMessage(), e);
                }
            } else {
                try {
                    final TokenRequest tokenRequest = new TokenRequest.Builder()
                            .code(code)
                            .grantType(OpenId.GRANT_TYPE__AUTHORIZATION_CODE)
                            .clientId(openIdConfig.getClientId())
                            .clientSecret(openIdConfig.getClientSecret())
                            .redirectUri(postAuthRedirectUri)
                            .build();
                    final String json = mapper.writeValueAsString(tokenRequest);

                    httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
                } catch (final JsonProcessingException e) {
                    throw new AuthenticationException(e.getMessage(), e);
                }
            }

            try (final CloseableHttpClient httpClient = httpClientProvider.get()) {
                try (final CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    if (HttpServletResponse.SC_OK == response.getStatusLine().getStatusCode()) {
                        final HttpEntity entity = response.getEntity();
                        String msg;
                        try (final InputStream is = entity.getContent()) {
                            msg = StreamUtil.streamToString(is);
                        }

                        final TokenResponse tokenResponse = mapper.readValue(msg, TokenResponse.class);
                        idToken = tokenResponse.getIdToken();
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

            if (idToken == null) {
                throw new AuthenticationException("'" +
                        OpenId.ID_TOKEN +
                        "' not provided in response");
            }

            final UserIdentityImpl token = createUIToken(session, state, idToken);
            if (token != null) {
                // Set the token in the session.
                UserIdentitySessionUtil.set(session, token);
                loggedIn = true;
            }

            // If we manage to login then redirect to the original URL held in the state.
            if (loggedIn) {
                LOGGER.info(() -> "Redirecting to initiating URL: " + state.getUrl());
                redirectUri = state.getUrl();
            }
        }

        return redirectUri;
    }

    /**
     * This method must create the token.
     * It does this by enacting the OpenId exchange of accessCode for idToken.
     */
    private UserIdentityImpl createUIToken(final HttpSession session,
                                           final AuthenticationState state,
                                           final String idToken) {
        UserIdentityImpl token = null;

        try {
            final String sessionId = session.getId();
            final Optional<JwtContext> optionalJwtContext = jwtClaimsResolver.getJwtContext(idToken);
            final JwtClaims jwtClaims = optionalJwtContext
                    .map(JwtContext::getJwtClaims)
                    .orElseThrow(() -> new RuntimeException("Unable to extract JWT claims"));

            final String nonce = (String) jwtClaims.getClaimsMap().get(OpenId.NONCE);
            final boolean match = nonce != null && nonce.equals(state.getNonce());
            if (match) {
                LOGGER.info(() -> "User is authenticated for sessionId " + sessionId);
                final String userId = getUserId(jwtClaims);
                final Optional<User> optionalUser = userCache.get(userId);
                final User user = optionalUser.orElseThrow(() ->
                        new AuthenticationException("Unable to find user: " + userId));
                token = new UserIdentityImpl(user.getUuid(), userId, idToken, sessionId);

            } else {
                // If the nonces don't match we need to redirect to log in again.
                // Maybe the request uses an out-of-date stroomSessionId?
                LOGGER.info(() -> "Received a bad nonce!");
            }
        } catch (final MalformedClaimException e) {
            LOGGER.warn(e::getMessage);
            throw new RuntimeException(e.getMessage(), e);
        }

        return token;
    }

    private String getUserId(final JwtClaims jwtClaims) throws MalformedClaimException {
        String userId = (String) jwtClaims.getClaimValue(OpenId.SCOPE__EMAIL);
        if (userId == null) {
            userId = jwtClaims.getSubject();
        }
        return userId;
    }

    /**
     * This method attempts to get a token from the request headers and, if present, use that to login.
     */
    public UserIdentity loginWithRequestToken(final HttpServletRequest request) {
        UserIdentityImpl userIdentity = null;
        try {
            final Optional<JwtContext> optionalJwtContext = jwtClaimsResolver.getJwtContext(request);
            if (optionalJwtContext.isPresent()) {
                userIdentity = optionalJwtContext
                        .flatMap(jwtContext -> getUserIdentity(request, jwtContext))
                        .orElse(null);
            } else {
                LOGGER.debug(() -> "No JWS found in headers in request to " + request.getRequestURI());
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }

        if (userIdentity == null) {
            LOGGER.debug(() -> "Cannot get a valid JWS for API request to " + request.getRequestURI() + ". " +
                    "This may be due to Stroom being left open in a browser after Stroom was restarted.");
        }

        return userIdentity;
    }

    private Optional<UserIdentityImpl> getUserIdentity(final HttpServletRequest request,
                                                       final JwtContext jwtContext) {
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
                        "Authenticating using default API key. For production use, set up an API key in Stroom!");
                // Using default creds so just fake a user
                // TODO Not sure if this is enough info in the user
                user = new User();
                user.setName(userId);
                user.setUuid(UUID.randomUUID().toString());
            } else {
                user = userCache.get(userId).orElseThrow(() ->
                        new AuthenticationException("Unable to find user: " + userId));
            }

            return Optional.of(new UserIdentityImpl(user.getUuid(), userId, jwtContext.getJwt(), sessionId));

        } catch (MalformedClaimException e) {
            LOGGER.error(() -> "Error extracting claims from token in request " + request.getRequestURI());
            return Optional.empty();
        }
    }
}
