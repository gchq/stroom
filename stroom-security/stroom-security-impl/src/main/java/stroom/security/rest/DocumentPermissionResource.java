package stroom.security.rest;

import io.swagger.annotations.Api;
import stroom.docref.DocRef;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Set;

@Api(
        value = "document permissions - /v1",
        description = "Stroom Document Permissions API")
@Path("/docPermissions/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface DocumentPermissionResource {

    @GET
    @Path("/forDocType/{docType}")
    Response getPermissionForDocType(@PathParam("docType") String docType);

    @GET
    @Path("/forDocForUser/{docType}/{docUuid}/{userUuid}")
    Response getPermissionsForDocumentForUser(
        @PathParam("docType") String docType,
        @PathParam("docUuid") String docUuid,
        @PathParam("userUuid") String userUuid
    );

    @POST
    @Path("/forDocForUser/{docType}/{docUuid}/{userUuid}/{permissionName}")
    Response addPermission(
            @PathParam("docType") String docType,
            @PathParam("docUuid") String docUuid,
            @PathParam("userUuid") String userUuid,
            @PathParam("permissionName") String permissionName
    );

    @DELETE
    @Path("/forDocForUser/{docType}/{docUuid}/{userUuid}/{permissionName}")
    Response removePermission(
            @PathParam("docType") String docType,
            @PathParam("docUuid") String docUuid,
            @PathParam("userUuid") String userUuid,
            @PathParam("permissionName") String permissionName
    );


    @DELETE
    @Path("/forDocForUser/{docType}/{docUuid}")
    Response clearDocumentPermissions(
            @PathParam("docType") String docType,
            @PathParam("docUuid") String docUuid
    );
}
