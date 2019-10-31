package stroom.index.impl.api;

import io.swagger.annotations.Api;
import stroom.index.shared.IndexVolumeGroup;
import stroom.util.RestResource;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(value = "stroom-index volumeGroup - /v1")
@Path("/stroom-index/volumeGroup/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface IndexVolumeGroupResource extends RestResource {

    @GET
    @Path("/names")
    Response getNames();

    @GET
    Response getAll();

    @GET
    @Path("/{id}")
    Response get(@PathParam("id") String id);

    @POST
    Response create();

    @PUT
    Response update(IndexVolumeGroup indexVolumeGroup);

    @DELETE
    @Path("/{id}")
    Response delete(@PathParam("id") String id);
}
