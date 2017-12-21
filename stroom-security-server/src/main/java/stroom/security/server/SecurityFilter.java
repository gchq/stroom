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

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.apiclients.AuthenticationServiceClients;
import stroom.auth.service.ApiException;
import stroom.security.shared.UserRef;

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
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * <p>
 * Filter to avoid posts to the wrong place (e.g. the root of the app)
 * </p>
 */
public class SecurityFilter implements Filter {
    private static final String IGNORE_URI_REGEX = "ignoreUri";

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityFilter.class);

    private final String authenticationServiceUrl;
    private final String advertisedStroomUrl;
    private final JWTService jwtService;
    private final NonceManager nonceManager;
    private final AuthenticationServiceClients authenticationServiceClients;
    private final AuthenticationService authenticationService;

    private Pattern pattern = null;

    public SecurityFilter(
            final String authenticationServiceUrl,
            final String advertisedStroomUrl,
            final JWTService jwtService,
            final NonceManager nonceManager,
            final AuthenticationServiceClients authenticationServiceClients,
            final AuthenticationService authenticationService) {
        this.authenticationServiceUrl = authenticationServiceUrl;
        this.advertisedStroomUrl = advertisedStroomUrl;
        this.jwtService = jwtService;
        this.nonceManager = nonceManager;
        this.authenticationServiceClients = authenticationServiceClients;
        this.authenticationService = authenticationService;
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        final String regex = filterConfig.getInitParameter(IGNORE_URI_REGEX);
        if (regex != null) {
            pattern = Pattern.compile(regex);
        }
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

        if (request.getMethod().toUpperCase().equals(HttpMethod.OPTIONS)) {
            // We need to allow CORS preflight requests
            chain.doFilter(request, response);

        } else if (ignoreUri(request.getRequestURI())) {
            // Allow some URIs to bypass authentication checks
            chain.doFilter(request, response);

        } else {
            // Try and get an existing user ref from the session.
            UserRef sessionUserRef = getSessionUserRef(request, response);
            if (sessionUserRef == null) {
                // If the session doesn't have a user ref then attempt login.
                if (login(request, response)) {
                    // If we managed to login then try and get the session user ref again.
                    sessionUserRef = getSessionUserRef(request, response);
                }
            }

            if (sessionUserRef != null) {
                // If the session already has a reference to a user then continue the chain as that user.
                try {
                    CurrentUserState.pushUserRef(sessionUserRef);

                    chain.doFilter(request, response);
                } finally {
                    CurrentUserState.popUserRef();
                }
            }
        }
    }

    private boolean ignoreUri(final String uri) {
        return pattern != null && pattern.matcher(uri).matches();
    }

    private UserRef getSessionUserRef(ServletRequest request, ServletResponse response) {
        UserRef userRef = null;
        if (request instanceof HttpServletRequest) {
            final HttpSession session = ((HttpServletRequest) request).getSession(false);
            if (session != null) {
                userRef = new UserSession(session).getUserRef();
            }
        }
        return userRef;
    }

    private boolean login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean loggedIn = false;

        // Authenticate requests from an API client
        boolean isAuthenticatedApiRequest = jwtService.containsValidJws(request);
        if (isAuthenticatedApiRequest) {
            loggedIn = executeLogin(request, response);
        } else {
            final String sessionId = request.getSession().getId();

            // Authenticate requests from a User Agent

            // If we have an access code we can try and log in.
            final String accessCode = request.getParameter("accessCode");
            if (accessCode != null) {
                LOGGER.debug("We have the following access code: {{}}", accessCode);
                loggedIn = executeLogin(request, response);
            }

            // If we're not logged in we need to start an AuthenticationRequest flow.
            if (!loggedIn) {
                // We need to distinguish between requests from an API client and from the GWT front-end.
                // If a request is from the GWT front-end and fails authentication then we need to redirect to the login page.
                // If a request is from an API client and fails authentication then we need to return HTTP 403 UNAUTHORIZED.
                final String servletPath = request.getServletPath();
                final boolean isApiRequest = servletPath.contains("/api");

                if (isApiRequest) {
                    LOGGER.debug("API request is unauthorised.");
                    response.setStatus(Response.Status.UNAUTHORIZED.getStatusCode());
                } else {
                    // We have a a new request so we're going to redirect with an AuthenticationRequest.
                    final String authenticationRequestBaseUrl = authenticationServiceUrl + "/authentication/v1/authenticate";
                    final String nonceHash = nonceManager.createNonce(sessionId);

                    final String authenticationRequestParams = "" +
                            "?scope=openid" +
                            "&response_type=code" +
                            "&client_id=stroom" +
                            "&redirect_url=" +
                            advertisedStroomUrl +
                            "&state=" + // TODO Not yet sure what's needed here
                            "&nonce=" +
                            nonceHash;

                    String authenticationRequestUrl = authenticationRequestBaseUrl + authenticationRequestParams;
                    LOGGER.info("Redirecting with an AuthenticationRequest to: {}", authenticationRequestUrl);
                    // We want to make sure that the client has the cookie.
                    response.sendRedirect(authenticationRequestUrl);
                }
            }
        }

        return loggedIn;
    }

    private boolean executeLogin(ServletRequest request, ServletResponse response) {
        try {
            final AuthenticationToken authenticationToken = createToken(request, response);
            return authenticationService.login(authenticationToken);
        } catch (final MalformedClaimException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return false;
    }

    /**
     * This method must create the token used by Shiro.
     * It does this by enacting the OpenId exchange of accessCode for idToken.
     */
    private AuthenticationToken createToken(ServletRequest request, ServletResponse response) throws MalformedClaimException {
        // First we'll check if this is an API request. If it is we'll check the JWS and create a token.
        String path = ((HttpServletRequest) request).getRequestURI();
        if (path.contains("/api/")) {
            Optional<String> optionalJws = jwtService.getJws(request);
            if (optionalJws.isPresent()) {
                Optional<JwtClaims> jwtClaimsOptional = jwtService.verifyToken(optionalJws.get());
                if (jwtClaimsOptional.isPresent()) {
                    return new AuthenticationToken(jwtClaimsOptional.get().getSubject(), optionalJws.get());
                } else {
                    return null;
                }
            } else {
                LOGGER.error("Cannot get a JWS for an API request!");
                return null;
            }
        }

        String sessionId = ((HttpServletRequest) request).getSession().getId();

        // If we're not dealing with an API request we'll assume we're completing the AuthenticationRequest
        // flow, i.e. we have an access code and we need to get an idToken.

        String accessCode = request.getParameter("accessCode");

        // TODO: check the optionals and handle empties.
        if (accessCode != null) {
            String idToken = null;
            try {
                idToken = authenticationServiceClients.newAuthenticationApi().getIdToken(accessCode);
            } catch (ApiException e) {
                if (e.getCode() == Response.Status.UNAUTHORIZED.getStatusCode()) {
                    LOGGER.error("The accessCode used to obtain an idToken was rejected. Has it already been used?", e);
                    // If we can't exchange the accessCode for an idToken then this probably means the
                    // accessCode doesn't exist any more, or has already been used. so we can't proceed.
                    // But we still need to return a token. We'll return an empty one and it'll end up being judged
                    // invalid and the authentication flow will begin again, leading to a fresh, correct token.
                    return null;
                }
            }


            Optional<JwtClaims> jwtClaimsOptional = jwtService.verifyToken(idToken);
            if (!jwtClaimsOptional.isPresent()) {
                return null;
            }
            String nonceHash = (String) jwtClaimsOptional.get().getClaimsMap().get("nonce");
            boolean doNoncesMatch = nonceManager.match(sessionId, nonceHash);
            if (!doNoncesMatch) {
                LOGGER.info("Received a bad nonce!");
                // If the nonces don't match we need to redirect to log in again.
                // Maybe the request uses an out-of-date stroomSessionId?
                // We still need to return a token. We'll return an empty one and it'll end up being judged
                // invalid and the authentication flow will begin again, leading to a fresh, correct token.
                return null;
            }

            LOGGER.info("User is authenticated for sessionId " + sessionId);
            // The user is authenticated now.
            nonceManager.forget(sessionId);
            return new AuthenticationToken(jwtClaimsOptional.get().getSubject(), idToken);
        }

        LOGGER.error("Attempted access without an access code!");
        // We still need to return a token. We'll return an empty one and it'll end up being judged
        // invalid and the authentication flow will begin again, leading to a fresh, correct token.
        return null;
    }

    @Override
    public void destroy() {
    }
}
