package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDesc;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

@Tag(name = "Authorisation")
@Path("/users" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface UserResource extends RestResource, DirectRestService {

    @POST
    @Path("/find")
    @Operation(
            summary = "Find the user names matching the supplied criteria of users who belong to at least " +
                    "one of the same groups as the current user. If the current user is admin or has " +
                    "Manage Users permission then they can see all users.",
            operationId = "findUsersByCriteria")
    ResultPage<User> find(@Parameter(description = "criteria", required = true) FindUserCriteria criteria);

//    @GET
//    @Path("/{userUuid}")
//    @Operation(
//            summary = "Fetches the user with the supplied UUID",
//            operationId = "fetchUser")
//    User fetch(@PathParam("userUuid") String userUuid);

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
    User createUser(@Parameter(description = "name", required = true) UserDesc userDesc);

    @POST
    @Path("/createUsers")
    @Operation(
            summary = "Creates a batch of users from a list of CSV entries. Each line is of the form " +
                    "'id,displayName,fullName', where displayName and fullName are optional",
            operationId = "createUsers")
    List<User> createUsersFromCsv(@Parameter(description = "users", required = true) String usersCsvData);

    @POST
    @Path("/updateUser")
    @Operation(
            summary = "Updates a user",
            operationId = "updateUser")
    User update(@Parameter(description = "user", required = true) User user);

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
}
