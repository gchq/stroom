package stroom.visualisation.shared;

import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "VisualisationAssets")
@Path("/visualisationAssets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface VisualisationAssetResource extends RestResource, DirectRestService {

    /**
     * Fetches the assets associated with a visualisation doc.
     * @param ownerDocId The ID of the owning doc.
     * @return Assets associated with the doc.
     */
    @GET
    @Path("/fetchDraftAssets/{ownerDocId}")
    @Operation(
            summary = "Fetch the assets belonging to a visualisation doc by the doc's UUID",
            operationId = "fetchDraftAssets")
    VisualisationAssets fetchDraftAssets(@PathParam("ownerDocId") String ownerDocId)
            throws RuntimeException;

    @PUT
    @Path("/updateNewFolder/{ownerDocId}")
    @Operation(
            summary = "Create new folder",
            operationId = "updateNewFolder")
    Boolean updateNewFolder(@PathParam("ownerDocId") String ownerDocId,
                            @Parameter(description = "Path") String path);

    @PUT
    @Path("/updateNewFile/{ownerDocId}")
    @Operation(
            summary = "Creates a new text file",
            operationId = "updateNewFile")
    Boolean updateNewFile(@PathParam("ownerDocId") String ownerDocId,
                          @Parameter(description="Path and Mimetype", required = false)
                          VisualisationAssetUpdateNewFile update);

    @PUT
    @Path("/updateNewUploadedFile/{ownerDocId}")
    @Operation(
            summary = "Creates a new uploaded file",
            operationId = "updateNewUploadedFile")
    Boolean updateNewUploadedFile(@PathParam("ownerDocId") String ownerDocId,
                                  @Parameter(description="Path, ResourceKey and Mimetype", required = true)
                                  VisualisationAssetUpdateNewFile update);

    @PUT
    @Path("/updateDelete/{ownerDocId}")
    @Operation(
            summary = "Deletes an asset",
            operationId = "updateDelete")
    Boolean updateDelete(@PathParam("ownerDocId") String ownerDocId,
                         @Parameter(description="Path and isFolder", required = true)
                         VisualisationAssetUpdateDelete update);

    @PUT
    @Path("/updateRename/{ownerDocId}")
    @Operation(
            summary = "Renames an asset",
            operationId = "updateRename")
    Boolean updateRename(@PathParam("ownerDocId") String ownerDocId,
                         @Parameter(description = "Old Path, New Path and isFolder", required = true)
                         VisualisationAssetUpdateRename update);

    @PUT
    @Path("/updateContent/{ownerDocId}")
    @Operation(
            summary = "Updates the content of an asset",
            operationId = "updateContent")
    Boolean updateContent(@PathParam("ownerDocId") String ownerDocId,
                          @Parameter(description="Path and Content", required = true)
                          VisualisationAssetUpdateContent update);

    @GET
    @Path("/getContent/{ownerDocId}")
    @Operation(
            summary = "Gets the content of an asset for editing",
            operationId = "getContent"
    )
    VisualisationAssetContent getDraftContent(@PathParam("ownerDocId") String ownerDocId,
                                              @QueryParam("path") String path);

    @PUT
    @Path("/saveDraftToLive/{ownerDocId}")
    @Operation(
            summary = "Save draft assets to main store to make them live",
            operationId = "saveDraftToLive")
    Boolean saveDraftToLive(@PathParam("ownerDocId") String ownerDocId)
        throws RuntimeException;

    @PUT
    @Path("/revertDraftFromLive/{ownerDocId}")
    @Operation(
            summary = "Revert draft assets to get rid of any changes",
            operationId = "revertDraftFromLive")
    Boolean revertDraftFromLive(@PathParam("ownerDocId") String ownerDocId)
        throws RuntimeException;

}
