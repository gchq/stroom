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

package stroom.proxy.app.servlet;

import stroom.proxy.app.ContentSyncConfig;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.event.EventResource;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourcePaths;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;

/**
 * <p>
 * Filter to avoid posts to the wrong place (e.g. the root of the app)
 * </p>
 */
public class ProxySecurityFilter implements Filter {

    private static final String IGNORE_URI_REGEX = "ignoreUri";
    private static final String BEARER = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxySecurityFilter.class);

    private final Provider<ContentSyncConfig> contentSyncConfigProvider;
    private final Provider<FeedStatusConfig> feedStatusConfigProvider;
    private final Provider<ProxyConfig> proxyConfigProvider;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final UserIdentityFactory userIdentityFactory;

    private Pattern pattern = null;

    @Inject
    public ProxySecurityFilter(final Provider<ContentSyncConfig> contentSyncConfigProvider,
                               final Provider<FeedStatusConfig> feedStatusConfigProvider,
                               final Provider<ProxyConfig> proxyConfigProvider,
                               final DefaultOpenIdCredentials defaultOpenIdCredentials,
                               final UserIdentityFactory userIdentityFactory) {
        this.contentSyncConfigProvider = contentSyncConfigProvider;
        this.feedStatusConfigProvider = feedStatusConfigProvider;
        this.proxyConfigProvider = proxyConfigProvider;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.userIdentityFactory = userIdentityFactory;
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        final String regex = filterConfig.getInitParameter(IGNORE_URI_REGEX);
        if (regex != null) {
            pattern = Pattern.compile(regex);
        }
    }

    @Override
    public void doFilter(final ServletRequest request,
                         final ServletResponse response,
                         final FilterChain chain)

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

        filter(httpServletRequest, httpServletResponse, chain);
    }

    private void filter(final HttpServletRequest request,
                        final HttpServletResponse response,
                        final FilterChain chain)

            throws IOException, ServletException {

        final String servletPath = request.getServletPath().toLowerCase();
        final String fullPath = request.getRequestURI().toLowerCase();

        LOGGER.debug("Filtering fullPath: {}, servletPath: {}", fullPath, servletPath);

        // TODO: 05/12/2022 Need to fugure out how we deal with chained proxies where the distant
        //  proxies can only see the downstream and not the IDP.

        if (request.getMethod().equalsIgnoreCase(HttpMethod.OPTIONS)) {
            // We need to allow CORS preflight requests
            chain.doFilter(request, response);

        } else if (ignoreUri(request.getRequestURI())) {
            // Allow some URIs to bypass authentication checks
            chain.doFilter(request, response);

        } else if (shouldBypassAuthentication(fullPath)) {
            // /noauth/ paths skip auth here but may handle it themselves
            chain.doFilter(request, response);

        } else {
            final boolean isApiRequest = fullPath.contains(ResourcePaths.API_ROOT_PATH);

            if (isApiRequest) {
                if (fullPath.contains(EventResource.BASE_RESOURCE_PATH)) {
                    // Allow all event requests through as security is applied elsewhere.
                    chain.doFilter(request, response);
                } else {
                    // All other rest API resources so authenticate them
                    final Optional<UserIdentity> optUserIdentity = userIdentityFactory.getApiUserIdentity(request);

                    if (optUserIdentity.isPresent()) {
                        LOGGER.debug("Authenticated request to fullPath: {}, servletPath: {}, userIdentity: {}",
                                fullPath, servletPath, optUserIdentity.get());
                        chain.doFilter(request, response);

                    } else {
                        LOGGER.debug("Unauthorised request to fullPath: {}, servletPath: {}", fullPath, servletPath);
                        response.setStatus(Response.Status.UNAUTHORIZED.getStatusCode());
                    }
                }
            } else {
                chain.doFilter(request, response);
            }
        }
    }

//    private String getConfiguredApiKey(final String requestUri) {
//        // TODO it could be argued that we should have a single API key to use for all of these resources.
//        final String apiKey;
//        final ProxyConfig proxyConfig = proxyConfigProvider.get();
//        if (requestUri.startsWith(ResourcePaths.API_ROOT_PATH + FeedStatusResource.BASE_RESOURCE_PATH)) {
//            final FeedStatusConfig feedStatusConfig = feedStatusConfigProvider.get();
//            if (proxyConfig.isUseDefaultOpenIdCredentials() && Strings.isNullOrEmpty(feedStatusConfig.getApiKey())) {
//                LOGGER.info("Authenticating using default API key. For production use, set up an API key in Stroom!");
//                apiKey = Objects.requireNonNull(defaultOpenIdCredentials.getApiKey());
//            } else {
//                apiKey = feedStatusConfig.getApiKey();
//            }
//        } else if (requestUri.startsWith(ResourcePaths.API_ROOT_PATH + ReceiveDataRuleSetResource.BASE_RESOURCE_PATH)) {
//            final ContentSyncConfig contentSyncConfig = contentSyncConfigProvider.get();
//            if (proxyConfig.isUseDefaultOpenIdCredentials() && Strings.isNullOrEmpty(contentSyncConfig.getApiKey())) {
//                LOGGER.info("Using default authentication token, should only be used in test/demo environments.");
//                apiKey = Objects.requireNonNull(defaultOpenIdCredentials.getApiKey());
//            } else {
//                apiKey = contentSyncConfig.getApiKey();
//            }
//        } else {
//            throw new RuntimeException(LogUtil.message(
//                    "Unable to determine which config to get API key from for requestURI {}", requestUri));
//        }
//        if (apiKey == null || apiKey.isEmpty()) {
//            throw new RuntimeException(LogUtil.message(
//                    "API key is empty, requestURI {}", requestUri));
//        }
//        return apiKey;
//    }

    private boolean ignoreUri(final String uri) {
        return pattern != null && pattern.matcher(uri).matches();
    }

    private String getJWS(final HttpServletRequest request) {
        final String bearerString = request.getHeader(AUTHORIZATION_HEADER);
        String jws = null;
        if (bearerString != null && !bearerString.isEmpty()) {
            if (bearerString.startsWith(BEARER)) {
                // This chops out 'Bearer' so we get just the token.
                jws = bearerString.substring(BEARER.length());
            } else {
                jws = bearerString;
            }
            LOGGER.debug("Found auth header in request. It looks like this: {}", jws);
        }
        return jws;
    }

    private boolean shouldBypassAuthentication(final String fullPath) {
        return fullPath.contains(ResourcePaths.NO_AUTH + "/");
    }

    @Override
    public void destroy() {
    }
}
