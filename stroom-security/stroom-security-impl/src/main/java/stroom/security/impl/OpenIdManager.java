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
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.AuthenticationState;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.SessionUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

class OpenIdManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenIdManager.class);

    private final OpenIdConfiguration openIdConfiguration;
    // We have to use the stroom specific one as only that one has the code flow
    private final StroomUserIdentityFactory userIdentityFactory;
    private final AuthenticationStateCache authenticationStateCache;
    private final UriFactory uriFactory;

    @Inject
    public OpenIdManager(final OpenIdConfiguration openIdConfiguration,
                         final StroomUserIdentityFactory userIdentityFactory,
                         final AuthenticationStateCache authenticationStateCache,
                         final UriFactory uriFactory) {
        this.openIdConfiguration = openIdConfiguration;
        this.userIdentityFactory = userIdentityFactory;
        this.authenticationStateCache = authenticationStateCache;
        this.uriFactory = uriFactory;
    }

    public RedirectUrl redirect(final HttpServletRequest request,
                                final String code,
                                final String stateId,
                                final String postAuthRedirectUri) {
        RedirectUrl redirectUri = null;
        LOGGER.debug("redirect() - requestURI: {}, code: {}, stateId: {}, postAuthRedirectUri: {}",
                request.getRequestURI(),
                code,
                stateId,
                postAuthRedirectUri);

        // Retrieve state if we have a state id param.
        final Optional<AuthenticationState> optionalState = getState(stateId);

        // If we have completed the front channel flow then we will have a code and state.
        if (code != null && optionalState.isPresent()) {
            redirectUri = backChannelOIDC(request, code, optionalState.get());
        }

        // If we aren't doing back channel check yet or the back channel check failed then proceed with front channel.
        if (redirectUri == null) {
            // Restore the initiating URI as needed for logout.
            final String url = optionalState
                    .map(state -> frontChannelOIDC(state.getInitiatingUri(), state.isPrompt()))
                    .orElse(frontChannelOIDC(postAuthRedirectUri, false));
            redirectUri = RedirectUrl.create(url);
        }

        LOGGER.debug("redirect() - redirectUri: {}", redirectUri);
        return redirectUri;
    }

    private Optional<AuthenticationState> getState(final String stateId) {
        if (stateId == null) {
            return Optional.empty();
        }

        // Check the state is one we requested.
        final Optional<AuthenticationState> optionalState = authenticationStateCache.getAndRemove(stateId);
        if (optionalState.isEmpty()) {
            LOGGER.debug("Unable to find state {}", stateId);
        } else {
            LOGGER.debug("Found state {} {}", stateId, optionalState.get());
        }
        return optionalState;
    }

    private String frontChannelOIDC(final String postAuthRedirectUri,
                                    final boolean prompt) {
        final String endpoint = openIdConfiguration.getAuthEndpoint();
        final String clientId = openIdConfiguration.getClientId();
        Objects.requireNonNull(endpoint,
                "To make an authentication request the OpenId config 'authEndpoint' must not be null");
        Objects.requireNonNull(clientId,
                "To make an authentication request the OpenId config 'clientId' must not be null");
        // Create a state for this authentication request.
        final AuthenticationState state = authenticationStateCache.create(postAuthRedirectUri, prompt);
        LOGGER.debug(() -> "frontChannelOIDC state: " + state);
        return createAuthUri(endpoint, clientId, state);
    }

    private RedirectUrl backChannelOIDC(final HttpServletRequest request,
                                        final String code,
                                        final AuthenticationState state) {
        Objects.requireNonNull(code, "Null code");
        RedirectUrl redirectUri;

        // If we have a state id then this should be a return from the auth service.
        LOGGER.debug(() -> LogUtil.message("backChannelOIDC() - state: {}, sessionId: {}, cookies: {}",
                state,
                NullSafe.get(request.getSession(false), HttpSession::getId),
                request.getCookies()));

        try {
            final Optional<UserIdentity> optionalUserIdentity =
                    userIdentityFactory.getAuthFlowUserIdentity(request, code, state);

            LOGGER.debug("backChannelOIDC() - optionalUserIdentity after back channel auth: {}", optionalUserIdentity);

            if (optionalUserIdentity.isPresent()) {
                // Set the token in the session so that when we re-direct to the initiating page (i.e. '/')
                // we will have the identity in session so won't go back round the code flow loop

                // Successful login, so redirect to the original URL held in the state.
                final String uri = state.getInitiatingUri();
                LOGGER.debug(() -> LogUtil.message(
                        "backChannelOIDC() - Using browser refresh to redirect to initiating URI: {}", uri));
                redirectUri = RedirectUrl.createWithRefresh(uri);
            } else {
                redirectUri = RedirectUrl.create(createErrorUri("Authentication failed"));
                LOGGER.debug("backChannelOIDC() - No userIdentity so redirect to error page: {}", redirectUri);
            }
        } catch (final Exception e) {
            LOGGER.debug("backChannelOIDC() - Error: {}", LogUtil.exceptionMessage(e), e);
            redirectUri = RedirectUrl.create(createErrorUri(e.getMessage()));
        }

        return redirectUri;
    }

    /**
     * This method attempts to get a token from the request headers and, if present, use that to login.
     */
    public Optional<UserIdentity> loginWithRequestToken(final HttpServletRequest request) {
        LOGGER.debug(() -> LogUtil.message("loginWithRequestToken() - session: {}",
                SessionUtil.getSessionId(request)));
        if (userIdentityFactory.hasAuthenticationToken(request)) {
            final Optional<UserIdentity> optApiUserIdentity = userIdentityFactory.getApiUserIdentity(request);
            if (LOGGER.isDebugEnabled()) {
                final UserIdentity userIdentity = optApiUserIdentity.get();
                LOGGER.debug("loginWithRequestToken() - Returning {} {}",
                        LogUtil.getSimpleClassName(userIdentity),
                        userIdentity);
            }
            return optApiUserIdentity;
        } else {
            LOGGER.trace("No token on request. This is valid for API calls from the front-end");
            return Optional.empty();
        }
    }

    public String logout(final String postAuthRedirectUri) {
        final String endpoint = openIdConfiguration.getLogoutEndpoint();
        final String clientId = openIdConfiguration.getClientId();
        Objects.requireNonNull(endpoint,
                "To make a logout request the OpenId config 'logoutEndpoint' must not be null");
        Objects.requireNonNull(clientId,
                "To make an authentication request the OpenId config 'clientId' must not be null");
        final AuthenticationState state = authenticationStateCache.create(postAuthRedirectUri, true);
        LOGGER.debug(() -> "logout state=" + state);
        return createLogoutUri(endpoint, clientId, state);
    }

    private String createAuthUri(final String endpoint,
                                 final String clientId,
                                 final AuthenticationState state) {
//                                final boolean isLogout) {

        // In some cases we might need to use an external URL as the current incoming one might have been proxied.
        // Use OIDC API.
        UriBuilder uriBuilder = UriBuilder.fromUri(endpoint);
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.RESPONSE_TYPE, OpenId.CODE);
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.CLIENT_ID, clientId);

