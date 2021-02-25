package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Application Permissions")
@Path("/permission/app" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AppPermissionResource extends RestResource, DirectRestService {

    @GET
    @Operation(
            summary = "User and app permissions for the current session",
            operationId = "getUserAndPermissions")
    UserAndPermissions getUserAndPermissions();

    @POST
    @Path("fetchUserAppPermissions")
    @Operation(
            summary = "User and app permissions for the specified user",
            operationId = "fetchUserAppPermissions")
    UserAndPermissions fetchUserAppPermissions(@Parameter(description = "user", required = true) User user);

    @GET
    @Path("fetchAllPermissions")
    @Operation(
            summary = "Get all possible permissions",
            operationId = "fetchAllPermissions")
    List<String> fetchAllPermissions();

    @POST
    @Path("changeUser")
    @Operation(
            summary = "User and app permissions for the current session",
            operationId = "changeUserPermissions")
    Boolean changeUser(
            @Parameter(description = "changeUserRequest", required = true) ChangeUserRequest changeUserRequest);
}
