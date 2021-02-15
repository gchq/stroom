/*
 *
 *  * Copyright 2018 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.meta.impl;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DocRefField;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaRow;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.api.SecurityContext;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import java.util.List;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static stroom.query.api.v2.ExpressionTerm.Condition;

@Api(tags = "Stream Attribute Maps")
@Path("/streamattributemap" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StreamAttributeMapResource implements RestResource {

    private final Provider<MetaService> dataMetaService;
    private final Provider<SecurityContext> securityContext;

    @Inject
    public StreamAttributeMapResource(final Provider<MetaService> metaServiceProvider,
                                      final Provider<SecurityContext> securityContextProvider) {
        this.dataMetaService = metaServiceProvider;
        this.securityContext = securityContextProvider;
    }

    @GET
    @ApiOperation(
            value = "Fetch a page of stream metadata, starting from the most recent.",
            response = SearchResponse.class)
    public Response page(@QueryParam("pageOffset") Long pageOffset,
                         @QueryParam("pageSize") Integer pageSize) {
        return securityContext.get().secureResult(() -> {
            // Validate pagination params
            if ((pageSize != null && pageOffset == null)
                    || (pageSize == null && pageOffset != null)) {
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity("A pagination request requires both a pageSize and an offset")
                        .build();
            }

            //Convert pageOffset (i.e. page from index 0) to item offset.
            final long itemOffset = pageOffset * pageSize;

            // Configure default criteria
            final FindMetaCriteria criteria = new FindMetaCriteria();
            criteria.setPageRequest(new PageRequest(itemOffset, pageSize));
            criteria.setSort(new CriteriaFieldSort("Create Time", true, false));

            // Set status to unlocked
            final ExpressionTerm expressionTerm = ExpressionTerm
                    .builder()
                    .field("Status")
                    .condition(Condition.EQUALS)
                    .value("Unlocked")
                    .build();
            final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                    .addTerm(expressionTerm)
                    .build();
            criteria.setExpression(expressionOperator);

            final ResultPage<MetaRow> results = dataMetaService.get().findRows(criteria);
            // TODO @AT I have no idea why we are not just returning a ResultPage
            final SearchResponse response = new SearchResponse(results.getPageResponse(), results.getValues());
            return Response.ok(response).build();
        });
    }

    @POST
    @ApiOperation(
            value = "Search for stream metadata matching the provided expression, " +
                    "starting from the most recent.",
            response = SearchResponse.class)
    public Response search(@QueryParam("pageOffset") Long pageOffset,
                           @QueryParam("pageSize") Integer pageSize,
                           @ApiParam("expression") final ExpressionOperator expression) {
        return securityContext.get().secureResult(() -> {
            // Validate pagination params
            if ((pageSize != null && pageOffset == null)
                    || (pageSize == null && pageOffset != null)) {
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity("A pagination request requires both a pageSize and an offset")
                        .build();
            }

            //Convert pageOffset (i.e. page from index 0) to item offset.
            final long itemOffset = pageOffset * pageSize;

            // Configure default criteria
            FindMetaCriteria criteria = new FindMetaCriteria();
            criteria.setPageRequest(new PageRequest(itemOffset, pageSize));
            criteria.setSort(new CriteriaFieldSort("Create Time", true, false));

            //TODO disable this and have it as a default field
            // Set status to unlocked
//             ExpressionTerm expressionTerm = new ExpressionTerm("Status", Condition.EQUALS, "Unlocked");
//             ExpressionOperator expressionOperator = ExpressionOperator.builder()(true, Op.AND, expressionTerm);
//             criteria.setExpression(expressionOperator);

            criteria.setExpression(expression);

            ResultPage<MetaRow> results = dataMetaService.get().findRows(criteria);
            // TODO @AT I have no idea why we are not just returning a ResultPage
            final SearchResponse response = new SearchResponse(results.getPageResponse(), results.getValues());
            return Response.ok(response)
                    .build();
        });
    }

    @GET
    @Path("/dataSource")
    @ApiOperation(
            value = "Fetch the datasource for stream metadata.",
            response = DataSource.class)
    public Response dataSource() {
        final DataSource dataSource = new DataSource(
                ImmutableList.of(new DocRefField(FeedDoc.DOCUMENT_TYPE, "Feed")));
        return Response
                .ok(dataSource)
                .build();
    }

    @GET
    @Path("/{id}/{anyStatus}/relations")
    @ApiOperation(
            value = "Fetch meta data related to the stream with the supplied id.",
            response = MetaRow.class,
            responseContainer = "List")
    public Response getRelations(@PathParam("id") Long id,
                                 @PathParam("anyStatus") Boolean anyStatus) {
        return securityContext.get().secureResult(() -> {
            final List<MetaRow> rows = dataMetaService.get().findRelatedData(id, anyStatus);
            return Response
                    .ok(rows)
                    .build();
        });
    }

    @GET
    @Path("/{id}")
    @ApiOperation(
            value = "Fetch the stream meta data for the supplied stream ID.",
            response = ResultPage.class)
    public Response search(@PathParam("id") Long id) {
        return securityContext.get().secureResult(() -> {
            // Configure default criteria
            final FindMetaCriteria criteria = FindMetaCriteria.createFromId(id);
            final ResultPage<MetaRow> results = dataMetaService.get().findRows(criteria);
            if (results.size() == 0) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response
                    .ok(results.getFirst())
                    .build();
        });
    }

    @JsonInclude(Include.NON_NULL)
    static class SearchResponse {

        @JsonProperty
        public final PageResponse pageResponse;
        @JsonProperty
        public final List<MetaRow> streamAttributeMaps;

        @JsonCreator
        SearchResponse(@JsonProperty("pageResponse") final PageResponse pageResponse,
                       @JsonProperty("streamAttributeMaps") final List<MetaRow> streamAttributeMaps) {
            this.pageResponse = pageResponse;
            this.streamAttributeMaps = streamAttributeMaps;
        }

        public PageResponse getPageResponse() {
            return pageResponse;
        }

        public List<MetaRow> getStreamAttributeMaps() {
            return streamAttributeMaps;
        }
    }

}
