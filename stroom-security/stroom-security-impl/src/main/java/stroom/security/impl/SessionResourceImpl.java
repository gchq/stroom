/*
 * Copyright 2020 Crown Copyright
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

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.UserIdentitySessionUtil;
import stroom.security.shared.SessionListResponse;
import stroom.security.shared.SessionResource;
import stroom.security.shared.UrlResponse;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.SessionUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.List;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class SessionResourceImpl implements SessionResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SessionResourceImpl.class);

    private final Provider<OpenIdManager> openIdManagerProvider;
    private final Provider<HttpServletRequest> httpServletRequestProvider;
    private final Provider<HttpServletResponse> httpServletResponseProvider;
    private final Provider<AuthenticationEventLog> authenticationEventLogProvider;
    private final Provider<SessionListService> sessionListService;
    private final Provider<StroomUserIdentityFactory> stroomUserIdentityFactoryProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<AuthenticationConfig> authenticationConfigProvider;

    @Inject
    SessionResourceImpl(final Provider<OpenIdManager> openIdManagerProvider,
                        final Provider<HttpServletRequest> httpServletRequestProvider,
                        final Provider<HttpServletResponse> httpServletResponseProvider,
                        final Provider<AuthenticationEventLog> authenticationEventLogProvider,
                        final Provider<SessionListService> sessionListService,
                        final Provider<StroomUserIdentityFactory> stroomUserIdentityFactoryProvider,
                        final Provider<SecurityContext> securityContextProvider,
                        final Provider<AuthenticationConfig> authenticationConfigProvider) {
        this.openIdManagerProvider = openIdManagerProvider;
        this.httpServletRequestProvider = httpServletRequestProvider;
        this.httpServletResponseProvider = httpServletResponseProvider;
        this.authenticationEventLogProvider = authenticationEventLogProvider;
        this.sessionListService = sessionListService;
        this.stroomUserIdentityFactoryProvider = stroomUserIdentityFactoryProvider;
        this.securityContextProvider = securityContextProvider;
        this.authenticationConfigProvider = authenticationConfigProvider;
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public UrlResponse logout(final String redirectUri) {
        final HttpServletRequest request = httpServletRequestProvider.get();

        // Get the session.
        final HttpSession session = SessionUtil.getExistingSession(request);
        boolean logoffEventRecorded = false;
        if (session != null) {
            final UserIdentity userIdentity = UserIdentitySessionUtil.getUserFromSession(session)
                    .orElse(null);
            LOGGER.info(() -> LogUtil.message(
                    "logout() - Logout called for {}, userIdentity: {} {} ({}), session: {}, redirectUri: {}",
                    securityContextProvider.get().getUserRef(),
                    NullSafe.get(userIdentity, UserIdentity::subjectId),
                    NullSafe.get(userIdentity, UserIdentity::getDisplayName),
                    NullSafe.get(userIdentity, identity -> identity.getFullName().orElse("-")),
                    SessionUtil.getSessionId(session),
                    redirectUri));
            if (userIdentity != null) {
                // Record the logoff event.
                stroomUserIdentityFactoryProvider.get().logoutUser(userIdentity);
                // Create an event for logout
                authenticationEventLogProvider.get().logoff(userIdentity.subjectId());
                logoffEventRecorded = true;
                // Remove the user identity from the current session.
                UserIdentitySessionUtil.setUserInSession(session, null);
            }
            session.invalidate();
        } else {
            LOGGER.info(() -> LogUtil.message(
                    "logout() - Logout called for {} but no active session, redirectUri: {}",
                    securityContextProvider.get().getUserRef(),
                    redirectUri));
        }

        // When an authenticating edge proxy is the relying party, invalidating stroom's session is
        // not enough: the proxy's own session cookie survives and would silently re-authenticate
        // the next request. There is also no stroom-owned flow, so the IDP logout below (which
        // creates flow state) does not apply.
        final EdgeAuthenticationConfig edgeConfig =
                authenticationConfigProvider.get().getEdgeAuthenticationConfig();
        if (edgeConfig.isEnabled()) {
            // In edge mode the identity is derived per request from headers, not held in a
            // session, so the session-keyed event logging above will not have fired - record the
            // logoff for the authenticated user making this call.
            if (!logoffEventRecorded) {
                NullSafe.consume(securityContextProvider.get().getUserRef(), userRef ->
                        authenticationEventLogProvider.get().logoff(userRef.getSubjectId()));
            }
            return edgeLogout(request, redirectUri, edgeConfig.getLogout());
        }

        final String url = openIdManagerProvider.get().logout(redirectUri);
        LOGGER.debug("Returning url: {}", url);
        return new UrlResponse(url);
    }

    /**
     * The edge-proxy logout contract (documented by AWS as the application's responsibility, and
     * equivalent for other authenticating proxies): expire the proxy's - possibly sharded -
     * session cookies, then send the browser to the proxy's/IDP's sign-out endpoint. The stroom
     * session and identity have already been cleared by the caller.
     */
    private UrlResponse edgeLogout(final HttpServletRequest request,
                                   final String redirectUri,
                                   final EdgeLogoutConfig logoutConfig) {
        final List<String> cookiePrefixes = logoutConfig.getCookiesToExpire();
        final HttpServletResponse response = httpServletResponseProvider.get();
        if (NullSafe.hasItems(cookiePrefixes)) {
            if (response != null) {
                expireEdgeCookies(request, response, cookiePrefixes);
            } else {
                LOGGER.error("edgeLogout() - No response available to expire edge cookies on");
            }
        }

        final String signOutUrl = logoutConfig.getSignOutUrl();
        if (!NullSafe.isBlankString(signOutUrl)) {
            LOGGER.debug("edgeLogout() - Redirecting to edge sign-out URL: {}", signOutUrl);
            return new UrlResponse(signOutUrl);
        }

        LOGGER.warn("edgeLogout() - 'edgeAuthentication.logout.signOutUrl' is not configured, so " +
                    "the authenticating proxy's/IDP's session has not been ended and the user may " +
                    "be silently signed back in by their next request.");
        return new UrlResponse(redirectUri);
    }

    /**
     * Expire every request cookie whose name starts with one of the configured prefixes. Prefixes,
     * because proxies shard large session cookies (an AWS ALB's 'AWSELBAuthSessionCookie' arrives
     * as 'AWSELBAuthSessionCookie-0', '-1', ...).
     */
    private void expireEdgeCookies(final HttpServletRequest request,
                                   final HttpServletResponse response,
                                   final List<String> cookiePrefixes) {
        final Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (final Cookie cookie : cookies) {
                final String name = cookie.getName();
                if (cookiePrefixes.stream().anyMatch(name::startsWith)) {
                    LOGGER.info("expireEdgeCookies() - Expiring edge session cookie: {}", name);
                    // Max-Age=0 expires the cookie now. Path=/ matches how the proxies set them.
                    final StringBuilder sb = new StringBuilder()
                            .append(name).append('=')
                            .append("; Path=/")
                            .append("; Max-Age=0")
                            .append("; HttpOnly");
                    if (request.isSecure()) {
                        sb.append("; Secure");
                    }
                    response.addHeader("Set-Cookie", sb.toString());
                }
            }
        }
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public SessionListResponse list(final String nodeName) {
        LOGGER.debug("list({}) called", nodeName);
        if (nodeName != null) {
            return sessionListService.get().listSessions(nodeName);
        } else {
            return sessionListService.get().listSessions();
        }
    }

    @Override
    @AutoLogged(OperationType.DELETE)
    public Boolean terminateOtherSessions() {
        final HttpServletRequest request = httpServletRequestProvider.get();
        final HttpSession session = request.getSession(false);
        final String currentSessionId = session != null
                ? session.getId()
                : null;
        final UserRef currentUser = securityContextProvider.get().getUserRef();
        if (currentUser == null) {
            return false;
        }
        // Self-service: terminate the current user's OTHER sessions, sparing the one making this request.
        LOGGER.info("terminateOtherSessions() - user: {}, keeping session: {}", currentUser, currentSessionId);
        sessionListService.get().evictUserSessions(currentUser.getSubjectId(), currentSessionId);
        return true;
    }

    @Override
    @AutoLogged(OperationType.DELETE)
    public Integer terminate(final String subjectId, final String exceptSessionId, final String nodeName) {
        // Per-node worker for the fan-out. Authorisation (self vs MANAGE_USERS) is enforced in
        // SessionListListener, not here, so the self-service run-as call is not rejected up front.
        return sessionListService.get().evictUserSessionsOnNode(subjectId, exceptSessionId, nodeName);
    }

    @Override
    @AutoLogged(OperationType.DELETE)
    public Boolean terminateSession(final String sessionHandle, final String nodeName) {
        // As with terminate(), authorisation (self vs MANAGE_USERS) is enforced per node in
        // SessionListListener, which is the only place that can tell who owns the session.
        LOGGER.debug("terminateSession(sessionHandle: {}, nodeName: {}) called", sessionHandle, nodeName);
        if (nodeName != null) {
            return sessionListService.get().evictSessionOnNode(sessionHandle, nodeName);
        }
        return sessionListService.get().evictSession(sessionHandle);
    }
}
