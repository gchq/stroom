package stroom.index.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.entity.shared.ExpressionCriteria;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

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

@Api(value = "index volume - /v2")
@Path(IndexVolumeResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface IndexVolumeResource extends RestResource, DirectRestService {
    String BASE_PATH = "/index/volume" + ResourcePaths.V2;
    String RESCAN_SUB_PATH = "/rescan";

    @POST
    @Path("find")
    @ApiOperation(
            value = "Finds index volumes matching request",
            response = ResultPage.class)
    ResultPage<IndexVolume> find(ExpressionCriteria request);

    @POST
    @ApiOperation(
            value = "Creates an index volume",
            response = IndexVolume.class)
    IndexVolume create(IndexVolume request);

    @GET
    @Path("/{id}")
    @ApiOperation(
            value = "Gets an index volume",
            response = IndexVolume.class)
    IndexVolume read(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @ApiOperation(
            value = "Updates an index volume",
            response = IndexVolume.class)
    IndexVolume update(@PathParam("id") Integer id, IndexVolume indexVolume);

    @DELETE
    @Path("/{id}")
    @ApiOperation(
            value = "Deletes an index volume",
            response = Boolean.class)
    Boolean delete(@PathParam("id") Integer id);

    @DELETE
    @Path(RESCAN_SUB_PATH)
    @ApiOperation(
            value = "Rescans index volumes",
            response = Boolean.class)
    Boolean rescan(@QueryParam("nodeName") String nodeName);
}
