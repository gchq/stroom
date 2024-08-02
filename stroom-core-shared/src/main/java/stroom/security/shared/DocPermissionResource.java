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
    @Path("/changeDocumentPermissions")
    @Operation(
            summary = "Change document permissions",
            operationId = "changeDocumentPermissions")
    Boolean changeDocumentPermissions(
            @Parameter(description = "request", required = true) ChangeDocumentPermissionsRequest request);

    @POST
    @Path("/fetchPermissionChangeImpact")
    @Operation(
            summary = "Fetch impact summary for a change of document permissions",
            operationId = "fetchPermissionChangeImpact")
    PermissionChangeImpactSummary fetchPermissionChangeImpact(
            @Parameter(description = "request", required = true) ChangeDocumentPermissionsRequest request);

//    @POST
//    @Path("/copyPermissionsFromParent")
//    @Operation(
//            summary = "Copy permissions from parent",
//            operationId = "copyPermissionFromParent")
//    DocumentPermissions copyPermissionFromParent(
//            @Parameter(description = "request", required = true) CopyPermissionsFromParentRequest request);


    @POST
    @Path("/fetchDocumentUsers")
    @Operation(
            summary = "Fetch document users",
            operationId = "fetchDocumentUsers")
    ResultPage<User> fetchDocumentUsers(
            @Parameter(description = "request", required = true) FetchDocumentUsersRequest request);

//    @POST
//    @Path("/fetchDocumentPermissions")
//    @Operation(
//            summary = "Fetch document permissions",
//            operationId = "fetchDocumentPermissions")
//    DocumentPermissionSet fetchDocumentPermissions(
//            @Parameter(description = "request", required = true) FetchDocumentPermissionsRequest request);

    @POST
    @Path("/checkDocumentPermission")
    @Operation(
            summary = "Check document permission",
            operationId = "checkDocumentPermission")
    Boolean checkDocumentPermission(
            @Parameter(description = "request", required = true) CheckDocumentPermissionRequest request);
}
