package stroom.data.store.impl.fs.api;

import io.swagger.annotations.Api;
import stroom.data.store.impl.fs.FsVolumeService;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(value = "datavolumes - /v1")
@Path("/datavolumes" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FsVolumeResource implements RestResource {
    private final FsVolumeService fsVolumeService;

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
    public Response fetchAll(){
        var findAll = new FindFsVolumeCriteria();
        findAll.getSelection().setMatchAll(true);
        ResultPage<FsVolume> volumes = fsVolumeService.find(findAll);
        return Response.ok(volumes).build();
    }

    @POST
    public Response create(){
        var fsVolume = new FsVolume();
        fsVolume.setPath("");
        var result = fsVolumeService.create(fsVolume);
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") final int id) {
        fsVolumeService.delete(id);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") final int id, final FsVolume toUpdate) {
        var updated = fsVolumeService.update(toUpdate);
        return Response.ok(updated).build();
    }
}
