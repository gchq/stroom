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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

@Api(value = "remoteSearch - /v1")
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
    @ApiOperation(
            value = "Start a search",
            response = Boolean.class)
    Boolean start(ClusterSearchTask clusterSearchTask);

    @GET
    @Path(POLL_PATH_PART)
    @Produces("application/octet-stream")
    StreamingOutput poll(@QueryParam("queryKey") String queryKey);

    @GET
    @Path(DESTROY_PATH_PART)
    @Timed
    @ApiOperation(
            value = "Destroy search results",
            response = Boolean.class)
    Boolean destroy(@QueryParam("queryKey") String queryKey);
}