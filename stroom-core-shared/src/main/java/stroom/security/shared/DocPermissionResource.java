package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Doc Permissions")
@Path("/permission/doc" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DocPermissionResource extends RestResource, DirectRestService {

    @POST
    @Path("/fetchDocumentUserPermissions")
    @Operation(
            summary = "Fetch document user permissions",
            operationId = "fetchDocumentUserPermissions")
    ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(
            @Parameter(description = "request", required = true) FetchDocumentUserPermissionsRequest request);

    /**
     * Check that the current user has the requested document permission.
     * This allows the UI to make some decisions but is not used for security purposes.
     *
     * @param request The request to find out if the current user has a certain document permission.
     * @return True if the permission is held.
     */
    @POST
    @Path("/checkDocumentPermission")
    @Operation(
            summary = "Check document permission",
            operationId = "checkDocumentPermission")
    Boolean checkDocumentPermission(
            @Parameter(description = "request", required = true) CheckDocumentPermissionRequest request);
}
