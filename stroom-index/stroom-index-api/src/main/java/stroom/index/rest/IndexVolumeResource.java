package stroom.index.rest;


import io.swagger.annotations.Api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(value = "stroom-index volumes - /v1")
@Path("/stroom-index/volume/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface IndexVolumeResource {

    @GET
    @Path("/inGroup/{groupName}")
    Response getVolumesInGroup(@PathParam("groupName") String groupName);
}
