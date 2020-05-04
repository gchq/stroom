package stroom.app.sysinfo;

import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.sysinfo.SystemInfoResultList;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api(value = "system info - /v1")
@Path(SystemInfoResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SystemInfoResource extends RestResource {

    String BASE_PATH = "/systemInfo" + ResourcePaths.V1;

    @GET
    @ApiOperation(
            value = "Get all system info results",
            response = SystemInfoResult.class)
    SystemInfoResultList getAll();

    @GET
    @Path("/{name}")
    @ApiOperation(
            value = "Get a system info result by name",
            response = List.class)
    SystemInfoResult get(@QueryParam("name") final String name);
}
