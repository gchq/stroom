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

package stroom.search.impl;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

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
    @Timed
    @Operation(summary = "Start a search")
    Boolean start(ClusterSearchTask clusterSearchTask);

    @GET
    @Path(POLL_PATH_PART)
    @Produces("application/octet-stream")
    @Operation(summary = "Poll the server for search results for the supplied queryKey")
    StreamingOutput poll(@QueryParam("queryKey") String queryKey);

    @GET
    @Path(DESTROY_PATH_PART)
    @Timed
    @Operation(summary = "Destroy search results")
    Boolean destroy(@QueryParam("queryKey") String queryKey);
}
