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

package stroom.analytics.shared;

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

@Tag(name = "ExecutionSchedule")
@Path("/executionSchedule" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ExecutionScheduleResource
        extends RestResource, DirectRestService {

    @POST
    @Path("/createExecutionSchedule")
    @Operation(
            summary = "Create Execution Schedule",
            operationId = "createExecutionSchedule")
    ExecutionSchedule createExecutionSchedule(@Parameter(description = "executionSchedule", required = true)
                                              ExecutionSchedule executionSchedule);

    @POST
    @Path("/updateExecutionSchedule")
    @Operation(
            summary = "Update Execution Schedule",
            operationId = "updateExecutionSchedule")
    ExecutionSchedule updateExecutionSchedule(@Parameter(description = "executionSchedule", required = true)
                                              ExecutionSchedule executionSchedule);

    @POST
    @Path("/deleteExecutionSchedule")
    @Operation(
            summary = "Delete Execution Schedule",
            operationId = "deleteExecutionSchedule")
    Boolean deleteExecutionSchedule(@Parameter(description = "executionSchedule", required = true)
                                    ExecutionSchedule executionSchedule);

    @POST
    @Path("/fetchExecutionSchedule")
    @Operation(
            summary = "Fetch execution schedule",
            operationId = "fetchExecutionSchedule")
    ResultPage<ExecutionSchedule> fetchExecutionSchedule(@Parameter(description = "request", required = true)
                                                         ExecutionScheduleRequest request);

    @POST
    @Path("/fetchExecutionHistory")
    @Operation(
            summary = "Fetch execution history",
            operationId = "fetchExecutionHistory")
    ResultPage<ExecutionHistory> fetchExecutionHistory(@Parameter(description = "request", required = true)
                                                       ExecutionHistoryRequest request);

    @POST
    @Path("/fetchTracker")
    @Operation(
            summary = "Fetch execution tracker",
            operationId = "fetchTracker")
    ExecutionTracker fetchTracker(@Parameter(description = "request", required = true)
                                  ExecutionSchedule schedule);
}
