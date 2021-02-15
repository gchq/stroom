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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Api(tags = "Index Volumes")
@Path(IndexVolumeResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface IndexVolumeResource extends RestResource, DirectRestService {

    String BASE_PATH = "/index/volume" + ResourcePaths.V2;
    String RESCAN_SUB_PATH = "/rescan";

    @POST
    @Path("find")
    @ApiOperation("Finds index volumes matching request")
    ResultPage<IndexVolume> find(@ApiParam("request") ExpressionCriteria request);

    @POST
    @ApiOperation("Creates an index volume")
    IndexVolume create(@ApiParam("request") IndexVolume request);

    @GET
    @Path("/{id}")
    @ApiOperation("Gets an index volume")
    IndexVolume read(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @ApiOperation("Updates an index volume")
    IndexVolume update(@PathParam("id") Integer id,
                       @ApiParam("indexVolume") IndexVolume indexVolume);

    @DELETE
    @Path("/{id}")
    @ApiOperation("Deletes an index volume")
    Boolean delete(@PathParam("id") Integer id);

    @DELETE
    @Path(RESCAN_SUB_PATH)
    @ApiOperation("Rescans index volumes")
    Boolean rescan(@QueryParam("nodeName") String nodeName);
}
