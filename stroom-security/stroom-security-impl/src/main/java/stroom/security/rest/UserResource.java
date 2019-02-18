package stroom.security.rest;

import io.swagger.annotations.Api;
import stroom.util.RestResource;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(
        value = "authorisation - /v1",
        description = "Stroom Authorisation API")
@Path("/users/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface UserResource extends RestResource {
    @GET
    Response get(@QueryParam("name") String name,
                       @QueryParam("isGroup") Boolean isGroup,
                       @QueryParam("uuid") String uuid);

    @GET
    @Path("/usersInGroup/{groupUuid}")
    Response findUsersInGroup(@PathParam("groupUuid") String groupUuid);

    @GET
    @Path("/groupsForUser/{userUuid}")
    Response findGroupsForUser(@PathParam("userUuid") String userUuid);

    @POST
    Response create(CreateDTO createDTO);

    @DELETE
    @Path("/{uuid}")
    Response deleteUser(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{userUuid}/{groupUuid}")
    Response addUserToGroup(@PathParam("userUuid") String userUuid,
                        @PathParam("groupUuid") String groupUuid);

    @DELETE
    @Path("/{userUuid}/{groupUuid}")
    Response removeUserFromGroup(@PathParam("userUuid") String userUuid,
                             @PathParam("groupUuid") String groupUuid);
}
