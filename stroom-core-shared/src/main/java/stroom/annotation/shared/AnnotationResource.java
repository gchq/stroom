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
import stroom.entity.shared.ExpressionCriteria;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
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

    @POST
    @Path("findAnnotations")
    @Operation(
            summary = "Finds annotations",
            operationId = "findAnnotations")
    ResultPage<Annotation> findAnnotations(@Parameter(description = "request", required = true)
                                           FindAnnotationRequest request);

    @GET
    @Operation(
            summary = "Gets an annotation by id",
            operationId = "getAnnotationById")
    Annotation getAnnotationById(@QueryParam("annotationId") Long annotationId);

//    @POST
//    @Path("getAnnotationByRef")
//    @Operation(
//            summary = "Gets an annotation by ref",
//            operationId = "getAnnotationByRef")
//    Annotation getAnnotationByRef(@Parameter(description = "annotationRef", required = true)
//                                  DocRef annotationRef);

    @POST
    @Path("getAnnotationEntries")
    @Operation(
            summary = "Gets annotation entries",
            operationId = "getAnnotationEntries")
    List<AnnotationEntry> getAnnotationEntries(@Parameter(description = "annotationRef", required = true)
                                               DocRef annotationRef);

    @POST
    @Path("create")
    @Operation(
            summary = "Creates an annotation",
            operationId = "createAnnotation")
    Annotation createAnnotation(@Parameter(description = "request", required = true)
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
    Boolean change(@Parameter(description = "request", required = true)
                   SingleAnnotationChangeRequest request);

    @POST
    @Path("batchChange")
    @Operation(
            summary = "Applies a change to multiple annotations",
            operationId = "batchChangeAnnotation")
    Integer batchChange(@Parameter(description = "request", required = true)
                        MultiAnnotationChangeRequest request);

    @GET
    @Path("getStandardComments")
    @Operation(
            summary = "Gets a list of predefined comments",
            operationId = "getAnnotationSampleComments")
    List<String> getStandardComments(@QueryParam("filter") String filter);


    @POST
    @Path("getLinkedEvents")
    @Operation(
            summary = "Gets a list of events linked to this annotation",
            operationId = "getAnnotationLinkedEvents")
    List<EventId> getLinkedEvents(@Parameter(description = "annotationRef", required = true) DocRef annotationRef);

    @POST
    @Path("createAnnotationTag")
    @Operation(
            summary = "Create an annotation tag",
            operationId = "createAnnotationTag")
    AnnotationTag createAnnotationTag(CreateAnnotationTagRequest request);

    @PUT
    @Path("updateAnnotationTag")
    @Operation(
            summary = "Update an annotation tag",
            operationId = "updateAnnotationTag")
    AnnotationTag updateAnnotationTag(AnnotationTag annotationTag);

    @DELETE
    @Path("deleteAnnotationTag")
    @Operation(
            summary = "Delete an annotation tag",
            operationId = "deleteAnnotationTag")
    Boolean deleteAnnotationTag(AnnotationTag annotationTag);

    @POST
    @Path("findAnnotationTags")
    @Operation(
            summary = "Finds annotation tags matching request",
            operationId = "findAnnotationTags")
    ResultPage<AnnotationTag> findAnnotationTags(
            @Parameter(description = "request", required = true) ExpressionCriteria request);

    @POST
    @Path("fetchAnnotationEntry")
    @Operation(
            summary = "Fetch an annotation entry",
            operationId = "fetchAnnotationEntry")
    AnnotationEntry fetchAnnotationEntry(
            @Parameter(description = "request", required = true) FetchAnnotationEntryRequest request);

    @POST
    @Path("changeAnnotationEntry")
    @Operation(
            summary = "Change an annotation entry",
            operationId = "changeAnnotationEntry")
    Boolean changeAnnotationEntry(
            @Parameter(description = "request", required = true) ChangeAnnotationEntryRequest request);

    @POST
    @Path("deleteAnnotationEntry")
    @Operation(
            summary = "Delete an annotation entry",
            operationId = "deleteAnnotationEntry")
    Boolean deleteAnnotationEntry(
            @Parameter(description = "request", required = true) DeleteAnnotationEntryRequest request);
}
