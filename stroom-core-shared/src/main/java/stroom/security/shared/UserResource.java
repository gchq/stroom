package stroom.security.shared;

import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
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
    @Path("/create/{name}/{isGroup}")
    @Operation(
            summary = "Creates a user or group with the supplied name",
            operationId = "createUser")
    User create(@PathParam("name") String name,
                @PathParam("isGroup") Boolean isGroup);

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
    List<String> getAssociates(@QueryParam("filter") String filter);
}
