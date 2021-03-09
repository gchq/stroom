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

package stroom.dashboard.shared;

import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Dashboards")
@Path("/dashboard" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DashboardResource extends RestResource, DirectRestService, FetchWithUuid<DashboardDoc> {

    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch a dashboard doc by its UUID",
            operationId = "fetchDashboard")
    DashboardDoc fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update a dashboard doc",
            operationId = "updateDashboard")
    DashboardDoc update(
            @PathParam("uuid") String uuid, @Parameter(description = "doc", required = true) DashboardDoc doc);

    @POST
    @Path("/validateExpression")
    @Operation(
            summary = "Validate an expression",
            operationId = "validateDashboardExpression")
    ValidateExpressionResult validateExpression(
            @Parameter(description = "expression", required = true) String expression);

    @POST
    @Path("/downloadQuery")
    @Operation(
            summary = "Download a query",
            operationId = "downloadDashboardQuery")
    ResourceGeneration downloadQuery(
            @Parameter(description = "downloadQueryRequest", required = true)
                    DownloadQueryRequest downloadQueryRequest);

    @POST
    @Path("/downloadSearchResults")
    @Operation(
            summary = "Download search results",
            operationId = "downloadDashboardSearchResults")
    ResourceGeneration downloadSearchResults(
            @Parameter(description = "request", required = true) DownloadSearchResultsRequest request);

    @POST
    @Path("/poll")
    @Operation(
            summary = "Poll for new search results",
            operationId = "pollDashboardSearchResults")
    Set<DashboardSearchResponse> poll(
            @Parameter(description = "request", required = true) SearchBusPollRequest request);

    @GET
    @Path("/fetchTimeZones")
    @Operation(
            summary = "Fetch time zone data from the server",
            operationId = "fetchTimeZones")
    List<String> fetchTimeZones();

    @GET
    @Path("/functions")
    @Operation(
            summary = "Fetch all expression functions",
            operationId = "fetchDashboardFunctions")
    List<FunctionSignature> fetchFunctions();
}
