package stroom.security.rest;

import io.swagger.annotations.Api;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(
        value = "application permissions - /v1",
        description = "Stroom Application Permissions API")
@Path("/appPermissions/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface UserAppPermissionResource {

    @GET
    @Path("/{userUuid}")
    Response getPermissionNamesForUser(@PathParam("userUuid") String userUuid);

    @GET
    Response getAllPermissionNames();

    @POST
    @Path("/{userUuid}/{groupUuid}")
    Response addPermission(@PathParam("userUuid") String userUuid,
                           @PathParam("groupUuid") String groupUuid);

    @DELETE
    @Path("/{userUuid}/{groupUuid}")
    Response removePermission(@PathParam("userUuid") String userUuid,
                              @PathParam("groupUuid") String groupUuid);
}
