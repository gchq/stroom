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

package stroom.activity.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.filter.FilterFieldDefinition;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api(value = "activity - /v1")
@Path("/activity" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ActivityResource extends RestResource, DirectRestService {

    @GET
    @ApiOperation(
            value = "Lists activities",
            response = ResultPage.class)
    ResultPage<Activity> list(@QueryParam("filter") String filter);

    @GET
    @Path("/fields")
    @ApiOperation(
            value = "Lists activity field definitions",
            response = List.class)
    List<FilterFieldDefinition> listFieldDefinitions();

    @POST
    @ApiOperation(
            value = "Create an Activity",
            response = Activity.class)
    Activity create();

    @GET
    @Path("/{id}")
    @ApiOperation(
            value = "Get an Activity",
            response = Activity.class)
    Activity read(@PathParam("id") Integer id);

    @PUT
    @Path("/{id}")
    @ApiOperation(
            value = "Update an Activity",
            response = Activity.class)
    Activity update(@PathParam("id") Integer id, Activity activity);

    @DELETE
    @Path("/{id}")
    @ApiOperation(
            value = "Delete an activity",
            response = Boolean.class)
    Boolean delete(@PathParam("id") Integer id);

    @POST
    @Path("/validate")
    @ApiOperation(
            value = "Create an Activity",
            response = ActivityValidationResult.class)
    ActivityValidationResult validate(@ApiParam("activity") Activity activity);

    @GET
    @Path("/current")
    @ApiOperation(
            value = "Gets the current activity",
            response = Activity.class)
    Activity getCurrentActivity();

    @PUT
    @Path("/current")
    @ApiOperation(
            value = "Gets the current activity",
            response = Activity.class)
    Activity setCurrentActivity(Activity activity);

    @POST
    @Path("/acknowledge")
    @ApiOperation(
            value = "Acknowledge the slash screen",
            response = Boolean.class)
    Boolean acknowledgeSplash(@ApiParam("request") AcknowledgeSplashRequest request);
}