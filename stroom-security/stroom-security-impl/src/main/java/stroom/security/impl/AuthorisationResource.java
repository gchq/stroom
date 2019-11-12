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

    //TODO Get rid of this and use UserResource instead
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
            User existingUser = userService.getUserByName(userId);
            if (existingUser == null) {
                userService.createUser(userId);
            }
            return Response.ok().build();
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to create user: {}", e.getMessage());
            return Response.serverError().build();
        }
    }

}
