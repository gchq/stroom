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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DocRefField;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaRow;
import stroom.meta.api.MetaService;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.api.SecurityContext;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.IdSet;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.Sort;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

import static stroom.query.api.v2.ExpressionTerm.Condition;

@Api(value = "stream attribute map - /v1")
@Path("/streamattributemap" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public class StreamAttributeMapResource implements RestResource {
    private MetaService dataMetaService;
    private SecurityContext securityContext;

    @Inject
    public StreamAttributeMapResource(final MetaService dataMetaService,
                                      final SecurityContext securityContext) {
        this.dataMetaService = dataMetaService;
        this.securityContext = securityContext;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response page(@QueryParam("pageOffset") Long pageOffset,
                         @QueryParam("pageSize") Integer pageSize) {
        return securityContext.secureResult(() -> {
            // Validate pagination params
            if ((pageSize != null && pageOffset == null) || (pageSize == null && pageOffset != null)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("A pagination request requires both a pageSize and an offset").build();
            }

            //Convert pageOffset (i.e. page from index 0) to item offset.
            final long itemOffset = pageOffset * pageSize;

            // Configure default criteria
            FindMetaCriteria criteria = new FindMetaCriteria();
            criteria.setPageRequest(new PageRequest(itemOffset, pageSize));
            criteria.setSort(new Sort("Create Time", Sort.Direction.DESCENDING, false));

            // Set status to unlocked
            ExpressionTerm expressionTerm = new ExpressionTerm("Status", Condition.EQUALS, "Unlocked");
            ExpressionOperator expressionOperator = new ExpressionOperator(true, ExpressionOperator.Op.AND, expressionTerm);
            criteria.setExpression(expressionOperator);

            ResultPage<MetaRow> results = dataMetaService.findRows(criteria);
            Object response = new Object() {
                public PageResponse pageResponse = results.getPageResponse();
                public List<MetaRow> streamAttributeMaps = results.getValues();
            };
            return Response.ok(response).build();
        });
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response search(@QueryParam("pageOffset") Long pageOffset,
                           @QueryParam("pageSize") Integer pageSize,
                           final ExpressionOperator expression) {
        return securityContext.secureResult(() -> {
            // Validate pagination params
            if ((pageSize != null && pageOffset == null) || (pageSize == null && pageOffset != null)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("A pagination request requires both a pageSize and an offset").build();
            }

            //Convert pageOffset (i.e. page from index 0) to item offset.
            final long itemOffset = pageOffset * pageSize;

            // Configure default criteria
            FindMetaCriteria criteria = new FindMetaCriteria();
            criteria.setPageRequest(new PageRequest(itemOffset, pageSize));
            criteria.setSort(new Sort("Create Time", Sort.Direction.DESCENDING, false));

            //TODO disable this and have it as a default field
            // Set status to unlocked
//             ExpressionTerm expressionTerm = new ExpressionTerm("Status", Condition.EQUALS, "Unlocked");
//             ExpressionOperator expressionOperator = new ExpressionOperator(true, ExpressionOperator.Op.AND, expressionTerm);
//             criteria.setExpression(expressionOperator);

            criteria.setExpression(expression);

            ResultPage<MetaRow> results = dataMetaService.findRows(criteria);
            Object response = new Object() {
                public PageResponse pageResponse = results.getPageResponse();
                public List<MetaRow> streamAttributeMaps = results.getValues();
            };
            return Response.ok(response).build();
        });
    }


    @GET
    @Path("/dataSource")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dataSource() {
        final DataSource dataSource = new DataSource(ImmutableList.of(new DocRefField(FeedDoc.DOCUMENT_TYPE, "Feed")));
        return Response.ok(dataSource).build();
    }

    @GET
    @Path("/{id}/{anyStatus}/relations")
    public Response getRelations(@PathParam("id") Long id,
                                 @PathParam("anyStatus") Boolean anyStatus) {
        return securityContext.secureResult(() -> {
            final List<MetaRow> rows = dataMetaService.findRelatedData(id, anyStatus);
            return Response.ok(rows).build();
        });
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(@PathParam("id") Long id) {
        return securityContext.secureResult(() -> {
            // Configure default criteria
            FindMetaCriteria criteria = new FindMetaCriteria();
            IdSet idSet = new IdSet();
            idSet.add(id);
            criteria.setSelectedIdSet(idSet);

            ResultPage<MetaRow> results = dataMetaService.findRows(criteria);
            if (results.size() == 0) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(results.getFirst()).build();
        });
    }
}
