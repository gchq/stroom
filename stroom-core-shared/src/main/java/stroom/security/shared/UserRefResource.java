package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Authorisation")
@Path("/userRef" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface UserRefResource extends RestResource, DirectRestService {

    @POST
    @Path("/find")
    @Operation(
            summary = "Find the users and groups matching the supplied criteria of users who belong to at least " +
                      "one of the same groups as the current user. If the current user is admin or has " +
                      "Manage Users permission then they can see all users.",
            operationId = "findUserRefs")
    ResultPage<UserRef> find(@Parameter(description = "criteria", required = true) FindUserCriteria criteria);

    @POST
    @Path("/getUserByUuid")
    @Operation(
            summary = "Resolve a user ref by UUID",
            operationId = "getUserByUuid")
    UserRef getUserByUuid(@Parameter(description = "request", required = true) GetUserRequest request);
}
