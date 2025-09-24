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

package stroom.planb.impl.data;

import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Used to remotely query Plan B for traces
 */
@Tag(name = "Traces Query")
@Path(TracesRemoteQueryResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface TracesRemoteQueryResource extends RestResource {

    String BASE_PATH = "/traces" + ResourcePaths.V1;
    String GET_TRACES_PATH = "/getTraces";
    String GET_TRACE_PATH = "/getTrace";

    @POST
    @Path(GET_TRACES_PATH)
    @Operation(
            summary = "Gets traces from a remote Plan B store.",
            operationId = "tracesGetTraces")
    TracesResultPage getTraces(FindTraceCriteria criteria);

    @POST
    @Path(GET_TRACE_PATH)
    @Operation(
            summary = "Gets trace from a remote Plan B store.",
            operationId = "tracesGetTrace")
    Trace getTrace(GetTraceRequest request);
}
