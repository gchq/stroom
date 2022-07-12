package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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
    String MEMBER_UUID_PARAM = "memberUuid";

    @GET
    @Path("/noauth/validateSession")
    @Operation(
            summary = "Validate the current session, return a redirect Uri if invalid.",
            operationId = "validateStroomSession")
    ValidateSessionResponse validateSession(@QueryParam("redirect_uri") @NotNull String redirectUri);

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
    SessionListResponse list(@QueryParam(MEMBER_UUID_PARAM) String memberUuid);
}
