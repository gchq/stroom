package stroom.security.impl;

import io.swagger.annotations.Api;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(
        value = "document permissions - /v1",
        description = "Stroom Document Permissions API")
@Path("/docPermissions" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DocumentPermissionResource extends RestResource {

    @GET
    @Path("/forDocType/{docType}")
    Response getPermissionForDocType(@PathParam("docType") String docType);

    @GET
    @Path("/forDocForUser/{docUuid}/{userUuid}")
    Response getPermissionsForDocumentForUser(
            @PathParam("docUuid") String docUuid,
            @PathParam("userUuid") String userUuid
    );

    @POST
    @Path("/forDocForUser/{docUuid}/{userUuid}/{permissionName}")
    Response addPermission(
            @PathParam("docUuid") String docUuid,
            @PathParam("userUuid") String userUuid,
            @PathParam("permissionName") String permissionName
    );

    @DELETE
    @Path("/forDocForUser/{docUuid}/{userUuid}/{permissionName}")
    Response removePermission(
            @PathParam("docUuid") String docUuid,
            @PathParam("userUuid") String userUuid,
            @PathParam("permissionName") String permissionName
    );

    @DELETE
    @Path("/forDocForUser/{docUuid}/{userUuid}")
    Response removePermissionForDocumentForUser(
            @PathParam("docUuid") String docUuid,
            @PathParam("userUuid") String userUuid
    );

    @GET
    @Path("/forDoc/{docUuid}")
    Response getPermissionsForDocument(
            @PathParam("docUuid") String docUuid);

    @DELETE
    @Path("/forDoc/{docUuid}")
    Response clearDocumentPermissions(
            @PathParam("docUuid") String docUuid
    );
}
