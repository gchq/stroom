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

package stroom.data.retention.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Data Retention Rules")
@Path("/dataRetentionRules" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DataRetentionRulesResource extends RestResource, DirectRestService {

    @GET
    @Path("/")
    @Operation(
            summary = "Get data retention rules",
            operationId = "fetchDataRetentionRules")
    DataRetentionRules fetch();

    @PUT
    @Path("/")
    @Operation(
            summary = "Update data retention rules",
            operationId = "updateDataRetentionRules")
    DataRetentionRules update(
            @Parameter(description = "dataRetentionRules", required = true) DataRetentionRules dataRetentionRules);

    @POST
    @Path("/impactSummary")
    @Operation(
            summary = "Get a summary of meta deletions with the passed data retention rules",
            operationId = "getDataRetentionImpactSummary")
    DataRetentionDeleteSummaryResponse getRetentionDeletionSummary(
            @Parameter(description = "request", required = true) DataRetentionDeleteSummaryRequest request);

    @DELETE
    @Path("/impactSummary/{queryId}")
    @Operation(
            summary = "Stop a running query",
            operationId = "stopDataRetentionImpactSummary")
    Boolean cancelQuery(@PathParam("queryId") final String queryId);
}
