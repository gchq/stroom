package stroom.index.shared;

import stroom.entity.shared.ExpressionCriteria;
import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Index Volume Groups")
@Path("/index/volumeGroup" + ResourcePaths.V2)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface IndexVolumeGroupResource extends RestResource, DirectRestService,
        FetchWithIntegerId<IndexVolumeGroup> {

    @POST
    @Path("find")
    @Operation(
            summary = "Finds index volume groups matching request",
            operationId = "findIndexVolumeGroups")
    ResultPage<IndexVolumeGroup> find(
            @Parameter(description = "request", required = true) ExpressionCriteria request);

    @POST
    @Operation(
            summary = "Creates an index volume group",
            operationId = "createIndexVolumeGroup")
    IndexVolumeGroup create(@Parameter(description = "indexVolumeGroup", required = true) IndexVolumeGroup indexVolumeGroup);

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Gets an index volume group",
            operationId = "fetchIndexVolumeGroup")
    IndexVolumeGroup fetch(@PathParam("id") Integer id);

    @GET
    @Path("/fetchByName/{name}")
    @Operation(
            summary = "Gets an index volume group by name",
            operationId = "fetchIndexVolumeGroupByName")
    IndexVolumeGroup fetchByName(@PathParam("name") String name);

    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Updates an index volume group",
            operationId = "updateIndexVolumeGroup")
    IndexVolumeGroup update(@PathParam("id") Integer id, IndexVolumeGroup indexVolumeGroup);

    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Deletes an index volume group",
            operationId = "deleteIndexVolumeGroup")
    Boolean delete(@PathParam("id") Integer id);
}
