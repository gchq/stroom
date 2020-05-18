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

package stroom.annotation.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Api(value = "annotations - /v1")
@Path("/annotation" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AnnotationResource extends RestResource, DirectRestService {
    @GET
    @ApiOperation(
            value = "Gets an annotation",
            response = Response.class)
    AnnotationDetail get(@QueryParam("annotationId") Long annotationId);

    @POST
    AnnotationDetail createEntry(@ApiParam("request") CreateEntryRequest request);

    @GET
    @Path("status")
    @ApiOperation(
            value = "Gets a list of allowed statuses",
            response = Response.class)
    List<String> getStatus(@QueryParam("filter") String filter);

    @GET
    @Path("comment")
    @ApiOperation(
            value = "Gets a list of predefined comments",
            response = Response.class)
    List<String> getComment(@QueryParam("filter") String filter);

    @GET
    @Path("linkedEvents")
    @ApiOperation(
            value = "Gets a list of events linked to this annotation",
            response = Response.class)
    List<EventId> getLinkedEvents(@QueryParam("annotationId") Long annotationId);

    @POST
    @Path("link")
    @ApiOperation(
            value = "Links an annotation to an event",
            response = Response.class)
    List<EventId> link(@ApiParam("eventLink") EventLink eventLink);

    @POST
    @Path("unlink")
    @ApiOperation(
            value = "Unlinks an annotation from an event",
            response = Response.class)
    List<EventId> unlink(@ApiParam("eventLink") EventLink eventLink);

    @POST
    @Path("setStatus")
    @ApiOperation(
            value = "Bulk action to set the status for several annotations",
            response = Response.class)
    Integer setStatus(@ApiParam("request") SetStatusRequest request);

    @POST
    @Path("setAssignedTo")
    @ApiOperation(
            value = "Bulk action to set the assignment for several annotations",
            response = Response.class)
    Integer setAssignedTo(@ApiParam("request") SetAssignedToRequest request);
}
