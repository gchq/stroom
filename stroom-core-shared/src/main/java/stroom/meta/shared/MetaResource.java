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

package stroom.meta.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
            @Parameter(description = "criteria", required = true) FindMetaCriteria criteria);

    @POST
    @Path("getReprocessSelectionSummary")
    @Operation(
            summary = "Get a summary of the parent items of the selected meta data",
            operationId = "getMetaReprocessSelectionSummary")
    SelectionSummary getReprocessSelectionSummary(
            @Parameter(description = "criteria", required = true) FindMetaCriteria criteria);

    @GET
    @Path("getTypes")
    @Operation(
            summary = "Get a list of possible stream types",
            operationId = "getStreamTypes")
    List<String> getTypes();
}
