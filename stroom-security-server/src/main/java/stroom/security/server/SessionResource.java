package stroom.security.server;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * This is used by the OpenId flow - when a user request a log out the remote authentication service
 * needs to ask all relying parties to log out. This is the back-channel resource that allows this to
 * happen.
 */
@Api(
    value = "session - /v1",
    description = "Stroom Session API")
@Path("/session/v1")
@Produces(MediaType.APPLICATION_JSON)
@Component
public class SessionResource {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SessionResource.class);
    private AuthenticationService authenticationService;

    @Inject
    public SessionResource(AuthenticationService authenticationService){
        this.authenticationService = authenticationService;
    }

    @GET
    @Path("logout/{sessionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Logs the specified session out of Stroom",
            response = Response.class)
    public Response logout(@PathParam("sessionId") String sessionId){
        LOGGER.info("Logging out session {}", sessionId);
        authenticationService.logout();
        return Response.status(Response.Status.OK).entity("Logout successful").build();
    }
}
