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

@Tag(name = "Pipelines")
@Path("/pipeline" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PipelineResource extends RestResource, DirectRestService, FetchWithUuid<PipelineDoc> {

    @GET
    @Path("/{uuid}")
    @Operation(summary = "Fetch a pipeline doc by its UUID")
    PipelineDoc fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(summary = "Update a pipeline doc")
    PipelineDoc update(@PathParam("uuid") String uuid,
                       @Parameter(description = "doc", required = true) PipelineDoc doc);

    @PUT
    @Path("/savePipelineXml")
    @Operation(summary = "Update a pipeline doc with XML directly")
    Boolean savePipelineXml(@Parameter(description = "request", required = true) SavePipelineXmlRequest request);

    @POST
    @Path("/fetchPipelineXml")
    @Operation(summary = "Fetch the XML for a pipeline")
    FetchPipelineXmlResponse fetchPipelineXml(@Parameter(description = "pipeline", required = true) DocRef pipeline);

    @POST
    @Path("/fetchPipelineData")
    @Operation(summary = "Fetch data for a pipeline")
    List<PipelineData> fetchPipelineData(@Parameter(description = "pipeline", required = true) DocRef pipeline);

    @GET
    @Path("/propertyTypes")
    @Operation(summary = "Get pipeline property types")
    List<FetchPropertyTypesResult> getPropertyTypes();
}
