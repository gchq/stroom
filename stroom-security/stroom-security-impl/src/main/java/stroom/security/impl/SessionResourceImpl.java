package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.common.impl.UserIdentitySessionUtil;
import stroom.security.shared.SessionListResponse;
import stroom.security.shared.SessionResource;
import stroom.security.shared.UrlResponse;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class SessionResourceImpl implements SessionResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionResourceImpl.class);

    private final Provider<OpenIdManager> openIdManagerProvider;
    private final Provider<HttpServletRequest> httpServletRequestProvider;
    private final Provider<AuthenticationEventLog> authenticationEventLogProvider;
    private final Provider<SessionListService> sessionListService;
    private final Provider<StroomUserIdentityFactory> stroomUserIdentityFactoryProvider;

    @Inject
    SessionResourceImpl(final Provider<OpenIdManager> openIdManagerProvider,
                        final Provider<HttpServletRequest> httpServletRequestProvider,
                        final Provider<AuthenticationEventLog> authenticationEventLogProvider,
                        final Provider<SessionListService> sessionListService,
                        final Provider<StroomUserIdentityFactory> stroomUserIdentityFactoryProvider) {
        this.openIdManagerProvider = openIdManagerProvider;
        this.httpServletRequestProvider = httpServletRequestProvider;
        this.authenticationEventLogProvider = authenticationEventLogProvider;
        this.sessionListService = sessionListService;
        this.stroomUserIdentityFactoryProvider = stroomUserIdentityFactoryProvider;
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public UrlResponse logout(final String redirectUri) {
        LOGGER.debug("logout() - redirectUri: {}", redirectUri);
        final HttpServletRequest request = httpServletRequestProvider.get();

        // Get the session.
        final HttpSession session = request.getSession(false);
        if (session != null) {
            // Record the logoff event.
            UserIdentitySessionUtil.get(session).ifPresent(userIdentity -> {
                stroomUserIdentityFactoryProvider.get().logoutUser(userIdentity);
                // Create an event for logout
                authenticationEventLogProvider.get().logoff(userIdentity.getSubjectId());
            });

            // Remove the user identity from the current session.
            UserIdentitySessionUtil.set(session, null);
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
