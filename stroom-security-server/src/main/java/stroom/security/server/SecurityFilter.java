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

package stroom.security.server;

import com.google.common.base.Strings;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.auth.service.ApiException;
import stroom.auth.service.api.model.IdTokenRequest;
import stroom.feed.server.UserAgentSessionUtil;
import stroom.security.UserTokenUtil;
import stroom.security.server.exception.AuthenticationException;
import stroom.security.shared.UserRef;
import stroom.servicediscovery.ResourcePaths;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <p>
 * Filter to avoid posts to the wrong place (e.g. the root of the app)
 * </p>
 */
public class SecurityFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityFilter.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SecurityFilter.class);

    public static String BYPASS_PARAM_PREFIX = "bypassRegex";
    public static String DISPATCH_PATH_REGEX_PARAM = "dispatchPathRegex";
    public static String API_PATH_REGEX_PARAM = "apiPathRegex";
    public static String PUBLIC_API_PATH_REGEX = "\\/api.*\\/noauth\\/.*"; // E.g. /api/authentication/v1/noauth/exchange

    private static final String IGNORE_URI_REGEX = "ignoreUri";

    private static final String SCOPE = "scope";
    private static final String RESPONSE_TYPE = "response_type";
    private static final String CLIENT_ID = "client_id";
    private static final String REDIRECT_URL = "redirect_url";
    private static final String STATE = "state";
    private static final String NONCE = "nonce";
    private static final String PROMPT = "prompt";
    private static final String ACCESS_CODE = "accessCode";

    private static final Set<String> RESERVED_PARAMS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            SCOPE,
            RESPONSE_TYPE,
            CLIENT_ID,
            REDIRECT_URL,
            STATE,
            NONCE,
            PROMPT,
            ACCESS_CODE)));

    private final SecurityConfig config;
    private final JWTService jwtService;
    private final AuthenticationServiceClients authenticationServiceClients;
    private final AuthenticationService authenticationService;

