package stroom.resources.authorisation.v1;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import stroom.resources.ResourcePaths;
import stroom.security.SecurityContext;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(
        value = "authorisation - " + ResourcePaths.V1,
        description = "Stroom Authorisation API")
@Path(ResourcePaths.AUTHORISATION + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public class AuthorisationResource {

    private SecurityContext securityContext;

    @GET
    @Path("/")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    @Timed
    @NotNull
    public final Response welcome() {
        return Response.status(Response.Status.OK).entity("Welcome to the authorisation service").build();
    }

    @POST
    @Path("isAuthorised")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
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
    @Timed
    public Response canManageUsers(UserPermissionRequest userPermissionRequest) {
        // TODO what happens if the permission is bad? What's the result of this method call and how should we handle it?
        boolean result = securityContext.hasAppPermission(userPermissionRequest.getPermission());
        // The user here will be the one logged in by the JWT.
        return result ? Response.ok().build() : Response.status(Response.Status.UNAUTHORIZED).build();
    }

    public void setSecurityContext(SecurityContext securityContext) {
        this.securityContext = securityContext;
    }
}