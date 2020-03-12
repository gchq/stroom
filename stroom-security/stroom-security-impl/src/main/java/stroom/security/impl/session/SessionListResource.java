package stroom.security.impl.session;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "sessionList - /v1")
@Path(SessionListResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public interface SessionListResource extends RestResource {

    String BASE_PATH = "/sessionList" + ResourcePaths.V1;




}
