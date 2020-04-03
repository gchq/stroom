package stroom.security.impl;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.security.impl.session.SessionDetails;
import stroom.security.impl.session.SessionListResponse;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * This is used by the OpenId flow - when a user request a log out the remote authentication service
 * needs to ask all relying parties to log out. This is the back-channel resource that allows this to
 * happen.
 */
@Api(value = "session - /v1")
@Path(SessionResource.BASE_PATH)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SessionResource extends RestResource, DirectRestService {
    String BASE_PATH = "/session" + ResourcePaths.V1;
    String LIST_PATH_PART = "/list";
    String NODE_NAME_PARAM = "nodeName";

    @GET
    @Path("/noauth/login")
    @ApiOperation(
            value = "Checks if the current session is authenticated and redirects to an auth flow if it is not",
            response = String.class)
    LoginResponse login(@Context @NotNull HttpServletRequest httpServletRequest, @QueryParam("redirect_uri") String redirectUri);

    @GET
    @Path("logout/{sessionId}")
    @ApiOperation(
            value = "Logs the specified session out of Stroom",
            response = String.class)
    Response logout(@PathParam("sessionId") String authSessionId);

    @GET
    @Path(LIST_PATH_PART)
    @ApiOperation(
            value = "Lists user sessions for a node, or all nodes in the cluster if nodeName is null",
            response = SessionDetails.class)
    SessionListResponse list(@QueryParam(NODE_NAME_PARAM) String nodeName);
}
