package stroom.security.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.api.OIDC;
import stroom.security.api.UserIdentity;
import stroom.security.impl.session.SessionListResponse;
import stroom.security.impl.session.SessionListService;
import stroom.security.impl.session.UserIdentitySessionUtil;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.Response;
import java.util.Map;

@SuppressWarnings("unused")
public class SessionResourceImpl implements SessionResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionResourceImpl.class);

    private final AuthenticationEventLog eventLog;
    private final SessionListService sessionListService;
    private final AuthenticationConfig authenticationConfig;
    private final OpenIdManager openIdManager;

    @Inject
    public SessionResourceImpl(final AuthenticationEventLog eventLog,
                               final SessionListService sessionListService,
                               final AuthenticationConfig authenticationConfig,
                               final OpenIdManager openIdManager) {
        this.eventLog = eventLog;
        this.sessionListService = sessionListService;
        this.authenticationConfig = authenticationConfig;
        this.openIdManager = openIdManager;
    }

    @Override
    public LoginResponse login(final HttpServletRequest request, final String referrer) {
        String redirectUri = null;
        try {
            LOGGER.info("Logging in session for '{}'", referrer);

            final UserIdentity userIdentity = UserIdentitySessionUtil.get(request.getSession(false));
            if (userIdentity == null) {

                // If the session doesn't have a user ref then attempt login.
                final Map<String, String> paramMap = UrlUtils.createParamMap(referrer);
                final String stateId = paramMap.get(OIDC.STATE);
                final String postAuthRedirectUri = OIDC.removeOIDCParams(referrer);
                if (stateId != null) {
                    redirectUri = openIdManager.backChannelOIDC(request, stateId, postAuthRedirectUri);
                } else {
                    redirectUri = openIdManager.frontChannelOIDC(request, postAuthRedirectUri);
                }
            }

            if (redirectUri == null) {
                redirectUri = OIDC.removeOIDCParams(referrer);
            }

            return new LoginResponse(userIdentity != null, redirectUri);

        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
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
        LOGGER.debug("list({}) called", nodeName);
        if (nodeName != null) {
            return sessionListService.listSessions(nodeName);
        } else {
            return sessionListService.listSessions();
        }
    }

}
