/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.impl;

import com.google.common.base.Strings;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.auth.service.ApiException;
import stroom.auth.service.api.model.IdTokenRequest;
import stroom.security.api.AuthenticationService;
import stroom.security.api.AuthenticationToken;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserTokenUtil;
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.impl.session.UserSessionUtil;
import stroom.security.shared.User;
import stroom.security.shared.UserToken;
import stroom.ui.config.shared.UiConfig;
import stroom.util.guice.ResourcePaths;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.UserAgentSessionUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <p>
 * Filter to avoid posts to the wrong place (e.g. the root of the app)
 * </p>
 */
@Singleton
class SecurityFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityFilter.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SecurityFilter.class);

    private static final String SCOPE = "scope";
    private static final String RESPONSE_TYPE = "response_type";
    private static final String CLIENT_ID = "client_id";
    private static final String REDIRECT_URL = "redirect_url";
    private static final String STATE = "state";
    private static final String NONCE = "nonce";
    private static final String PROMPT = "prompt";
    private static final String ACCESS_CODE = "accessCode";
    private static final String NO_AUTH_PATH = ResourcePaths.ROOT_PATH + ResourcePaths.NO_AUTH_PATH + "/";
    public static String PUBLIC_API_PATH_REGEX = "\\/api.*\\/noauth\\/.*"; // E.g. /api/authentication/v1/noauth/exchange

    private static final Set<String> RESERVED_PARAMS = Set.of(
            SCOPE, RESPONSE_TYPE, CLIENT_ID, REDIRECT_URL, STATE, NONCE, PROMPT, ACCESS_CODE);

    private final AuthenticationConfig config;
    private final UiConfig uiConfig;
    private final JWTService jwtService;
    private final AuthenticationServiceClients authenticationServiceClients;
    private final AuthenticationService authenticationService;
    private final SecurityContext securityContext;
    private final Pattern publicApiPathPattern;

    @Inject
    SecurityFilter(
            final AuthenticationConfig config,
            final UiConfig uiConfig,
            final JWTService jwtService,
            final AuthenticationServiceClients authenticationServiceClients,
            final AuthenticationService authenticationService,
            final SecurityContext securityContext) {
        this.config = config;
        this.uiConfig = uiConfig;
        this.jwtService = jwtService;
        this.authenticationServiceClients = authenticationServiceClients;
        this.authenticationService = authenticationService;
        this.securityContext = securityContext;

        if (!config.isAuthenticationRequired()) {
            LOGGER.warn("All authentication is disabled");
        }
        publicApiPathPattern = Pattern.compile(PUBLIC_API_PATH_REGEX);
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        LOGGER.debug("Initialising {}", this.getClass().getSimpleName());
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        if (!(response instanceof HttpServletResponse)) {
            final String message = "Unexpected response type: " + response.getClass().getName();
            LOGGER.error(message);
            return;
        }
        final HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        if (!(request instanceof HttpServletRequest)) {
            final String message = "Unexpected request type: " + request.getClass().getName();
            LOGGER.error(message);
            httpServletResponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
            return;
        }
        final HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        filter(httpServletRequest, httpServletResponse, chain);
    }

    private void filter(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
            throws IOException, ServletException {

        // We need to permit all CORS preflight requests. These use the OPTIONS method, so we'll let these through.
        if (request.getMethod().toUpperCase().equals(HttpMethod.OPTIONS)) {
            chain.doFilter(request, response);
        } else {
            final String servletPath = request.getServletPath().toLowerCase();
            final String fullPath = request.getRequestURI().toLowerCase();
            if(isPublicApiRequest(fullPath)) {
                authenticateAsProcUser(request, response, chain, false);
            }
            else if (isApiRequest(servletPath)) {
                if (!config.isAuthenticationRequired()) {
                    authenticateAsAdmin(request, response, chain, false);
                } else {
                    // Authenticate requests to the API.
                    final User userRef = loginAPI(request, response);

                    final String sessionId = Optional.ofNullable(request.getSession(false))
                            .map(HttpSession::getId)
                            .orElse(null);

                    final UserToken userToken = UserTokenUtil.create(userRef.getName(), sessionId);

                    continueAsUser(request, response, chain, userToken, userRef);
                }
            } else if (shouldBypassAuthentication(servletPath)) {
                // Some servet requests need to bypass authentication -- this happens if the servlet class
                // is annotated with @Unauthenticated. E.g. the status servlet doesn't require authentication.
                authenticateAsProcUser(request, response, chain, false);
            } else {
                // We assume all other requests are from the UI, and instigate an OpenID authentication flow
                // like the good relying party we are.

                // Try and get an existing user ref from the session.
                final User userRef = UserSessionUtil.get(request.getSession(false));
                if (userRef != null) {
                    final String sessionId = Optional.ofNullable(request.getSession(false))
                            .map(HttpSession::getId)
                            .orElse(null);

                    final UserToken userToken = UserTokenUtil.create(userRef.getName(), sessionId);

                    continueAsUser(request, response, chain, userToken, userRef);

                } else if (!config.isAuthenticationRequired()) {
                    authenticateAsAdmin(request, response, chain, true);

                } else {
                    // If the session doesn't have a user ref then attempt login.
                    final boolean loggedIn = loginUI(request, response);

                    // If we're not logged in we need to start an AuthenticationRequest flow.
                    // If this is a dispatch request then we won't try and log in. This avoids a race-condition:
                    //   1. User logs out and a new authentication flow is started
                    //   2. Before the browser is redirected GWT makes a dispatch.rpc request
                    //   3. This request, not being logged in, starts a new authentication flow
                    //   4. This new authentication flow partially over-writes the relying party data in auth.
                    // This would manifest as a bad redirect_url, one which contains 'dispatch.rpc'.
                    if (!loggedIn && !isDispatchRequest(servletPath)) {
                        // We were unable to login so we're going to redirect with an AuthenticationRequest.
                        redirectToAuthService(request, response);
                    }
                }
            }
        }
    }

    private boolean isPublicApiRequest(String servletPath) {
        return publicApiPathPattern != null && publicApiPathPattern.matcher(servletPath).matches();
    }

    private boolean isApiRequest(String servletPath) {
        return servletPath.startsWith(ResourcePaths.API_PATH);
    }

    private boolean isDispatchRequest(String servletPath) {
        return servletPath.endsWith(ResourcePaths.DISPATCH_RPC_PATH);
    }

    private boolean shouldBypassAuthentication(String servletPath) {
        return servletPath.startsWith(NO_AUTH_PATH);
    }

    private void authenticateAsAdmin(final HttpServletRequest request,
                                     final HttpServletResponse response,
                                     final FilterChain chain,
                                     final boolean useSession) throws IOException, ServletException {

        bypassAuthentication(request, response, chain, useSession, User.ADMIN_USER_NAME);
    }

    private void authenticateAsProcUser(final HttpServletRequest request,
                                        final HttpServletResponse response,
                                        final FilterChain chain,
                                        final boolean useSession) throws IOException, ServletException {

        bypassAuthentication(request, response, chain, useSession, UserTokenUtil.getInternalProcessingUserId());
    }

    private void bypassAuthentication(final HttpServletRequest request,
                                      final HttpServletResponse response,
                                      final FilterChain chain,
                                      final boolean useSession,
                                      final String userId) throws IOException, ServletException {

        LAMBDA_LOGGER.debug(() ->
                LogUtil.message("Authenticating as user {} for request {}", userId, request.getRequestURI()));
        final AuthenticationToken token = new AuthenticationToken("admin", null);
        final User userRef = securityContext.asProcessingUserResult(() -> authenticationService.getUser(token));
        if (userRef != null) {
            HttpSession session;
            if (useSession) {
                session = request.getSession(true);

                // Set the user ref in the session.
                UserSessionUtil.set(session, userRef);

            } else {
                session = request.getSession(false);
            }

            final String sessionId = Optional.ofNullable(session)
                    .map(HttpSession::getId)
                    .orElse(null);

            final UserToken userToken = UserTokenUtil.create(userRef.getName(), sessionId);
            continueAsUser(request, response, chain, userToken, userRef);
        }
    }

    private void continueAsUser(final HttpServletRequest request,
                                final HttpServletResponse response,
                                final FilterChain chain,
                                final UserToken userToken,
                                final User userRef)
            throws IOException, ServletException {
        if (userRef != null) {
            // If the session already has a reference to a user then continue the chain as that user.
            try {
                CurrentUserState.push(userToken, userRef);

                chain.doFilter(request, response);
            } finally {
                CurrentUserState.pop();
            }
        }
    }

    private boolean loginUI(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        boolean loggedIn = false;

        // If we have a state id then this should be a return from the auth service.
        final String stateId = getLastParam(request, STATE);
        if (stateId != null) {
            LOGGER.debug("We have the following state: {{}}", stateId);

            // Check the state is one we requested.
            final AuthenticationState state = AuthenticationStateSessionUtil.pop(request, stateId);
            if (state == null) {
                LOGGER.warn("Unexpected state: " + stateId);

            } else {

                // If we have an access code we can try and log in.
                final String accessCode = getLastParam(request, ACCESS_CODE);
                if (accessCode != null) {
                    // Invalidate the current session.
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        session.invalidate();
                    }

                    LOGGER.debug("We have the following access code: {{}}", accessCode);
                    session = request.getSession(true);

                    UserAgentSessionUtil.set(request);

                    final AuthenticationToken token = createUIToken(session, state, accessCode);
                    final User user = authenticationService.getUser(token);

                    if (user != null) {
                        // Set the user ref in the session.
                        UserSessionUtil.set(session, user);

                        loggedIn = true;
                    }

                    // If we manage to login then redirect to the original URL held in the state.
                    if (loggedIn) {
                        LOGGER.info("Redirecting to initiating URL: {}", state.getUrl());
                        response.sendRedirect(state.getUrl());
                    }
                }
            }
        }

        return loggedIn;
    }

    /**
     * Gets the last parameter assuming that it has been appended to the end of the URL.
     *
     * @param request The request containing the parameters.
     * @param name    The parameter name to get.
     * @return The last value of the parameter if it exists, else null.
     */
    private String getLastParam(final HttpServletRequest request, final String name) {
        final String[] arr = request.getParameterValues(name);
        if (arr != null && arr.length > 0) {
            return arr[arr.length - 1];
        }
        return null;
    }

    private void redirectToAuthService(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
//        // Invalidate the current session.
//        HttpSession session = request.getSession(false);
//        if (session != null) {
//            session.invalidate();
//        }

        // We have a a new request so we're going to redirect with an AuthenticationRequest.
        // Get the redirect URL for the auth service from the current request.
        final String url = getFullUrl(request);
        final UriBuilder uriBuilder = UriBuilder.fromUri(url);

        // When the auth service has performed authentication it will redirect back to the current URL with some
        // additional parameters (e.g. `state` and `accessCode`). It is important that these parameters are not
        // provided by our redirect URL else the redirect URL that the authentication service redirects back to may
        // end up with multiple copies of these parameters which will confuse Stroom as it will not know which one
        // of the param values to use (i.e. which were on the original redirect request and which have been added by
        // the authentication service). For this reason we will cleanse the URL of any reserved parameters here. The
        // authentication service should do the same to the redirect URL before adding its additional parameters.
        RESERVED_PARAMS.forEach(param -> uriBuilder.replaceQueryParam(param, new Object[0]));

        URI redirectUri = uriBuilder.build();

        if (uiConfig.getUrlConfig() != null && uiConfig.getUrlConfig().getUi() != null && uiConfig.getUrlConfig().getUi().trim().length() > 0) {
            LOGGER.debug("Using the advertised URL as the OpenID redirect URL");
            final UriBuilder builder = UriBuilder.fromUri(uiConfig.getUrlConfig().getUi());
            if (redirectUri.getPath() != null) {
                builder.path(redirectUri.getPath());
            }
            if (redirectUri.getFragment() != null) {
                builder.fragment(redirectUri.getFragment());
            }
            if (redirectUri.getQuery() != null) {
                builder.replaceQuery(redirectUri.getQuery());
            }
            redirectUri = builder.build();
        }
        // Create a state for this authentication request.
        final String redirectUrl = redirectUri.toString();
        final AuthenticationState state = AuthenticationStateSessionUtil.create(request, redirectUrl);

        // In some cases we might need to use an external URL as the current incoming one might have been proxied.
        final UriBuilder authenticationRequest = UriBuilder.fromUri(config.getAuthenticationServiceUrl())
                .path("/authenticate")
                .queryParam(SCOPE, "openid")
                .queryParam(RESPONSE_TYPE, "code")
                .queryParam(CLIENT_ID, config.getClientId())
                .queryParam(REDIRECT_URL, redirectUrl)
                .queryParam(STATE, state.getId())
                .queryParam(NONCE, state.getNonce());

        // If there's 'prompt' in the request then we'll want to pass that on to the AuthenticationService.
        // In OpenId 'prompt=login' asks the IP to present a login page to the user, and that's the effect
        // this will have. We need this so that we can bypass certificate logins, e.g. for when we need to
        // log in as the 'admin' user but the browser is always presenting a certificate.
        final String prompt = getLastParam(request, PROMPT);
        if (!Strings.isNullOrEmpty(prompt)) {
            authenticationRequest.queryParam(PROMPT, prompt);
        }

        final String authenticationRequestUrl = authenticationRequest.build().toString();
        LOGGER.info("Redirecting with an AuthenticationRequest to: {}", authenticationRequestUrl);
        // We want to make sure that the client has the cookie.
        response.sendRedirect(authenticationRequestUrl);
    }

    private String getFullUrl(final HttpServletRequest request) {
        if (request.getQueryString() == null) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + request.getQueryString();
    }

    /**
     * This method must create the token.
     * It does this by enacting the OpenId exchange of accessCode for idToken.
     */
    private AuthenticationToken createUIToken(final HttpSession session, final AuthenticationState state, final String accessCode) {
        AuthenticationToken token = null;

        try {
            String sessionId = session.getId();
            IdTokenRequest idTokenRequest = new IdTokenRequest()
                    .clientId(config.getClientId())
                    .clientSecret(config.getClientSecret())
                    .accessCode(accessCode);
            final String idToken = authenticationServiceClients.newAuthenticationApi().getIdToken(idTokenRequest);
            final JwtClaims jwtClaimsOptional = jwtService.verifyToken(idToken);
            final String nonce = (String) jwtClaimsOptional.getClaimsMap().get("nonce");
            final boolean match = nonce.equals(state.getNonce());
            if (match) {
                LOGGER.info("User is authenticated for sessionId " + sessionId);
                token = new AuthenticationToken(jwtClaimsOptional.getSubject(), idToken);

            } else {
                // If the nonces don't match we need to redirect to log in again.
                // Maybe the request uses an out-of-date stroomSessionId?
                LOGGER.info("Received a bad nonce!");
            }
        } catch (ApiException e) {
            if (e.getCode() == Response.Status.UNAUTHORIZED.getStatusCode()) {
                // If we can't exchange the accessCode for an idToken then this probably means the
                // accessCode doesn't exist any more, or has already been used. so we can't proceed.
                LOGGER.error("The accessCode used to obtain an idToken was rejected. Has it already been used?");
            } else {
                LOGGER.error("Unable to retrieve idToken!", e);
            }
        } catch (final MalformedClaimException | InvalidJwtException e) {
            LOGGER.warn(e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }

        return token;
    }

    private User loginAPI(final HttpServletRequest request, final HttpServletResponse response) {
        User userRef = null;

        // Authenticate requests from an API client
        boolean isAuthenticatedApiRequest = jwtService.containsValidJws(request);
        if (isAuthenticatedApiRequest) {
            final AuthenticationToken token = createAPIToken(request);
            userRef = securityContext.asProcessingUserResult(() -> authenticationService.getUser(token));
        }

        if (userRef == null) {
            LOGGER.debug("API request is unauthorised.");
            response.setStatus(Response.Status.UNAUTHORIZED.getStatusCode());
        }

        return userRef;
    }

    /**
     * This method creates a token for the API auth flow.
     */
    private AuthenticationToken createAPIToken(final HttpServletRequest request) {
        AuthenticationToken token = null;

        try {
            if (jwtService.containsValidJws(request)) {
                final Optional<String> optionalJws = jwtService.getJws(request);
                final String jws = optionalJws.orElseThrow(() -> new AuthenticationException("Unable to get JWS"));
                final JwtClaims jwtClaims = jwtService.verifyToken(jws);
                token = new AuthenticationToken(jwtClaims.getSubject(), optionalJws.get());
            } else {
                LOGGER.error("Cannot get a valid JWS for API request!");
            }
        } catch (final MalformedClaimException | InvalidJwtException e) {
            LOGGER.warn(e.getMessage());
            throw new AuthenticationException(e.getMessage(), e);
        }

        return token;
    }

    @Override
    public void destroy() {
    }
}
