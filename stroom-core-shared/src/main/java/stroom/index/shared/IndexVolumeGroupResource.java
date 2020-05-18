package stroom.index.shared;

import stroom.entity.shared.ExpressionCriteria;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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

@Api(value = "index volumeGroup - /v2")
@Path("/index/volumeGroup" + ResourcePaths.V2)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface IndexVolumeGroupResource extends RestResource, DirectRestService {
    @POST
    @Path("find")
    @ApiOperation(
            value = "Finds index volume groups matching request",
            response = ResultPage.class)
    ResultPage<IndexVolumeGroup> find(@ApiParam("request") ExpressionCriteria request);

    @POST
    @ApiOperation(
            value = "Creates an index volume group",
            response = IndexVolumeGroup.class)
    IndexVolumeGroup create(@ApiParam("name") String name);

    @GET
    @Path("/{id}")
    @ApiOperation(
            value = "Gets an index volume group",
            response = IndexVolumeGroup.class)
    IndexVolumeGroup read(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @ApiOperation(
            value = "Updates an index volume group",
            response = IndexVolumeGroup.class)
    IndexVolumeGroup update(@PathParam("id") Integer id, IndexVolumeGroup indexVolumeGroup);

    @DELETE
    @Path("/{id}")
    @ApiOperation(
            value = "Deletes an index volume group",
            response = Boolean.class)
    Boolean delete(@PathParam("id") Integer id);
}
