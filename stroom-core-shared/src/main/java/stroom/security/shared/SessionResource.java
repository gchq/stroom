package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

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
    @Path("logout")
    @Operation(
            summary = "Logout of Stroom session",
            operationId = "stroomLogout")
    UrlResponse logout(@QueryParam("redirect_uri") @NotNull String redirectUri);

    @GET
    @Path(LIST_PATH_PART)
    @Operation(
            summary = "Lists user sessions for a node, or all nodes in the cluster if nodeName is null",
            operationId = "listSessions")
    SessionListResponse list(@QueryParam(NODE_NAME_PARAM) String nodeName);
}
