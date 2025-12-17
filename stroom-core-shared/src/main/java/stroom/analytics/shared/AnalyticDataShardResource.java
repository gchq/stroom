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

package stroom.analytics.shared;

import stroom.query.api.Result;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

@Tag(name = "AnalyticNotifications")
@Path(AnalyticDataShardResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AnalyticDataShardResource extends RestResource, DirectRestService {

    String BASE_PATH = "/analyticDataShard" + ResourcePaths.V1;
    String FIND_SUB_PATH = "/find";
    String GET_DATA_SUB_PATH = "/getData";

    @POST
    @Path(FIND_SUB_PATH)
    @Operation(
            summary = "Find the analytic data shards for the specified analytic",
            operationId = "findAnalyticDataShards")
    ResultPage<AnalyticDataShard> find(@QueryParam("nodeName") String nodeName,
                                       @Parameter(description = "criteria", required = true)
                                       FindAnalyticDataShardCriteria criteria);

    @POST
    @Path(GET_DATA_SUB_PATH)
    @Operation(
            summary = "Get the data for the shard",
            operationId = "getShardData")
    Result getData(@QueryParam("nodeName") String nodeName,
                   @Parameter(description = "request", required = true) GetAnalyticShardDataRequest request);

//    @POST
//    @Operation(
//            summary = "Create an analytic notification",
//            operationId = "createAnalyticNotification")
//    AnalyticNotification create(
//            @Parameter(description = "notification", required = true) final AnalyticNotification notification);
//
//    @PUT
//    @Path("/{uuid}")
//    @Operation(
//            summary = "Update an analytic notification",
//            operationId = "updateAnalyticNotification")
//    AnalyticNotification update(@PathParam("uuid")
//                                String uuid,
//                                @Parameter(description = "notification", required = true)
//                                AnalyticNotification notification);
//
//    @DELETE
//    @Path("/{uuid}")
//    @Operation(
//            summary = "Delete an analytic notification",
//            operationId = "updateAnalyticNotification")
//    Boolean delete(@PathParam("uuid")
//                                String uuid,
//                                @Parameter(description = "notification", required = true)
//                                AnalyticNotification notification);
}
