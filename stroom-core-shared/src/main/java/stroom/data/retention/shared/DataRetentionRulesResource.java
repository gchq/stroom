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

package stroom.data.retention.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

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
