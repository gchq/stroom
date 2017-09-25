package stroom.security.server;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(
        value = "authorisation - /v1",
        description = "Stroom Authorisation API")
@Path("/authorisation/v1")
@Produces(MediaType.APPLICATION_JSON)
public class AuthorisationResource {
    private final AuthorisationService authorisationService;

    public AuthorisationResource(final AuthorisationService authorisationService) {
        this.authorisationService = authorisationService;
    }

    /**
     * Authenticates using JWT
     */
    @POST
    @Path("isAuthorised")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    @ApiOperation(
            value = "Submit a request to verify if the user has the requested permission on a 'document'",
            response = Response.class)
    public Response isAuthorised(@ApiParam("AuthorisationRequest") final AuthorisationRequest authorisationRequest) {

        boolean result = authorisationService.hasDocumentPermission(
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
}
