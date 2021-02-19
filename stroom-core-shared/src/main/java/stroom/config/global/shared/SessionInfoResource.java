package stroom.config.global.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.SessionInfo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Session Info")
@Path("/sessionInfo" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SessionInfoResource extends RestResource, DirectRestService {

    @GET
    @Operation(
            summary = "Get information for the current session",
            operationId = "getSessionInfo")
    SessionInfo get();
}
