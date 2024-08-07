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

import stroom.entity.shared.ExpressionCriteria;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Processor Tasks")
@Path(ProcessorTaskResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ProcessorTaskResource extends RestResource, DirectRestService {

    String BASE_PATH = "/processorTask" + ResourcePaths.V1;
    String ASSIGN_TASKS_PATH_PART = "/assign";
    String ABANDON_TASKS_PATH_PART = "/abandon";
    String NODE_NAME_PATH_PARAM = "/{nodeName}";

    @POST
    @Path("find")
    @Operation(
            summary = "Finds processors tasks",
            operationId = "findProcessorTasks")
    ResultPage<ProcessorTask> find(
            @Parameter(description = "expressionCriteria", required = true) ExpressionCriteria expressionCriteria);

    @POST
    @Path("summary")
    @Operation(
            summary = "Finds processor task summaries",
            operationId = "findProcessorTaskSummary")
    ResultPage<ProcessorTaskSummary> findSummary(
            @Parameter(description = "expressionCriteria", required = true) ExpressionCriteria expressionCriteria);

    @POST
    @Path(ASSIGN_TASKS_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Assign some tasks",
            operationId = "assignProcessorTasks")
    ProcessorTaskList assignTasks(@PathParam("nodeName") String nodeName,
                                  @Parameter(description = "request", required = true) AssignTasksRequest request);

    @POST
    @Path(ABANDON_TASKS_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Abandon some tasks",
            operationId = "abandonProcessorTasks")
    Boolean abandonTasks(@PathParam("nodeName") String nodeName,
                         @Parameter(description = "request", required = true) ProcessorTaskList request);
}
