/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.explorer.shared;

import stroom.docref.DocRef;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import java.util.Set;

@Tag(name = "Explorer (v2)")
@Path("/explorer" + ResourcePaths.V2)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ExplorerResource extends RestResource, DirectRestService {

    @POST
    @Path("/create")
    @Operation(
            summary = "Create explorer item",
            operationId = "createExplorerItem")
    ExplorerNode create(@Parameter(description = "request", required = true) ExplorerServiceCreateRequest request);

    @DELETE
    @Path("/delete")
    @Operation(
            summary = "Delete explorer items",
            operationId = "deleteExplorerItems")
    BulkActionResult delete(@Parameter(description = "request", required = true) ExplorerServiceDeleteRequest request);

    @POST
    @Path("/copy")
    @Operation(
            summary = "Copy explorer items",
            operationId = "copyExplorerItems")
    BulkActionResult copy(@Parameter(description = "request", required = true) ExplorerServiceCopyRequest request);

    @PUT
    @Path("/move")
    @Operation(
            summary = "Move explorer items",
            operationId = "moveExplorerItems")
    BulkActionResult move(@Parameter(description = "request", required = true) ExplorerServiceMoveRequest request);

    @PUT
    @Path("/rename")
    @Operation(
            summary = "Rename explorer items",
            operationId = "renameExplorerItems")
    ExplorerNode rename(@Parameter(description = "request", required = true) ExplorerServiceRenameRequest request);

    @PUT
    @Path("/tags")
    @Operation(
            summary = "Update explorer node tags",
            operationId = "updateExplorerNodeTags")
    ExplorerNode updateNodeTags(
            @Parameter(description = "request", required = true) ExplorerNode explorerNode);

    @PUT
    @Path("/addTags")
    @Operation(
            summary = "Add tags to explorer nodes",
            operationId = "addTags")
    void addTags(
            @Parameter(description = "request", required = true) AddRemoveTagsRequest request);

    @DELETE
    @Path("/removeTags")
    @Operation(
            summary = "Remove tags from explorer nodes",
            operationId = "removeTags")
    void removeTags(
            @Parameter(description = "request", required = true) AddRemoveTagsRequest request);

    @POST
    @Path("/info")
    @Operation(
            summary = "Get document info",
            operationId = "fetchExplorerItemInfo")
    ExplorerNodeInfo info(@Parameter(description = "docRef", required = true) DocRef docRef);

    /**
     * @param decorateRequest
     * @return A {@link DocRef} if the doc could be found and any required permissions are met. The
     * {@link DocRef} will either be the same as passed, have a different name or have a name when
     * the passed {@link DocRef} didn't. If the doc is not found or the user doesn't have the required
     * permissions then null will be returned.
     */
    @POST
    @Path("/decorate")
    @Operation(
            summary = "Decorate the docRef will all values, e.g. add the name",
            operationId = "decorateDocRef")
    DocRef decorate(@Parameter(description = "decorateRequest", required = true) DecorateRequest decorateRequest);

    @POST
    @Path("/getFromDocRef")
    @Operation(
            summary = "Get a node from a document reference, decorated with its root node UUID",
            operationId = "getRootNodeRef")
    ExplorerNode getFromDocRef(@Parameter(description = "docRef", required = true) DocRef docRef);

    @POST
    @Path("/fetchDocRefs")
    @Operation(
            summary = "Fetch document references",
            operationId = "fetchExplorerDocRefs")
    Set<DocRef> fetchDocRefs(@Parameter(description = "docRefs", required = true) Set<DocRef> docRefs);

    @GET
    @Path("/fetchDocumentTypes")
    @Operation(
            summary = "Fetch document types",
            operationId = "fetchExplorerDocumentTypes")
    DocumentTypes fetchDocumentTypes();

    @GET
    @Path("/fetchExplorerNodeTags")
    @Operation(
            summary = "Fetch all known explorer node tags",
            operationId = "fetchExplorerNodeTags")
    Set<String> fetchExplorerNodeTags();

    @POST
    @Path("/fetchExplorerNodeTagsByDocRefs")
    @Operation(
            summary = "Fetch explorer node tags held by at least one of decRefs",
            operationId = "fetchExplorerNodeTagsByDocRefs")
    Set<String> fetchExplorerNodeTags(final List<DocRef> docRefs);

    @POST
    @Path("/fetchExplorerPermissions")
    @Operation(
            summary = "Fetch permissions for explorer items",
            operationId = "fetchExplorerPermissions")
    Set<ExplorerNodePermissions> fetchExplorerPermissions(
            @Parameter(description = "explorerNodes", required = true) List<ExplorerNode> explorerNodes);

    @POST
    @Path("/fetchExplorerNodes")
    @Operation(
            summary = "Fetch explorer nodes",
            operationId = "fetchExplorerNodes")
    FetchExplorerNodeResult fetchExplorerNodes(
            @Parameter(description = "request", required = true) FetchExplorerNodesRequest request);

    @POST
    @Path("/find")
    @Operation(
            summary = "Find documents with names and types matching the supplied request",
            operationId = "find")
    ResultPage<FindResult> find(
            @Parameter(description = "request", required = true) DocumentFindRequest request);

    @POST
    @Path("/advancedFind")
    @Operation(
            summary = "Find documents with names and types matching the supplied request",
            operationId = "advancedFind")
    ResultPage<FindResult> advancedFind(
            @Parameter(description = "request", required = true) AdvancedDocumentFindRequest request);

    @POST
    @Path("/advancedFindWithPermissions")
    @Operation(
            summary = "Find documents with names and types matching the supplied request and the accociated " +
                    "permissions for a given user",
            operationId = "advancedFindWithPermissions")
    ResultPage<FindResultWithPermissions> advancedFindWithPermissions(
            @Parameter(description = "request", required = true) AdvancedDocumentFindWithPermissionsRequest request);

    @POST
    @Path("/findInContent")
    @Operation(
            summary = "Find documents with content matching the supplied request",
            operationId = "findInContent")
    ResultPage<FindInContentResult> findInContent(
            @Parameter(description = "request", required = true) FindInContentRequest request);

    @POST
    @Path("/fetchHighlights")
    @Operation(
            summary = "Fetch match highlights on found content",
            operationId = "fetchHighlights")
    DocContentHighlights fetchHighlights(
            @Parameter(description = "request", required = true) FetchHighlightsRequest request);

    @POST
    @Path("/changeDocumentPermissions")
    @Operation(
            summary = "Change document permissions",
            operationId = "changeDocumentPermissions")
    Boolean changeDocumentPermissions(
            @Parameter(description = "request", required = true) BulkDocumentPermissionChangeRequest request);

    // --------------------------------------------------------------------------------


    enum TagFetchMode {
        /**
         * Tags held by ALL nodes.
         */
        AND,
        /**
         * Tags held by at least one node
         */
        OR
    }
}
