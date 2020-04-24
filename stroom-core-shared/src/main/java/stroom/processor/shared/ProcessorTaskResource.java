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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.entity.shared.ExpressionCriteria;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "processorTask - /v1")
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
    @ApiOperation(
            value = "Finds processors tasks",
            response = ResultPage.class)
    ResultPage<ProcessorTask> find(ExpressionCriteria expressionCriteria);

    @POST
    @Path("summary")
    @ApiOperation(
            value = "Finds processor task summaries",
            response = ResultPage.class)
    ResultPage<ProcessorTaskSummary> findSummary(ExpressionCriteria expressionCriteria);

    @POST
    @Path(ASSIGN_TASKS_PATH_PART + NODE_NAME_PATH_PARAM)
    @ApiOperation(value = "Assign some tasks",
            response = ProcessorTaskList.class)
    ProcessorTaskList assignTasks(@PathParam("nodeName") String nodeName, AssignTasksRequest request);

    @POST
    @Path(ABANDON_TASKS_PATH_PART + NODE_NAME_PATH_PARAM)
    @ApiOperation(value = "Abandon some tasks",
            response = Boolean.class)
    Boolean abandonTasks(@PathParam("nodeName") String nodeName, ProcessorTaskList request);
}