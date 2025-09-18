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

import stroom.config.common.UriFactory;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.UserIdentitySessionUtil;
import stroom.security.impl.OpenIdManager.RedirectUrl;
import stroom.security.openid.api.OpenId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.net.UrlUtils;
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
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.Optional;

/**
 * Filter to avoid posts to the wrong place (e.g. the root of the app)
 */
@Singleton
class SecurityFilter implements Filter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SecurityFilter.class);

    private final UriFactory uriFactory;
    private final SecurityContext securityContext;
    private final OpenIdManager openIdManager;
    private final AuthenticationBypassChecker authenticationBypassChecker;

    @Inject
    SecurityFilter(
            final UriFactory uriFactory,
            final SecurityContext securityContext,
            final OpenIdManager openIdManager,
            final AuthenticationBypassChecker authenticationBypassChecker) {
        this.uriFactory = uriFactory;
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
        } else {
            // Api requests that are not from the front-end should have a token.
            // Also request from an AWS ALB will have an ALB signed token containing the claims
            // Need to do this first, so we get a fresh token from AWS ALB rather than using a stale
            // one from session.
            Optional<UserIdentity> optUserIdentity = openIdManager.loginWithRequestToken(request);

            // Log current user.
            if (LOGGER.isDebugEnabled()) {
                logUserIdentityToDebug(
                        optUserIdentity, fullPath, "after trying to login with request token");
            }

            // If no user from header token, see if we have one in session already.
            if (optUserIdentity.isEmpty()) {
                optUserIdentity = UserIdentitySessionUtil.get(SessionUtil.getExistingSession(request));
                if (LOGGER.isDebugEnabled()) {
                    logUserIdentityToDebug(optUserIdentity, fullPath, "from session");
                }
            }

            if (optUserIdentity.isPresent()) {
                final UserIdentity userIdentity = optUserIdentity.get();

                // Now we have the session make note of the user-agent for logging and sessionListServlet duties
                UserAgentSessionUtil.setUserAgentInSession(request);

                // Now handle the request as this user
                securityContext.asUser(userIdentity, () ->
                        process(request, response, chain));

            } else if (shouldBypassAuthentication(request, fullPath, servletPath, servletName)) {
                LOGGER.debug("Running as proc user for unauthenticated servletName: {}, " +
                             "fullPath: {}, servletPath: {}", servletName, fullPath, servletPath);
                // Some paths don't need authentication. If that is the case then proceed as proc user.
                securityContext.asProcessingUser(() ->
                        process(request, response, chain));

//            } else if (isApiRequest(servletPath)) {
            } else if (Objects.equals(ResourcePaths.STROOM_SERVLET_NAME, servletName)) {
                doOpenIdFlow(request, response, fullPath);
            } else {
                // If we couldn't log in with a token or couldn't get a token then error as this is an API call
                // or no login flow is possible/expected.
                LOGGER.debug("No user identity so responding with UNAUTHORIZED for servletName: {}, " +
                             "fullPath: {}, servletPath: {}", servletName, fullPath, servletPath);
                response.setStatus(Response.Status.UNAUTHORIZED.getStatusCode());
            }
        }
    }

    private void doOpenIdFlow(final HttpServletRequest request,
                              final HttpServletResponse response,
                              final String fullPath) throws IOException {
        // No identity found and not an unauthenticated servlet/api so assume it is
        // a UI request. Thus instigate an OpenID authentication flow
        try {
            final String postAuthRedirectUri = getPostAuthRedirectUri(request);
            final String code = UrlUtils.getLastParam(request, OpenId.CODE);
            final String stateId = UrlUtils.getLastParam(request, OpenId.STATE);
            final RedirectUrl redirectUri = openIdManager.redirect(
                    request, code, stateId, postAuthRedirectUri);
            LOGGER.debug("Doing code flow postAuthRedirectUri: {}, code: {}, stateId: {}, redirectUri: {}",
                    postAuthRedirectUri, code, stateId, redirectUri);
            // HTTP 1.1.
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            // HTTP 1.0.
            response.setHeader("Pragma", "no-cache");
            // Proxies.
            response.setHeader("Expires", "0");

            switch (redirectUri.redirectMode()) {
                case REFRESH -> {
                    // If the session has only just been created, and we do a normal redirect,
                    // then the browser will never get the session cookie as the response is terminated
                    // for the redirect. Thus, the session ID will not be known at the redirect URI.
                    // A refresh will ensure the cookie is set.
                    LOGGER.debug("Responding with a http-equiv refresh to {}", redirectUri);
                    response.setContentType(ContentType.TEXT_HTML.getMimeType());
                    try (final PrintWriter responseWriter = response.getWriter()) {
                        responseWriter.print("<html>");
                        responseWriter.print("<head>");
                        responseWriter.print("<meta http-equiv=\"refresh\" content=\"0; URL='");
                        responseWriter.print(redirectUri.redirectUrl());
                        responseWriter.print("'\" />");
                        responseWriter.print("</html>");
                        responseWriter.print("</head>");
                    }
                }
                case REDIRECT -> {
                    // Do a standard http redirect, e.g. to the IDP
                    final String url = redirectUri.redirectUrl();
                    LOGGER.debug("Code flow UI request so redirecting to:, " +
                                 "redirectUri: {}, url: {}, postAuthRedirectUri: {}, path: {}",
                            redirectUri, url, postAuthRedirectUri, fullPath);

                    response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
                    response.setHeader("Location", url);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private void logUserIdentityToDebug(final Optional<UserIdentity> optUserIdentity,
                                        final String fullPath,
                                        final String msg) {
        LOGGER.debug("User identity ({}): {} path: {}",
                msg,
                optUserIdentity.map(
                                identity -> {
                                    final String id = identity.getDisplayName() != null
                                            ? identity.getSubjectId() + " (" + identity.getDisplayName() + ")"
                                            : identity.getSubjectId();
                                    return LogUtil.message("'{}' {}",
                                            id,
                                            identity.getClass().getSimpleName());
                                })
                        .orElse("<empty>"),
                fullPath);
    }

    private String getPostAuthRedirectUri(final HttpServletRequest request) {
        // We have a new request, so we're going to redirect with an AuthenticationRequest.
        // Get the redirect URL for the auth service from the current request.
        final String originalPath = request.getRequestURI() + Optional.ofNullable(request.getQueryString())
                .map(queryStr -> "?" + queryStr)
                .orElse("");

        // Dropwiz is likely sat behind Nginx with requests reverse proxied to it,
        // so we need to append just the path/query part to the public URI defined in config
        // rather than using the full url of the request
        return uriFactory.publicUri(originalPath).toString();
    }

    private boolean isStaticResource(final String fullPath,
                                     final String servletPath,
                                     final String servletName) {
        // Test for internal IdP sign in request.
        if (ResourcePaths.UI_SERVLET_NAME.equals(servletName)
            || ResourcePaths.SIGN_IN_SERVLET_NAME.equals(servletName)) {
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

        if (LOGGER.isDebugEnabled()) {
            if (shouldBypass) {
                LOGGER.debug("Bypassing authentication for servletName: {}, fullPath: {}, servletPath: {}",
                        NullSafe.get(
                                servletRequest.getHttpServletMapping(),
                                HttpServletMapping::getServletName),
                        fullPath,
                        servletPath);
            }
        }
        return shouldBypass;
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
