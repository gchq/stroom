package stroom.security.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
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
import java.util.List;

@Api(
        value = "authorisation - /v1",
        description = "Stroom Authorisation API")
@Path("/users" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public interface UserResource extends RestResource, DirectRestService {
    @GET
    List<User> get(@QueryParam("name") String name,
                   @QueryParam("isGroup") Boolean isGroup,
                   @QueryParam("uuid") String uuid);

    @GET
    @Path("/{userUuid}")
    User get(@PathParam("userUuid") String userUuid);

    @GET
    @Path("/usersInGroup/{groupUuid}")
    List<User> findUsersInGroup(@PathParam("groupUuid") String groupUuid);

    @GET
    @Path("/groupsForUserName/{userName}")
    List<User> findGroupsForUserName(@PathParam("userName") String userName);

    @GET
    @Path("/groupsForUser/{userUuid}")
    List<User> findGroupsForUser(@PathParam("userUuid") String userUuid);

    @POST
    @Path("/create/{name}/{isGroup}")
    User create(@PathParam("name") String name,
                @PathParam("isGroup") Boolean isGroup);

    @DELETE
    @Path("/{uuid}")
    void deleteUser(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{userName}/status")
    void setStatus(@PathParam("userName") String userName, @QueryParam("enabled") boolean status);

    @PUT
    @Path("/{userUuid}/{groupUuid}")
    void addUserToGroup(@PathParam("userUuid") String userUuid,
                        @PathParam("groupUuid") String groupUuid);

    @DELETE
    @Path("/{userUuid}/{groupUuid}")
    void removeUserFromGroup(@PathParam("userUuid") String userUuid,
                             @PathParam("groupUuid") String groupUuid);

    @GET
    @Path("associates")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Gets a list of associated users",
            response = Response.class)
    List<String> getAssociates(@QueryParam("filter") String filter);
}
