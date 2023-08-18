/*
 * Copyright 2022 Crown Copyright
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
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "AnalyticProcess")
@Path("/analyticProcess" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AnalyticProcessResource
        extends RestResource, DirectRestService {

    @POST
    @Path("/find")
    @Operation(
            summary = "Find the analytic process for the specified analytic",
            operationId = "findAnalyticProcess")
    ResultPage<AnalyticProcess> find(@Parameter(description = "criteria", required = true)
                                     FindAnalyticProcessCriteria criteria);

    @POST
    @Operation(
            summary = "Create an analytic process",
            operationId = "createAnalyticProcess")
    AnalyticProcess create(@Parameter(description = "process", required = true)
                                   final AnalyticProcess analyticProcess);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update an analytic process",
            operationId = "updateAnalyticProcess")
    AnalyticProcess update(@PathParam("uuid")
                                   String uuid,
                           @Parameter(description = "process", required = true)
                           AnalyticProcess analyticProcess);

    @DELETE
    @Path("/{uuid}")
    @Operation(
            summary = "Delete an analytic process",
            operationId = "updateAnalyticProcess")
    Boolean delete(@PathParam("uuid")
                   String uuid,
                   @Parameter(description = "process", required = true)
                   AnalyticProcess analyticProcess);

    @POST
    @Path("/tracker")
    @Operation(
            summary = "Find the analytic process tracker for the specified process",
            operationId = "findAnalyticProcessTracker")
    AnalyticProcessTracker getTracker(@Parameter(description = "processUuid", required = true)
                                              String processUuid);
}
