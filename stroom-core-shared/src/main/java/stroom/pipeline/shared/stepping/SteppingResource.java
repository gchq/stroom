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

package stroom.pipeline.shared.stepping;

import stroom.docref.DocRef;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Stepping")
@Path("/stepping" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SteppingResource extends RestResource, DirectRestService {

    @POST
    @Path("/getPipelineForStepping")
    @Operation(
            summary = "Get a pipeline for stepping",
            operationId = "getPipelineForStepping")
    DocRef getPipelineForStepping(
            @Parameter(description = "request", required = true) GetPipelineForMetaRequest request);

    @POST
    @Path("/findElementDoc")
    @Operation(
            summary = "Load the document for an element",
            operationId = "findElementDoc")
    DocRef findElementDoc(FindElementDocRequest request);

    @POST
    @Path("/step")
    @Operation(
            summary = "Step a pipeline",
            operationId = "step")
    SteppingResult step(@Parameter(description = "request", required = true) PipelineStepRequest request);

    @POST
    @Path("/terminateStepping")
    @Operation(
            summary = "Terminate pipeline stepping process",
            operationId = "terminateStepping")
    Boolean terminateStepping(@Parameter(description = "request", required = true) PipelineStepRequest request);
}
