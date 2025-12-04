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

package stroom.activity.shared;

import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.filter.FilterFieldDefinition;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

@Tag(name = "Activities")
@Path("/activity" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ActivityResource extends RestResource, DirectRestService, FetchWithIntegerId<Activity> {

    @GET
    @Operation(
            summary = "Lists activities",
            operationId = "listActivities")
    ResultPage<Activity> list(@QueryParam("filter") String filter);

    @GET
    @Path("/fields")
    @Operation(
            summary = "Lists activity field definitions",
            operationId = "listActivityFieldDefinitions")
    List<FilterFieldDefinition> listFieldDefinitions();

    @POST
    @Operation(
            summary = "Create an Activity",
            operationId = "createActivity")
    Activity create();

    @Override
    @GET
    @Path("/{id}")
    @Operation(
            summary = "Fetch an Activity",
            operationId = "fetchActivity")
    Activity fetch(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Update an Activity",
            operationId = "updateActivity")
    Activity update(@PathParam("id") final Integer id, final Activity activity);

    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Delete an activity",
            operationId = "deleteActivity")
    Boolean delete(@PathParam("id") final Integer id);

    @POST
    @Path("/validate")
    @Operation(
            summary = "Validate an Activity",
            operationId = "validateActivity")
    ActivityValidationResult validate(@Parameter(description = "activity", required = true) final Activity activity);

    @GET
    @Path("/current")
    @Operation(
            summary = "Gets the current activity",
            operationId = "getCurrentActivity")
    Activity getCurrentActivity();

    @PUT
    @Path("/current")
    @Operation(
            summary = "Gets the current activity",
            operationId = "setCurrentActivity")
    Activity setCurrentActivity(Activity activity);

    @POST
    @Path("/acknowledge")
    @Operation(
            summary = "Acknowledge the slash screen",
            operationId = "acknowledgeSplash")
    Boolean acknowledgeSplash(@Parameter(description = "request", required = true) AcknowledgeSplashRequest request);
}
