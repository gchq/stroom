package stroom.config.global.shared;

import io.swagger.annotations.Api;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.SessionInfo;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "sessionInfo - /v1")
@Path("/sessionInfo" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SessionInfoResource extends RestResource, DirectRestService {
    @GET
    SessionInfo get();
}
