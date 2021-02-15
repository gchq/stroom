package stroom.index.impl;

import stroom.index.shared.IndexVolumeGroup;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

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

@Api(tags = "Stroom-Index Volume Groups")
@Path("/stroom-index/volumeGroup" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface NewUIIndexVolumeGroupResource extends RestResource {

    @GET
    @Path("/names")
    @ApiOperation(
            value = "Get all index volume group names.",
            response = String.class,
            responseContainer = "List")
    Response getNames();

    @GET
    @ApiOperation(
            value = "Get all index volume groups.",
            response = IndexVolumeGroup.class,
            responseContainer = "List")
    Response getAll();

    @GET
    @Path("/{id}")
    @ApiOperation(
            value = "Get the index volume group identified by the provided ID",
            response = IndexVolumeGroup.class)
    Response get(@PathParam("id") String id);

    @POST
    @ApiOperation(
            value = "Create a new index volume group",
            response = IndexVolumeGroup.class)
    Response create();

    @PUT
    @ApiOperation(
            value = "Update an index volume group",
            response = IndexVolumeGroup.class)
    Response update(@ApiParam("indexVolumeGroup") IndexVolumeGroup indexVolumeGroup);

    @DELETE
    @Path("/{id}")
    @ApiOperation(
            value = "Delete the index volume group identified by the provided ID",
            response = IndexVolumeGroup.class)
    Response delete(@PathParam("id") String id);
}
