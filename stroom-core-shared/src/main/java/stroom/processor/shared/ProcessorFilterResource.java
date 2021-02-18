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

package stroom.processor.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Processor Filters")
@Path("/processorFilter" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ProcessorFilterResource extends RestResource, DirectRestService {

    @POST
    @Path("find")
    @Operation(summary = "Finds processors and filters matching request")
    ProcessorListRowResultPage find(
            @Parameter(description = "request", required = true) FetchProcessorRequest request);

    @POST
    @Operation(summary = "Creates a filter")
    ProcessorFilter create(
            @Parameter(description = "request", required = true) CreateProcessFilterRequest request);


    @POST
    @Path("/reprocess")
    @Operation(summary = "Create filters to reprocess data")
    List<ReprocessDataInfo> reprocess(
            @Parameter(description = "criteria", required = true) CreateReprocessFilterRequest request);

    @GET
    @Path("/{id}")
    @Operation(summary = "Gets a filter")
    ProcessorFilter read(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Updates a filter")
    ProcessorFilter update(@PathParam("id") Integer id, ProcessorFilter processorFilter);

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Deletes a filter")
    void delete(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}/priority")
    @Operation(summary = "Sets the priority for a filter")
    void setPriority(@PathParam("id") Integer id, Integer priority);

    @PUT
    @Path("/{id}/enabled")
    @Operation(summary = "Sets the enabled/disabled state for a filter")
    void setEnabled(@PathParam("id") Integer id, Boolean enabled);
}
