package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.UserRef;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.Set;

@Tag(name = "Application Permissions")
@Path("/permission/app" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AppPermissionResource extends RestResource, DirectRestService {

    @GET
    @Operation(
            summary = "User and app permissions for the current session",
            operationId = "getUserAndPermissions")
    UserAndEffectivePermissions getUserAndPermissions();

    @POST
    @Path("fetchUserAppPermissions")
    @Operation(
            summary = "User and app permissions for the specified user",
            operationId = "fetchUserAppPermissions")
    Set<AppPermission> fetchUserAppPermissions(@Parameter(description = "user", required = true) UserRef user);

    @POST
    @Path("changeUser")
    @Operation(
            summary = "User and app permissions for the current session",
            operationId = "changeUserPermissions")
    Boolean changeUser(
            @Parameter(description = "changeUserRequest", required = true) ChangeUserRequest changeUserRequest);
}
