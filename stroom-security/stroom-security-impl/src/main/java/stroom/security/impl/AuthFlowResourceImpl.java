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
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.AuthenticationState;
import stroom.security.common.impl.UserIdentitySessionUtil;
import stroom.security.openid.api.IdpType;
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
import jakarta.ws.rs.ForbiddenException;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Singleton
@AutoLogged(OperationType.UNLOGGED)
class AuthFlowResourceImpl implements AuthFlowResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AuthFlowResourceImpl.class);

    // Cookie binding the OIDC flow to the initiating browser (login-CSRF / forced-login defence). It holds
    // the ids of the flows currently in flight for this browser, newest first, so that several tabs can each
    // be part-way through a flow without clobbering one another's binding.
    private static final String STATE_COOKIE_NAME = "STROOM_OIDC_STATE";
    private static final int STATE_COOKIE_MAX_AGE_SECONDS = 600;
    // Not a regex metacharacter, and outside the base64url alphabet the state ids are drawn from.
    private static final String STATE_SEPARATOR = "~";
    private static final int MAX_PENDING_STATES = 5;

    // Where the user was heading when the flow began. This is a convenience, not a security decision - it is
    // re-validated as same-origin before use, exactly as the 'redirect_uri' request parameter it came from
    // is - so it deliberately outlives the state binding above. Losing the binding means the flow must be
    // restarted; it does not mean we have to forget where the user was going.
    private static final String TARGET_COOKIE_NAME = "STROOM_OIDC_TARGET";
    private static final int TARGET_COOKIE_MAX_AGE_SECONDS = 3_600;

    // Marks that we have already restarted a flow for this browser very recently. Stops a genuinely broken
    // setup bouncing the browser between here and the IDP forever. Short lived, because a user who has to
    // sign in again takes far longer than this, and that case should still be allowed to self-heal.
    private static final String RETRY_COOKIE_NAME = "STROOM_OIDC_RETRY";
    private static final int RETRY_COOKIE_MAX_AGE_SECONDS = 60;

    private final Provider<OpenIdManager> openIdManagerProvider;
    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;
    private final Provider<AuthenticationStateCache> authenticationStateCacheProvider;
    private final Provider<UriFactory> uriFactoryProvider;
    private final Provider<StroomUserIdentityFactory> stroomUserIdentityFactoryProvider;
    private final Provider<AuthenticationConfig> authenticationConfigProvider;

    @Inject
    AuthFlowResourceImpl(final Provider<OpenIdManager> openIdManagerProvider,
                         final Provider<OpenIdConfiguration> openIdConfigurationProvider,
                         final Provider<AuthenticationStateCache> authenticationStateCacheProvider,
                         final Provider<UriFactory> uriFactoryProvider,
                         final Provider<StroomUserIdentityFactory> stroomUserIdentityFactoryProvider,
                         final Provider<AuthenticationConfig> authenticationConfigProvider) {
        this.openIdManagerProvider = openIdManagerProvider;
        this.openIdConfigurationProvider = openIdConfigurationProvider;
        this.authenticationStateCacheProvider = authenticationStateCacheProvider;
        this.uriFactoryProvider = uriFactoryProvider;
        this.stroomUserIdentityFactoryProvider = stroomUserIdentityFactoryProvider;
        this.authenticationConfigProvider = authenticationConfigProvider;
    }

    @Unauthenticated
    @Override
    public AuthFlowResponse status(final String postAuthRedirectUri,
                                   final HttpServletRequest request,
                                   final HttpServletResponse response) {
        LOGGER.debug(() -> LogUtil.message("status() - postAuthRedirectUri: {}", postAuthRedirectUri));

        // This response reports who the user is and, when they are not signed in, hands out a single-use
        // state. Neither may be served from a cache to a later request.
        response.setHeader("Cache-Control", "no-store");

        // Check existing session for a user identity.
        final HttpSession session = SessionUtil.getExistingSession(request);
        if (session != null) {
            final Optional<AuthFlowResponse> optResponse =
                    UserIdentitySessionUtil.getUserFromSession(session)
                            .flatMap(identity -> toAuthenticatedResponse(identity, "session"));
            if (optResponse.isPresent()) {
                return optResponse.get();
            }
        }

        // Nothing usable in the session, but that does not mean nobody has authenticated this user:
        // the request itself may carry a verifiable credential. Behind an AWS ALB with
        // authenticate-oidc/authenticate-cognito (or any edge proxy relaying an IDP token) the proxy
        // is the relying party: it has completed the flow already and injects a signed token into
        // every request. There is no flow left for us to start there - the state, nonce and PKCE
        // challenge we would mint belong to a second, redundant flow stacked on the proxy's - so
        // report the proxy's user and let the app load. Deliberately no session is created:
        // SecurityFilter re-derives this identity per request from the headers, which is also how
        // the proxy's own token refresh reaches us.
        final Optional<AuthFlowResponse> optTokenResponse = getRequestTokenResponse(request);
        if (optTokenResponse.isPresent()) {
            return optTokenResponse.get();
        }

        // Nobody has authenticated this user. When an edge proxy owns the auth flow there is no
        // flow for us to start - returning no redirectUrl tells the bootstrap to reload the page,
        // which lets the proxy run its own redirect as a top-level navigation (its 302 answer to
        // the bootstrap's fetch() is cross-origin and cannot be followed by script). No state or
        // target cookies either: there is no flow to bind them to.
        if (isEdgeAuthentication()) {
            LOGGER.debug("status() - Unauthenticated and edgeAuthentication is enabled, " +
                         "returning no redirect URL");
            return AuthFlowResponse.unauthenticated(null);
        }

        // No valid identity found anywhere - build the OIDC auth URL.
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
        addPendingState(request, response, state.getId());

        // Remember the destination separately, so an expired binding costs the user their place in the app
        // but not their deep link.
        setTargetCookie(request, response, effectiveRedirectUri);

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

        // When an edge proxy is the relying party stroom never starts a flow, so no authorization
        // code can legitimately arrive here. Refusing outright keeps 'exactly one relying party'
        // true in code, and closes the endpoint to code/state injection games.
        if (isEdgeAuthentication()) {
            LOGGER.warn("callback() - Rejecting OIDC callback: edgeAuthentication is enabled so " +
                        "stroom runs no auth flow of its own");
            throw new ForbiddenException(
                    "Authentication is handled by the edge proxy; stroom's OIDC callback is disabled.");
        }

        Objects.requireNonNull(code, "Missing 'code' parameter");
        Objects.requireNonNull(stateId, "Missing 'state' parameter");

        // This URL carries a single-use authorization code, so nothing about it may be cached.
        response.setHeader("Cache-Control", "no-store");

        // Reject unless the state is bound to THIS browser (forced-login / login-CSRF defence): the
        // incoming 'state' must match one of the pending ids in the SameSite=Lax cookie set when the flow
        // began. This consumes only the matched id, leaving any other tab's in-flight flow bound.
        if (!consumePendingState(request, response, stateId)) {
            LOGGER.warn(() -> LogUtil.message("callback() - state '{}' is not bound to this browser", stateId));
            restartFlow(request, response);
            return;
        }

        final AuthenticationStateCache authenticationStateCache = authenticationStateCacheProvider.get();
        final Optional<AuthenticationState> optionalState = authenticationStateCache.getAndRemove(stateId);

        if (optionalState.isEmpty()) {
            LOGGER.warn(() -> LogUtil.message("callback() - Unknown or expired state: {}", stateId));
            restartFlow(request, response);
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

                // The flow completed, so drop the restart guard.
                clearCookie(request, response, RETRY_COOKIE_NAME);

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

    /**
     * Report the user as authenticated iff the identity is still usable.
     *
     * @return Empty when the identity has expired, i.e. is no longer usable and the caller must
     * fall through to the next identity source.
     */
    private Optional<AuthFlowResponse> toAuthenticatedResponse(final UserIdentity identity,
                                                               final String source) {
        if (identity instanceof final HasExpiry hasExpiry) {
            final Instant expireTime = hasExpiry.getExpireTime();
            if (expireTime != null) {
                if (Instant.now().isAfter(expireTime)) {
                    LOGGER.debug(() -> LogUtil.message(
                            "status() - {} identity has expired, treating as unauthenticated", source));
                    return Optional.empty();
                }
                final long secondsTilExpiry = Duration.between(Instant.now(), expireTime).getSeconds();
                LOGGER.debug(() -> LogUtil.message(
                        "status() - Found authenticated {} identity: {}, expiresInSec: {}",
                        source, identity.subjectId(), secondsTilExpiry));
                return Optional.of(AuthFlowResponse.authenticated(
                        identity.subjectId(),
                        identity.getDisplayName(),
                        secondsTilExpiry));
            }
        }
        // No expiry, or an identity type with no expiry information - assume valid.
        LOGGER.debug(() -> LogUtil.message(
                "status() - Found authenticated {} identity with no expiry: {}",
                source, identity.subjectId()));
        return Optional.of(AuthFlowResponse.authenticated(
                identity.subjectId(),
                identity.getDisplayName(),
                null));
    }

    /**
     * See whether the request itself carries a credential we can verify (an ALB-signed
     * {@code x-amzn-oidc-data} token, or an IDP token relayed as a bearer credential by an
     * authenticating proxy), and if so report its user as authenticated.
     */
    private Optional<AuthFlowResponse> getRequestTokenResponse(final HttpServletRequest request) {
        final IdpType idpType = openIdConfigurationProvider.get().getIdentityProviderType();
        if (IdpType.NO_IDP.equals(idpType)) {
            // No token verification is possible: DelegatingJwtContextFactory has no delegate for
            // NO_IDP and would throw.
            return Optional.empty();
        }
        try {
            return openIdManagerProvider.get()
                    .loginWithRequestToken(request)
                    .flatMap(identity -> toAuthenticatedResponse(identity, "request token"));
        } catch (final AuthenticationException e) {
            // The request carried a token that did not yield a usable identity. This covers both
            // an unknown/disabled user AND an unverifiable/expired token, because
            // AbstractUserIdentityFactory wraps all verification failures in AuthenticationException.
            if (isEdgeAuthentication()) {
                // Behind an edge proxy, falling through would start a flow the IDP silently
                // re-completes and bounces straight back - endlessly. Fail terminally instead; the
                // bootstrap's one-shot reload still gives the proxy a single chance to refresh a
                // stale token before the error is shown.
                LOGGER.warn(() -> LogUtil.message(
                        "status() - Request token failed authentication in edge mode: {}",
                        e.getMessage()));
                throw new ForbiddenException(
                        "Authenticated user is not permitted to use stroom: " + e.getMessage());
            }
            // No edge proxy: no bounce is possible, so fall through and let stroom's own flow run,
            // as it would have before request tokens were consulted here. A genuinely unknown or
            // disabled user then fails terminally at the callback, exactly as they always did.
            LOGGER.warn(() -> LogUtil.message(
                    "status() - Ignoring request token that failed authentication: {}",
                    e.getMessage()));
            return Optional.empty();
        }
    }

    private boolean isEdgeAuthentication() {
        return authenticationConfigProvider.get().getEdgeAuthenticationConfig().isEnabled();
    }

    /**
     * Send the browser back to start a fresh flow rather than dead-ending on an error.
     * <p>
     * An expired or unrecognised state is far more often a user who took their time over the sign in form
     * than it is an attack, and in both cases the right answer is the same: discard the code without
     * redeeming it and begin again, bound to this browser. The login-CSRF defence is untouched by this -
     * a code an attacker planted is still never exchanged - so all this changes is that a recoverable
     * situation now recovers by itself instead of stranding the user on a 400.
     */
    private void restartFlow(final HttpServletRequest request,
                             final HttpServletResponse response) throws IOException {
        if (readCookie(request, RETRY_COOKIE_NAME) != null) {
            // We restarted moments ago and are right back here, so restarting again would just loop.
            LOGGER.warn("callback() - Flow was already restarted for this browser, giving up");
            clearCookie(request, response, RETRY_COOKIE_NAME);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown or expired state");
            return;
        }

        final URI publicRoot = uriFactoryProvider.get().publicUri("/");
        final String target = resolveRestartTarget(request, publicRoot);
        LOGGER.debug(() -> LogUtil.message("restartFlow() - Restarting the auth flow at: {}", target));

        setCookie(request, response, RETRY_COOKIE_NAME, "1", RETRY_COOKIE_MAX_AGE_SECONDS);
        response.sendRedirect(target);
    }

    /**
     * Where to send the browser to begin again. The remembered destination is request-derived, so it is
     * re-validated as same-origin here just as it was when it arrived as a 'redirect_uri' parameter -
     * anything else falls back to the application root.
     */
    private String resolveRestartTarget(final HttpServletRequest request, final URI publicRoot) {
        final String target = decode(readCookie(request, TARGET_COOKIE_NAME));
        return UrlUtils.isSameOrigin(target, publicRoot)
                ? target
                : publicRoot.toString();
    }

    // --------------------------------------------------------------------------------
    // Pending state bindings
    // --------------------------------------------------------------------------------

    /**
     * Record a newly created state as in flight for this browser, keeping the most recent
     * {@link #MAX_PENDING_STATES}. Holding more than one means a second tab starting a flow no longer
     * silently invalidates the first tab's.
     */
    private void addPendingState(final HttpServletRequest request,
                                 final HttpServletResponse response,
                                 final String stateId) {
        final List<String> pending = new ArrayList<>();
        pending.add(stateId);
        for (final String existing : readPendingStates(request)) {
            if (!existing.equals(stateId) && pending.size() < MAX_PENDING_STATES) {
                pending.add(existing);
            }
        }
        writePendingStates(request, response, pending);
    }

    /**
     * Consume the binding for the given state, if this browser holds one.
     * <p>
     * Only the matched id is removed. Clearing the whole cookie would let one stale callback destroy the
     * binding of a different, still valid flow. Rewriting the cookie does restart its {@code Max-Age}, but
     * that extends nothing meaningful: the authoritative lifetime is the server side cache entry, which is
     * written once and not refreshed.
     *
     * @return True if the state was bound to this browser.
     */
    private boolean consumePendingState(final HttpServletRequest request,
                                        final HttpServletResponse response,
                                        final String stateId) {
        final List<String> pending = readPendingStates(request);
        if (!pending.contains(stateId)) {
            return false;
        }
        writePendingStates(request, response, pending.stream()
                .filter(id -> !id.equals(stateId))
                .toList());
        return true;
    }

    private List<String> readPendingStates(final HttpServletRequest request) {
        final String value = readCookie(request, STATE_COOKIE_NAME);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(STATE_SEPARATOR))
                .filter(id -> !id.isBlank())
                .toList();
    }

    private void writePendingStates(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final List<String> stateIds) {
        if (stateIds.isEmpty()) {
            clearCookie(request, response, STATE_COOKIE_NAME);
        } else {
            setCookie(request,
                    response,
                    STATE_COOKIE_NAME,
                    String.join(STATE_SEPARATOR, stateIds),
                    STATE_COOKIE_MAX_AGE_SECONDS);
        }
    }

    private void setTargetCookie(final HttpServletRequest request,
                                 final HttpServletResponse response,
                                 final String target) {
        // Percent encoded, as a URL may legitimately contain characters a cookie value may not.
        setCookie(request,
                response,
                TARGET_COOKIE_NAME,
                URLEncoder.encode(target, StandardCharsets.UTF_8),
                TARGET_COOKIE_MAX_AGE_SECONDS);
    }

    // --------------------------------------------------------------------------------
    // Cookies
    // --------------------------------------------------------------------------------

    private void setCookie(final HttpServletRequest request,
                           final HttpServletResponse response,
                           final String name,
                           final String value,
                           final int maxAgeSeconds) {
        response.addHeader("Set-Cookie", buildCookieHeader(request, name, value, maxAgeSeconds));
    }

    private void clearCookie(final HttpServletRequest request,
                             final HttpServletResponse response,
                             final String name) {
        response.addHeader("Set-Cookie", buildCookieHeader(request, name, "", 0));
    }

    private String buildCookieHeader(final HttpServletRequest request,
                                     final String name,
                                     final String value,
                                     final int maxAgeSeconds) {
        // SameSite=Lax so the cookie accompanies the IdP's top-level cross-site GET back to the callback,
        // HttpOnly so script cannot read it, and Secure only over HTTPS so it still works for local http.
        final StringBuilder sb = new StringBuilder()
                .append(name).append('=').append(value)
                .append("; Path=/")
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; HttpOnly")
                .append("; SameSite=Lax");
        if (request.isSecure()) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    private String readCookie(final HttpServletRequest request, final String name) {
        final Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (final Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String decode(final String value) {
        if (value == null) {
            return null;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException e) {
            LOGGER.debug(() -> LogUtil.message("decode() - Malformed cookie value: {}", value));
            return null;
        }
    }
}
