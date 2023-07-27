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

package stroom.analytics.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "AnalyticNotifications")
@Path("/analyticNotification" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AnalyticNotificationResource extends RestResource, DirectRestService {

    @POST
    @Path("/find")
    @Operation(
            summary = "Find the analytic notifications for the specified analytic",
            operationId = "findAnalyticNotifications")
    ResultPage<AnalyticNotificationRow> find(@Parameter(description = "criteria", required = true)
                                             FindAnalyticNotificationCriteria criteria);

    @POST
    @Operation(
            summary = "Create an analytic notification",
            operationId = "createAnalyticNotification")
    AnalyticNotification create(
            @Parameter(description = "notification", required = true) final AnalyticNotification notification);

    @PUT
    @Path("/{uuid}")
    @Operation(
            summary = "Update an analytic notification",
            operationId = "updateAnalyticNotification")
    AnalyticNotification update(@PathParam("uuid")
                                String uuid,
                                @Parameter(description = "notification", required = true)
                                AnalyticNotification notification);

    @DELETE
    @Path("/{uuid}")
    @Operation(
            summary = "Delete an analytic notification",
            operationId = "updateAnalyticNotification")
    Boolean delete(@PathParam("uuid")
                                String uuid,
                                @Parameter(description = "notification", required = true)
                                AnalyticNotification notification);
}
