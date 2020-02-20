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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.docref.DocRef;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

@Api(value = "dashboard")
@Path("/dashboard" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public interface DashboardResource extends RestResource, DirectRestService {
    @POST
    @Path("/read")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Get a dashboard doc",
            response = DashboardDoc.class)
    DashboardDoc read(DocRef docRef);

    @PUT
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Update a dashboard doc",
            response = DashboardDoc.class)
    DashboardDoc update(DashboardDoc doc);

    @POST
    @Path("/validateExpression")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Validate an expression",
            response = ValidateExpressionResult.class)
    ValidateExpressionResult validateExpression(String expression);

    @POST
    @Path("/downloadQuery")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Download a query",
            response = ResourceGeneration.class)
    ResourceGeneration downloadQuery(DownloadQueryRequest downloadQueryRequest);

    @POST
    @Path("/downloadSearchResults")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Download search results",
            response = ResourceGeneration.class)
    ResourceGeneration downloadSearchResults(DownloadSearchResultsRequest request);

    @POST
    @Path("/poll")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Poll for new search results",
            response = Set.class)
    Set<SearchResponse> poll(SearchBusPollRequest request);

    @GET
    @Path("/fetchTimeZones")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Fetch time zone data from the server",
            response = List.class)
    List<String> fetchTimeZones();
}