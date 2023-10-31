package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.UserName;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

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
    @Path("/copyPermissionsFromParent")
    @Operation(
            summary = "Copy permissions from parent",
            operationId = "copyPermissionFromParent")
    DocumentPermissions copyPermissionFromParent(
            @Parameter(description = "request", required = true) CopyPermissionsFromParentRequest request);


    @POST
    @Path("/fetchAllDocumentPermissions")
    @Operation(
            summary = "Fetch document permissions",
            operationId = "fetchAllDocumentPermissions")
    DocumentPermissions fetchAllDocumentPermissions(
            @Parameter(description = "request", required = true) FetchAllDocumentPermissionsRequest request);

    @POST
    @Path("/checkDocumentPermission")
    @Operation(
            summary = "Check document permission",
            operationId = "checkDocumentPermission")
    Boolean checkDocumentPermission(
            @Parameter(description = "request", required = true) CheckDocumentPermissionRequest request);

    @GET
    @Path("/getPermissionForDocType/${docType}")
    @Operation(
            summary = "Get all permissions for a given document type",
            operationId = "getPermissionForDocType")
    List<String> getPermissionForDocType(@PathParam("docType") String docType);

    @POST
    @Path("/filterUsers")
    @Operation(
            summary = "Get all permissions for a given document type",
            operationId = "filterUsers")
    List<UserName> filterUsers(FilterUsersRequest filterUsersRequest);

    @POST
    @Path("/getDocumentOwners")
    @Operation(
            summary = "Get the owners of the specified document",
            operationId = "getDocumentOwners")
    List<UserName> getDocumentOwners(@Parameter(description = "documentUuid", required = true) String documentUuid);
}
