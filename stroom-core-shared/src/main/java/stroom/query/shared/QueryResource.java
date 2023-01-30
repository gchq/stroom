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

package stroom.query.shared;

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Queries")
@Path("/query" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface QueryResource extends RestResource, DirectRestService, FetchWithUuid<QueryDoc> {

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
    @Path("/downloadSearchResults")
    @Operation(
            summary = "Download search results",
            operationId = "downloadQuerySearchResults")
    ResourceGeneration downloadSearchResults(
            @Parameter(description = "request", required = true) DownloadQueryResultsRequest request);

    @POST
    @Path("/search")
    @Operation(
            summary = "Perform a new search or get new results",
            operationId = "querySearch")
    DashboardSearchResponse search(
            @Parameter(description = "request", required = true) QuerySearchRequest request);
//
//    @POST
//    @Path("/destroy")
//    @Operation(
//            summary = "Destroy a running query",
//            operationId = "queryDestroySearch")
//    Boolean destroy(
//            @Parameter(description = "request", required = true) DestroyQueryRequest request);

    @GET
    @Path("/fetchTimeZones")
    @Operation(
            summary = "Fetch time zone data from the server",
            operationId = "fetchTimeZones")
    List<String> fetchTimeZones();
}
