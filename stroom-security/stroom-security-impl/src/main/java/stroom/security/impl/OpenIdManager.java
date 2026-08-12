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

import stroom.security.api.UserIdentity;
import stroom.security.common.impl.AuthenticationState;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.Pkce;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.SessionUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.UriBuilder;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

class OpenIdManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenIdManager.class);

    /**
     * OIDC authentication request parameters that stroom sets itself; config-supplied extras must
     * not be able to override them.
     */
    private static final Set<String> RESERVED_AUTH_REQUEST_PARAMS = Set.of(
            OpenId.RESPONSE_TYPE,
            OpenId.CLIENT_ID,
            OpenId.REDIRECT_URI,
            OpenId.SCOPE,
            OpenId.STATE,
            OpenId.NONCE,
            OpenId.CODE_CHALLENGE,
            OpenId.CODE_CHALLENGE_METHOD);

    private final OpenIdConfiguration openIdConfiguration;
    // We have to use the stroom specific one as only that one has the code flow
    private final StroomUserIdentityFactory userIdentityFactory;
    private final AuthenticationStateCache authenticationStateCache;

    @Inject
    public OpenIdManager(final OpenIdConfiguration openIdConfiguration,
                         final StroomUserIdentityFactory userIdentityFactory,
                         final AuthenticationStateCache authenticationStateCache) {
        this.openIdConfiguration = openIdConfiguration;
        this.userIdentityFactory = userIdentityFactory;
        this.authenticationStateCache = authenticationStateCache;
    }

    /**
     * This method attempts to get a token from the request headers and, if present, use that to login.
     */
    public Optional<UserIdentity> loginWithRequestToken(final HttpServletRequest request) {
        return loginWithRequestCredential(request)
                .map(AuthenticatedCredential::identity);
    }

    /**
     * As {@link #loginWithRequestToken(HttpServletRequest)}, but also reporting which kind of
     * credential proved the identity (API key, cluster token, request token), for
     * {@link SecurityFilter}'s CSRF classification.
     */
    public Optional<AuthenticatedCredential> loginWithRequestCredential(final HttpServletRequest request) {
        LOGGER.debug(() -> LogUtil.message("loginWithRequestCredential() - session: {}",
                SessionUtil.getSessionId(request)));
        if (userIdentityFactory.hasAuthenticationToken(request)) {
            final Optional<AuthenticatedCredential> optCredential =
                    userIdentityFactory.getApiCredential(request);
            optCredential.ifPresent(credential ->
                    LOGGER.debug(() -> LogUtil.message(
                            "loginWithRequestCredential() - Returning {} ({}) {}",
                            LogUtil.getSimpleClassName(credential.identity()),
                            credential.source(),
                            credential.identity())));
            return optCredential;
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

    String createAuthUri(final String endpoint,
                                 final String clientId,
                                 final AuthenticationState state) {
        // In some cases we might need to use an external URL as the current incoming one might have been proxied.
        // Use OIDC API.
        UriBuilder uriBuilder = UriBuilder.fromUri(endpoint);
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.RESPONSE_TYPE, OpenId.CODE);
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.CLIENT_ID, clientId);
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.REDIRECT_URI, state.getRedirectUri());

        final List<String> requestScopes = openIdConfiguration.getRequestScopes();
        if (NullSafe.hasItems(requestScopes)) {
            uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.SCOPE, String.join(" ", requestScopes));
        }

        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.STATE, state.getId());
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.NONCE, state.getNonce());

        // PKCE (RFC 7636): send the S256 challenge for this flow's verifier. Sent to every IDP; providers
        // that require PKCE (and OAuth 2.1) need it, and those that do not simply ignore it.
        uriBuilder = UriBuilderUtil.addParam(uriBuilder,
                OpenId.CODE_CHALLENGE, Pkce.createS256Challenge(state.getCodeVerifier()));
        uriBuilder = UriBuilderUtil.addParam(uriBuilder,
                OpenId.CODE_CHALLENGE_METHOD, OpenId.CODE_CHALLENGE_METHOD__S256);

        // Determine if we want to force login regardless of IDP auth state.
        if (state.isPrompt()) {
            uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.PROMPT, OpenId.LOGIN_PROMPT);
        }

        // Provider-specific extras, e.g. Google's 'access_type=offline' without which no refresh
        // token is issued and the session dies with the first access token. Parameters stroom sets
        // itself are skipped so config cannot corrupt the flow; 'prompt' additionally defers to a
        // forced login.
        final Map<String, String> extraParams =
                openIdConfiguration.getAuthenticationRequestExtraParams();
        for (final Entry<String, String> entry : NullSafe.map(extraParams).entrySet()) {
            final String key = NullSafe.trim(entry.getKey());
            if (RESERVED_AUTH_REQUEST_PARAMS.contains(key)
                || (OpenId.PROMPT.equals(key) && state.isPrompt())) {
                LOGGER.warn("createAuthUri() - Ignoring configured authenticationRequestExtraParams " +
                            "entry '{}' as it clashes with a parameter stroom sets itself", key);
            } else if (!key.isEmpty()) {
                uriBuilder = UriBuilderUtil.addParam(uriBuilder, key, entry.getValue());
            }
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
}
