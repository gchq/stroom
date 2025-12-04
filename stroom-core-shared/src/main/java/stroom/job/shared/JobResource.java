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

import stroom.node.shared.NodeSetJobsEnabledRequest;
import stroom.node.shared.NodeSetJobsEnabledResponse;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Jobs")
@Path("/job" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface JobResource extends RestResource, DirectRestService {

    String NODE_NAME_PATH_PARAM = "/{nodeName}";

    @GET
    @Operation(
            summary = "Lists jobs",
            operationId = "listJobs")
    ResultPage<Job> list();

    @PUT
    @Path("/{id}/enabled")
    @Operation(
            summary = "Sets the enabled status of the job",
            operationId = "setJobEnabled")
    void setEnabled(@PathParam("id") Integer id,
                    @Parameter(description = "enabled", required = true) Boolean enabled);

    @PUT
    @Path("/setJobsEnabled" + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Sets the enabled state of jobs for the selected node. If both `includeJobs` and `excludeJobs` " +
                    "are unspecified or empty, this action will apply to ALL jobs.",
            operationId = "setNodeJobsEnabled"
    )
    NodeSetJobsEnabledResponse setJobsEnabled(
            @PathParam("nodeName") String nodeName,
            @Parameter(description = "Request parameters", required = true) NodeSetJobsEnabledRequest params);
}
