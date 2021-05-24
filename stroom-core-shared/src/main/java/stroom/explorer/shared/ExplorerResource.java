/*
 * Copyright 2017 Crown Copyright
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
import stroom.docref.DocRefInfo;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
    DocRef create(@Parameter(description = "request", required = true) ExplorerServiceCreateRequest request);

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
    DocRef rename(@Parameter(description = "request", required = true) ExplorerServiceRenameRequest request);

    @POST
    @Path("/info")
    @Operation(
            summary = "Get document info",
            operationId = "fetchExplorerItemInfo")
    DocRefInfo info(@Parameter(description = "docRef", required = true) DocRef docRef);

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
            @Parameter(description = "request", required = true) FindExplorerNodeCriteria request);

    @POST
    @Path("/listQuickFindResults")
    @Operation(
            summary = "List quick find results",
            operationId = "listQuickFindResults")
    QuickFindResults listQuickFindResults(
            @Parameter(description = "quickFilter", required = true) QuickFindCriteria quickFindCriteria);
}
