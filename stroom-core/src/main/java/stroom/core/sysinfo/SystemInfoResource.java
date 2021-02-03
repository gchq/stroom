package stroom.core.sysinfo;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.sysinfo.SystemInfoResultList;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api(tags = "System Info")
@Path(SystemInfoResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SystemInfoResource extends RestResource {

    String BASE_PATH = "/systemInfo" + ResourcePaths.V1;
    String NAMES_PATH_PART = "/names";

    @GET
    @ApiOperation(value = "Get all system info results")
    SystemInfoResultList getAll();

    @GET
    @Path(NAMES_PATH_PART)
    @ApiOperation(value = "Get all system info result names")
    List<String> getNames();

    @GET
    @Path("/{name}")
    @ApiOperation(value = "Get a system info result by name")
    SystemInfoResult get(@PathParam("name") final String name);
}
