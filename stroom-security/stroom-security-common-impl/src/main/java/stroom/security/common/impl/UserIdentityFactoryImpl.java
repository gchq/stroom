package stroom.security.common.impl;

import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenRequest;
import stroom.security.openid.api.TokenResponse;
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
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

public class UserIdentityFactoryImpl implements UserIdentityFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserIdentityFactoryImpl.class);

    private final JwtContextFactory jwtContextFactory;
    private final Provider<OpenIdConfiguration> openIdConfigProvider;
    private final Provider<CloseableHttpClient> httpClientProvider;
    private final IdpIdentityMapper idpIdentityMapper;

    @Inject
    public UserIdentityFactoryImpl(final JwtContextFactory jwtContextFactory,
                                   final Provider<OpenIdConfiguration> openIdConfigProvider,
                                   final Provider<CloseableHttpClient> httpClientProvider,
                                   final IdpIdentityMapper idpIdentityMapper) {
        this.jwtContextFactory = jwtContextFactory;
        this.openIdConfigProvider = openIdConfigProvider;
        this.httpClientProvider = httpClientProvider;
        this.idpIdentityMapper = idpIdentityMapper;
    }

    @Override
    public Optional<UserIdentity> getApiUserIdentity(final HttpServletRequest request) {
        Optional<UserIdentity> optUserIdentity = Optional.empty();

        // See if we can login with a token if one is supplied.
        try {
            final Optional<JwtContext> optJwtContext = jwtContextFactory.getJwtContext(request);

            optUserIdentity = optJwtContext.flatMap(jwtContext ->
                            idpIdentityMapper.mapApiIdentity(jwtContext, request))
                    .or(() -> {
                        LOGGER.debug(() ->
                                "No JWS found in headers in request to " + request.getRequestURI());
                        return Optional.empty();
                    });
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }

        if (optUserIdentity.isEmpty()) {
            LOGGER.debug(() -> "Cannot get a valid JWS for API request to " + request.getRequestURI() + ". " +
                    "This may be due to Stroom being left open in a browser after Stroom was restarted.");
        }

        return optUserIdentity;
    }

    @Override
    public boolean hasAuthenticationToken(final HttpServletRequest request) {
        return jwtContextFactory.hasToken(request);
    }

    @Override
    public void removeAuthorisationEntries(final Map<String, String> headers) {
        jwtContextFactory.removeAuthorisationEntries(headers);
    }

    @Override
    public Optional<UserIdentity> getAuthFlowUserIdentity(final HttpServletRequest request,
                                                          final String code,
                                                          final AuthenticationState state) {
        final OpenIdConfiguration openIdConfiguration = openIdConfigProvider.get();

        final ObjectMapper mapper = getMapper();
        final String tokenEndpoint = openIdConfiguration.getTokenEndpoint();
        final HttpPost httpPost = new HttpPost(tokenEndpoint);

        // AWS requires form content and not a JSON object.
        if (openIdConfiguration.isFormTokenRequest()) {
            final List<NameValuePair> nvps = new ArrayList<>();
            nvps.add(new BasicNameValuePair(OpenId.CODE, code));
            nvps.add(new BasicNameValuePair(OpenId.GRANT_TYPE, OpenId.GRANT_TYPE__AUTHORIZATION_CODE));
            nvps.add(new BasicNameValuePair(OpenId.CLIENT_ID, openIdConfiguration.getClientId()));
            nvps.add(new BasicNameValuePair(OpenId.CLIENT_SECRET, openIdConfiguration.getClientSecret()));
            nvps.add(new BasicNameValuePair(OpenId.REDIRECT_URI, state.getUri()));
            setFormParams(httpPost, nvps);

        } else {
            try {
                final TokenRequest tokenRequest = TokenRequest.builder()
                        .code(code)
                        .grantType(OpenId.GRANT_TYPE__AUTHORIZATION_CODE)
                        .clientId(openIdConfiguration.getClientId())
                        .clientSecret(openIdConfiguration.getClientSecret())
                        .redirectUri(state.getUri())
                        .build();
                final String json = mapper.writeValueAsString(tokenRequest);

                httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            } catch (final JsonProcessingException e) {
                throw new AuthenticationException(e.getMessage(), e);
            }
        }

        final TokenResponse tokenResponse = getTokenResponse(mapper, httpPost, tokenEndpoint);

        return jwtContextFactory.getJwtContext(tokenResponse.getIdToken())
                .flatMap(jwtContext ->
                        createUserIdentity(request, state, tokenResponse, jwtContext))
                .or(() -> {
                    throw new RuntimeException("Unable to extract JWT claims");
                });
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
        final OpenIdConfiguration openIdConfiguration = openIdConfigProvider.get();

        try {
            LOGGER.debug("Refreshing token " + identity);

            if (identity.getTokenResponse() == null ||
                    identity.getTokenResponse().getRefreshToken() == null) {
                throw new NullPointerException("Unable to refresh token as no refresh token is available");
            }

            final ObjectMapper mapper = getMapper();
            final String tokenEndpoint = openIdConfiguration.getTokenEndpoint();
            final HttpPost httpPost = new HttpPost(tokenEndpoint);

            // AWS requires form content and not a JSON object.
            if (openIdConfiguration.isFormTokenRequest()) {
                final List<NameValuePair> nvps = new ArrayList<>();
                nvps.add(new BasicNameValuePair(OpenId.GRANT_TYPE, OpenId.REFRESH_TOKEN));
                nvps.add(new BasicNameValuePair(OpenId.REFRESH_TOKEN,
                        identity.getTokenResponse().getRefreshToken()));
                nvps.add(new BasicNameValuePair(OpenId.CLIENT_ID, openIdConfiguration.getClientId()));
                nvps.add(new BasicNameValuePair(OpenId.CLIENT_SECRET,
                        openIdConfiguration.getClientSecret()));
                setFormParams(httpPost, nvps);

            } else {
                throw new UnsupportedOperationException("JSON not supported for token refresh");
            }

            tokenResponse = getTokenResponse(mapper, httpPost, tokenEndpoint);

            jwtClaims = jwtContextFactory.getJwtContext(tokenResponse.getIdToken())
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
            final OpenIdConfiguration openIdConfiguration = openIdConfigProvider.get();
            String authorization = openIdConfiguration.getClientId()
                    + ":"
                    + openIdConfiguration.getClientSecret();
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

        } else {
            // If the nonces don't match we need to redirect to log in again.
            // Maybe the request uses an out-of-date stroomSessionId?
            LOGGER.info(() -> "Received a bad nonce!");
        }

        return optUserIdentity;
    }
}
