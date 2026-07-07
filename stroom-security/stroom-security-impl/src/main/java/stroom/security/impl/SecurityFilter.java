/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.UserIdentitySessionUtil;
import stroom.util.authentication.HasExpiry;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.SessionUtil;
import stroom.util.servlet.UserAgentSessionUtil;
import stroom.util.shared.AuthenticationBypassChecker;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.Optional;

/**
 * Filter to avoid posts to the wrong place (e.g. the root of the app)
 */
@Singleton
class SecurityFilter implements Filter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SecurityFilter.class);

    private final SecurityContext securityContext;
    private final OpenIdManager openIdManager;
    private final AuthenticationBypassChecker authenticationBypassChecker;

    private static final String CSRF_HEADER = "X-CSRF";
    private static final String CSRF_EXPECTED_VALUE = "1";

    @Inject
    SecurityFilter(
            final SecurityContext securityContext,
            final OpenIdManager openIdManager,
            final AuthenticationBypassChecker authenticationBypassChecker) {
        this.securityContext = securityContext;
        this.openIdManager = openIdManager;
        this.authenticationBypassChecker = authenticationBypassChecker;
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        LOGGER.debug("Initialising {}", this.getClass().getSimpleName());
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        if (!(response instanceof final HttpServletResponse httpServletResponse)) {
            final String message = "Unexpected response type: " + response.getClass().getName();
            LOGGER.error(message);
            return;
        }

        if (!(request instanceof final HttpServletRequest httpServletRequest)) {
            final String message = "Unexpected request type: " + request.getClass().getName();
            LOGGER.error(message);
            httpServletResponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, message);
            return;
        }

        try {
            filter(httpServletRequest, httpServletResponse, chain);
        } catch (final AuthenticationException e) {
            // Return a sensible HTTP code for auth failures
            httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        }
    }

    private void filter(final HttpServletRequest request,
                        final HttpServletResponse response,
                        final FilterChain chain)
            throws IOException, ServletException {

        LOGGER.debug(() ->
                LogUtil.message("Filtering request uri: {}, servletPath: {}, servletName: {}, " +
                                "session: {}, getQueryString: '{}'",
                        request.getRequestURI(),
                        request.getServletPath(),
                        NullSafe.get(request.getHttpServletMapping(), HttpServletMapping::getServletName),
                        SessionUtil.getSessionId(request),
                        request.getQueryString()));

        // Log the request for debug purposes.
        RequestLog.log(request);

        final String servletPath = request.getServletPath();
        final String fullPath = request.getRequestURI();
        final String servletName = NullSafe.get(request.getHttpServletMapping(), HttpServletMapping::getServletName);

        if (HttpMethod.OPTIONS.equalsIgnoreCase(request.getMethod())) {
            // We need to allow CORS preflight requests
            LOGGER.debug("Passing on OPTIONS request to next filter, servletName: {}, fullPath: {}, servletPath: {}",
                    servletName, fullPath, servletPath);
            chain.doFilter(request, response);
        } else if (isStaticResource(fullPath, servletPath, servletName)) {
            chain.doFilter(request, response);
        } else if (shouldBypassAuthentication(request, fullPath, servletPath, servletName)) {
            LOGGER.debug("Running as proc user for unauthenticated resource, servletName: {}, " +
                         "fullPath: {}, servletPath: {}", servletName, fullPath, servletPath);
            // Some paths don't need authentication. If that is the case then proceed as proc user.
            securityContext.asProcessingUser(() ->
                    process(request, response, chain));
        } else {
            // First see if a previous call has placed a userIdentity in session
            Optional<UserIdentity> optUserIdentity = UserIdentitySessionUtil.getUserFromSession(
                    SessionUtil.getExistingSession(request));
            logUserIdentityToDebug(optUserIdentity, fullPath, servletPath, "from session");

            // Check if the underlying claims/token have expired. The expiry time of some impls
            // may get refreshed over time, so we may never hit it. When code flow is handled by
            // AWS ALB we will expire, so will just get the latest token from headers which the
            // ALB will be refreshing.
            optUserIdentity = optUserIdentity.map(userIdentity -> {
                if (userIdentity instanceof final HasExpiry hasExpiry) {
                    if (hasExpiry.hasExpired()) {
                        LOGGER.info("UserIdentity {} obtained from session has expired, expiry: {}. " +
                                    "Will attempt to re-authenticate using headers or will initiate code-flow.",
                                userIdentityToString(userIdentity), hasExpiry.getExpireTime());
                        // Clear the identity, so we have to re-acquire it from headers or code flow
                        return null;
                    } else {
                        LOGGER.debug(() -> LogUtil.message("UserIdentity {} obtained from session expires in {}",
                                userIdentityToString(userIdentity), hasExpiry.getTimeTilExpired()));
                    }
                }
                return userIdentity;
            });

            // Track whether the identity was obtained from a session cookie (vs a request token).
            // CSRF protection is only needed for cookie-based auth because the browser automatically
            // attaches cookies to cross-origin requests. API keys and Bearer tokens are not
            // automatically attached, so they are not vulnerable to CSRF.
            final boolean identityFromSession = optUserIdentity.isPresent();

            // API requests that are not from the front-end should have a token.
            // Also requests from an AWS ALB will have an ALB signed token containing the claims
            if (optUserIdentity.isEmpty()) {
                optUserIdentity = openIdManager.loginWithRequestToken(request);
                logUserIdentityToDebug(optUserIdentity, fullPath, servletPath, "from request token");
            }

            if (optUserIdentity.isPresent()) {
                final UserIdentity userIdentity = optUserIdentity.get();

                // Now we have the session make note of the user-agent for logging and sessionListServlet duties
                UserAgentSessionUtil.setUserAgentInSession(request);

                // CSRF check — only for session/cookie-based identity.
                // API key / Bearer token requests are not vulnerable to CSRF because the
                // browser does not automatically attach Authorization headers cross-origin.
                if (identityFromSession && !isCsrfValid(request)) {
                    LOGGER.warn("Rejecting request due to missing CSRF header: {} {}",
                            request.getMethod(), fullPath);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }

                // Now handle the request as this user
                securityContext.asUser(userIdentity, () ->
                        process(request, response, chain));
            } else {
                // If we couldn't log in with a token or couldn't get a token then error as this is an API call
                // or no login flow is possible/expected.
                LOGGER.debug("No user identity so responding with UNAUTHORIZED for servletName: {}, " +
                             "fullPath: {}, servletPath: {}", servletName, fullPath, servletPath);
                response.setStatus(Response.Status.UNAUTHORIZED.getStatusCode());
            }
        }
    }


    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private void logUserIdentityToDebug(final Optional<UserIdentity> optUserIdentity,
                                        final String fullPath,
                                        final String servletName,
                                        final String msg) {
        LOGGER.debug(() -> LogUtil.message("User identity ({}): {}, fullPath: {}, servletName: {}",
                msg,
                optUserIdentity.map(this::userIdentityToString)
                        .orElse("<empty>"),
                fullPath,
                servletName));
    }

    private String userIdentityToString(final UserIdentity userIdentity) {
        if (userIdentity == null) {
            return "";
        } else {
            final String id = userIdentity.getDisplayName() != null
                    ? userIdentity.subjectId() + " (" + userIdentity.getDisplayName() + ")"
                    : userIdentity.subjectId();
            return LogUtil.message("'{}' {}",
                    id,
                    userIdentity.getClass().getSimpleName());
        }
    }


    private boolean isStaticResource(final String fullPath,
                                     final String servletPath,
                                     final String servletName) {
        // Test for internal IdP sign in request.
        if (ResourcePaths.UI_SERVLET_NAME.equals(servletName)
            || ResourcePaths.SIGN_IN_SERVLET_NAME.equals(servletName)
            || ResourcePaths.STROOM_SERVLET_NAME.equals(servletName)) {
            LOGGER.debug("Unauthenticated static content, servletName: {}, fullPath: {}, servletPath: {}",
                    servletName, fullPath, servletPath);
            return true;
        } else {
            return false;
        }
    }

    private boolean shouldBypassAuthentication(final HttpServletRequest servletRequest,
                                               final String fullPath,
                                               final String servletPath,
                                               final String servletName) {
        final boolean shouldBypass;
        if (servletPath == null) {
            shouldBypass = false;
        } else {
            shouldBypass = authenticationBypassChecker.isUnauthenticated(servletName, servletPath, fullPath);
        }
        return shouldBypass;
    }

    /**
     * Check for the presence of a custom CSRF header on state-changing requests.
     * Browsers prevent cross-site scripts from adding custom headers to requests,
     * so the presence of this header proves the request originated from same-origin
     * JavaScript code (our UI), not from a cross-site form submission or link.
     */
    private boolean isCsrfValid(final HttpServletRequest request) {
        final String method = request.getMethod();
        // GET, OPTIONS, and HEAD are safe methods — no CSRF check needed
        if (HttpMethod.GET.equalsIgnoreCase(method)
                || HttpMethod.OPTIONS.equalsIgnoreCase(method)
                || HttpMethod.HEAD.equalsIgnoreCase(method)) {
            return true;
        }
        // For state-changing methods (POST, PUT, DELETE, PATCH), require the header
        return CSRF_EXPECTED_VALUE.equals(request.getHeader(CSRF_HEADER));
    }

    private void process(final HttpServletRequest request,
                         final HttpServletResponse response,
                         final FilterChain chain) {
        try {
            chain.doFilter(request, response);
        } catch (final IOException | ServletException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void destroy() {
    }

    private Optional<HttpSession> ensureSessionIfCookiePresent(final HttpServletRequest request) {
        if (SessionUtil.requestHasSessionCookie(request)) {
            final HttpSession session = SessionUtil.getOrCreateSession(request, newSession ->
                    LOGGER.debug(() -> LogUtil.message(
                            "ensureSessionIfCookiePresent() - Created new session {}, request URL",
                            SessionUtil.getSessionId(newSession), request.getRequestURI())));
            return Optional.of(session);
        }
        return Optional.empty();
    }
}
