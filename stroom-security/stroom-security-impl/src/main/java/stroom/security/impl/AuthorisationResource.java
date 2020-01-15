package stroom.security.impl;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.SecurityContext;
import stroom.security.shared.User;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(
        value = "authorisation - /v1",
        description = "Stroom Authorisation API")
@Path("/authorisation/v1")
@Produces(MediaType.APPLICATION_JSON)
public class AuthorisationResource implements RestResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorisationResource.class);

    private final SecurityContext securityContext;
    private final UserService userService;

    @Inject
    public AuthorisationResource(final SecurityContext securityContext, UserService userService) {
        this.securityContext = securityContext;
        this.userService = userService;
    }

    //TODO: Is this used?

    /**
     * Authenticates using JWT
     */
    @POST
    @Path("isAuthorised")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
        value = "Submit a request to verify if the user has the requested permission on a 'document'",
        response = Response.class)
    public Response isAuthorised(@ApiParam("permission") final String permission) {

        boolean result = securityContext.hasAppPermission(permission);

        return result
                ? Response.ok().build()
                : Response.status(Response.Status.UNAUTHORIZED).build();
    }

    @POST
    @Path("hasPermission")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response hasPermission(UserPermissionRequest userPermissionRequest) {
        // TODO what happens if the permission is bad? What's the result of this method call and how should we handle it?
        boolean result = securityContext.hasAppPermission(userPermissionRequest.getPermission());
        // The user here will be the one logged in by the JWT.
        return result ? Response.ok().build() : Response.status(Response.Status.UNAUTHORIZED).build();
    }

    /**
     * This function is used by the Users UI to create a Stroom user for authorisation purposes.
     * It solves the problem of Users having to log in before they're available to assign permissions to.
     */
    @POST
    @Path("createUser")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createUser(@QueryParam("id") String userId) {
        try{
            User existingUser = userService.getUserByName(userId);
            if(existingUser == null){
                userService.createUser(userId);
            }
            return Response.ok().build();
        }
        catch(Exception e){
            LOGGER.error("Unable to create user: {}", e.getMessage());
            return Response.serverError().build();
        }
    }

    /**
     * Updates the user's status
     */
    @GET
    @Path("setUserStatus")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setUserStatus(@QueryParam("userId") String userId, @QueryParam("status") String status) {
        try{
            boolean isEnabled = status.equals("active") || status.equals("enabled");
            User existingUser = userService.getUserByName(userId);
            if(existingUser != null){
                User user = userService.loadByUuid(existingUser.getUuid());
                user.setEnabled(isEnabled);
                userService.update(user);
                return Response.ok().build();
            }
            else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        }
        catch(Exception e){
            LOGGER.error("Unable to change user's status: {}", e.getMessage());
            return Response.serverError().build();
        }
    }
}
