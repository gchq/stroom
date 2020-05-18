package stroom.data.store.impl.fs.api;

import stroom.data.store.impl.fs.FsVolumeService;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;

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

@Api(value = "datavolumes - /v1")
@Path("/datavolumes" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FsVolumeResource implements RestResource {
    private final FsVolumeService fsVolumeService;

    @Inject
    public FsVolumeResource(final FsVolumeService fsVolumeService) {
        this.fsVolumeService = fsVolumeService;
    }

    /**
     * We won't have a huge number of data volumes so we're not going to do any
     * complicated paging and filtering until we need to. This method just returns
     * all the data volumes.
     */
    @GET
    public ResultPage<FsVolume> fetchAll() {
        return fsVolumeService.find(FindFsVolumeCriteria.matchAll());
    }

    @POST
    public FsVolume create() {
        final FsVolume fsVolume = new FsVolume();
        fsVolume.setPath("");
        return fsVolumeService.create(fsVolume);
    }

    @DELETE
    @Path("/{id}")
    public Integer delete(@PathParam("id") final int id) {
        return fsVolumeService.delete(id);
    }

    @PUT
    @Path("/{id}")
    public FsVolume update(@PathParam("id") final int id, 
                           @ApiParam("toUpdate") final FsVolume toUpdate) {
        return fsVolumeService.update(toUpdate);
    }
}
