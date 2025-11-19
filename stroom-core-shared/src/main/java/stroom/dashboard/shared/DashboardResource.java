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
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "Dashboards")
@Path(DashboardResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DashboardResource extends RestResource, DirectRestService, FetchWithUuid<DashboardDoc> {

    String BASE_PATH = "/dashboard" + ResourcePaths.V1;

    String DOWNLOAD_SEARCH_RESULTS_PATH_PART = "/downloadSearchResults";
    String ASK_STROOM_AI_PATH_PART = "/askStroomAi";
    String SEARCH_PATH_PART = "/search";
    String COLUMN_VALUES_PATH_PART = "/columnValues";
    String NODE_NAME_PATH_PARAM = "/{nodeName}";

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
            DashboardSearchRequest request);

    @POST
    @Path(DOWNLOAD_SEARCH_RESULTS_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Download search results",
            operationId = "downloadDashboardSearchResultsNode")
    ResourceGeneration downloadSearchResults(
            @PathParam("nodeName") String nodeName,
            @Parameter(description = "request", required = true) DownloadSearchResultsRequest request);

    @POST
    @Path(DOWNLOAD_SEARCH_RESULTS_PATH_PART)
    @Operation(
            summary = "Download search results",
            operationId = "downloadDashboardSearchResultsLocal")
    default ResourceGeneration downloadSearchResults(
            @Parameter(description = "request", required = true) final DownloadSearchResultsRequest request) {
        return downloadSearchResults(null, request);
    }

    @POST
    @Path(ASK_STROOM_AI_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Ask Stroom AI a question relating to the current search context",
            operationId = "askStroomAi")
    AskStroomAiResponse askStroomAi(
            @PathParam("nodeName") String nodeName,
            @Parameter(description = "request", required = true) final AskStroomAiRequest request);

    @POST
    @Path(ASK_STROOM_AI_PATH_PART)
    @Operation(
            summary = "Ask Stroom AI a question relating to the current search context",
            operationId = "askStroomAi")
    default AskStroomAiResponse askStroomAi(
            @Parameter(description = "request", required = true) final AskStroomAiRequest request) {
        return askStroomAi(null, request);
    }

    @POST
    @Path(SEARCH_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Perform a new search or get new results",
            operationId = "dashboardSearch")
    DashboardSearchResponse search(
            @PathParam("nodeName") String nodeName,
            @Parameter(description = "request", required = true) DashboardSearchRequest request);

    @POST
    @Path(SEARCH_PATH_PART)
    @Operation(
            summary = "Perform a new search or get new results",
            operationId = "dashboardSearch")
    default DashboardSearchResponse search(
            @Parameter(description = "request", required = true) final DashboardSearchRequest request) {
        return search(null, request);
    }

    @POST
    @Path(COLUMN_VALUES_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Get unique column values so the user can filter table results more easily",
            operationId = "getColumnValues")
    ColumnValues getColumnValues(
            @PathParam("nodeName") String nodeName,
            @Parameter(description = "request", required = true) ColumnValuesRequest request);

    @POST
    @Path(COLUMN_VALUES_PATH_PART)
    @Operation(
            summary = "Get unique column values so the user can filter table results more easily",
            operationId = "getColumnValues")
    default ColumnValues getColumnValues(
            @Parameter(description = "request", required = true) final ColumnValuesRequest request) {
        return getColumnValues(null, request);
    }
}
