package stroom.security.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "authentication - /v1")
@Path("/logout" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public interface AuthenticationResource extends RestResource, DirectRestService {
    @GET
    @Path("gwt_logout")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Logout the current session",
            response = Boolean.class)
    Boolean logout();
}
