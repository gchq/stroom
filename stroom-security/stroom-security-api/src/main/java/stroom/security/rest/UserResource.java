package stroom.security.rest;

import io.swagger.annotations.Api;
import stroom.security.shared.UserJooq;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api(
        value = "authorisation - /v1",
        description = "Stroom Authorisation API")
@Path("/users/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface UserResource {
    @GET
    List<UserJooq> get(@QueryParam("name") String name,
                       @QueryParam("isGroup") Boolean isGroup,
                       @QueryParam("id") Long id,
                       @QueryParam("uuid") String uuid);

    @GET
    @Path("/usersInGroup/{groupName}")
    List<UserJooq> findUsersInGroup(@PathParam("groupName") String groupUuid);

    @GET
    @Path("/groupsForUser/{userUuid}")
    List<UserJooq> findGroupsForUser(@PathParam("userUuid") String userUuid);

    @POST
    UserJooq create(CreateDTO createDTO);

    @DELETE
    @Path("/{uuid}")
    Boolean deleteUser(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{userUuid}/{groupUuid}")
    Boolean addUserToGroup(@PathParam("userUuid") String userUuid,
                           @PathParam("groupUuid") String groupUuid);

    @DELETE
    @Path("/{userUuid}/{groupUuid}")
    Boolean removeUserFromGroup(@PathParam("userUuid") String userUuid,
                                @PathParam("groupUuid") String groupUuid);
}
