package stroom.security.impl;

import com.google.common.base.Strings;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.api.OIDC;
import stroom.security.api.UserIdentity;
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.impl.session.UserIdentitySessionUtil;
import stroom.security.shared.User;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.servlet.UserAgentSessionUtil;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

class OpenIdManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenIdManager.class);

    private final WebTargetFactory webTargetFactory;
    private final ResolvedOpenIdConfig openIdConfig;
    private final JWTService jwtService;
    private final UserCache userCache;

    @Inject
    public OpenIdManager(final WebTargetFactory webTargetFactory,
                         final ResolvedOpenIdConfig openIdConfig,
                         final JWTService jwtService,
                         final UserCache userCache) {
        this.webTargetFactory = webTargetFactory;
        this.openIdConfig = openIdConfig;
        this.jwtService = jwtService;
        this.userCache = userCache;
    }

    public String frontChannelOIDC(final HttpServletRequest request, final String postAuthRedirectUri) {
        // Create a state for this authentication request.
        final AuthenticationState state = AuthenticationStateSessionUtil.create(request, postAuthRedirectUri);

        // In some cases we might need to use an external URL as the current incoming one might have been proxied.
        // Use OIDC API.
        UriBuilder authenticationRequest = UriBuilder.fromUri(openIdConfig.getAuthEndpoint())
                .queryParam(OIDC.RESPONSE_TYPE, OIDC.CODE)
                .queryParam(OIDC.CLIENT_ID, openIdConfig.getClientId())
                .queryParam(OIDC.REDIRECT_URI, postAuthRedirectUri)
                .queryParam(OIDC.SCOPE, OIDC.SCOPE__OPENID + " " + OIDC.SCOPE__EMAIL)
                .queryParam(OIDC.STATE, state.getId())
                .queryParam(OIDC.NONCE, state.getNonce());

        // If there's 'prompt' in the request then we'll want to pass that on to the AuthenticationService.
        // In OpenId 'prompt=login' asks the IP to present a login page to the user, and that's the effect
        // this will have. We need this so that we can bypass certificate logins, e.g. for when we need to
        // log in as the 'admin' user but the browser is always presenting a certificate.
        final String prompt = UrlUtils.getLastParam(request, OIDC.PROMPT);
        if (!Strings.isNullOrEmpty(prompt)) {
            authenticationRequest.queryParam(OIDC.PROMPT, prompt);
        }

        final String authenticationRequestUrl = authenticationRequest.build().toString();
        LOGGER.info("Redirecting with an AuthenticationRequest to: {}", authenticationRequestUrl);
        // We want to make sure that the client has the cookie.
        return authenticationRequestUrl;
    }

    public String backChannelOIDC(final HttpServletRequest request, final String code, final String stateId, final String postAuthRedirectUri) {
        Objects.requireNonNull(code, "Null code");
        Objects.requireNonNull(stateId, "Null state Id");

        boolean loggedIn = false;
        String redirectUri = null;

        // If we have a state id then this should be a return from the auth service.
        LOGGER.debug("We have the following state: {{}}", stateId);

        // Check the state is one we requested.
        final AuthenticationState state = AuthenticationStateSessionUtil.pop(request, stateId);
        if (state == null) {
            LOGGER.warn("Unexpected state: " + stateId);

        } else {
            // Invalidate the current session.
            HttpSession session = request.getSession(false);
            UserAgentSessionUtil.set(request);

            // Verify code.
            final Map<String, String> params = new HashMap<>();
            params.put(OIDC.GRANT_TYPE, OIDC.GRANT_TYPE__AUTHORIZATION_CODE);
            params.put(OIDC.CLIENT_ID, openIdConfig.getClientId());
            params.put(OIDC.CLIENT_SECRET, openIdConfig.getClientSecret());
            params.put(OIDC.REDIRECT_URI, postAuthRedirectUri);
            params.put(OIDC.CODE, code);

            final String tokenEndpoint = openIdConfig.getTokenEndpoint();
            final Response res = webTargetFactory
                    .create(tokenEndpoint)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(params, MediaType.APPLICATION_JSON));

            Map responseMap;
            if (HttpServletResponse.SC_OK == res.getStatus()) {
                responseMap = res.readEntity(Map.class);
            } else {
                throw new AuthenticationException("Received status " + res.getStatus() + " from " + tokenEndpoint);
            }

            final String idToken = (String) responseMap.get(OIDC.ID_TOKEN);
            if (idToken == null) {
                throw new AuthenticationException("'" + OIDC.ID_TOKEN + "' not provided in response");
            }

            final UserIdentityImpl token = createUIToken(session, state, idToken);
            if (token != null) {
                // Set the token in the session.
                UserIdentitySessionUtil.set(session, token);
                loggedIn = true;
            }

            // If we manage to login then redirect to the original URL held in the state.
            if (loggedIn) {
                LOGGER.info("Redirecting to initiating URL: {}", state.getUrl());
                redirectUri = state.getUrl();
            }
        }

        return redirectUri;
    }

    /**
     * This method must create the token.
     * It does this by enacting the OpenId exchange of accessCode for idToken.
     */
    private UserIdentityImpl createUIToken(final HttpSession session, final AuthenticationState state, final String idToken) {
        UserIdentityImpl token = null;

        try {
            String sessionId = session.getId();
            final JwtClaims jwtClaims = jwtService.verifyToken(idToken);
            final String nonce = (String) jwtClaims.getClaimsMap().get(OIDC.NONCE);
            final boolean match = nonce.equals(state.getNonce());
            if (match) {
                LOGGER.info("User is authenticated for sessionId " + sessionId);
                final String userId = getUserId(jwtClaims);
                final Optional<User> optionalUser = userCache.get(userId);
                final User user = optionalUser.orElseThrow(() -> new AuthenticationException("Unable to find user: " + userId));
                token = new UserIdentityImpl(user.getUuid(), userId, idToken, sessionId);

            } else {
                // If the nonces don't match we need to redirect to log in again.
                // Maybe the request uses an out-of-date stroomSessionId?
                LOGGER.info("Received a bad nonce!");
            }
        } catch (final MalformedClaimException | InvalidJwtException e) {
            LOGGER.warn(e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }

        return token;
    }

    private String getUserId(final JwtClaims jwtClaims) throws MalformedClaimException {
        String userId = (String) jwtClaims.getClaimValue(OIDC.SCOPE__EMAIL);
        if (userId == null) {
            userId = jwtClaims.getSubject();
        }
        return userId;
    }

    /**
     * This method creates a token for the API auth flow.
     */
    public UserIdentity createAPIToken(final HttpServletRequest request) {
        UserIdentityImpl token = null;

        final Optional<String> optionalJws = jwtService.getJws(request);
        if (optionalJws.isPresent()) {
            final Optional<String> optionalUserId = jwtService.getUserId(optionalJws.get());

            if (optionalUserId.isPresent()) {
                String sessionId = null;
                final HttpSession session = request.getSession(false);
                if (session != null) {
                    sessionId = session.getId();
                }

                final String userId = optionalUserId.get();
                final Optional<User> optionalUser = userCache.get(userId);
                final User user = optionalUser.orElseThrow(() -> new AuthenticationException("Unable to find user: " + userId));
                token = new UserIdentityImpl(user.getUuid(), userId, optionalJws.get(), sessionId);
            }
        }

        if (token == null) {
            LOGGER.error("Cannot get a valid JWS for API request!");
        }

        return token;
    }
}
