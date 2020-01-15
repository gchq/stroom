package stroom.security.impl;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.UserIdentity;
import stroom.security.impl.session.UserIdentitySessionUtil;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * This is used by the OpenId flow - when a user request a log out the remote authentication service
 * needs to ask all relying parties to log out. This is the back-channel resource that allows this to
 * happen.
 */
@Api(value = "session - /v1")
@Path("/session/v1")
@Produces(MediaType.APPLICATION_JSON)
public class SessionResource implements RestResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionResource.class);

    private final AuthenticationEventLog eventLog;

    @Inject
    SessionResource(final AuthenticationEventLog eventLog) {
        this.eventLog = eventLog;
    }

    @GET
    @Path("logout/{sessionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Logs the specified session out of Stroom",
            response = Response.class)
    public Response logout(@PathParam("sessionId") String authSessionId) {
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
}
