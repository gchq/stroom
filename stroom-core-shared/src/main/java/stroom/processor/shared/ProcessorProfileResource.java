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

import stroom.entity.shared.ExpressionCriteria;
import stroom.util.shared.FetchWithIntegerId;
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
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Processor Profiles")
@Path("/processorProfile" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ProcessorProfileResource extends RestResource, DirectRestService,
        FetchWithIntegerId<ProcessorProfile> {

    @POST
    @Path("find")
    @Operation(
            summary = "Finds processor profiles matching request",
            operationId = "findProcessorProfiles")
    ResultPage<ProcessorProfile> find(
            @Parameter(description = "request", required = true) ExpressionCriteria request);

    @POST
    @Operation(
            summary = "Creates a processor profile",
            operationId = "createProcessorProfile")
    ProcessorProfile create(@Parameter(description = "name", required = true) String name);

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Gets a processor profile",
            operationId = "fetchProcessorProfile")
    ProcessorProfile fetch(@PathParam("id") Integer id);

    @GET
    @Path("/fetchByName/{name}")
    @Operation(
            summary = "Gets a processor profile by name",
            operationId = "fetchProcessorProfileByName")
    ProcessorProfile fetchByName(@PathParam("name") String name);

    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Updates a processor profile",
            operationId = "updateProcessorProfile")
    ProcessorProfile update(@PathParam("id") Integer id, ProcessorProfile nodeGroup);

    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Deletes a processor profile",
            operationId = "deleteProcessorProfile")
    Boolean delete(@PathParam("id") Integer id);
}
