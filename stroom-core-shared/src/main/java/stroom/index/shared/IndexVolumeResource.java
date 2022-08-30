package stroom.index.shared;

import stroom.entity.shared.ExpressionCriteria;
import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Tag(name = "Index Volumes")
@Path(IndexVolumeResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface IndexVolumeResource extends RestResource, DirectRestService, FetchWithIntegerId<IndexVolume> {

    String BASE_PATH = "/index/volume" + ResourcePaths.V2;
    String RESCAN_SUB_PATH = "/rescan";

    @POST
    @Path("find")
    @Operation(
            summary = "Finds index volumes matching request",
            operationId = "findIndexVolumes")
    ResultPage<IndexVolume> find(@Parameter(description = "request", required = true) ExpressionCriteria request);

    @POST
    @Path("/validate")
    @Operation(
            summary = "Validates an index volume",
            operationId = "validateIndexVolume")
    ValidationResult validate(@Parameter(description = "request", required = true) IndexVolume request);

    @POST
    @Operation(
            summary = "Creates an index volume",
            operationId = "createIndexVolume")
    IndexVolume create(@Parameter(description = "request", required = true) IndexVolume request);

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Fetch an index volume",
            operationId = "fetchIndexVolume")
    IndexVolume fetch(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Updates an index volume",
            operationId = "updateIndexVolume")
    IndexVolume update(@PathParam("id") Integer id,
                       @Parameter(description = "indexVolume", required = true) IndexVolume indexVolume);

    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Deletes an index volume",
            operationId = "deleteIndexVolume")
    Boolean delete(@PathParam("id") Integer id);

    @GET
    @Path(RESCAN_SUB_PATH)
    @Operation(
            summary = "Rescans index volumes",
            operationId = "rescanIndexVolumes")
    Boolean rescan(@QueryParam("nodeName") String nodeName);
}
