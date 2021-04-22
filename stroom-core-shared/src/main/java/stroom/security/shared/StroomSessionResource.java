package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Tag(name = "Stroom Sessions")
@Path("/stroomSession" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface StroomSessionResource extends RestResource, DirectRestService {

    @GET
    @Path("/noauth/validateSession")
    @Operation(
            summary = "Validate the current session, return a redirect Uri if invalid.",
            operationId = "validateStroomSession")
    ValidateSessionResponse validateSession(@QueryParam("redirect_uri") @NotNull String redirectUri);

    @GET
    @Path("logout")
    @Operation(
            summary = "Logout of Stroom",
            operationId = "stroomLogout")
    UrlResponse logout(@QueryParam("redirect_uri") @NotNull String redirectUri);
}
