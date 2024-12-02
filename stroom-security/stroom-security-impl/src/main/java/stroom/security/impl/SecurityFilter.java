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
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.openid.api.OpenId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.net.UrlUtils;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ServletAuthenticationChecker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
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
import javax.ws.rs.core.Response.Status;

/**
 * Filter to avoid posts to the wrong place (e.g. the root of the app)
 */
@Singleton
class SecurityFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityFilter.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SecurityFilter.class);

    private static final Set<String> STATIC_RESOURCE_EXTENSIONS = Set.of(
            ".js", ".css", ".htm", ".html", ".json", ".png", ".jpg", ".gif", ".ico", ".svg", ".ttf", ".woff", ".woff2");

    private final Provider<AuthenticationConfig> authenticationConfigProvider;
    private final UriFactory uriFactory;
    private final SecurityContext securityContext;
    private final OpenIdManager openIdManager;
    private final ServletAuthenticationChecker servletAuthenticationChecker;

    @Inject
    SecurityFilter(
            final Provider<AuthenticationConfig> authenticationConfigProvider,
            final UriFactory uriFactory,
            final SecurityContext securityContext,
            final OpenIdManager openIdManager,
            final ServletAuthenticationChecker servletAuthenticationChecker) {
        this.authenticationConfigProvider = authenticationConfigProvider;
        this.uriFactory = uriFactory;
        this.securityContext = securityContext;
        this.openIdManager = openIdManager;
        this.servletAuthenticationChecker = servletAuthenticationChecker;
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

        try {
            filter(httpServletRequest, httpServletResponse, chain);
        } catch (AuthenticationException e) {
            // Return a sensible HTTP code for auth failures
            httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        }
    }

    private void filter(final HttpServletRequest request,
                        final HttpServletResponse response,
                        final FilterChain chain)
            throws IOException, ServletException {
        LAMBDA_LOGGER.debug(() ->
                LogUtil.message("Filtering request uri: {},  servletPath: {}",
                        request.getRequestURI(), request.getServletPath()));

        // Log the request for debug purposes.
        RequestLog.log(request);

        final String servletPath = request.getServletPath().toLowerCase();
        final String fullPath = request.getRequestURI().toLowerCase();

        if (request.getMethod().equalsIgnoreCase(HttpMethod.OPTIONS) ||
                (!isApiRequest(servletPath) && isStaticResource(request))) {
            // We need to allow CORS preflight requests
            LOGGER.debug("Passing on to next filter");
            chain.doFilter(request, response);

        } else {
            LAMBDA_LOGGER.debug(() -> LogUtil.message("Session ID {}, request URI {}",
                    Optional.ofNullable(request.getSession(false))
                            .map(HttpSession::getId)
                            .orElse("-"),
                    request.getRequestURI() + Optional.ofNullable(request.getQueryString())
                            .map(str -> "/" + str)
                            .orElse("")));

            if (!authenticationConfigProvider.get().isAuthenticationRequired()) {
                // If authentication is turned off then proceed as admin.
                final String propPath = authenticationConfigProvider.get().getFullPathStr(
                        AuthenticationConfig.PROP_NAME_AUTHENTICATION_REQUIRED);
                LOGGER.debug("{} is false, authenticating as admin for {}", propPath, fullPath);
                securityContext.asAdminUser(() -> {
                    // Set the user ref in the session.
                    openIdManager.getOrSetSessionUser(request, Optional.of(securityContext.getUserIdentity()));
                    process(request, response, chain);
                });

            } else {
                // Try to get a token from the request for login.
                Optional<UserIdentity> userIdentity = openIdManager.loginWithRequestToken(request);
                userIdentity = openIdManager.getOrSetSessionUser(request, userIdentity);
                if (userIdentity.isPresent()) {
                    securityContext.asUser(userIdentity.get(), () -> process(request, response, chain));

                } else if (shouldBypassAuthentication(fullPath, servletPath)) {
                    // Some paths don't need authentication. If that is the case then proceed as proc user.
                    securityContext.asProcessingUser(() ->
                            process(request, response, chain));

                } else if (isApiRequest(servletPath)) {
                    // If we couldn't login with a token or couldn't get a token then error as this is an API call
                    // or no login flow is possible/expected.
                    LOGGER.debug("API request is unauthorised.");
                    response.setStatus(Response.Status.UNAUTHORIZED.getStatusCode());

                } else if (request.getRequestURI().equals("/")) {
                    // UI request, so instigate an OpenID authentication flow
                    try {
                        final String postAuthRedirectUri = getPostAuthRedirectUri(request);

                        LOGGER.debug("Using postAuthRedirectUri: {}", postAuthRedirectUri);

                        final String code = UrlUtils.getLastParam(request, OpenId.CODE);
                        final String stateId = UrlUtils.getLastParam(request, OpenId.STATE);
                        final String redirectUri = openIdManager.redirect(request, code, stateId, postAuthRedirectUri);
//                        response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
//                        response.sendRedirect(redirectUri);
                        // HTTP 1.1.
                        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                        // HTTP 1.0.
                        response.setHeader("Pragma", "no-cache");
                        // Proxies.
                        response.setHeader("Expires", "0");

                        response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
                        response.setHeader("Location", redirectUri);

                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                        throw e;
                    }
                } else {
                    final int statusCode = Status.NOT_FOUND.getStatusCode();
                    LOGGER.debug("Unexpected URI {}, returning {}", request.getRequestURI(), statusCode);
                    response.setStatus(statusCode);
                }
            }
        }
    }

    private String getPostAuthRedirectUri(final HttpServletRequest request) {
        // We have a new request so we're going to redirect with an AuthenticationRequest.
        // Get the redirect URL for the auth service from the current request.
        final String originalPath = request.getRequestURI() + Optional.ofNullable(request.getQueryString())
                .map(queryStr -> "?" + queryStr)
                .orElse("");

        // Dropwiz is likely sat behind Nginx with requests reverse proxied to it
        // so we need to append just the path/query part to the public URI defined in config
        // rather than using the full url of the request
        return uriFactory.publicUri(originalPath).toString();
    }

    private boolean isStaticResource(final HttpServletRequest request) {
        final String url = request.getRequestURL().toString();

        if (url.contains("/s/") ||
                url.contains("/static/") ||
                url.endsWith("manifest.json")) { // New UI - For some reason this is requested without a session cookie
            return true;
        }

        int index = url.lastIndexOf(".");
        if (index > 0) {
            final String extension = url.substring(index);
            return STATIC_RESOURCE_EXTENSIONS.contains(extension);
        }

        return false;
    }

    private boolean isApiRequest(String servletPath) {
        return servletPath.startsWith(ResourcePaths.API_ROOT_PATH);
    }

    private boolean shouldBypassAuthentication(final String fullPath, final String servletPath) {
        if (servletPath == null) {
            return false;
        } else if (fullPath.contains(ResourcePaths.NO_AUTH + "/")) {
            return true;
        } else {
            return servletAuthenticationChecker.isUnauthenticatedPath(servletPath);
        }
    }

//    private void authenticateAsAdmin(final HttpServletRequest request,
//                                     final HttpServletResponse response,
//                                     final FilterChain chain) throws IOException, ServletException {
//
//        bypassAuthentication(request, response, chain, UserIdentitySessionUtil.requestHasSessionCookie(request),
//                securityContext.createIdentity(User.ADMIN_USER_NAME));
//    }

//    private void bypassAuthentication(final HttpServletRequest request,
//                         final HttpServletResponse response,
//                         final FilterChain chain) {
//        try {
//            chain.doFilter(request, response);
//        } catch (final IOException | ServletException e) {
//            throw new RuntimeException(e);
//        }
//    }

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
}
