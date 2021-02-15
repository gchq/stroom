package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(tags = "Application Permissions")
@Path("/permission/app" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AppPermissionResource extends RestResource, DirectRestService {

    @GET
    @ApiOperation("User and app permissions for the current session")
    UserAndPermissions getUserAndPermissions();

    @POST
    @Path("fetchUserAppPermissions")
    @ApiOperation("User and app permissions for the specified user")
    UserAndPermissions fetchUserAppPermissions(@ApiParam("user") User user);

    @GET
    @Path("fetchAllPermissions")
    @ApiOperation("Get all possible permissions")
    List<String> fetchAllPermissions();

    @POST
    @Path("changeUser")
    @ApiOperation("User and app permissions for the current session")
    Boolean changeUser(@ApiParam("changeUserRequest") ChangeUserRequest changeUserRequest);
}
