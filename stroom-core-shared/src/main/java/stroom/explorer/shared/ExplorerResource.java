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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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

@Api(tags = "Explorer (v2)")
@Path("/explorer" + ResourcePaths.V2)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ExplorerResource extends RestResource, DirectRestService {

    @POST
    @Path("/create")
    @ApiOperation("Create explorer item")
    DocRef create(@ApiParam("request") ExplorerServiceCreateRequest request);

    @DELETE
    @Path("/delete")
    @ApiOperation("Delete explorer items")
    BulkActionResult delete(@ApiParam("request") ExplorerServiceDeleteRequest request);

    @POST
    @Path("/copy")
    @ApiOperation("Copy explorer items")
    BulkActionResult copy(@ApiParam("request") ExplorerServiceCopyRequest request);

    @PUT
    @Path("/move")
    @ApiOperation("Move explorer items")
    BulkActionResult move(@ApiParam("request") ExplorerServiceMoveRequest request);

    @PUT
    @Path("/rename")
    @ApiOperation("Rename explorer items")
    DocRef rename(@ApiParam("request") ExplorerServiceRenameRequest request);

    @POST
    @Path("/info")
    @ApiOperation("Get document info")
    DocRefInfo info(@ApiParam("docRef") DocRef docRef);

    @POST
    @Path("/fetchDocRefs")
    @ApiOperation("Fetch document references")
    Set<DocRef> fetchDocRefs(@ApiParam("docRefs") Set<DocRef> docRefs);

    @GET
    @Path("/fetchDocumentTypes")
    @ApiOperation("Fetch document types")
    DocumentTypes fetchDocumentTypes();

    @POST
    @Path("/fetchExplorerPermissions")
    @ApiOperation("Fetch permissions for explorer items")
    Set<ExplorerNodePermissions> fetchExplorerPermissions(@ApiParam("explorerNodes") List<ExplorerNode> explorerNodes);

    @POST
    @Path("/fetchExplorerNodes")
    @ApiOperation("Fetch explorer nodes")
    FetchExplorerNodeResult fetch(@ApiParam("request") FindExplorerNodeCriteria request);
}
