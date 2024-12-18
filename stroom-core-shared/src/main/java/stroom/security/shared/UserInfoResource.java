package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.UserInfo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Authorisation")
@Path("/userInfo" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface UserInfoResource extends RestResource, DirectRestService {

    /**
     * Fetch the userInfo of a user that may or may not have been deleted.
     */
    @GET
    @Path("/{userUuid}")
    @Operation(
            summary = "Fetches the userInfo with the supplied user UUID",
            operationId = "fetchUser")
    UserInfo fetch(@PathParam("userUuid") String userUuid);
}
