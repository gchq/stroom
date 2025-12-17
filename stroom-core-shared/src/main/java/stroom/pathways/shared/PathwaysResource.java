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

package stroom.pathways.shared;

import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Pathways")
@Path(PathwaysResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PathwaysResource extends RestResource, DirectRestService, FetchWithUuid<PathwaysDoc> {

    String BASE_PATH = "/pathways" + ResourcePaths.V2;
    String FIND_PATHWAYS_SUB_PATH = "/findPathways";

    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch pathways doc by its UUID",
            operationId = "fetchPathways")
    PathwaysDoc fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update an pathways doc",
            operationId = "updatePathways")
    PathwaysDoc update(@PathParam("uuid") String uuid,
                       @Parameter(description = "doc", required = true) PathwaysDoc doc);

    @POST
    @Path(FIND_PATHWAYS_SUB_PATH)
    @Operation(
            summary = "Find pathways",
            operationId = "findPathways")
    PathwayResultPage findPathways(
            @Parameter(description = "criteria", required = true) FindPathwayCriteria criteria);

    @POST
    @Path("/addPathway")
    @Operation(
            summary = "Add pathway",
            operationId = "addPathway")
    Boolean addPathway(
            @Parameter(description = "change", required = true) AddPathway addPathway);

    @PUT
    @Path("/updatePathway")
    @Operation(
            summary = "Update pathway",
            operationId = "updatePathway")
    Boolean updatePathway(
            @Parameter(description = "change", required = true) UpdatePathway updatePathway);

    @DELETE
    @Path("/deletePathway")
    @Operation(
            summary = "Delete pathway",
            operationId = "deletePathway")
    Boolean deletePathway(
            @Parameter(description = "change", required = true) DeletePathway deletePathway);
}
