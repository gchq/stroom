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

package stroom.search.impl;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.StreamingOutput;

@Tag(name = "Remote Search")
@Path(RemoteSearchResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface RemoteSearchResource extends RestResource {

    String BASE_PATH = "/remoteSearch" + ResourcePaths.V1;
    String START_PATH_PART = "/start";
    String POLL_PATH_PART = "/poll";
    String DESTROY_PATH_PART = "/destroy";

    @POST
    @Path(START_PATH_PART)
    @Operation(
            summary = "Start a search",
            operationId = "startRemoteSearch")
    Boolean start(NodeSearchTask nodeSearchTask);

    @GET
    @Path(POLL_PATH_PART)
    @Produces("application/octet-stream")
    @Operation(
            summary = "Poll the server for search results for the supplied queryKey",
            operationId = "pollRemoteSearch")
    StreamingOutput poll(@QueryParam("queryKey") String queryKey);

    @GET
    @Path(DESTROY_PATH_PART)
    @Operation(
            summary = "Destroy search results",
            operationId = "destroyRemoteSearch")
    Boolean destroy(@QueryParam("queryKey") String queryKey);
}
