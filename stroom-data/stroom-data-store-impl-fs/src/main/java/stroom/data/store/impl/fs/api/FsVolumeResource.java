package stroom.data.store.impl.fs.api;

import io.swagger.annotations.Api;
import stroom.data.store.impl.fs.FsVolumeService;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(value = "datavolumes - /v1")
@Path("/datavolumes" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public class FsVolumeResource implements RestResource {
    private FsVolumeService fsVolumeService;

    @Inject
    public FsVolumeResource(final FsVolumeService fsVolumeService){
        this.fsVolumeService = fsVolumeService;
    }

    /**
     * We won't have a huge number of data volumes so we're not going to do any
     * complicated paging and filtering until we need to. This method just returns
     * all the data volumes.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response fetchAll(){
        var findAll = new FindFsVolumeCriteria();
        findAll.getStatusSet().setMatchAll(true);
        ResultPage<FsVolume> volumes = fsVolumeService.find(findAll);
        return Response.ok(volumes).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(){
        var fsVolume = new FsVolume();
        fsVolume.setPath("");
        var result = fsVolumeService.create(fsVolume);
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") final int id) {
        fsVolumeService.delete(id);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") final int id, final FsVolume toUpdate) {
        var updated = fsVolumeService.update(toUpdate);
        return Response.ok(updated).build();
    }
}