//    private Pattern pattern = null;
    private List<Pattern> bypassPatterns = new ArrayList<>();
    private Pattern apiPathPattern;
    private Pattern publicApiPathPattern;
    private Pattern dispatchPathPattern;

    public SecurityFilter(
            final SecurityConfig config,
            final JWTService jwtService,
            final AuthenticationServiceClients authenticationServiceClients,
            final AuthenticationService authenticationService) {
        this.config = config;
        this.jwtService = jwtService;
        this.authenticationServiceClients = authenticationServiceClients;
        this.authenticationService = authenticationService;
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        final Enumeration<String> initParams = filterConfig.getInitParameterNames();
        while (initParams.hasMoreElements()) {
            final String name = initParams.nextElement();
            final String value = filterConfig.getInitParameter(name);

            LOGGER.debug("Initialising SecurityFilter with name: {}, value: {}", name, value);

            if (name.startsWith(BYPASS_PARAM_PREFIX)) {
                try {
                    LOGGER.debug("Adding bypass pattern {}", value);
                    final Pattern pattern = Pattern.compile(value);
                    bypassPatterns.add(pattern);
                } catch (Exception e) {
                    throw new RuntimeException(LambdaLogger.buildMessage("Error compiling pattern for regex {}",
                            value));
                }
            } else if (name.equals(DISPATCH_PATH_REGEX_PARAM)) {
                dispatchPathPattern = Pattern.compile(value);
            } else if (name.equals(API_PATH_REGEX_PARAM)) {
                apiPathPattern = Pattern.compile(value);
            }
        }
        publicApiPathPattern = Pattern.compile(PUBLIC_API_PATH_REGEX);
        Objects.requireNonNull(dispatchPathPattern);
        Objects.requireNonNull(apiPathPattern);
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

        LAMBDA_LOGGER.debug(() ->
                LambdaLogger.buildMessage("Filtering request uri: {},  servletPath: {}",
                        request.getRequestURI(), request.getServletPath()));

        if (request.getMethod().toUpperCase().equals(HttpMethod.OPTIONS)) {
            // We need to allow CORS preflight requests
            LOGGER.debug("Passing on to next filter");
            chain.doFilter(request, response);
        } else {
            // We need to distinguish between requests from an API client and from the UI.
            // - If a request is from the UI and fails authentication then we need to redirect to the login page.
            // - If a request is from an API client and fails authentication then we need to return HTTP 403 UNAUTHORIZED.
            // - If a request is for clustercall.rpc then it's a back-channel stroom-to-stroom request and we want to
            //   let it through. It is essential that port 8080 is not exposed and that any reverse-proxy
            //   blocks requests that look like '.*clustercall.rpc$'.
            final String servletPath = request.getServletPath().toLowerCase();
            final String fullPath = request.getRequestURI().toLowerCase();
            if(isPublicApiRequest(fullPath)) {
                authenticateAsProcUser(request, response, chain, false);
            }
            else if (isApiRequest(servletPath)) {
                LOGGER.debug("API request");
                if (!config.isAuthenticationRequired()) {
                    authenticateAsAdmin(request, response, chain, false);
                } else {
                    // Authenticate requests to the API.
                    final UserRef userRef = loginAPI(request, response);
                    continueAsUser(request, response, chain, userRef);
                }
            } else if (shouldBypassNormalAuthentication(servletPath)) {
                authenticateAsProcUser(request, response, chain, false);
            } else {
                // Authenticate requests from the UI.

                // Try and get an existing user ref from the session.
                final UserRef userRef = UserRefSessionUtil.get(request.getSession(false));
                if (userRef != null) {
                    continueAsUser(request, response, chain, userRef);
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

    private boolean shouldBypassNormalAuthentication(String servletPath) {
        boolean result = bypassPatterns.stream()
                .anyMatch(pattern ->
                        pattern.matcher(servletPath).matches());
        LOGGER.debug("shouldBypassNormalAuthentication({}) result {}", servletPath, result);
        return result;
    }

    private boolean isPublicApiRequest(String servletPath) {
        return publicApiPathPattern != null && publicApiPathPattern.matcher(servletPath).matches();
    }

    private boolean isApiRequest(String servletPath) {
        return apiPathPattern != null && apiPathPattern.matcher(servletPath).matches();
    }

    private boolean isDispatchRequest(String servletPath) {
        return dispatchPathPattern != null && dispatchPathPattern.matcher(servletPath).matches();
    }

    private void authenticateAsAdmin(final HttpServletRequest request,
                                     final HttpServletResponse response,
                                     final FilterChain chain,
                                     final boolean useSession) throws IOException, ServletException {

        bypassAuthentication(request, response, chain, useSession, UserService.ADMIN_USER_NAME);
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
                LambdaLogger.buildMessage("Authenticating as user {} for request {}",
                        userId, request.getRequestURI()));

        final AuthenticationToken token = new AuthenticationToken(userId, null);

        final UserRef userRef = authenticationService.getUserRef(token);
        if (userRef != null) {
            if (useSession) {
                // Set the user ref in the session.
                UserRefSessionUtil.set(request.getSession(true), userRef);
            }
            continueAsUser(request, response, chain, userRef);
        }
    }

    private void continueAsUser(final HttpServletRequest request,
                                final HttpServletResponse response,
                                final FilterChain chain,
                                final UserRef userRef)
            throws IOException, ServletException {
        if (userRef != null) {
            // If the session already has a reference to a user then continue the chain as that user.
            try {
                CurrentUserState.pushUserRef(userRef);

                chain.doFilter(request, response);
            } finally {
                CurrentUserState.popUserRef();
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
                    final UserRef userRef = authenticationService.getUserRef(token);

                    if (userRef != null) {
                        // Set the user ref in the session.
                        UserRefSessionUtil.set(session, userRef);

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

        if (config.getAdvertisedStroomUrl() != null && config.getAdvertisedStroomUrl().trim().length() > 0) {
            LOGGER.debug("Using the advertised URL as the OpenID redirect URL");
            final UriBuilder builder = UriBuilder.fromUri(config.getAdvertisedStroomUrl());
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
                .queryParam(CLIENT_ID, "stroom")
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

    private UserRef loginAPI(final HttpServletRequest request, final HttpServletResponse response) {
        UserRef userRef = null;

        // Authenticate requests from an API client
        boolean isAuthenticatedApiRequest = jwtService.containsValidJws(request);
        if (isAuthenticatedApiRequest) {
            final AuthenticationToken token = createAPIToken(request);
            userRef = authenticationService.getUserRef(token);
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

    public static String makeBypassAuthInitKey(final String key) {
        return SecurityFilter.BYPASS_PARAM_PREFIX + key;
    }
}
