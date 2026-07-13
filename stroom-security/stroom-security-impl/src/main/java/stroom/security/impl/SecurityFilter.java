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

import stroom.config.common.UriFactory;
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
import jakarta.inject.Provider;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Filter to avoid posts to the wrong place (e.g. the root of the app)
 */
@Singleton
class SecurityFilter implements Filter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SecurityFilter.class);

    private final SecurityContext securityContext;
    private final OpenIdManager openIdManager;
    private final AuthenticationBypassChecker authenticationBypassChecker;
    private final Provider<UriFactory> uriFactoryProvider;

    private static final String CSRF_HEADER = "X-CSRF";
    private static final String CSRF_EXPECTED_VALUE = "1";
    private static final String ORIGIN_HEADER = "Origin";
    private static final String REFERER_HEADER = "Referer";

    @Inject
    SecurityFilter(
            final SecurityContext securityContext,
            final OpenIdManager openIdManager,
            final AuthenticationBypassChecker authenticationBypassChecker,
            final Provider<UriFactory> uriFactoryProvider) {
        this.securityContext = securityContext;
        this.openIdManager = openIdManager;
        this.authenticationBypassChecker = authenticationBypassChecker;
        this.uriFactoryProvider = uriFactoryProvider;
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

                // CSRF checks — only for session/cookie-based identity.
                // API key / Bearer token requests are not vulnerable to CSRF because the
                // browser does not automatically attach Authorization headers cross-origin.
                // Two independent, complementary defences are applied for defence in depth:
                //   1. Same-origin verification via the Origin/Referer header (works for both
                //      XHR and native form posts, and covers requests the header check can't).
                //   2. A custom X-CSRF header that browsers forbid cross-origin scripts from setting.
                // Each check logs its own specific rejection reason.
                if (identityFromSession && (!isOriginValid(request) || !isCsrfValid(request))) {
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
    boolean isCsrfValid(final HttpServletRequest request) {
        // GET, OPTIONS, and HEAD are safe methods — no CSRF check needed
        if (isSafeMethod(request)) {
            return true;
        }
        // For state-changing methods (POST, PUT, DELETE, PATCH), require the header
        if (CSRF_EXPECTED_VALUE.equals(request.getHeader(CSRF_HEADER))) {
            return true;
        }
        LOGGER.warn("Rejecting request due to missing CSRF header: {} {}",
                request.getMethod(), request.getRequestURI());
        return false;
    }

    /**
     * Verify that a state-changing request originated from one of our own known origins.
     * <p>
     * The {@code Origin} header (falling back to {@code Referer}) is set by the browser and
     * cannot be spoofed by cross-site JavaScript, so comparing it against the configured
     * public/UI URIs proves the request came from our own front-end. Unlike the custom-header
     * check, this also covers native HTML form posts. If neither header is present we fall back
     * to the {@link #isCsrfValid(HttpServletRequest)} header check (applied separately).
     */
    boolean isOriginValid(final HttpServletRequest request) {
        // GET, OPTIONS, and HEAD are safe methods — no CSRF check needed
        if (isSafeMethod(request)) {
            return true;
        }

        // Prefer the Origin header, falling back to Referer. Both are browser-controlled.
        String originHeader = request.getHeader(ORIGIN_HEADER);
        if (isBlankOrNullLiteral(originHeader)) {
            originHeader = request.getHeader(REFERER_HEADER);
        }

        // No Origin or Referer to check against, so rely on the X-CSRF header check instead.
        if (isBlankOrNullLiteral(originHeader)) {
            return true;
        }

        final String requestOrigin = normaliseOrigin(originHeader);
        final Set<String> allowedOrigins = getAllowedOrigins(request);
        if (requestOrigin != null && allowedOrigins.contains(requestOrigin)) {
            return true;
        }

        LOGGER.warn("Rejecting request due to invalid Origin '{}' (header value: '{}', allowed: {}): {} {}",
                requestOrigin, originHeader, allowedOrigins, request.getMethod(), request.getRequestURI());
        return false;
    }

    private boolean isSafeMethod(final HttpServletRequest request) {
        final String method = request.getMethod();
        return HttpMethod.GET.equalsIgnoreCase(method)
               || HttpMethod.OPTIONS.equalsIgnoreCase(method)
               || HttpMethod.HEAD.equalsIgnoreCase(method);
    }

    /**
     * The set of origins ({@code scheme://host:port}) that we consider to be our own front-end.
     * <p>
     * Includes:
     * <ul>
     *   <li>The host the browser actually connected to (from {@code X-Forwarded-Host}/{@code Host}).
     *       A legitimate same-origin request always has an Origin equal to this, whereas a
     *       cross-site attacker's Origin never does, so this alone is a valid CSRF check and
     *       avoids any dependency on the public URI being configured correctly.</li>
     *   <li>The configured public URI and UI URI, which cover split UI/API deployments where the
     *       UI is served from a different origin than the API (the UI URI falls back to the public
     *       URI, which falls back to the node URI, when not configured).</li>
     * </ul>
     */
    private Set<String> getAllowedOrigins(final HttpServletRequest request) {
        final Set<String> origins = new HashSet<>();

        // The origin the browser connected to, honouring a reverse proxy if present.
        final String requestHostOrigin = getRequestHostOrigin(request);
        if (requestHostOrigin != null) {
            origins.add(requestHostOrigin);
        }

        final UriFactory uriFactory = uriFactoryProvider.get();
        addOrigin(origins, uriFactory.publicUri("/"));
        addOrigin(origins, uriFactory.uiUri("/"));
        return origins;
    }

    /**
     * Derive the origin string for the host the browser connected to, preferring the
     * {@code X-Forwarded-*} headers set by a reverse proxy (e.g. NGINX) over the raw
     * {@code Host}/connector values.
     */
    private String getRequestHostOrigin(final HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (isBlankOrNullLiteral(scheme)) {
            scheme = request.getScheme();
        }

        String host = request.getHeader("X-Forwarded-Host");
        if (isBlankOrNullLiteral(host)) {
            host = request.getHeader("Host");
        }
        if (isBlankOrNullLiteral(host)) {
            host = request.getServerName() + ":" + request.getServerPort();
        }

        if (isBlankOrNullLiteral(scheme) || isBlankOrNullLiteral(host)) {
            return null;
        }
        // A comma-separated X-Forwarded-Host lists the closest proxy last; use the first (client) entry.
        final int commaIndex = host.indexOf(',');
        if (commaIndex != -1) {
            host = host.substring(0, commaIndex).trim();
        }
        return normaliseOrigin(scheme + "://" + host);
    }

    private void addOrigin(final Set<String> origins, final URI uri) {
        final String origin = normaliseOrigin(uri);
        if (origin != null) {
            origins.add(origin);
        }
    }

    /**
     * Normalise a URI to a canonical origin string {@code scheme://host:port}, resolving the
     * default port for the scheme so that e.g. {@code https://example.com} and
     * {@code https://example.com:443} compare equal. Returns null if the value can't be parsed
     * or lacks a scheme/host (e.g. the literal {@code "null"} origin sent by sandboxed contexts).
     */
    private static String normaliseOrigin(final String value) {
        try {
            return normaliseOrigin(new URI(value));
        } catch (final URISyntaxException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to parse origin '{}'", value));
            return null;
        }
    }

    private static String normaliseOrigin(final URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return null;
        }
        final String scheme = uri.getScheme().toLowerCase();
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(scheme)
                    ? 443
                    : 80;
        }
        return scheme + "://" + uri.getHost().toLowerCase() + ":" + port;
    }

    private static boolean isBlankOrNullLiteral(final String value) {
        // NullSafe.isBlankString already treats a null reference (and whitespace) as blank. The
        // additional check is for the literal string "null", i.e. the opaque Origin that browsers
        // send from sandboxed contexts, which we want to treat as absent.
        return NullSafe.isBlankString(value) || "null".equals(value);
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
