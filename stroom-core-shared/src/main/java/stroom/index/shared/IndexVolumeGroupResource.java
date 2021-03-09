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
import javax.ws.rs.core.MediaType;

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
    IndexVolumeGroup create(@Parameter(description = "name", required = true) String name);

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Gets an index volume group",
            operationId = "fetchIndexVolumeGroup")
    IndexVolumeGroup fetch(@PathParam("id") Integer id);

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
