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

package stroom.processor.shared;

import stroom.util.shared.FetchWithIntegerId;
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

import java.util.List;

@Tag(name = "Processor Filters")
@Path("/processorFilter" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ProcessorFilterResource extends RestResource, DirectRestService, FetchWithIntegerId<ProcessorFilter> {

    @POST
    @Path("find")
    @Operation(
            summary = "Finds processors and filters matching request",
            operationId = "findProcessorFilters")
    ProcessorListRowResultPage find(
            @Parameter(description = "request", required = true) FetchProcessorRequest request);

    @POST
    @Operation(
            summary = "Creates a filter",
            operationId = "createProcessorFilter")
    ProcessorFilter create(
            @Parameter(description = "request", required = true) CreateProcessFilterRequest request);


    @POST
    @Path("/reprocess")
    @Operation(
            summary = "Create filters to reprocess data",
            operationId = "reprocessData")
    List<ReprocessDataInfo> reprocess(
            @Parameter(description = "criteria", required = true) CreateProcessFilterRequest request);

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Fetch a filter",
            operationId = "fetchProcessorFilter")
    ProcessorFilter fetch(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Updates a filter",
            operationId = "updateProcessorFilter")
    ProcessorFilter update(@PathParam("id") Integer id, ProcessorFilter processorFilter);

    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Deletes a filter",
            operationId = "deleteProcessorFilter")
    boolean delete(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}/priority")
    @Operation(
            summary = "Sets the priority for a filter",
            operationId = "setProcessorFilterPriority")
    boolean setPriority(@PathParam("id") Integer id, Integer priority);

    @PUT
    @Path("/{id}/maxProcessingTasks")
    @Operation(
            summary = "Sets the optional cluster-wide limit on the number of tasks that may be processed for this " +
                    "filter, at any one time",
            operationId = "setProcessorFilterMaxProcessingTasks")
    boolean setMaxProcessingTasks(@PathParam("id") Integer id, Integer maxProcessingTasks);


    @PUT
    @Path("/{id}/enabled")
    @Operation(
            summary = "Sets the enabled/disabled state for a filter",
            operationId = "setProcessorFilterEnabled")
    boolean setEnabled(@PathParam("id") Integer id, Boolean enabled);

    @POST
    @Path("/bulkChange")
    @Operation(
            summary = "Bulk change processors",
            operationId = "bulkChange")
    Boolean bulkChange(
            @Parameter(description = "request", required = true) BulkProcessorFilterChangeRequest request);
}
