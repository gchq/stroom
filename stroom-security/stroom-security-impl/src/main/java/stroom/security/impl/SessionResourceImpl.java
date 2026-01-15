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

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class SessionResourceImpl implements SessionResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SessionResourceImpl.class);

    private final Provider<OpenIdManager> openIdManagerProvider;
    private final Provider<HttpServletRequest> httpServletRequestProvider;
    private final Provider<AuthenticationEventLog> authenticationEventLogProvider;
    private final Provider<SessionListService> sessionListService;
    private final Provider<StroomUserIdentityFactory> stroomUserIdentityFactoryProvider;
    private final Provider<SecurityContext> securityContextProvider;

    @Inject
    SessionResourceImpl(final Provider<OpenIdManager> openIdManagerProvider,
                        final Provider<HttpServletRequest> httpServletRequestProvider,
                        final Provider<AuthenticationEventLog> authenticationEventLogProvider,
                        final Provider<SessionListService> sessionListService,
                        final Provider<StroomUserIdentityFactory> stroomUserIdentityFactoryProvider,
                        final Provider<SecurityContext> securityContextProvider) {
        this.openIdManagerProvider = openIdManagerProvider;
        this.httpServletRequestProvider = httpServletRequestProvider;
        this.authenticationEventLogProvider = authenticationEventLogProvider;
        this.sessionListService = sessionListService;
        this.stroomUserIdentityFactoryProvider = stroomUserIdentityFactoryProvider;
        this.securityContextProvider = securityContextProvider;
    }

//    @Override
//    @AutoLogged(OperationType.UNLOGGED)
//    public ValidateSessionResponse validateSession(final String postAuthRedirectUri) {
//        final OpenIdManager openIdManager = openIdManagerProvider.get();
//        final HttpServletRequest request = httpServletRequestProvider.get();
//        Optional<UserIdentity> userIdentity = openIdManager.loginWithRequestToken(request);
//        userIdentity = openIdManager.getOrSetSessionUser(request, userIdentity);
//        if (userIdentity.isPresent()) {
//            return new ValidateSessionResponse(true, userIdentity.get().subjectId(), null);
//        }
//
//        // If the session doesn't have a user ref then attempt login.
//        try {
//            LOGGER.debug("Using postAuthRedirectUri: {}", postAuthRedirectUri);
//
//            // We might have completed the back channel authentication now so see if we have a user session.
//            userIdentity = UserIdentitySessionUtil.get(request.getSession(false));
//            return userIdentity
//                    .map(identity ->
//                            createValidResponse(identity.subjectId()))
//                    .orElseGet(() -> createRedirectResponse(request, postAuthRedirectUri));
//
//        } catch (final RuntimeException e) {
//            LOGGER.error(e.getMessage(), e);
//            throw e;
//        }
//    }
//
//    private ValidateSessionResponse createValidResponse(final String userId) {
//        return new ValidateSessionResponse(true, userId, null);
//    }
//
//    private ValidateSessionResponse createRedirectResponse(final HttpServletRequest request, final String url) {
//        final OpenIdManager openIdManager = openIdManagerProvider.get();
//        final String code = getParam(url, OpenId.CODE);
//        final String stateId = getParam(url, OpenId.STATE);
//        final String redirectUri = openIdManager.redirect(request, code, stateId, url);
//        return new ValidateSessionResponse(false, null, redirectUri);
//    }
//
//    private String getParam(final String url, final String param) {
//        int start = url.indexOf(param + "=");
//        if (start != -1) {
//            start += param.length() + 1;
//            final int end = url.indexOf("&", start);
//            if (end != -1) {
//                return URLDecoder.decode(url.substring(start, end), StandardCharsets.UTF_8);
//            }
//            return URLDecoder.decode(url.substring(start), StandardCharsets.UTF_8);
//        }
//        return null;
//    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public UrlResponse logout(final String redirectUri) {
        final HttpServletRequest request = httpServletRequestProvider.get();

        // Get the session.
        final HttpSession session = SessionUtil.getExistingSession(request);
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

        final String url = openIdManagerProvider.get().logout(redirectUri);
        LOGGER.debug("Returning url: {}", url);
        return new UrlResponse(url);
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
}
