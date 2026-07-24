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
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.AuthenticationState;
import stroom.security.common.impl.UserIdentitySessionUtil;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.shared.AuthFlowResponse;
import stroom.util.authentication.HasExpiry;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.net.UrlUtils;
import stroom.util.servlet.SessionUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.Unauthenticated;

import com.google.common.html.HtmlEscapers;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Singleton
@AutoLogged(OperationType.UNLOGGED)
class AuthFlowResourceImpl implements AuthFlowResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AuthFlowResourceImpl.class);

    // Cookie binding the OIDC flow to the initiating browser (login-CSRF / forced-login defence).
    private static final String STATE_COOKIE_NAME = "STROOM_OIDC_STATE";
    private static final int STATE_COOKIE_MAX_AGE_SECONDS = 600;

    private final Provider<OpenIdManager> openIdManagerProvider;
    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;
    private final Provider<AuthenticationStateCache> authenticationStateCacheProvider;
    private final Provider<UriFactory> uriFactoryProvider;
    private final Provider<StroomUserIdentityFactory> stroomUserIdentityFactoryProvider;

    @Inject
    AuthFlowResourceImpl(final Provider<OpenIdManager> openIdManagerProvider,
                         final Provider<OpenIdConfiguration> openIdConfigurationProvider,
                         final Provider<AuthenticationStateCache> authenticationStateCacheProvider,
                         final Provider<UriFactory> uriFactoryProvider,
                         final Provider<StroomUserIdentityFactory> stroomUserIdentityFactoryProvider) {
        this.openIdManagerProvider = openIdManagerProvider;
        this.openIdConfigurationProvider = openIdConfigurationProvider;
        this.authenticationStateCacheProvider = authenticationStateCacheProvider;
        this.uriFactoryProvider = uriFactoryProvider;
        this.stroomUserIdentityFactoryProvider = stroomUserIdentityFactoryProvider;
    }

    @Unauthenticated
    @Override
    public AuthFlowResponse status(final String postAuthRedirectUri,
                                   final HttpServletRequest request,
                                   final HttpServletResponse response) {
        LOGGER.debug(() -> LogUtil.message("status() - postAuthRedirectUri: {}", postAuthRedirectUri));

        // Check existing session for a user identity.
        final HttpSession session = SessionUtil.getExistingSession(request);
        if (session != null) {
            final Optional<UserIdentity> optIdentity = UserIdentitySessionUtil.getUserFromSession(session);
            if (optIdentity.isPresent()) {
                final UserIdentity identity = optIdentity.get();

                // Check if the identity has expired.
                if (identity instanceof final HasExpiry hasExpiry) {
                    final Instant expireTime = hasExpiry.getExpireTime();
                    if (expireTime != null) {
                        if (Instant.now().isAfter(expireTime)) {
                            LOGGER.debug("status() - Session identity has expired, treating as unauthenticated");
                        } else {
                            final long secondsTilExpiry = Duration.between(Instant.now(), expireTime).getSeconds();
                            LOGGER.debug(() -> LogUtil.message(
                                    "status() - Found authenticated identity: {}, expiresInSec: {}",
                                    identity.subjectId(), secondsTilExpiry));
                            return AuthFlowResponse.authenticated(
                                    identity.subjectId(),
                                    identity.getDisplayName(),
                                    secondsTilExpiry);
                        }
                    } else {
                        // No expire time, assume valid.
                        LOGGER.debug(() -> LogUtil.message(
                                "status() - Found authenticated identity with no expiry: {}",
                                identity.subjectId()));
                        return AuthFlowResponse.authenticated(
                                identity.subjectId(),
                                identity.getDisplayName(),
                                null);
                    }
                } else {
                    // Identity doesn't implement HasExpiry, assume valid.
                    LOGGER.debug(() -> LogUtil.message(
                            "status() - Found authenticated identity (no expiry info): {}",
                            identity.subjectId()));
                    return AuthFlowResponse.authenticated(
                            identity.subjectId(),
                            identity.getDisplayName(),
                            null);
                }
            }
        }

        // No valid session identity found - build the OIDC auth URL.
        final UriFactory uriFactory = uriFactoryProvider.get();

        // Only ever return the browser to our own origin after login. redirect_uri is unauthenticated
        // request input, so honouring an off-origin value would be an open redirect - landing the user on
        // an attacker's site immediately after a genuine authentication. Anything off-origin falls back
        // to the application root.
        final String effectiveRedirectUri =
                UrlUtils.isSameOrigin(postAuthRedirectUri, uriFactory.publicUri("/"))
                        ? postAuthRedirectUri
                        : "/";

        // The redirect_uri is the OIDC sign-in callback, a single fixed value the IdP exact-matches.
        final String callbackUri = uriFactory.publicUri(
                ResourcePaths.buildSignInOidcCallbackPath()).toString();

        final AuthenticationStateCache authenticationStateCache = authenticationStateCacheProvider.get();
        final AuthenticationState state = authenticationStateCache.create(
                effectiveRedirectUri, callbackUri, false);

        // Bind this flow to the initiating browser: a SameSite=Lax cookie carrying the stateId, which the
        // callback requires the incoming 'state' to match. This stops forced login / login CSRF - an
        // attacker cannot complete, in a victim's browser, a flow they began in their own, because the
        // victim's browser holds no matching cookie. Lax (not Strict) because the IdP returns via a
        // top-level cross-site GET, which a Strict cookie would not accompany.
        setStateCookie(request, response, state.getId());

        final OpenIdConfiguration openIdConfiguration = openIdConfigurationProvider.get();
        final OpenIdManager openIdManager = openIdManagerProvider.get();
        final String authUrl = openIdManager.createAuthUri(
                openIdConfiguration.getAuthEndpoint(),
                openIdConfiguration.getClientId(),
                state);

        LOGGER.debug(() -> LogUtil.message("status() - Returning unauthenticated with authUrl: {}", authUrl));
        return AuthFlowResponse.unauthenticated(authUrl);
    }

    @Unauthenticated
    @Override
    public void callback(final String code,
                         final String stateId,
                         final HttpServletRequest request,
                         final HttpServletResponse response) throws IOException {
        LOGGER.debug(() -> LogUtil.message("callback() - code: {}, stateId: {}", code, stateId));

        Objects.requireNonNull(code, "Missing 'code' parameter");
        Objects.requireNonNull(stateId, "Missing 'state' parameter");

        // Reject unless the state is bound to THIS browser (forced-login / login-CSRF defence): the
        // incoming 'state' must match the SameSite=Lax cookie set when the flow began. Always clear the
        // single-use binding cookie. Reported with the same generic message as an unknown state so it is
        // not an oracle.
        final String boundStateId = readStateCookie(request);
        clearStateCookie(request, response);
        if (boundStateId == null || !boundStateId.equals(stateId)) {
            LOGGER.warn(() -> LogUtil.message("callback() - state '{}' is not bound to this browser", stateId));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown or expired state");
            return;
        }

        final AuthenticationStateCache authenticationStateCache = authenticationStateCacheProvider.get();
        final Optional<AuthenticationState> optionalState = authenticationStateCache.getAndRemove(stateId);

        if (optionalState.isEmpty()) {
            LOGGER.warn(() -> LogUtil.message("callback() - Unknown or expired state: {}", stateId));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown or expired state");
            return;
        }

        final AuthenticationState state = optionalState.get();
        final StroomUserIdentityFactory userIdentityFactory = stroomUserIdentityFactoryProvider.get();

        try {
            final Optional<UserIdentity> optionalUserIdentity =
                    userIdentityFactory.getAuthFlowUserIdentity(request, code, state);

            if (optionalUserIdentity.isPresent()) {
                LOGGER.debug(() -> LogUtil.message(
                        "callback() - Authentication successful, redirecting to: {}",
                        state.getInitiatingUri()));

                // Respond with an HTML page that uses meta-refresh to redirect to the
                // initiating URI. This avoids issues with the browser caching the OIDC
                // callback URL with the code and state parameters.
                response.setContentType("text/html;charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_OK);
                try (final PrintWriter writer = response.getWriter()) {
                    // Escape the URL before writing it into the HTML attribute, as it derives from the
                    // initiating request.
                    writer.write("<!DOCTYPE html><html><head>"
                                 + "<meta http-equiv=\"refresh\" content=\"0;url="
                                 + HtmlEscapers.htmlEscaper().escape(state.getInitiatingUri())
                                 + "\"></head><body>Redirecting...</body></html>");
                }
            } else {
                LOGGER.warn("callback() - Authentication failed, no user identity returned");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
            }
        } catch (final Exception e) {
            LOGGER.error("callback() - Error during authentication: {}", LogUtil.exceptionMessage(e), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Authentication error: " + e.getMessage());
        }
    }

    private void setStateCookie(final HttpServletRequest request,
                                final HttpServletResponse response,
                                final String stateId) {
        response.addHeader("Set-Cookie",
                buildStateCookieHeader(request, stateId, STATE_COOKIE_MAX_AGE_SECONDS));
    }

    private void clearStateCookie(final HttpServletRequest request, final HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildStateCookieHeader(request, "", 0));
    }

    private String buildStateCookieHeader(final HttpServletRequest request,
                                          final String value,
                                          final int maxAgeSeconds) {
        // SameSite=Lax so the cookie accompanies the IdP's top-level cross-site GET back to the callback,
        // HttpOnly so script cannot read it, and Secure only over HTTPS so it still works for local http.
        final StringBuilder sb = new StringBuilder()
                .append(STATE_COOKIE_NAME).append('=').append(value)
                .append("; Path=/")
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; HttpOnly")
                .append("; SameSite=Lax");
        if (request.isSecure()) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    private String readStateCookie(final HttpServletRequest request) {
        final Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (final Cookie cookie : cookies) {
                if (STATE_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
