package stroom.security.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.task.shared.TerminateTaskProgressRequest;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api(value = "application permissions - /v1")
@Path("/permission/doc" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public interface DocPermissionResource extends RestResource, DirectRestService {
    @POST
    @Path("/changeDocumentPermissions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Change document permissions",
            response = Boolean.class)
    Boolean changeDocumentPermissions(ChangeDocumentPermissionsRequest request);

    @POST
    @Path("/copyPermissionsFromParent")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Copy permissions from parent",
            response = DocumentPermissions.class)
    DocumentPermissions copyPermissionFromParent(CopyPermissionsFromParentRequest request);


    @POST
    @Path("/fetchAllDocumentPermissions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Fetch document permissions",
            response = DocumentPermissions.class)
    DocumentPermissions fetchAllDocumentPermissions(FetchAllDocumentPermissionsRequest request);

    @POST
    @Path("/checkDocumentPermission")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Check document permission",
            response = Boolean.class)
    Boolean checkDocumentPermission(CheckDocumentPermissionRequest request);

    @GET
    @Path("/getPermissionForDocType/${docType}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Get all permissions for a given document type",
            response = List.class)
    List<String> getPermissionForDocType(@PathParam("docType") String docType);
}
