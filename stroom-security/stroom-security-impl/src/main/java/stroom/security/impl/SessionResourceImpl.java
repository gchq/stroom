package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.UserIdentity;
import stroom.security.impl.session.SessionListResponse;
import stroom.security.impl.session.SessionListService;
import stroom.security.impl.session.UserIdentitySessionUtil;
import stroom.security.openid.api.OpenId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@AutoLogged(OperationType.MANUALLY_LOGGED)
public class SessionResourceImpl implements SessionResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionResourceImpl.class);

    private final Provider<AuthenticationEventLog> eventLog;
    private final Provider<SessionListService> sessionListService;
    private final Provider<OpenIdManager> openIdManager;

    @Inject
    public SessionResourceImpl(final Provider<AuthenticationEventLog> eventLog,
                               final Provider<SessionListService> sessionListService,
                               final Provider<OpenIdManager> openIdManager) {
        this.eventLog = eventLog;
        this.sessionListService = sessionListService;
        this.openIdManager = openIdManager;
    }

    @Override
    public SessionLoginResponse login(final HttpServletRequest request, final String referrer) {
        String redirectUri = null;
        try {
            LOGGER.info("Logging in session for '{}'", referrer);

            final Optional<UserIdentity> userIdentity = openIdManager.get().loginWithRequestToken(request);
            if (userIdentity.isEmpty()) {
                // If the session doesn't have a user ref then attempt login.
                final Map<String, String> paramMap = UrlUtils.createParamMap(referrer);
                final String code = paramMap.get(OpenId.CODE);
                final String stateId = paramMap.get(OpenId.STATE);
                final String postAuthRedirectUri = OpenId.removeReservedParams(referrer);
                redirectUri = openIdManager.get().redirect(request, code, stateId, postAuthRedirectUri);
            }

            if (redirectUri == null) {
                redirectUri = OpenId.removeReservedParams(referrer);
            }

            return new SessionLoginResponse(userIdentity.isPresent(), redirectUri);

        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Boolean logout(final String authSessionId) {
        LOGGER.info("Logging out session {}", authSessionId);

        // TODO : We need to lookup the auth session in our user sessions

        final HttpSession session = SessionMap.getSession(authSessionId);
        final Optional<UserIdentity> userIdentity = UserIdentitySessionUtil.get(session);
        if (session != null) {
            // Invalidate the current user session
            session.invalidate();
        }
        userIdentity.ifPresent(ui -> {
            // Create an event for logout
            eventLog.get().logoff(ui.getId());
        });

        return Boolean.TRUE;
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
