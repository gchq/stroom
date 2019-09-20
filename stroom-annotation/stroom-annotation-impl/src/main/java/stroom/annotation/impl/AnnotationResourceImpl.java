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

package stroom.annotation.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import org.springframework.stereotype.Component;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationResource;
import stroom.security.SecurityContext;
import stroom.task.server.TaskContext;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;

@Component
public class AnnotationResourceImpl implements AnnotationResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationResourceImpl.class);

    private final AnnotationsService annotationsService;
    private final SecurityContext securityContext;
    private final TaskContext taskContext;

    @Inject
    public AnnotationResourceImpl(final AnnotationsService annotationsService,
                                  final SecurityContext securityContext,
                                  final TaskContext taskContext) {
        this.annotationsService = annotationsService;
        this.securityContext = securityContext;
        this.taskContext = taskContext;
    }

    @Override
    public Annotation get(String id) {
        LOGGER.info(() -> "Getting annotation " + id);

        final String[] parts = id.split(":");
        final long metaId = Long.parseLong(parts[0]);
        final long eventId = Long.parseLong(parts[1]);

        // Create a new annotation.
        final Annotation annotation = new Annotation();
        annotation.setMetaId(metaId);
        annotation.setMetaId(eventId);
        annotation.setStatus("New");
        annotation.setCreatedBy(securityContext.getUserId());
        annotation.setCreatedOn(System.currentTimeMillis());

        return annotation;
//        try (final SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
//            final Index index = annotationsService.loadByUuid(docRef.getUuid());
//            return new DataSource(IndexDataSourceFieldUtil.getDataSourceFields(index, securityContext));
//        }
    }
//
//    @POST
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("/search")
//    @Timed
//    @ApiOperation(
//            value = "Submit a search request",
//            response = SearchResponse.class)
//    public SearchResponse search(@ApiParam("SearchRequest") final SearchRequest request) {
//
//        //if this is the first call for this query key then it will create a searchResponseCreator (& store) that have
//        //a lifespan beyond the scope of this request and then begin the search for the data
//        //If it is not the first call for this query key then it will return the existing searchResponseCreator with
//        //access to whatever data has been found so far
//        final SearchResponseCreator searchResponseCreator = searchResponseCreatorManager.get(new SearchResponseCreatorCache.Key(request));
//
//        //create a response from the data found so far, this could be complete/incomplete
//        SearchResponse searchResponse = searchResponseCreator.create(request, taskContext);
//
//        LAMBDA_LOGGER.trace(() ->
//                getResponseInfoForLogging(request, searchResponse));
//
//        return searchResponse;
//    }
//
//    private String getResponseInfoForLogging(@ApiParam("SearchRequest") final SearchRequest request, final SearchResponse searchResponse) {
//        String resultInfo;
//
//        if (searchResponse.getResults() != null) {
//            resultInfo = "\n" + searchResponse.getResults().stream()
//                    .map(result -> {
//                        if (result instanceof FlatResult) {
//                            FlatResult flatResult = (FlatResult) result;
//                            return LambdaLogger.buildMessage(
//                                    "  FlatResult - componentId: {}, size: {}, ",
//                                    flatResult.getComponentId(),
//                                    flatResult.getSize());
//                        } else if (result instanceof TableResult) {
//                            TableResult tableResult = (TableResult) result;
//                            return LambdaLogger.buildMessage(
//                                    "  TableResult - componentId: {}, rows: {}, totalResults: {}, " +
//                                            "resultRange: {}",
//                                    tableResult.getComponentId(),
//                                    tableResult.getRows().size(),
//                                    tableResult.getTotalResults(),
//                                    tableResult.getResultRange());
//                        } else {
//                            return "  Unknown type " + result.getClass().getName();
//                        }
//                    })
//                    .collect(Collectors.joining("\n"));
//        } else {
//            resultInfo = "null";
//        }
//
//        return LambdaLogger.buildMessage("Return search response, key: {}, result sets: {}, " +
//                        "complete: {}, errors: {}, results: {}",
//                request.getKey().toString(),
//                searchResponse.getResults(),
//                searchResponse.complete(),
//                searchResponse.getErrors(),
//                resultInfo);
//    }
//
//    @POST
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("/destroy")
//    @Timed
//    @ApiOperation(
//            value = "Destroy a running query",
//            response = Boolean.class)
//    public Boolean destroy(@ApiParam("QueryKey") final QueryKey queryKey) {
//        searchResponseCreatorManager.remove(new SearchResponseCreatorCache.Key(queryKey));
//        return Boolean.TRUE;
//    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}