//        final String redirectParamName = isLogout
//                ? openIdConfiguration.getLogoutRedirectParamName()
//                : OpenId.REDIRECT_URI;
//        uriBuilder = UriBuilderUtil.addParam(
//                uriBuilder,
//                redirectParamName,
//                state.getUri());
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.REDIRECT_URI, state.getRedirectUri());

        final List<String> requestScopes = openIdConfiguration.getRequestScopes();
        if (NullSafe.hasItems(requestScopes)) {
            uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.SCOPE, String.join(" ", requestScopes));
        }

        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.STATE, state.getId());
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.NONCE, state.getNonce());

        // Determine if we want to force login regardless of IDP auth state.
        if (state.isPrompt()) {
            uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.PROMPT, OpenId.LOGIN_PROMPT);
        }

        final String authenticationRequestUrl = uriBuilder.build().toString();
        LOGGER.info(() -> "Redirecting with an AuthenticationRequest to: " + authenticationRequestUrl);
        // We want to make sure that the client has the cookie.
        return authenticationRequestUrl;
    }

    private String createLogoutUri(final String endpoint,
                                   final String clientId,
                                   final AuthenticationState state) {
        UriBuilder redirect = UriBuilder.fromUri(state.getRedirectUri());
        redirect = UriBuilderUtil.addParam(redirect, OpenId.STATE, state.getId());

        UriBuilder uriBuilder = UriBuilder.fromUri(endpoint);
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.CLIENT_ID, clientId);
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.POST_LOGOUT_REDIRECT_URI, redirect.build().toString());
        final String uriStr = uriBuilder.build().toString();
        LOGGER.debug("Sending user to logout screen with uri: {}", uriStr);
        return uriStr;
    }

    private String createErrorUri(final String message) {
        final URI uri = uriFactory.uiUri(ResourcePaths.buildServletPath(ResourcePaths.SIGN_IN_PATH));
        UriBuilder uriBuilder = UriBuilder.fromUri(uri);
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, "error", message);
        final String uriStr = uriBuilder.build().toString();
        LOGGER.debug("Sending user to error screen with uri: {}", uriStr);
        return uriStr;
    }


    // --------------------------------------------------------------------------------


    public record RedirectUrl(String redirectUrl, RedirectMode redirectMode) {

        public RedirectUrl(final String redirectUrl, final RedirectMode redirectMode) {
            this.redirectUrl = redirectUrl;
            this.redirectMode = Objects.requireNonNullElse(redirectMode, RedirectMode.REDIRECT);
        }

        static RedirectUrl create(final String url) {
            return new RedirectUrl(url, RedirectMode.REDIRECT);
        }

        static RedirectUrl createWithRefresh(final String url) {
            return new RedirectUrl(url, RedirectMode.REFRESH);
        }
    }


    // --------------------------------------------------------------------------------


    public enum RedirectMode {
        REDIRECT,
        REFRESH,
        ;
    }
}
