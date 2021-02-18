package stroom.security.impl;

import stroom.security.impl.session.SessionListResponse;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

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

/**
 * This is used by the OpenId flow - when a user request a log out the remote authentication service
 * needs to ask all relying parties to log out. This is the back-channel resource that allows this to
 * happen.
 */
@Tag(name = "Sessions")
@Path(SessionResource.BASE_PATH)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SessionResource extends RestResource, DirectRestService {

    String BASE_PATH = "/session" + ResourcePaths.V1;
    String LIST_PATH_PART = "/list";
    String NODE_NAME_PARAM = "nodeName";

    @GET
    @Path("/noauth/login")
    @Operation(summary = "Checks if the current session is authenticated and redirects to an auth flow if it is not")
    SessionLoginResponse login(@Context @NotNull HttpServletRequest httpServletRequest,
                               @QueryParam("redirect_uri") String redirectUri);

    @GET
    @Path("logout/{sessionId}")
    @Operation(summary = "Logs the specified session out of Stroom")
    Boolean logout(@PathParam("sessionId") String authSessionId);

    @GET
    @Path(LIST_PATH_PART)
    @Operation(summary = "Lists user sessions for a node, or all nodes in the cluster if nodeName is null")
    SessionListResponse list(@QueryParam(NODE_NAME_PARAM) String nodeName);
}
