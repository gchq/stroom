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

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

@Tag(name = "Annotations")
@Path("/annotation" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AnnotationResource extends RestResource, DirectRestService {

    @GET
    @Operation(
            summary = "Gets an annotation",
            operationId = "getAnnotationDetail")
    AnnotationDetail get(@QueryParam("annotationId") Long annotationId);

    @POST
    @Operation(
            summary = "Gets an annotation",
            operationId = "createAnnotationEntry")
    AnnotationDetail createEntry(@Parameter(description = "request", required = true) CreateEntryRequest request);

    @GET
    @Path("status")
    @Operation(
            summary = "Gets a list of allowed statuses",
            operationId = "getAnnotationDStatus")
    List<String> getStatus(@QueryParam("filter") String filter);

    @GET
    @Path("comment")
    @Operation(
            summary = "Gets a list of predefined comments",
            operationId = "getAnnotationComments")
    List<String> getComment(@QueryParam("filter") String filter);

    @GET
    @Path("linkedEvents")
    @Operation(
            summary = "Gets a list of events linked to this annotation",
            operationId = "getAnnotationLinkedEvents")
    List<EventId> getLinkedEvents(@QueryParam("annotationId") Long annotationId);

    @POST
    @Path("link")
    @Operation(
            summary = "Links an annotation to an event",
            operationId = "linkAnnotationEvents")
    List<EventId> link(@Parameter(description = "eventLink", required = true) EventLink eventLink);

    @POST
    @Path("unlink")
    @Operation(
            summary = "Unlinks an annotation from an event",
            operationId = "unlinkAnnotationEvents")
    List<EventId> unlink(@Parameter(description = "eventLink", required = true) EventLink eventLink);

    @POST
    @Path("setStatus")
    @Operation(
            summary = "Bulk action to set the status for several annotations",
            operationId = "setAnnotationStatus")
    Integer setStatus(@Parameter(description = "request", required = true) SetStatusRequest request);

    @POST
    @Path("setAssignedTo")
    @Operation(
            summary = "Bulk action to set the assignment for several annotations",
            operationId = "setAnnotationAssignedTo")
    Integer setAssignedTo(@Parameter(description = "request", required = true) SetAssignedToRequest request);
}
