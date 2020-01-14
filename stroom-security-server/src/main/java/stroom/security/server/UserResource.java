package stroom.security.server;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.security.SecurityContext;
import stroom.security.shared.UserRef;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

@Api(
        value = "user - /v1",
        description = "Stroom Users API")
@Path("/users/v1")
@Produces(MediaType.APPLICATION_JSON)
@Component
public class UserResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserResource.class);

    private final SecurityContext securityContext;
    private final UserService userService;

    private static final Map<String, UserStatus> STATUS_MAPPINGS = new HashMap<String, UserStatus>() {
        {
            put("locked", UserStatus.LOCKED);
            put("inactive", UserStatus.EXPIRED);
            put("active", UserStatus.ENABLED);
            put("enabled", UserStatus.ENABLED);
            put("disabled", UserStatus.DISABLED);
        }
    };

    @Inject
    public UserResource(final SecurityContext securityContext, UserService userService) {
        this.securityContext = securityContext;
        this.userService = userService;
    }

    /**
     * This function is used by the Users UI to create a Stroom user for authorisation purposes.
     * It solves the problem of Users having to log in before they're available to assign permissions to.
     */
    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createUser(@ApiParam("NewUser") NewUser newUser) {
        try{
            UserRef existingUser = userService.getUserByName(newUser.getName());
            if(existingUser == null){
                userService.createUser(newUser.getName());
            }
            return Response.status(Response.Status.NO_CONTENT).build();
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
