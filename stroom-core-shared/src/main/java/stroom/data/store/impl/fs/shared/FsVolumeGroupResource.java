package stroom.data.store.impl.fs.shared;

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


@Tag(name = "Data Volume Groups")
@Path("/fsVolume/volumeGroup" + ResourcePaths.V2)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface FsVolumeGroupResource extends RestResource, DirectRestService,
        FetchWithIntegerId<FsVolumeGroup> {

    @POST
    @Path("find")
    @Operation(
            summary = "Finds data volume groups matching request",
            operationId = "findFsVolumeGroups")
    ResultPage<FsVolumeGroup> find(
            @Parameter(description = "request", required = true) ExpressionCriteria request);

    @POST
    @Operation(
            summary = "Creates a data volume group",
            operationId = "createFsVolumeGroup")
    FsVolumeGroup create(@Parameter(description = "volumeGroup", required = true) FsVolumeGroup volumeGroup);

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Gets a data volume group",
            operationId = "fetchFsVolumeGroup")
    FsVolumeGroup fetch(@PathParam("id") Integer id);

    @GET
    @Path("/fetchByName/{name}")
    @Operation(
            summary = "Gets a data volume group by name",
            operationId = "fetchFsVolumeGroupByName")
    FsVolumeGroup fetchByName(@PathParam("name") String name);

    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Updates a data volume group",
            operationId = "updateFsVolumeGroup")
    FsVolumeGroup update(@PathParam("id") Integer id, FsVolumeGroup volumeGroup);

    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Deletes a data volume group",
            operationId = "deleteFsVolumeGroup")
    Boolean delete(@PathParam("id") Integer id);
}
