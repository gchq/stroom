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

package stroom.pipeline.shared.stepping;

import stroom.docref.DocRef;
import stroom.docstore.shared.Doc;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "stepping - /v1")
@Path("/stepping" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SteppingResource extends RestResource, DirectRestService {
    @POST
    @Path("/getPipelineForStepping")
    @ApiOperation(
            value = "Get a pipeline for stepping",
            response = DocRef.class)
    DocRef getPipelineForStepping(@ApiParam("request") GetPipelineForMetaRequest request);

    @POST
    @Path("/findElementDoc")
    @ApiOperation(
            value = "Load the document for an element",
            response = Doc.class)
    DocRef findElementDoc(FindElementDocRequest request);

    @POST
    @Path("/step")
    @ApiOperation(
            value = "Step a pipeline",
            response = SteppingResult.class)
    SteppingResult step(@ApiParam("request") PipelineStepRequest request);
}
