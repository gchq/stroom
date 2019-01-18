package stroom.security.rest;

import io.swagger.annotations.Api;
import stroom.security.shared.UserJooq;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Api(
        value = "authorisation - /v1",
        description = "Stroom Authorisation API")
@Path("/users/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface UserResource {
    @GET
    Response get(@QueryParam("name") String name,
                       @QueryParam("isGroup") Boolean isGroup,
                       @QueryParam("uuid") String uuid);

    @GET
    @Path("/usersInGroup/{groupName}")
    Response findUsersInGroup(@PathParam("groupName") String groupUuid);

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
