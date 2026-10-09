/*
 * Copyright 2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.document.asset.shared;

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

@Tag(name = "DocumentAssets")
@Path("/visualisationAssets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DocumentAssetResource extends RestResource, DirectRestService {

    /**
     * Fetches the assets associated with a document.
     * @param ownerDocId The ID of the owning doc.
     * @return Assets associated with the doc.
     */
    @GET
    @Path("/fetchDraftAssets/{ownerDocId}")
    @Operation(
            summary = "Fetch the assets belonging to a document by the doc's UUID",
            operationId = "fetchDraftAssets")
    DocumentAssets fetchDraftAssets(@PathParam("ownerDocId") String ownerDocId)
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
                          @Parameter(description = "Path and Mimetype", required = false)
                          DocumentAssetUpdateNewFile update);

    @PUT
    @Path("/updateNewUploadedFile/{ownerDocId}")
    @Operation(
            summary = "Creates a new uploaded file",
            operationId = "updateNewUploadedFile")
    Boolean updateNewUploadedFile(@PathParam("ownerDocId") String ownerDocId,
                                  @Parameter(description = "Path, ResourceKey and Mimetype", required = true)
                                  DocumentAssetUpdateNewFile update);

    @PUT
    @Path("/updateDelete/{ownerDocId}")
    @Operation(
            summary = "Deletes an asset",
            operationId = "updateDelete")
    Boolean updateDelete(@PathParam("ownerDocId") String ownerDocId,
                         @Parameter(description = "Path and isFolder", required = true)
                         DocumentAssetUpdateDelete update);

    @PUT
    @Path("/updateRename/{ownerDocId}")
    @Operation(
            summary = "Renames an asset",
            operationId = "updateRename")
    Boolean updateRename(@PathParam("ownerDocId") String ownerDocId,
                         @Parameter(description = "Old Path, New Path and isFolder", required = true)
                         DocumentAssetUpdateRename update);

    @PUT
    @Path("/updateContent/{ownerDocId}")
    @Operation(
            summary = "Updates the content of an asset",
            operationId = "updateContent")
    Boolean updateContent(@PathParam("ownerDocId") String ownerDocId,
                          @Parameter(description = "Path and Content", required = true)
                          DocumentAssetUpdateContent update);

    @GET
    @Path("/getContent/{ownerDocId}")
    @Operation(
            summary = "Gets the content of an asset for editing",
            operationId = "getContent"
    )
    DocumentAssetContent getDraftContent(@PathParam("ownerDocId") String ownerDocId,
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

    @PUT
    @Path("/saveAs/{fromOwnerDocId}")
    @Operation(
            summary = "Save the document's assets to another document",
            operationId = "saveAs")
    Boolean saveAs(@PathParam("fromOwnerDocId") String fromOwnerDocId,
                   @Parameter(description = "SaveAs parameters", required = true)
                   DocumentAssetSaveAsParameters updateParameters);

    @GET
    @Path("/saveAs/{ownerDocId}")
    @Operation(
            summary = "Check if an index.html asset exists for the document ID",
            operationId = "indexAssetExists")
    Boolean indexAssetExists(@PathParam("ownerDocId") String ownerDocId);

}
