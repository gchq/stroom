package stroom.security.impl;

import io.swagger.annotations.Api;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

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
@Path("/appPermissions" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public interface UserAppPermissionResource extends RestResource {

    @GET
    @Path("/{userUuid}")
    Response getPermissionNamesForUser(@PathParam("userUuid") String userUuid);

    @GET
    @Path("/byName/{userName}")
    Response getPermissionNamesForUserName(@PathParam("userName") String userName);

    @GET
    Response getAllPermissionNames();

    @POST
    @Path("/{userUuid}/{permission}")
    Response addPermission(@PathParam("userUuid") String userUuid,
                           @PathParam("permission") String permission);

    @DELETE
    @Path("/{userUuid}/{permission}")
    Response removePermission(@PathParam("userUuid") String userUuid,
                              @PathParam("permission") String permission);
}
