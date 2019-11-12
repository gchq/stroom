package stroom.index.impl.api;


import io.swagger.annotations.Api;
import stroom.index.impl.CreateVolumeDTO;
import stroom.index.impl.UpdateVolumeDTO;
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

@Api(value = "stroom-index volumes - /v1")
@Path("/stroom-index/volume/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface IndexVolumeResource extends RestResource {

    @GET
    Response getAll();

    @GET
    @Path("{id}")
    Response getById(@PathParam("id") int id);

    @POST
    Response create(CreateVolumeDTO createVolumeDTO);

    @PUT
    Response update(UpdateVolumeDTO updateVolumeDTO);

    @DELETE
    @Path("{id}")
    Response delete(@PathParam("id") int id);

//    /**
//     * Retrieve the list of volumes that are within a group
//     * @param groupName The name of the group
//     * @return The list of Index Volumes in that group
//     */
//    @GET
//    @Path("/inGroup/{groupName}")
//    Response getVolumesInGroup(@PathParam("groupName") String groupName);
//
//    /**
//     * Retrieve the list of groups that a given volume belongs to
//     * @param id The ID of the volume
//     * @return The list of Index Volume Groups for that volume
//     */
//    @GET
//    @Path("/groupsFor/{id}")
//    Response getGroupsForVolume(@PathParam("id") int id);
//
//    /**
//     * Add a volume to the membership of a group.
//     * @param volumeId The ID of the volume
//     * @param groupName the name of the group.
//     * @return Empty response if all went well
//     */
//    @POST
//    @Path("/inGroup/{volumeId}/{groupName}")
//    Response addVolumeToGroup(@PathParam("volumeId") int volumeId,
//                              @PathParam("groupName") String groupName);
//
//    /**
//     * Remove a volume from the membership of a group.
//     * @param volumeId The ID of the volume
//     * @param groupName the name of the group.
//     * @return Empty response if all went well
//     */
//    @DELETE
//    @Path("/inGroup/{volumeId}/{groupName}")
//    Response removeVolumeFromGroup(@PathParam("volumeId") int volumeId,
//                                   @PathParam("groupName") String groupName);
}
