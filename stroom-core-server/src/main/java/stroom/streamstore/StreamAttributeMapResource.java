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

package stroom.streamstore;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import stroom.data.meta.api.DataMetaService;
import stroom.data.meta.api.DataRow;
import stroom.data.meta.api.FindDataCriteria;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceField;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.IdSet;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.PageResponse;
import stroom.entity.shared.Sort;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.Security;
import stroom.util.HasHealthCheck;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;

import static stroom.datasource.api.v2.DataSourceField.DataSourceFieldType.FIELD;
import static stroom.query.api.v2.ExpressionTerm.Condition;

@Api(
        value = "stream attribute map - /v1",
        description = "Stream Attribute Map API")
@Path("/streamattributemap/v1")
@Produces(MediaType.APPLICATION_JSON)
public class StreamAttributeMapResource implements HasHealthCheck {

    private DataMetaService dataMetaService;
    private Security security;

    @Inject
    public StreamAttributeMapResource(final DataMetaService dataMetaService,
                                      final Security security) {
        this.dataMetaService = dataMetaService;
        this.security = security;
    }

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public Response page(@QueryParam("pageOffset") Long pageOffset,
                         @QueryParam("pageSize") Integer pageSize) {
        return security.secureResult(() -> {
            // Validate pagination params
            if ((pageSize != null && pageOffset == null) || (pageSize == null && pageOffset != null)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("A pagination request requires both a pageSize and an offset").build();
            }

            //Convert pageOffset (i.e. page from index 0) to item offset.
            final long itemOffset = pageOffset * pageSize;

            // Configure default criteria
            FindDataCriteria criteria = new FindDataCriteria();
            criteria.setPageRequest(new PageRequest(itemOffset, pageSize));
            criteria.setSort(new Sort("Create Time", Sort.Direction.DESCENDING, false));

            // Set status to unlocked
            ExpressionTerm expressionTerm = new ExpressionTerm("Status", Condition.EQUALS, "Unlocked");
            ExpressionOperator expressionOperator = new ExpressionOperator(true, ExpressionOperator.Op.AND, expressionTerm);
            criteria.setExpression(expressionOperator);

            BaseResultList<DataRow> results = dataMetaService.findRows(criteria);
            Object response = new Object() {
                public PageResponse pageResponse = results.getPageResponse();
                public List<DataRow> streamAttributeMaps = results.getValues();
            };
            return Response.ok(response).build();
        });
    }

    @POST
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response search(@QueryParam("pageOffset") Long pageOffset,
                           @QueryParam("pageSize") Integer pageSize,
                           final ExpressionOperator expression) {
        return security.secureResult(() -> {
            // Validate pagination params
            if ((pageSize != null && pageOffset == null) || (pageSize == null && pageOffset != null)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("A pagination request requires both a pageSize and an offset").build();
            }

            //Convert pageOffset (i.e. page from index 0) to item offset.
            final long itemOffset = pageOffset * pageSize;

            // Configure default criteria
            FindDataCriteria criteria = new FindDataCriteria();
            criteria.setPageRequest(new PageRequest(itemOffset, pageSize));
            criteria.setSort(new Sort("Create Time", Sort.Direction.DESCENDING, false));

            //TODO disbale this and have it as a default field
            // Set status to unlocked
            // ExpressionTerm expressionTerm = new ExpressionTerm("Status", Condition.EQUALS, "Unlocked");
            // ExpressionOperator expressionOperator = new ExpressionOperator(true, ExpressionOperator.Op.AND, expressionTerm);
            // criteria.getFindStreamCriteria().setExpression(expressionOperator);

            criteria.setExpression(expression);

            BaseResultList<DataRow> results = dataMetaService.findRows(criteria);
            Object response = new Object() {
                public PageResponse pageResponse = results.getPageResponse();
                public List<DataRow> streamAttributeMaps = results.getValues();
            };
            return Response.ok(response).build();
        });
    }


    @GET
    @Path("/dataSource")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dataSource() {
        DataSource dataSource = new DataSource(
                ImmutableList.of(new DataSourceField(
                        FIELD,
                        "feedName",
                        true,
                        Arrays.asList(Condition.EQUALS, Condition.CONTAINS)
                )));

        return Response.ok(dataSource).build();
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(@PathParam("id") Long id) {
        return security.secureResult(() -> {
            // Configure default criteria
            FindDataCriteria criteria = new FindDataCriteria();
            IdSet idSet = new IdSet();
            idSet.add(id);
            criteria.setSelectedIdSet(idSet);

            BaseResultList<DataRow> results = dataMetaService.findRows(criteria);
            if (results.size() == 0) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(results.getFirst()).build();
        });
    }


    @Override
    public HealthCheck.Result getHealth() {
        return null;
    }
}
