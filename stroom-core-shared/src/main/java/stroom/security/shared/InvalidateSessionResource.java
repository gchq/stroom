package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "stroomSession - /v1")
@Path("/stroomSession" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface InvalidateSessionResource extends RestResource, DirectRestService {
    @GET
    @Path("invalidate")
    @ApiOperation(
            value = "Invalidate the current session",
            response = Boolean.class)
    Boolean invalidate();
}
