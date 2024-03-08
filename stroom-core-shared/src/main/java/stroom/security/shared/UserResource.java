package stroom.security.shared;

import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

@Tag(name = "Authorisation")
@Path("/users" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface UserResource extends RestResource, DirectRestService, FetchWithUuid<User> {

    @GET
    @Operation(
            summary = "Find the users matching the supplied criteria",
            operationId = "findUsers")
    List<User> find(@QueryParam("name") String name,
                    @QueryParam("isGroup") Boolean isGroup,
                    @QueryParam("uuid") String uuid);

    @POST
    @Path("/find")
    @Operation(
            summary = "Find the users matching the supplied criteria",
            operationId = "findUsersByCriteria")
    ResultPage<User> find(@Parameter(description = "criteria", required = true) FindUserCriteria criteria);

    @GET
    @Path("/{userUuid}")
    @Operation(
            summary = "Fetches the user with the supplied UUID",
            operationId = "fetchUser")
    User fetch(@PathParam("userUuid") String userUuid);

    @POST
    @Path("/createGroup")
    @Operation(
            summary = "Creates a group with the supplied name",
            operationId = "createGroup")
    User createGroup(@Parameter(description = "name", required = true) String name);

    @POST
    @Path("/createUser")
    @Operation(
            summary = "Creates a user with the supplied name",
            operationId = "createUser")
    User createUser(@Parameter(description = "name", required = true) UserName name);

    @POST
    @Path("/createUsers")
    @Operation(
            summary = "Creates a batch of users from a list of CSV entries. Each line is of the form " +
                    "'id,displayName,fullName', where displayName and fullName are optional",
            operationId = "createUsers")
    List<User> createUsersFromCsv(@Parameter(description = "users", required = true) String usersCsvData);

    @DELETE
    @Path("/{uuid}")
    @Operation(
            summary = "Deletes the user with the supplied UUID",
            operationId = "deleteUser")
    Boolean delete(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{userUuid}/{groupUuid}")
    @Operation(
            summary = "Adds user with UUID userUuid to the group with UUID groupUuid",
            operationId = "addUserToGroup")
    Boolean addUserToGroup(@PathParam("userUuid") String userUuid,
                           @PathParam("groupUuid") String groupUuid);

    @DELETE
    @Path("/{userUuid}/{groupUuid}")
    @Operation(
            summary = "Removes user with UUID userUuid from the group with UUID groupUuid",
            operationId = "removeUserFromGroup")
    Boolean removeUserFromGroup(@PathParam("userUuid") String userUuid,
                                @PathParam("groupUuid") String groupUuid);

    @GET
    @Path("associates")
    @Operation(
            summary = "Gets a list of associated users",
            operationId = "getAssociatedUsers")
    List<UserName> getAssociates(@QueryParam("filter") String filter);
}
