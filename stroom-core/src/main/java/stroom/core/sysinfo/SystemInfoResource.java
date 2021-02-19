package stroom.core.sysinfo;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.sysinfo.SystemInfoResultList;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "System Info")
@Path(SystemInfoResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SystemInfoResource extends RestResource {

    String BASE_PATH = "/systemInfo" + ResourcePaths.V1;
    String NAMES_PATH_PART = "/names";

    @GET
    @Operation(
            summary = "Get all system info results",
            operationId = "getAllSystemInfo")
    SystemInfoResultList getAll();

    @GET
    @Path(NAMES_PATH_PART)
    @Operation(
            summary = "Get all system info result names",
            operationId = "getSystemInfoNames")
    List<String> getNames();

    @GET
    @Path("/{name}")
    @Operation(
            summary = "Get a system info result by name",
            operationId = "getSystemInfoByName")
    SystemInfoResult get(@PathParam("name") final String name);
}
