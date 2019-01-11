package stroom.security;

import com.google.common.base.Strings;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.shared.UserRef;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

@Api(
        value = "authorisation - /v1",
        description = "Stroom Authorisation API")
@Path("/authorisation/v1")
@Produces(MediaType.APPLICATION_JSON)
public class AuthorisationResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorisationResource.class);

    private final SecurityContext securityContext;
    private final UserService userService;

    private static final Map<String, UserStatus> STATUS_MAPPINGS = new HashMap<String, UserStatus>() {
        {
            put("locked", UserStatus.LOCKED);
            put("inactive", UserStatus.EXPIRED);
            put("active", UserStatus.ENABLED);
            put("disabled", UserStatus.DISABLED);
        }

    };

    @Inject
    public AuthorisationResource(final SecurityContext securityContext, UserService userService) {
        this.securityContext = securityContext;
        this.userService = userService;
    }

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
    public Response isAuthorised(@ApiParam("AuthorisationRequest") final AuthorisationRequest authorisationRequest) {

        boolean result = securityContext.hasDocumentPermission(
                authorisationRequest.getDocRef().getType(),
                authorisationRequest.getDocRef().getUuid(),
                authorisationRequest.getPermission());

        return result
                ? Response
                .ok()
                .build()
                : Response
                .status(Response.Status.UNAUTHORIZED)
                .build();
    }

    @POST
    @Path("canManageUsers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response canManageUsers(UserPermissionRequest userPermissionRequest) {
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
        try {
            UserRef existingUser = userService.getUserByName(userId);
            if (existingUser == null) {
                userService.createUser(userId);
            }
            return Response.ok().build();
        } catch (Exception e) {
            LOGGER.error("Unable to create user: {}", e.getMessage());
            return Response.serverError().build();
        }
    }

    /**
     * Added alongside canManagerUsers so that this endpoint still keeps working.
     */
    @POST
    @Path("hasAppPermission")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response hasAppPermission(@NotNull UserPermissionRequest userPermissionRequest) {
        if (Strings.isNullOrEmpty(userPermissionRequest.getPermission())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Please supply a permission.").build();
        }
        // TODO what happens if the permission is bad? What's the result of this method call and how should we handle it?
        boolean result = securityContext.hasAppPermission(userPermissionRequest.getPermission());
        // The user here will be the one logged in by the JWT.
        return result ? Response.ok().build() : Response.status(Response.Status.UNAUTHORIZED).build();
    }

    /**
     * Updates the user's status
     */
    @POST
    @Path("setUserStatus")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setUserStatus(@QueryParam("id") String userId, @QueryParam("status") String status) {
        try{
            UserStatus newUserStatus = STATUS_MAPPINGS.get(status);
            UserRef existingUser = userService.getUserByName(userId);
            if(existingUser != null){
                User user = userService.loadByUuid(existingUser.getUuid());
                user.updateStatus(newUserStatus);
                userService.save(user);
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
