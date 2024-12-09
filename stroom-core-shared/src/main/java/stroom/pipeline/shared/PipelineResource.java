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

package stroom.pipeline.shared;

import stroom.docref.DocRef;
import stroom.pipeline.shared.data.PipelineData;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

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

@Tag(name = "Pipelines")
@Path("/pipeline" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PipelineResource extends RestResource, DirectRestService, FetchWithUuid<PipelineDoc> {

    @POST
    @Path("/")
    @Operation(
            summary = "Create a pipeline doc",
            operationId = "createPipeline")
    PipelineDoc create();

    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch a pipeline doc by its UUID",
            operationId = "fetchPipeline")
    PipelineDoc fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update a pipeline doc",
            operationId = "updatePipeline")
    PipelineDoc update(@PathParam("uuid") String uuid,
                       @Parameter(description = "doc", required = true) PipelineDoc doc);

    @PUT
    @Path("/savePipelineXml")
    @Operation(
            summary = "Update a pipeline doc with XML directly",
            operationId = "savePipelineXml")
    Boolean savePipelineXml(@Parameter(description = "request", required = true) SavePipelineXmlRequest request);

    @POST
    @Path("/fetchPipelineXml")
    @Operation(
            summary = "Fetch the XML for a pipeline",
            operationId = "fetchPipelineXml")
    FetchPipelineXmlResponse fetchPipelineXml(@Parameter(description = "pipeline", required = true) DocRef pipeline);

    @POST
    @Path("/fetchPipelineData")
    @Operation(
            summary = "Fetch data for a pipeline",
            operationId = "fetchPipelineData")
    List<PipelineData> fetchPipelineData(@Parameter(description = "pipeline", required = true) DocRef pipeline);

    @GET
    @Path("/propertyTypes")
    @Operation(
            summary = "Get pipeline property types",
            operationId = "getPipelinePropertyTypes")
    List<FetchPropertyTypesResult> getPropertyTypes();
}
