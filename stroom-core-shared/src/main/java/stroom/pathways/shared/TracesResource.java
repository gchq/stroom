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

package stroom.pathways.shared;

import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Traces")
@Path(TracesResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface TracesResource extends RestResource, DirectRestService {

    String BASE_PATH = "/traces" + ResourcePaths.V2;
    String FIND_TRACES_SUB_PATH = "/findTraces";
    String FIND_TRACE_SUB_PATH = "/findTrace";

    @POST
    @Path(FIND_TRACES_SUB_PATH)
    @Operation(
            summary = "Find traces",
            operationId = "findTraces")
    ResultPage<TraceRoot> findTraces(
            @Parameter(description = "criteria", required = true) FindTraceCriteria criteria);

    @POST
    @Path(FIND_TRACE_SUB_PATH)
    @Operation(
            summary = "Find trace",
            operationId = "findTrace")
    Trace findTrace(
            @Parameter(description = "request", required = true) GetTraceRequest request);
}
