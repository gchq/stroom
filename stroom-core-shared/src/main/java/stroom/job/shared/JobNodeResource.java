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

package stroom.job.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Tag(name = "Jobs (Node)")
@Path(JobNodeResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface JobNodeResource extends RestResource, DirectRestService {

    String BASE_PATH = "/jobNode" + ResourcePaths.V1;
    String FIND_PATH_PART = "/find";
    String INFO_PATH_PART = "/info";
    String SCHEDULE_PATH_PART = "/schedule";
    String ENABLED_PATH_PART = "/enabled";
    String TASK_LIMIT_PATH_PART = "/taskLimit";
    String INFO_PATH = BASE_PATH + INFO_PATH_PART;

    @GET
    @Operation(
            summary = "Lists job nodes",
            operationId = "listJobNodes")
    ResultPage<JobNode> list(@QueryParam("jobName") String jobName,
                             @QueryParam("nodeName") String nodeName);

    @POST
    @Path("/find")
    @Operation(
            summary = "Finds job nodes matching criteria and sort order",
            operationId = "findJobNodes")
    ResultPage<JobNode> find(@Parameter(description = "findJobNodeCriteria", required = true) final FindJobNodeCriteria findJobNodeCriteria);

    @GET
    @Path(INFO_PATH_PART)
    @Operation(
            summary = "Gets current info for a job node",
            operationId = "fetchJobNodeInfo")
    JobNodeInfo info(@QueryParam("jobName") String jobName,
                     @QueryParam("nodeName") String nodeName);

    @PUT
    @Path("/{id}" + TASK_LIMIT_PATH_PART)
    @Operation(
            summary = "Sets the task limit for the job node",
            operationId = "setJobNodeTaskLimit")
    void setTaskLimit(@PathParam("id") Integer id,
                      @Parameter(description = "taskLimit", required = true) Integer taskLimit);

    @PUT
    @Path("/{id}" + SCHEDULE_PATH_PART)
    @Operation(
            summary = "Sets the schedule job node",
            operationId = "setJobNodeSchedule")
    void setSchedule(@PathParam("id") Integer id,
                     @Parameter(description = "schedule", required = true) String schedule);

    @PUT
    @Path("/{id}" + ENABLED_PATH_PART)
    @Operation(
            summary = "Sets the enabled status of the job node",
            operationId = "setJobNodeEnabled")
    void setEnabled(@PathParam("id") Integer id,
                    @Parameter(description = "enabled", required = true) Boolean enabled);
}
