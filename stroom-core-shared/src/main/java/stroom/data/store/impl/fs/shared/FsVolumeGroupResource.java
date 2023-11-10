package stroom.data.store.impl.fs.shared;

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
import javax.ws.rs.core.MediaType;

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
    FsVolumeGroup create(@Parameter(description = "name", required = true) String name);

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
