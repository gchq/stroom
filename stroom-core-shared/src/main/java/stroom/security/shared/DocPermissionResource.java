package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.fusesource.restygwt.client.DirectRestService;

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
@Consumes(MediaType.APPLICATION_JSON)
public interface DocPermissionResource extends RestResource, DirectRestService {

    @POST
    @Path("/changeDocumentPermissions")
    @ApiOperation(
            value = "Change document permissions",
            response = Boolean.class)
    Boolean changeDocumentPermissions(@ApiParam("request") ChangeDocumentPermissionsRequest request);

    @POST
    @Path("/copyPermissionsFromParent")
    @ApiOperation(
            value = "Copy permissions from parent",
            response = DocumentPermissions.class)
    DocumentPermissions copyPermissionFromParent(@ApiParam("request") CopyPermissionsFromParentRequest request);


    @POST
    @Path("/fetchAllDocumentPermissions")
    @ApiOperation(
            value = "Fetch document permissions",
            response = DocumentPermissions.class)
    DocumentPermissions fetchAllDocumentPermissions(@ApiParam("request") FetchAllDocumentPermissionsRequest request);

    @POST
    @Path("/checkDocumentPermission")
    @ApiOperation(
            value = "Check document permission",
            response = Boolean.class)
    Boolean checkDocumentPermission(@ApiParam("request") CheckDocumentPermissionRequest request);

    @GET
    @Path("/getPermissionForDocType/${docType}")
    @ApiOperation(
            value = "Get all permissions for a given document type",
            response = List.class)
    List<String> getPermissionForDocType(@PathParam("docType") String docType);

    @POST
    @Path("/filterUsers")
    @ApiOperation(
            value = "Get all permissions for a given document type",
            response = List.class)
    List<User> filterUsers(final FilterUsersRequest filterUsersRequest);

}
