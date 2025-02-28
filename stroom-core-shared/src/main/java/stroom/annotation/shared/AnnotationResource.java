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

import stroom.docref.DocRef;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.time.SimpleDuration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
    AnnotationDetail getById(@QueryParam("annotationId") Long annotationId);

    @POST
    @Path("create")
    @Operation(
            summary = "Creates an annotation",
            operationId = "createAnnotation")
    AnnotationDetail createAnnotation(@Parameter(description = "request", required = true)
                                      CreateAnnotationRequest request);

    @DELETE
    @Path("delete")
    @Operation(
            summary = "Deletes an annotation",
            operationId = "deleteAnnotation")
    Boolean deleteAnnotation(@Parameter(description = "annotationRef", required = true)
                             DocRef annotationRef);

    @POST
    @Path("change")
    @Operation(
            summary = "Applies a change to an annotation",
            operationId = "changeAnnotation")
    AnnotationDetail change(@Parameter(description = "request", required = true)
                            SingleAnnotationChangeRequest request);

    @POST
    @Path("batchChange")
    @Operation(
            summary = "Applies a change to multiple annotations",
            operationId = "batchChangeAnnotation")
    Integer batchChange(@Parameter(description = "request", required = true)
                        MultiAnnotationChangeRequest request);

    @GET
    @Path("getStatusValues")
    @Operation(
            summary = "Gets a list of allowed statuses",
            operationId = "getAnnotationStatus")
    List<String> getStatusValues(@QueryParam("filter") String filter);

    @GET
    @Path("getStandardComments")
    @Operation(
            summary = "Gets a list of predefined comments",
            operationId = "getAnnotationSampleComments")
    List<String> getStandardComments(@QueryParam("filter") String filter);

//    @GET
//    @Path("getDefaultRetentionPeriod")
//    @Operation(
//            summary = "Gets the default retention period",
//            operationId = "getAnnotationDefaultRetentionPeriod")
//    SimpleDuration getDefaultRetentionPeriod();

    @POST
    @Path("getLinkedEvents")
    @Operation(
            summary = "Gets a list of events linked to this annotation",
            operationId = "getAnnotationLinkedEvents")
    List<EventId> getLinkedEvents(@Parameter(description = "annotationRef", required = true) DocRef annotationRef);

    @POST
    @Path("/changeDocumentPermissions")
    @Operation(
            summary = "Change document permissions",
            operationId = "changeDocumentPermissions")
    Boolean changeDocumentPermissions(
            @Parameter(description = "request", required = true) SingleDocumentPermissionChangeRequest request);
}
