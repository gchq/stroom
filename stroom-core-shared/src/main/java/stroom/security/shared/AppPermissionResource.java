package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;
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

@Tag(name = "Application Permissions")
@Path("/permission/app" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AppPermissionResource extends RestResource, DirectRestService {

    @GET
    @Operation(
            summary = "User and app permissions for the current session",
            operationId = "getEffectiveAppPermissions")
    AppUserPermissions getEffectiveAppPermissions();

    @POST
    @Path("/getAppUserPermissionsReport")
    @Operation(
            summary = "Get a detailed report of app permissions for the specified user",
            operationId = "getAppUserPermissionsReport")
    AppUserPermissionsReport getAppUserPermissionsReport(@Parameter(description = "user", required = true) UserRef
                                                                 user);

    @POST
    @Path("/fetchAppUserPermissions")
    @Operation(
            summary = "Fetch app user permissions",
            operationId = "fetchAppUserPermissions")
    ResultPage<AppUserPermissions> fetchAppUserPermissions(
            @Parameter(description = "request", required = true) FetchAppUserPermissionsRequest request);

    @POST
    @Path("/changeAppPermission")
    @Operation(
            summary = "User and app permissions for the current session",
            operationId = "changeAppPermission")
    Boolean changeAppPermission(
            @Parameter(description = "changeUserRequest", required = true) AbstractAppPermissionChange request);
}
