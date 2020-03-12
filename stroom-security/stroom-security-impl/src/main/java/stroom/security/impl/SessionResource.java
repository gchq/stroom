package stroom.security.impl;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.UserIdentity;
import stroom.security.impl.session.SessionDetails;
import stroom.security.impl.session.SessionListResponse;
import stroom.security.impl.session.SessionListService;
import stroom.security.impl.session.UserIdentitySessionUtil;
import stroom.task.shared.TaskProgressResponse;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Objects;

/**
 * This is used by the OpenId flow - when a user request a log out the remote authentication service
 * needs to ask all relying parties to log out. This is the back-channel resource that allows this to
 * happen.
 */
@Api(value = "session - /v1")
@Path(SessionResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public class SessionResource implements RestResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionResource.class);

    public static final String BASE_PATH = "/session" + ResourcePaths.V1;
    public static final String LIST_PATH_PART = "/list";

    private final AuthenticationEventLog eventLog;
    private final SessionListService sessionListService;

    @Inject
    SessionResource(final AuthenticationEventLog eventLog,
                    final SessionListService sessionListService) {
        this.eventLog = eventLog;
        this.sessionListService = sessionListService;
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

    @GET
    @Path(LIST_PATH_PART + "/{nodeName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Lists user sessions for a node, or all nodes if nodeName is null",
            response = SessionDetails.class)
    public SessionListResponse list(@PathParam("nodeName") String nodeName) {
        final SessionListResponse sessionList;
        if (nodeName != null) {
            sessionList = sessionListService.listSessions(nodeName);
        } else {
            sessionList = sessionListService.listSessions();
        }
        return sessionList;
    }

}
