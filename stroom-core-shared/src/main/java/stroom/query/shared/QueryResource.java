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

package stroom.query.shared;

import stroom.dashboard.shared.ColumnValues;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

@Tag(name = "Queries")
@Path(QueryResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface QueryResource extends RestResource, DirectRestService, FetchWithUuid<QueryDoc> {

    String BASE_PATH = "/query" + ResourcePaths.V1;

    String DOWNLOAD_SEARCH_RESULTS_PATH_PATH = "/downloadSearchResults";
    String SEARCH_PATH_PART = "/search";
    String COLUMN_VALUES_PATH_PART = "/columnValues";
    String NODE_NAME_PATH_PARAM = "/{nodeName}";

    @GET
    @Path("/{uuid}")
    @Operation(
            summary = "Fetch a query doc by its UUID",
            operationId = "fetchQuery")
    QueryDoc fetch(@PathParam("uuid") String uuid);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update a query doc",
            operationId = "updateQuery")
    QueryDoc update(
            @PathParam("uuid") String uuid, @Parameter(description = "doc", required = true) QueryDoc doc);

    @POST
    @Path("/validateQuery")
    @Operation(
            summary = "Validate an expression",
            operationId = "validateQuery")
    ValidateExpressionResult validateQuery(
            @Parameter(description = "query", required = true) String query);

    @POST
    @Path(DOWNLOAD_SEARCH_RESULTS_PATH_PATH + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Download search results",
            operationId = "downloadQuerySearchResultsNode")
    ResourceGeneration downloadSearchResults(
            @PathParam("nodeName") String nodeName,
            @Parameter(description = "request", required = true) DownloadQueryResultsRequest request);

    @POST
    @Path(DOWNLOAD_SEARCH_RESULTS_PATH_PATH)
    @Operation(
            summary = "Download search results",
            operationId = "downloadQuerySearchResultsLocal")
    default ResourceGeneration downloadSearchResults(
            @Parameter(description = "request", required = true) final DownloadQueryResultsRequest request) {
        return downloadSearchResults(null, request);
    }


    @POST
    @Path(COLUMN_VALUES_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Get unique column values so the user can filter table results more easily",
            operationId = "getColumnValues")
    ColumnValues getColumnValues(
            @PathParam("nodeName") String nodeName,
            @Parameter(description = "request", required = true) QueryColumnValuesRequest request);

    @POST
    @Path(COLUMN_VALUES_PATH_PART)
    @Operation(
            summary = "Get unique column values so the user can filter table results more easily",
            operationId = "getColumnValues")
    default ColumnValues getColumnValues(
            @Parameter(description = "request", required = true) final QueryColumnValuesRequest request) {
        return getColumnValues(null, request);
    }


    @POST
    @Path(SEARCH_PATH_PART + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Perform a new search or get new results",
            operationId = "querySearchNode")
    DashboardSearchResponse search(
            @PathParam("nodeName") String nodeName,
            @Parameter(description = "request", required = true) QuerySearchRequest request);

    @POST
    @Path(SEARCH_PATH_PART)
    @Operation(
            summary = "Perform a new search or get new results",
            operationId = "querySearchLocal")
    default DashboardSearchResponse search(
            @Parameter(description = "request", required = true) final QuerySearchRequest request) {
        return search(null, request);
    }

    @GET
    @Path("/csv/search")
    @Operation(
            summary = "Perform a csv query",
            operationId = "queryCsv")
    @Produces(MediaType.TEXT_PLAIN)
    String csvSearch(@QueryParam("query") final String query,
                     @QueryParam("offset") final int offset,
                     @DefaultValue("100") @QueryParam("length") final int length);

    @GET
    @Path("/fetchTimeZones")
    @Operation(
            summary = "Fetch time zone data from the server",
            operationId = "fetchTimeZones")
    List<String> fetchTimeZones();

    @POST
    @Path("/helpItems")
    @Operation(
            summary = "Fetch all (optionally filtered) query help items",
            operationId = "fetchHelpItems")
    ResultPage<QueryHelpRow> fetchQueryHelpItems(
            @Parameter(description = "request", required = true) QueryHelpRequest request);

    @POST
    @Path("/fetchCompletions")
    @Operation(
            summary = "Fetch completions for the query",
            operationId = "fetchCompletions")
    ResultPage<CompletionItem> fetchCompletions(CompletionsRequest completionsRequest);

    @POST
    @Path("/fetchDetail")
    @Operation(
            summary = "Fetch detail for help item",
            operationId = "fetchDetail")
    QueryHelpDetail fetchDetail(QueryHelpRow row);

    @POST
    @Path("/fetchQueryDataSource")
    @Operation(
            summary = "Fetch the datasource referenced by a query",
            operationId = "fetchQueryDataSource")
    DocRef fetchQueryDataSource(DocRef queryDocRef);

    @POST
    @Path("/fetchDataSourceFromQueryString")
    @Operation(
            summary = "Fetch a data source from a query string",
            operationId = "fetchDataSourceFromQueryString")
    DocRef fetchDataSourceFromQueryString(
            @Parameter(description = "query", required = true) String query);
}
