package stroom.resources.authorisation.v1;

import com.codahale.metrics.annotation.Timed;
import stroom.resources.ResourcePaths;
import stroom.security.Insecure;
import stroom.security.server.AuthorisationService;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path(ResourcePaths.AUTHORISATION + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Insecure
public class AuthorisationResource {

    private AuthorisationService authorisationService;

    /**
     * Authenticates using JWT
     */
    @POST
    @Path("isAuthorised")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    public Response isAuthorisedForStatistic(AuthorisationRequest authorisationRequest) {
        boolean result = authorisationService.hasDocumentPermission(
                authorisationRequest.getDocRef().getType(),
                authorisationRequest.getDocRef().getUuid(),
                authorisationRequest.getPermissions());

        return result
                ? Response
                .ok()
                .build()
                : Response
                .status(Response.Status.UNAUTHORIZED)
                .build();
    }

    public void setAuthorisationService(AuthorisationService authorisationService) {
        this.authorisationService = authorisationService;
    }

}
