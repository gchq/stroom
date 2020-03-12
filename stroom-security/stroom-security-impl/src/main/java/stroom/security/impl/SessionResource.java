package stroom.security.impl;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.security.impl.session.SessionDetails;
import stroom.security.impl.session.SessionListResponse;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

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
@Path(SessionResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public interface SessionResource extends RestResource, DirectRestService {

    String BASE_PATH = "/session" + ResourcePaths.V1;
    String LIST_PATH_PART = "/list";

    @GET
    @Path("logout/{sessionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Logs the specified session out of Stroom",
            response = Response.class)
    Response logout(@PathParam("sessionId") final String authSessionId);

    @GET
    @Path(LIST_PATH_PART + "/{nodeName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Lists user sessions for a node, or all nodes if nodeName is null",
            response = SessionDetails.class)
    SessionListResponse list(@PathParam("nodeName") final String nodeName);
}
