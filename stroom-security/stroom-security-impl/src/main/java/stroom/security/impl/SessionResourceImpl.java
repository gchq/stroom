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
                    NullSafe.get(userIdentity, UserIdentity::getSubjectId),
                    NullSafe.get(userIdentity, UserIdentity::getDisplayName),
                    NullSafe.get(userIdentity, identity -> identity.getFullName().orElse("-")),
                    SessionUtil.getSessionId(session),
                    redirectUri));
            if (userIdentity != null) {
                // Record the logoff event.
                stroomUserIdentityFactoryProvider.get().logoutUser(userIdentity);
                // Create an event for logout
                authenticationEventLogProvider.get().logoff(userIdentity.getSubjectId());
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
