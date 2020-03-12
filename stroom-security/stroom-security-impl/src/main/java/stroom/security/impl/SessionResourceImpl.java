package stroom.security.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.UserIdentity;
import stroom.security.impl.session.SessionListResponse;
import stroom.security.impl.session.SessionListService;
import stroom.security.impl.session.UserIdentitySessionUtil;

import javax.inject.Inject;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.Response;

@SuppressWarnings("unused")
public class SessionResourceImpl implements SessionResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionResourceImpl.class);

    private final AuthenticationEventLog eventLog;
    private final SessionListService sessionListService;

    @Inject
    SessionResourceImpl(final AuthenticationEventLog eventLog,
                        final SessionListService sessionListService) {
        this.eventLog = eventLog;
        this.sessionListService = sessionListService;
    }

    @Override
    public Response logout(final String authSessionId) {
        LOGGER.info("Logging out session {}", authSessionId);

        // TODO : We need to lookup the auth session in our user sessions

        final HttpSession session = SessionMap.getSession(authSessionId);
        final UserIdentity userIdentity = UserIdentitySessionUtil.get(session);
        if (session != null) {
            // Invalidate the current user session
            session.invalidate();
        }
        if (userIdentity != null) {
            // Create an event for logout
            eventLog.logoff(userIdentity.getId());
        }

        return Response.status(Response.Status.OK).entity("Logout successful").build();
    }

    @Override
    public SessionListResponse list(final String nodeName) {
        final SessionListResponse sessionList;
        if (nodeName != null) {
            sessionList = sessionListService.listSessions(nodeName);
        } else {
            sessionList = sessionListService.listSessions();
        }
        return sessionList;
    }

}
