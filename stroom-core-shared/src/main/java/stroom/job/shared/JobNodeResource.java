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

package stroom.job.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.scheduler.Schedule;

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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Jobs (Node)")
@Path(JobNodeResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface JobNodeResource extends RestResource, DirectRestService {

    String BASE_PATH = "/jobNode" + ResourcePaths.V1;
    String FIND_PATH_PART = "/find";
    String FIND_NODE_JOBS_PATH_PART = "/findNodeJobs";
    String INFO_PATH_PART = "/info";
    String SCHEDULE_PATH_PART = "/schedule";
    String ENABLED_PATH_PART = "/enabled";
    String EXECUTE_PATH_PART = "/execute";
    String TASK_LIMIT_PATH_PART = "/taskLimit";
    String INFO_PATH = BASE_PATH + INFO_PATH_PART;

//    @GET
//    @Operation(
//            summary = "Lists job nodes",
//            operationId = "listJobNodes")
//    ResultPage<JobNode> list(@QueryParam("jobName") String jobName,
//                             @QueryParam("nodeName") String nodeName);

    @POST
    @Path(FIND_PATH_PART)
    @Operation(
            summary = "Finds job nodes matching criteria and sort order",
            operationId = "findJobNodes")
    JobNodeAndInfoListResponse find(
            @Parameter(description = "findJobNodeCriteria",
                    required = true) final FindJobNodeCriteria findJobNodeCriteria);

//    @POST
//    @Path(FIND_NODE_JOBS_PATH_PART)
//    @Operation(
//            summary = "Gets current info for all jobs on a node",
//            operationId = "fetchJobNodeInfoByNode")
//    JobNodeAndInfoListResponse findNodeJobs(@QueryParam("findJobNodeCriteria") final FindJobNodeCriteria criteria);

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
            summary = "Sets the schedule of a job node",
            operationId = "setJobNodeSchedule")
    void setSchedule(@PathParam("id") Integer id,
                     @Parameter(description = "schedule", required = true) Schedule schedule);

    @PUT
    @Path(SCHEDULE_PATH_PART)
    @Operation(
            summary = "Sets the schedule of multiple job nodes",
            operationId = "setJobNodeScheduleBatch")
    void setScheduleBatch(
            @Parameter(description = "schedule", required = true) BatchScheduleRequest batchScheduleRequest);

    @PUT
    @Path("/{id}" + ENABLED_PATH_PART)
    @Operation(
            summary = "Sets the enabled status of the job node",
            operationId = "setJobNodeEnabled")
    void setEnabled(@PathParam("id") Integer id,
                    @Parameter(description = "enabled", required = true) Boolean enabled);

    @POST
    @Path("/{id}" + EXECUTE_PATH_PART)
    @Operation(
            summary = "Execute job node now",
            operationId = "executeJobNode")
    boolean execute(@PathParam("id") Integer id);
}
