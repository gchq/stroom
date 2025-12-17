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

package stroom.meta.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

@Tag(name = "Meta")
@Path("/meta" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface MetaResource extends RestResource, DirectRestService {

    @GET
    @Path("{id}")
    @Operation(
            summary = "Get a meta record for a given id, if permitted.",
            operationId = "fetch")
    Meta fetch(@PathParam("id") final long id);

    @PUT
    @Path("update/status")
    @Operation(
            summary = "Update status on matching meta data",
            operationId = "updateMetaStatus")
    Integer updateStatus(UpdateStatusRequest request);

    @POST
    @Path("find")
    @Operation(
            summary = "Find matching meta data",
            operationId = "findMetaRow")
    ResultPage<MetaRow> findMetaRow(
            @Parameter(description = "criteria", required = true) FindMetaCriteria criteria);

    @POST
    @Path("getSelectionSummary")
    @Operation(
            summary = "Get a summary of the selected meta data",
            operationId = "getMetaSelectionSummary")
    SelectionSummary getSelectionSummary(
            @Parameter(description = "request", required = true) SelectionSummaryRequest request);

    @POST
    @Path("getReprocessSelectionSummary")
    @Operation(
            summary = "Get a summary of the parent items of the selected meta data",
            operationId = "getMetaReprocessSelectionSummary")
    SelectionSummary getReprocessSelectionSummary(
            @Parameter(description = "request", required = true) SelectionSummaryRequest request);

    @GET
    @Path("getTypes")
    @Operation(
            summary = "Get a list of possible stream types",
            operationId = "getStreamTypes")
    List<String> getTypes();
}
